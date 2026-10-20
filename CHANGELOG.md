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
| `supabase/migrations/20261021000000_updater_and_telemetry.sql` | Backend | Postgres schema: `app_releases` (with `apk_sha256`, https-only `apk_url`), `app_crashes`, `app_events`, indexes, RLS, and `pg_cron` auto-purge. |
| `supabase/functions/api/store.ts` | Backend | Store interface extensions (`latestRelease`, batch `addCrashes`, batch `addEvents`) in `Store`, `MemoryStore`, `PgStore`. |
| `supabase/functions/api/app.ts` | Backend | Route handlers for `GET /api/version` (returns `apkSha256`) and `POST /api/telemetry` (size limit, allowlist, property rules, hour-truncated timestamps, scrubbing). |
| `supabase/functions/api/scrub.ts` | Backend | Pure `scrub(s)`: replaces phone-like digit runs, emails and coordinate pairs in crash text. |
| `supabase/functions/api/api_test.ts`, `scrub_test.ts` | Backend | Deno unit tests for `/version`, `/telemetry` and `scrub()`. |
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
   - The only device identifier is `InstallId.get(ctx)`, a randomly generated UUID v4 reset on app re-installation. The server rejects (400) any `installId` that is not `^[0-9a-f-]{36}$`.
   - **Server-side enforcement** (`POST /api/telemetry`), so a buggy or hostile client cannot defeat it:
     - Event names are an allowlist: `trip_started`, `checkin_triggered`, `checkin_answered`, `checkin_timed_out`, `plate_scanned`, `alert_sent`, `update_prompted`, `update_completed`. Anything else is dropped. Adding a new event means adding it to `EVENT_NAMES` in `app.ts`.
     - Event properties: at most 8 keys; key must match `^[a-z_]{1,32}$`; value must be a boolean, a finite number, or a string (trimmed, cut to 40 chars). Objects, arrays and null are dropped. Never put free text, places or identifiers in a property.
     - **Timestamps are truncated to the hour (UTC)** before storage (`occurredAt`, `crashedAt`). Invalid, more than 1 day in the future, or older than 30 days are replaced by the server's current time (then truncated). We only ever need hour granularity; do not add finer timestamps.
     - Crash `message` and `stackTrace` pass through `scrub()` (`scrub.ts`): runs of 6+ digits (spaces, dashes, leading `+` allowed) become `[number]`, emails become `[email]`, coordinate pairs with 4+ decimals become `[place]`. Stack-frame line numbers (`MainActivity.kt:123`) are kept.
2. **Safety Isolation**:
   - Update prompts are suppressed if a trip is active (`TripState.now.onTrip`) or if an alert is active (`TripState.now.alertActive`).
   - Crash saving is strictly non-blocking and wrapped in `runCatching` before delegating to Android's default handler.
3. **Free Tier / Storage Cost**:
   - Do **NOT** store APK files in Supabase Storage. APKs are hosted on GitHub Releases CDN. Supabase Postgres only stores lightweight metadata rows.
   - Postgres rows are automatically purged: `app_events` after 30 days, `app_crashes` after 60 days via `pg_cron`.
   - The 500 MB free database must not be fillable by a stranger. Limits enforced in `app.ts`: request body at most 64,000 characters (else `413 {"error":"too large"}`, not parsed); at most 50 events and 20 crashes kept per request; `exception_type` at most 100 chars, `message` at most 500, `stack_trace` at most 8,000.
   - A batch with nothing valid left returns `200 {"ok":true,"crashes":0,"events":0}` (not 400) so clients do not retry junk forever. The response counts are rows actually stored.
   - Inserts are batched: one `addCrashes(rows)` and one `addEvents(rows)` call per request (skipped when empty).
4. **Update integrity**:
   - `app_releases.apk_sha256` is the lowercase hex SHA-256 of the APK file (`^[0-9a-f]{64}$`, enforced by a check constraint); `apk_url` must start with `https://`. `GET /api/version` returns it as `apkSha256`. The client must verify the downloaded APK against it before launching the installer.

---

### 4. Milestone Telemetry Reference

Events currently instrumented across the codebase:

| Event Name | File Source | Properties Captured |
|---|---|---|
| `trip_started` | `TripService.kt:startTrip` | `"mode"` (WALK/CAB/AUTO/BUS/METRO), `"auto_detected"` (boolean) |
| `checkin_triggered` | `TripService.kt:onFix` | `"reason"` (DETOUR/LONG_STOP/LATE_ARRIVAL) |
| `plate_scanned` | `PlateCheckActivity.kt` | `"success"` (boolean) |
| `checkin_answered` | `AlertManager.kt` | none |
| `checkin_timed_out` | `AlertManager.kt` | none |
| `alert_sent` | `AlertManager.kt:raise` | `"reason"`, `"contacts_count"`, `"offline"` (boolean) |
| `update_prompted` | `AppUpdateManager.kt` | `"from_version"`, `"to_version"` |
| `update_completed` | `AppUpdateManager.kt` | `"download_time_s"` |

To track a new milestone in any UI or background service:
```kotlin
app.saathi.data.TelemetryManager.track(context, "event_name", mapOf("key" to value))
```
The server only accepts the event names listed above (see section 3); property keys must be `[a-z_]` (max 32 chars), at most 8 per event, values boolean / number / string (max 40 chars). Everything else is silently dropped.

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
   - Copy the direct download URL (must be `https://`).
4. **Compute the APK's SHA-256** (lowercase hex, 64 characters), on the exact file you uploaded:
   ```powershell
   certutil -hashfile saathi.apk SHA256
   ```
   or on Linux/macOS/Git Bash: `sha256sum saathi.apk`. `certutil` may print uppercase; lowercase it before inserting (the database rejects anything not matching `^[0-9a-f]{64}$`).
5. **Insert Metadata in Supabase**:
   Execute via SQL or Supabase Studio (`app_releases` table):
   ```sql
   insert into public.app_releases (version_code, version_name, apk_url, apk_sha256, changelog, is_mandatory)
   values (
     2,
     '0.2',
     'https://github.com/xANTHREXx/saathi-app/releases/download/v0.2/saathi.apk',
     '<64 lowercase hex characters from step 4>',
     'Detour checks are smoother; fixed plate scan on Samsung devices.',
     false
   );
   ```
6. **App Behavior**:
   - Active user devices will fetch the release via `GET /api/version` within 6 hours (or on next launch).
   - The user sees `UpdateDialog` with the changelog.
   - Tapping **Update Now** streams the APK to cache, passes it to the Android package installer via `FileProvider`, and updates the app in-place without data loss.
