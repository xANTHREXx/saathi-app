# Changelog & Agent Reference

## [Unreleased] - 2026-10-22: More ways to ask for help, and texts that make silence mean something

### What she gets
- **Call 112** on the alert screen and the alert notification (opens the dialer with 112 filled in).
- **"Tell them you added them" sends one text from her SIM**, naming the "Saathi alert" prefix. Contacts
  added later are offered one; nobody is texted twice.
- **Low battery and switched-off texts:** during a trip or a running timer, her contacts get her last location
  when the battery reaches 5% or the phone shuts down. On by default (Settings › Help options).
- **"Check on me" timer** on Home: 10, 20, 30 or 45 minutes, then the usual check-in. "Add 10 min" and
  "Cancel check" work from the lock screen.
- **Arriving ends the trip** when it reaches the saved place it was headed to. An optional arrival text is
  off by default (Settings › Places).
- **Saathi Help** as a long-press shortcut, and as an app-list icon for back-tap gestures that she turns on in
  Settings › Ways to ask for help (off by default: it is the `.HelpLauncher` activity-alias). 5 s to undo.
- **Earphone button ×3** for help, on trips only and off until she turns it on. (A shake for a check-in was
  tried and removed: it clashed with phone makers' shake-for-torch gestures, and bumpy rides could set it off.)

### For coding agents: rules to keep
- A deliberate gesture (Saathi Help, earphone button) calls `AlertManager.raiseAfter(reason, 5 s)`. An unclear
  signal (timer) calls `AlertManager.startCheckIn(event)`, never `raise` directly.
- The extra texts (`batteryText`, `offText`, `arrivedText` in `core/AlertText.kt`) are not alerts. They go
  through `TripService.textContacts` and never set `alertActive`. None may say "safe".
- The timer lives in `TripState.checkAtMs` and is persisted in `RunState.check` so a restarted service fires it.
- New `EventKind`s (`TIMER`) need wording in `ui/Localised.kt` `eventText`.  `SHAKE` is never produced now but stays in the enum so old trip records decode.
- New strings go in `values/` and `values-hi/` (`StringsParityTest`), and in the review sheet
  `tools/translation/hi-review.md`.

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
| `android/app/src/main/java/app/saathi/data/AppUpdateManager.kt` | Android Client | 6-hour check throttling (stamped only when the check succeeds), release vetting (`isValidSha256`, `isTrustedUrl`, `rejectionReason`), streamed download to a `.part` file, SHA-256 verification (`sha256Hex`), FileProvider install intent. |
| `android/app/src/main/java/app/saathi/SaathiApp.kt` | Android Client | Application subclass registering `CrashTracker.install()` and flushing pending crashes/telemetry on cold start. |
| `android/app/src/main/res/xml/filepaths.xml` | Android Client | FileProvider cache path configuration for `updates/` directory. |
| `android/app/src/main/AndroidManifest.xml` | Android Client | Declares `REQUEST_INSTALL_PACKAGES` permission, `SaathiApp`, and `androidx.core.content.FileProvider`. |
| `android/app/src/main/java/app/saathi/ui/UpdateDialog.kt` | Android Client | `UpdatePrompt` (Home-only check) and `UpdateDialog`: changelog, progress, metered-network note, install-permission flow, errors via `ProblemText`. Always dismissable. |
| `android/app/src/main/java/app/saathi/ui/MainActivity.kt` | Android Client | Calls `UpdatePrompt()` inside Home, so nothing is checked or shown over setup. |
| `android/app/src/main/java/app/saathi/ui/SettingsScreen.kt` | Android Client | "App" section: version and a "Check for updates" button (forces a check, opens the same dialog). |
| `android/app/build.gradle.kts` | Android Client | `UPDATE_URL_PREFIX` BuildConfig field; release signing config read from `local.properties`. |

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
   - **The updater never blocks help.** `UpdateDialog` is always dismissable (back, outside tap, "Later"), including when `isMandatory` is true; `isMandatory` only changes the wording ("Important update", "This fixes something important. Please update soon."). There is no mandatory mode and no modal she can't leave. Do not add one.
   - The prompt appears only on Home: never during setup, and never while `TripState.ui` shows `onTrip`, `alertActive`, a pending check-in (`checkInEndsAtMs != null`) or a `practice`. If any of these start while the dialog is open it closes itself. A finished, verified download stays in the cache and is picked up again from Settings > App > Check for updates.
   - Crash saving is strictly non-blocking and wrapped in `runCatching` before delegating to Android's default handler.
3. **Free Tier / Storage Cost**:
   - Do **NOT** store APK files in Supabase Storage. APKs are hosted on GitHub Releases CDN. Supabase Postgres only stores lightweight metadata rows.
   - Postgres rows are automatically purged: `app_events` after 30 days, `app_crashes` after 60 days via `pg_cron`.
   - The 500 MB free database must not be fillable by a stranger. Limits enforced in `app.ts`: request body at most 64,000 characters (else `413 {"error":"too large"}`, not parsed); at most 50 events and 20 crashes kept per request; `exception_type` at most 100 chars, `message` at most 500, `stack_trace` at most 8,000.
   - A batch with nothing valid left returns `200 {"ok":true,"crashes":0,"events":0}` (not 400) so clients do not retry junk forever. The response counts are rows actually stored.
   - Inserts are batched: one `addCrashes(rows)` and one `addEvents(rows)` call per request (skipped when empty).
4. **Update integrity**:
   - `app_releases.apk_sha256` is the lowercase hex SHA-256 of the APK file (`^[0-9a-f]{64}$`, enforced by a check constraint); `apk_url` must start with `https://`. `GET /api/version` returns it as `apkSha256`. The client must verify the downloaded APK against it before launching the installer.
   - **Client checks** (`AppUpdateManager`): a release is ignored (treated as no update, warning logged) unless `apkSha256` is 64 lowercase hex characters and `apkUrl` is `https://` and starts with `BuildConfig.UPDATE_URL_PREFIX` (default `https://github.com/xANTHREXx/saathi-app/releases/download/`; forks set `UPDATE_URL_PREFIX` in `local.properties`). After download the file is hashed (streamed) and compared to `apkSha256`; on mismatch it is deleted, she sees "The download didn't check out, so it wasn't installed. Try again later." and nothing is installed. Only a verified file ever reaches the installer.
   - `Api.latestRelease()` returns `Result<Release?>`: failure means "couldn't check" (offline, HTTP error) and does not stamp `last_check_ms`, so an offline launch doesn't suppress checks for 6 hours.
   - **Install permission:** before launching the installer, if `canRequestPackageInstalls()` is false the app opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for its own package and the dialog says "Allow Saathi to install updates, then come back and tap Install." The verified file is kept; on return (ON_RESUME) she taps Install. Granting this permission can restart the app; Settings > App > Check for updates reopens the dialog and finds the verified file again.
   - **Mobile data:** on a metered network the dialog says "This is a large download (about 100 MB). Use Wi-Fi if you can." The button still works. This needs `ACCESS_NETWORK_STATE` in the manifest.
5. **Release signing**: `assembleRelease` signs with the key named by `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` in `local.properties`. If any of the four is missing the release APK is left unsigned (`app-release-unsigned.apk`), so a wrongly signed APK can't be published by accident. Debug builds are unchanged. Every update must be signed by the same key as the installed app, or Android refuses it.

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

0. **One-time: create the release key.** Keep the file outside the repo and back it up somewhere safe. Losing it means no user can ever update the app.
   ```powershell
   keytool -genkeypair -v -keystore saathi-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias saathi
   ```
   Then add four lines to `android/local.properties` (which is not committed):
   ```properties
   RELEASE_STORE_FILE=C:/path/outside/repo/saathi-release.jks
   RELEASE_STORE_PASSWORD=...
   RELEASE_KEY_ALIAS=saathi
   RELEASE_KEY_PASSWORD=...
   ```
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
   The APK will be generated at `android/app/build/outputs/apk/release/app-release.apk` (signed with the release key from step 0; if it is named `app-release-unsigned.apk`, the `RELEASE_*` lines are missing and the APK must not be published).
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
   - On Home (not during setup, a trip, an alert, a check-in or a practice) she sees `UpdateDialog` with the changelog. She can always leave it with **Later**, back, or a tap outside, even if `is_mandatory` is true (that only changes the wording).
   - Tapping **Update** streams the APK to cache, verifies its SHA-256, then (after the install-permission step if needed) passes it to the Android package installer via `FileProvider`, and updates the app in-place without data loss.
