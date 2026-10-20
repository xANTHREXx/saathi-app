# Changelog & Agent Reference

## [Unreleased] - 2026-10-21: In-App Updater & Diagnostic Telemetry Subsystems

### Overview for Coding Agents

This section details the newly implemented **In-App Updater** and **Diagnostic Telemetry** subsystems.
Future agents working on this codebase should consult this guide to understand how update distribution and diagnostic data collection are wired, what constraints must be maintained, and how to push updates.

---

### 1. Architectural Highlights

```
┌────────────────────────────────────────────────────────────────────────┐
│ Android Client (app.saathi.data)                                       │
│                                                                        │
│ ┌─────────────────────┐  ┌──────────────────┐  ┌─────────────────────┐ │
│ │  AppUpdateManager   │  │   CrashTracker   │  │  TelemetryManager   │ │
│ │ (checks /version,   │  │ (uncaught ex     │  │ (in-memory buffer,  │ │
│ │  downloads APK,     │  │  handler, saves  │  │  disk fallback,     │ │
│ │  launches installer)│  │  crashes on disk)│  │  batch flushes)     │ │
│ └──────────┬──────────┘  └────────┬─────────┘  └──────────┬──────────┘ │
└────────────┼──────────────────────┼───────────────────────┼────────────┘
             │                      │                       │
             │ GET /api/version     │ POST /api/telemetry   │ POST /api/telemetry
             ▼                      ▼                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Supabase Edge Function (supabase/functions/api)                        │
│ ┌─────────────────────┐  ┌──────────────────┐  ┌─────────────────────┐ │
│ │    app_releases     │  │   app_crashes    │  │     app_events      │ │
│ │ (Release metadata)  │  │ (Crash logs)     │  │ (Milestone metrics) │ │
│ └─────────────────────┘  └──────────────────┘  └─────────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ apk_url redirects to:
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ GitHub Releases CDN (Unlimited bandwidth, zero Supabase storage cost)   │
│ https://github.com/xANTHREXx/saathi-app/releases/download/.../saathi.apk│
└────────────────────────────────────────────────────────────────────────┘
```

---

### 2. File Map & Responsibilities

| File Path | Subsystem | Responsibility |
|---|---|---|
| `supabase/migrations/20261021000000_updater_and_telemetry.sql` | Backend | Postgres schema: `app_releases`, `app_crashes`, `app_events`, indexes, RLS, and `pg_cron` auto-purge. |
| `supabase/functions/api/store.ts` | Backend | Store interface extensions (`latestRelease`, `addCrash`, `addEvent`) in `Store`, `MemoryStore`, `PgStore`. |
| `supabase/functions/api/app.ts` | Backend | Route handlers for `GET /api/version` and `POST /api/telemetry`. |
| `supabase/functions/api/api_test.ts` | Backend | Deno unit tests for `/version` and `/telemetry` endpoints. |
| `android/app/src/main/java/app/saathi/data/InstallId.kt` | Android Client | Generates & caches a persistent random UUID v4 per installation (zero hardware ID / PII). |
| `android/app/src/main/java/app/saathi/data/Api.kt` | Android Client | Added `Api.Release` model, `parseRelease()`, `latestRelease()`, `buildTelemetryJson()`, `postTelemetry()`. |
| `android/app/src/main/java/app/saathi/data/CrashTracker.kt` | Android Client | Default uncaught exception handler, saves crash to `filesDir/crashes/<timestamp>.json`, flushes on next launch. |
| `android/app/src/main/java/app/saathi/data/TelemetryManager.kt` | Android Client | Event queue with 10-event flush threshold, 200-event disk queue fallback (`telemetry_queue.json`). |
| `android/app/src/main/java/app/saathi/data/AppUpdateManager.kt` | Android Client | 6-hour check throttling, OkHttp APK download streaming with progress callback, FileProvider install intent. |
| `android/app/src/main/java/app/saathi/SaathiApp.kt` | Android Client | Application subclass registering `CrashTracker.install()` and flushing pending crashes/telemetry on cold start. |
| `android/app/src/main/res/xml/filepaths.xml` | Android Client | FileProvider cache path configuration for `updates/` directory. |
| `android/app/src/main/AndroidManifest.xml` | Android Client | Declares `REQUEST_INSTALL_PACKAGES` permission, `SaathiApp`, and `androidx.core.content.FileProvider`. |
| `android/app/src/main/java/app/saathi/ui/UpdateDialog.kt` | Android Client | Compose dialog rendering changelog, download progress bar, retry state, and mandatory mode. |
| `android/app/src/main/java/app/saathi/ui/MainActivity.kt` | Android Client | Triggers update check on launch (deferred if mid-trip or mid-alert). |

---

### 3. Safety & Privacy Constraints (MUST PRESERVE)

1. **Zero PII**:
   - Telemetry must **NEVER** include GPS coordinates, addresses, phone numbers, contact names, raw plate numbers, or `ANDROID_ID`.
   - The only device identifier is `InstallId.get(ctx)`, a randomly generated UUID v4 reset on app re-installation.
2. **Safety Isolation**:
   - Update prompts are suppressed if a trip is active (`TripState.now.onTrip`) or if an alert is active (`TripState.now.alertActive`).
   - Crash saving is strictly non-blocking and wrapped in `runCatching` before delegating to Android's default handler.
3. **Free Tier / Storage Cost**:
   - Do **NOT** store APK files in Supabase Storage. APKs are hosted on GitHub Releases CDN. Supabase Postgres only stores lightweight metadata rows.
   - Postgres rows are automatically purged: `app_events` after 30 days, `app_crashes` after 60 days via `pg_cron`.

---

### 4. Milestone Telemetry Reference

Events currently instrumented across the codebase:

| Event Name | File Source | Properties Captured |
|---|---|---|
| `trip_started` | `TripService.kt:startTrip` | `"mode"` (WALK/CAB/AUTO/BUS/METRO), `"auto_detected"` (boolean) |
| `checkin_triggered` | `TripService.kt:onFix` | `"reason"` (DETOUR/LONG_STOP/LATE_ARRIVAL) |
| `plate_scanned` | `PlateCheckActivity.kt` | `"success"` (boolean) |
| `checkin_responded` | `AlertManager.kt` | `"response"` ("safe" / "timeout_alert") |
| `alert_sent` | `AlertManager.kt:raise` | `"reason"`, `"contacts_count"`, `"offline"` (boolean) |
| `update_prompted` | `AppUpdateManager.kt` | `"from_version"`, `"to_version"` |
| `update_completed` | `AppUpdateManager.kt` | `"download_time_s"` |

To track a new milestone in any UI or background service:
```kotlin
app.saathi.data.TelemetryManager.track(context, "event_name", mapOf("key" to value))
```

---

### 5. How to Release an App Update

When publishing a new version of Saathi:

1. **Bump Version** in `android/app/build.gradle.kts`:
   ```kotlin
   versionCode = 2
   versionName = "0.2"
   ```
2. **Build Release APK**:
   ```powershell
   cd android
   .\gradlew.bat assembleRelease
   ```
   The APK will be generated at `android/app/build/outputs/apk/release/app-release.apk` (signed with the keystore).
3. **Upload to GitHub Releases**:
   - Create a tag (e.g. `v0.2`) on GitHub.
   - Attach the built APK (e.g. `saathi-v0.2.apk`).
   - Copy the direct download URL.
4. **Insert Metadata in Supabase**:
   Execute via SQL or Supabase Studio (`app_releases` table):
   ```sql
   insert into public.app_releases (version_code, version_name, apk_url, changelog, is_mandatory)
   values (
     2,
     '0.2',
     'https://github.com/xANTHREXx/saathi-app/releases/download/v0.2/saathi.apk',
     'Detour checks are smoother; fixed plate scan on Samsung devices.',
     false
   );
   ```
5. **App Behavior**:
   - Active user devices will fetch the release via `GET /api/version` within 6 hours (or on next launch).
   - The user sees `UpdateDialog` with the changelog.
   - Tapping **Update Now** streams the APK to cache, passes it to the Android package installer via `FileProvider`, and updates the app in-place without data loss.
