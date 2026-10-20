# Design: In-App Updater & Telemetry for Saathi

**Date:** 2026-10-21  
**Status:** Draft  
**Scope:** New subsystem — adds self-updating capability and anonymous crash/usage telemetry  
**Constraints:** Free tier only (Supabase free plan, GitHub free); no billing account; zero PII collected

---

## 1. Problem

Saathi is sideloaded via direct APK (not on Google Play). Once teammates and friends install it,
there is no mechanism to:

1. **Push updates** — bug fixes and new features require manually re-sharing a new APK file.
2. **Know what's breaking** — crashes on real devices are invisible; the only feedback channel is
   "it crashed" over WhatsApp.
3. **Understand usage** — which flows are used, which are abandoned, whether the companion falls
   back to Gemini too often — all unknown.

## 2. Solution Overview

Two new subsystems, both routed through the existing Supabase Edge Function (`/api`) and stored in
Supabase Postgres. Neither subsystem touches Supabase Storage (the 90 MB APK is hosted on GitHub
Releases for free, unlimited-bandwidth downloads).

```
┌─────────────────────────────────────────────────────────────────────┐
│  Android App                                                        │
│                                                                     │
│  ┌──────────────────┐   ┌──────────────────┐   ┌────────────────┐  │
│  │  AppUpdateManager│   │  CrashTracker    │   │TelemetryManager│  │
│  │  (data package)  │   │  (data package)  │   │ (data package) │  │
│  └────────┬─────────┘   └────────┬─────────┘   └───────┬────────┘  │
│           │                      │                      │           │
└───────────┼──────────────────────┼──────────────────────┼───────────┘
            │                      │                      │
            │ GET /api/version     │ POST /api/telemetry  │ POST /api/telemetry
            │                      │ (type: crash)        │ (type: events)
            ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Supabase Edge Function (/api)                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ app_releases │  │ app_crashes  │  │ app_events   │              │
│  │ (Postgres)   │  │ (Postgres)   │  │ (Postgres)   │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
            │
            │ apk_url points to:
            ▼
┌─────────────────────────────────────────────────────────────────────┐
│  GitHub Releases CDN (free, unlimited bandwidth)                    │
│  github.com/xANTHREXx/saathi-app/releases/download/vX.Y/saathi.apk│
└─────────────────────────────────────────────────────────────────────┘
```

## 3. Database Schema

New Supabase migration: `20261021000000_updater_and_telemetry.sql`

### 3.1 `app_releases`

Stores version metadata. You insert a row from the Supabase dashboard (or a future CLI script)
when publishing a new version. The app reads the latest row to decide whether to prompt an update.

```sql
create table public.app_releases (
  version_code   integer primary key,
  version_name   text    not null,            -- e.g. '0.2'
  apk_url        text    not null check (apk_url like 'https://%'), -- GitHub Releases download URL
  apk_sha256     text    not null check (apk_sha256 ~ '^[0-9a-f]{64}$'), -- lowercase hex SHA-256 of the APK file
  changelog      text    not null default '', -- shown in the update dialog
  is_mandatory   boolean not null default false,
  created_at     timestamptz not null default now()
);

alter table public.app_releases enable row level security;
revoke all on public.app_releases from anon, authenticated;
grant select on public.app_releases to service_role;
grant insert, update, delete on public.app_releases to service_role;
```

### 3.2 `app_crashes`

Stores uncaught exception reports. One row per crash.

```sql
create table public.app_crashes (
  id             bigint generated always as identity primary key,
  install_id     text    not null,            -- random UUID per install, not tied to identity
  version_code   integer not null,
  version_name   text    not null,
  os_version     integer not null,            -- Build.VERSION.SDK_INT
  device_model   text    not null,            -- Build.MODEL
  exception_type text    not null,            -- e.g. 'NullPointerException'
  message        text,
  stack_trace    text    not null,
  crashed_at     timestamptz not null,
  received_at    timestamptz not null default now()
);

alter table public.app_crashes enable row level security;
revoke all on public.app_crashes from anon, authenticated;
grant select, insert, delete on public.app_crashes to service_role;
```

### 3.3 `app_events`

Stores anonymous milestone events for usage analytics.

```sql
create table public.app_events (
  id             bigint generated always as identity primary key,
  install_id     text    not null,
  version_code   integer not null,
  event_name     text    not null,            -- e.g. 'trip_started', 'checkin_triggered'
  properties     jsonb   not null default '{}'::jsonb,
  occurred_at    timestamptz not null,
  received_at    timestamptz not null default now()
);

create index app_events_name_time on public.app_events (event_name, occurred_at);
create index app_crashes_time     on public.app_crashes (crashed_at);

alter table public.app_events enable row level security;
revoke all on public.app_events from anon, authenticated;
grant select, insert, delete on public.app_events to service_role;
```

### 3.4 Auto-Purge Cron

Prevents unbounded growth. Follows the same pattern as Saathi's existing `saathi-live-cleanup`
and `saathi-rides-cleanup` cron jobs.

```sql
-- inside a do-block guarded by `exists (select 1 from pg_extension where extname = 'pg_cron')`
-- and an `exception when others` for the no-pg_cron case; unschedule only jobs that exist
-- (cron.unschedule raises for unknown jobs), so re-running the migration stays safe.
if exists (select 1 from cron.job where jobname = 'saathi-events-cleanup') then
  perform cron.unschedule('saathi-events-cleanup');
end if;
if exists (select 1 from cron.job where jobname = 'saathi-crashes-cleanup') then
  perform cron.unschedule('saathi-crashes-cleanup');
end if;
perform cron.schedule('saathi-events-cleanup', '23 3 * * *',
  'delete from public.app_events where received_at < now() - interval ''30 days''');
perform cron.schedule('saathi-crashes-cleanup', '29 3 * * *',
  'delete from public.app_crashes where received_at < now() - interval ''60 days''');
```

## 4. Edge Function Endpoints

Added to the existing router in `supabase/functions/api/app.ts`. Both endpoints follow the
existing pattern: service-role Postgres client, JSON in/out, CORS headers, try/catch with 500.

### 4.1 `GET /api/version`

Returns the latest release row. The Android app calls this on launch to check for updates.

**Response** (200):
```json
{
  "latest": {
    "versionCode": 2,
    "versionName": "0.2",
    "apkUrl": "https://github.com/xANTHREXx/saathi-app/releases/download/v0.2/saathi.apk",
    "apkSha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    "changelog": "Detour checks are smoother; fixed plate scan on Samsung.",
    "isMandatory": false
  }
}
```

`apkSha256` is the lowercase hex SHA-256 of the APK. The client verifies the downloaded file
against it before launching the installer.

If no releases exist yet, returns `{ "latest": null }`.

**Implementation:** `store.latestRelease()` → single query:
```sql
select * from app_releases order by version_code desc limit 1
```

### 4.2 `POST /api/telemetry`

Receives a batch of crashes and events in a single request. The Android app batches
locally and flushes periodically.

**Request body:**
```json
{
  "installId": "a1b2c3d4-...",
  "versionCode": 1,
  "versionName": "0.1",
  "crashes": [
    {
      "exceptionType": "NullPointerException",
      "message": "...",
      "stackTrace": "...",
      "crashedAt": "2026-10-21T18:30:00Z"
    }
  ],
  "events": [
    {
      "name": "trip_started",
      "properties": { "mode": "uber", "auto_detected": true },
      "occurredAt": "2026-10-21T18:25:00Z"
    }
  ]
}
```

**Response** (200): `{ "ok": true, "crashes": 1, "events": 1 }`

**Validation and sanitising** (all server-side; the database is free-tier and the data must not
let anyone reconstruct where or when someone travels):
- The raw body is read as text first. Longer than 64,000 characters: `413 {"error":"too large"}`,
  not parsed. Invalid JSON, or JSON that is not an object: `400`.
- `installId` must match `^[0-9a-f-]{36}$` (a UUID), else `400`.
- `crashes` and `events` are optional arrays. At most 20 crashes and 50 events are kept.
- Events whose `name` is not in the allowlist are dropped: `trip_started`, `checkin_triggered`,
  `checkin_answered`, `checkin_timed_out`, `plate_scanned`, `alert_sent`, `update_prompted`,
  `update_completed`.
- Event properties: at most 8 keys; key must match `^[a-z_]{1,32}$`; value must be a boolean,
  a finite number, or a string (trimmed, cut to 40 chars). Anything else is dropped.
- Crashes: `exceptionType` at most 100 chars, `message` at most 500, `stackTrace` at most 8,000.
  `message` and `stackTrace` are passed through `scrub()` (`scrub.ts`): runs of 6+ digits (spaces,
  dashes and a leading `+` allowed) become `[number]`, emails `[email]`, coordinate pairs with
  4+ decimals `[place]`. Stack-frame line numbers such as `MainActivity.kt:123` are kept.
- Timestamps (`occurredAt`, `crashedAt`) are parsed as dates. Invalid, more than 1 day in the
  future, or older than 30 days: replaced by the server's current time. The result is truncated
  to the start of the hour (UTC) and stored as an ISO string. Hour granularity is all we keep.
- If nothing valid remains the response is `200 {"ok":true,"crashes":0,"events":0}` (not 400), so
  clients do not retry junk forever. Counts in the response are rows actually stored.

**Implementation:** one batched insert per table via `store.addCrashes(rows)` /
`store.addEvents(rows)`; each call is skipped when its array is empty.

## 5. Store Interface Changes

New methods added to the `Store` interface in `store.ts`:

```typescript
// In the Store interface:
latestRelease(): Promise<ReleaseRow | null>;
addCrashes(rows: CrashRow[]): Promise<void>; // single insert call in PgStore
addEvents(rows: EventRow[]): Promise<void>;  // single insert call in PgStore

// Types:
interface ReleaseRow {
  version_code: number;
  version_name: string;
  apk_url: string;
  apk_sha256: string;
  changelog: string;
  is_mandatory: boolean;
}
interface CrashRow {
  install_id: string;
  version_code: number;
  version_name: string;
  os_version: number;
  device_model: string;
  exception_type: string;
  message: string | null;
  stack_trace: string;
  crashed_at: string; // ISO timestamp, truncated to the hour (UTC)
}
interface EventRow {
  install_id: string;
  version_code: number;
  event_name: string;
  properties: Record<string, unknown>;
  occurred_at: string; // ISO timestamp, truncated to the hour (UTC)
}
```

Both `MemoryStore` and `PgStore` implement these. `MemoryStore` pushes to in-memory arrays
(for tests); `PgStore` inserts via the Supabase client.

## 6. Android Client: In-App Updater

### 6.1 `AppUpdateManager` (new file: `data/AppUpdateManager.kt`)

Responsibilities: check for updates, show UI, download APK, trigger install.

**Version check:**
- Called from `MainActivity.onCreate()` in a `lifecycleScope.launch(Dispatchers.IO)`.
- Calls `GET /api/version` using the existing `Api` object's HTTP client and URL pattern.
- Compares `response.latest.versionCode > BuildConfig.VERSION_CODE`.
- If the device is mid-trip (`TripService.isRunning`), the check result is saved but the
  prompt is deferred until the trip ends.
- Check is throttled to once per 6 hours via a `last_update_check` timestamp in SharedPreferences.

**Update dialog (Compose):**
- Non-mandatory: `AlertDialog` with title "Update Available (v{name})", changelog text,
  and two buttons: **[Update Now]** / **[Later]**.
- Mandatory (`is_mandatory == true`): The dialog is not dismissible. The app shows a
  full-screen message explaining why an update is required.

**APK download:**
- Uses OkHttp (already a dependency) to stream the APK from `apkUrl` (GitHub Releases CDN)
  into `context.cacheDir/updates/saathi-update.apk`.
- Progress is reported to the Compose UI via a `StateFlow<DownloadState>` where
  `DownloadState` is `Idle | Checking | Downloading(progress: Float) | Ready | Failed(message)`.
- On failure: shows a **[Retry]** button. The partial file is deleted.

**Triggering the system installer:**
- A `FileProvider` is declared in `AndroidManifest.xml` pointing to a `filepaths.xml` resource
  that exposes the `cache-path` `updates/` directory.
- The download completion launches:
  ```kotlin
  val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", apkFile)
  val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  ctx.startActivity(intent)
  ```
- Requires `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`
  in `AndroidManifest.xml`.
- Because the new APK is signed with the same debug keystore, Android performs an in-place
  update without data loss.

### 6.2 Manifest & Resource Changes

**`AndroidManifest.xml`:** Add permission and `FileProvider`:
```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/filepaths" />
</provider>
```

**`res/xml/filepaths.xml`** (new file):
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="updates" path="updates/" />
</paths>
```

## 7. Android Client: Crash & Telemetry

### 7.1 `CrashTracker` (new file: `data/CrashTracker.kt`)

**Registration:** Called once in `SaathiApp.onCreate()` (new `Application` subclass).

```kotlin
object CrashTracker {
    fun install(ctx: Context) {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrash(ctx, throwable)
            default?.uncaughtException(thread, throwable)
        }
    }
}
```

**Crash persistence:** On crash, synchronously writes a JSON file to
`context.filesDir/crashes/<timestamp>.json` containing:
- `install_id` (from `InstallId.get(ctx)`)
- `version_code`, `version_name` (from `BuildConfig`)
- `os_version` (`Build.VERSION.SDK_INT`), `device_model` (`Build.MODEL`)
- `exception_type` (throwable class name), `message`, `stack_trace` (full `stackTraceToString()`,
  truncated to 8,000 chars)
- `crashed_at` (ISO 8601 timestamp)

**Delivery:** On next app launch (from `SaathiApp.onCreate()`), `CrashTracker.flushPending(ctx)`
scans the `crashes/` directory, reads each file, `POST`s to `/api/telemetry`, and deletes the
file on a 200 response.

### 7.2 `TelemetryManager` (new file: `data/TelemetryManager.kt`)

**Usage:** Call `TelemetryManager.track(ctx, "event_name", properties)` at key points in the
existing code. Fire-and-forget; never blocks the caller.

**Key events to instrument** (the server only accepts this allowlist; other names are dropped):

| Event | Where instrumented | Properties |
|-------|-------------------|------------|
| `trip_started` | `TripService.startTrip()` | `mode`, `app` (Uber/Ola/Rapido/manual), `auto_detected` |
| `plate_scanned` | `PlateCheckActivity` result | `success`, `duration_ms` |
| `checkin_triggered` | `TripService` watcher callback | `reason` (detour/long_stop/late_arrival) |
| `checkin_answered` | `CheckInActivity` dismiss | none |
| `checkin_timed_out` | `CheckInActivity` / `AlertManager` timeout | none |
| `alert_sent` | `AlertSender` | `contacts_count`, `offline` |
| `update_prompted` | `AppUpdateManager` | `from_version`, `to_version` |
| `update_completed` | `AppUpdateManager` download done | `to_version`, `download_time_s` |

**Batching:**
- Events are queued in an in-memory `MutableList`.
- Flushed when: (a) the buffer reaches 10 events, (b) 5 minutes have elapsed since last flush,
  or (c) the app moves to the background (`Lifecycle.Event.ON_STOP`).
- On flush, the batch is `POST`ed to `/api/telemetry`. On failure, events are appended to
  `context.filesDir/telemetry_queue.json` and retried on the next flush cycle.
- The local queue file is capped at 200 events (~50 KB). Oldest events are dropped if the cap
  is exceeded.

### 7.3 `InstallId` (new file: `data/InstallId.kt`)

Generates a random UUID on first launch and persists it in SharedPreferences. This is the only
identifier sent with telemetry. It is:
- Not tied to any Google account, phone number, device ID, or ANDROID_ID.
- Reset if the user reinstalls the app.
- Used only for grouping crash reports from the same install.

```kotlin
object InstallId {
    private const val KEY = "install_id"
    fun get(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("saathi_telemetry", Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY, it).apply()
        }
    }
}
```

## 8. Safety-First Isolation

Both subsystems are strictly additive and never interfere with Saathi's core safety functions.

| Scenario | Behavior |
|----------|----------|
| Supabase is down / unreachable | Version check silently returns "up to date"; events queue locally |
| APK download fails mid-stream | Progress dialog shows error + **Retry** button; partial file deleted |
| Crash reporter itself throws | Caught in try/catch; delegates to system handler for clean termination |
| User is mid-trip or mid-alert | Update prompt is **never shown**; telemetry flushes are deferred |
| Device is offline | Crashes saved to disk; events buffered; both delivered on next connectivity |
| Telemetry POST times out | Silently retried on next flush cycle; no user-visible error |

The updater and telemetry code are in the `data` package, separate from `core`, `alert`,
`trigger`, and `trip`. No existing file in those packages imports the new classes.

## 9. Privacy Summary

| Data point | Collected? | Notes |
|-----------|-----------|-------|
| GPS / route coordinates | ❌ | Never sent via telemetry |
| Phone number / contacts | ❌ | |
| License plate text | ❌ | |
| Audio recordings | ❌ | |
| User name / identity | ❌ | |
| ANDROID_ID / device ID | ❌ | Telemetry uses its own random UUID |
| Install ID (random UUID) | ✅ | Not tied to any account; reset on reinstall |
| Device model + Android version | ✅ | For debugging device-specific crashes |
| Stack traces | ✅ | Code-level crash info only |
| Event names + timestamps | ✅ | e.g. `"trip_started"`; no location data attached; timestamps truncated to the hour (UTC) server-side |
| Crash message / stack trace text | ✅ | Phone-like numbers, emails and coordinate pairs scrubbed server-side |

## 10. Your Workflow: How to Push an Update

1. **Increment version** in `android/app/build.gradle.kts`:
   ```kotlin
   versionCode = 2
   versionName = "0.2"
   ```
2. **Build**: `gradlew assembleRelease`
3. **Upload APK** to GitHub Releases: create tag `v0.2`, attach the APK file.
4. **Hash the APK**: `certutil -hashfile saathi.apk SHA256` (Windows) or `sha256sum saathi.apk`.
   Lowercase the result (64 hex characters).
5. **Insert a row** in Supabase Studio → Table Editor → `app_releases`:
   ```
   version_code: 2
   version_name: "0.2"
   apk_url: "https://github.com/xANTHREXx/saathi-app/releases/download/v0.2/saathi.apk"
   apk_sha256: "<lowercase hex from step 4>"
   changelog: "Detour checks are smoother; fixed plate scan on Samsung."
   is_mandatory: false
   ```
6. **Done.** Every running app will detect the update within 6 hours (or on next launch) and
   prompt the user.

To **force-update** (e.g. a critical bug): set `is_mandatory = true` on the release row. The
app will show a non-dismissible update screen.

To **roll back**: delete the bad release row and insert one pointing to the previous APK URL.
All devices will stop seeing the update prompt.

## 11. Files Changed

### Backend (Supabase)
| File | Change |
|------|--------|
| `supabase/migrations/20261021000000_updater_and_telemetry.sql` | **New.** Tables, indexes, RLS, cron. |
| `supabase/functions/api/store.ts` | Add `Store` interface methods + `PgStore`/`MemoryStore` impls |
| `supabase/functions/api/app.ts` | Add `GET /version` and `POST /telemetry` route handlers |
| `supabase/functions/api/scrub.ts` | **New.** `scrub()` for crash text (numbers, emails, coordinates) |

### Android
| File | Change |
|------|--------|
| `app/src/main/java/app/saathi/data/AppUpdateManager.kt` | **New.** Version check, download, install intent. |
| `app/src/main/java/app/saathi/data/CrashTracker.kt` | **New.** Uncaught exception handler + flush-on-launch. |
| `app/src/main/java/app/saathi/data/TelemetryManager.kt` | **New.** Event buffering, batching, offline queue. |
| `app/src/main/java/app/saathi/data/InstallId.kt` | **New.** Random UUID per install. |
| `app/src/main/java/app/saathi/SaathiApp.kt` | **New.** `Application` subclass; registers crash handler. |
| `app/src/main/AndroidManifest.xml` | Add `REQUEST_INSTALL_PACKAGES`, `FileProvider`, `SaathiApp`. |
| `app/src/main/res/xml/filepaths.xml` | **New.** FileProvider paths for cache/updates/. |
| `app/src/main/java/app/saathi/ui/MainActivity.kt` | Call `AppUpdateManager.check()` on launch. |
| `app/src/main/java/app/saathi/trip/TripService.kt` | Add `TelemetryManager.track()` at trip milestones. |
| `app/src/main/java/app/saathi/ui/PlateCheckActivity.kt` | Add `TelemetryManager.track()` on scan result. |
| `app/src/main/java/app/saathi/ui/CheckInActivity.kt` | Add `TelemetryManager.track()` on response. |
| `app/src/main/java/app/saathi/alert/` | Add `TelemetryManager.track()` on alert sent. |
| `app/build.gradle.kts` | No new dependencies needed (OkHttp and kotlinx-serialization already present). |

## 12. Testing Strategy

- **Unit tests:** `CrashTracker` write/read/flush cycle; `TelemetryManager` batching logic,
  queue cap, offline persistence; `AppUpdateManager` version comparison; `InstallId` generation
  and persistence.
- **Integration tests:** Mock Edge Function responses for version check (newer, same, 500,
  timeout). Mock telemetry POST (success, failure, retry).
- **Store tests:** `MemoryStore` implementations for `latestRelease()`, `addCrashes()`, `addEvents()`.
- **Manual verification:** Trigger a deliberate crash on a test device, relaunch, confirm the
  crash row appears in Supabase Studio. Publish a test release row, confirm the update dialog
  appears.

## 13. Cost

| Resource | Usage | Free tier limit |
|----------|-------|-----------------|
| Supabase Database | ~50 KB/month (telemetry rows, auto-purged) | 500 MB |
| Supabase Edge Function | +2 routes, negligible invocations | 500K invocations/month |
| GitHub Releases | APK hosting + downloads | Unlimited |
| New Android dependencies | None | N/A |
