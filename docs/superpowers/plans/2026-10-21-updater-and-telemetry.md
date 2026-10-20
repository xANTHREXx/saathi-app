# In-App Updater & Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement client-side in-app self-updating and anonymous crash/usage telemetry for Saathi, backed by Supabase Edge Function endpoints, Postgres tables, and GitHub Releases APK hosting.

**Architecture:** The Android app polls `GET /api/version` on launch to detect newer APK releases hosted on GitHub Releases, downloading and prompting install via `FileProvider`. An uncaught exception handler (`CrashTracker`) and an event queue (`TelemetryManager`) batch and flush diagnostic data to `POST /api/telemetry`, storing rows in Postgres via Supabase Edge Function with auto-purge cron jobs.

**Tech Stack:** Kotlin, Jetpack Compose, OkHttp, kotlinx.serialization, Android `FileProvider`, TypeScript (Deno / Supabase Edge Functions), Supabase Postgres & pg_cron.

**Spec:** [`docs/superpowers/specs/2026-10-21-updater-and-telemetry-design.md`](file:///e:/Second%20brain/Projects/AI_builder/work/saathi/docs/superpowers/specs/2026-10-21-updater-and-telemetry-design.md)

## Global Constraints

- Android `minSdk = 29`, `targetSdk = 35`, Kotlin JVM target 17.
- Zero PII collected: no GPS coordinates, names, phone numbers, or hardware identifiers (e.g. `ANDROID_ID`) in telemetry or crashes; uses a random per-install UUID.
- Free tier only: zero bytes stored in Supabase Storage; APK binary is served from GitHub Releases CDN.
- Safety isolation: updater and telemetry components live in `app.saathi.data` and NEVER block core safety flows (trips, alerts, SOS). Mid-trip or mid-alert, update prompts are deferred.
- No new external Android library dependencies: utilize existing OkHttp 4.12.0, kotlinx.serialization, and Android Jetpack.

---

### Task 1: Supabase Database Migration for Releases & Telemetry

**Files:**
- Create: `supabase/migrations/20261021000000_updater_and_telemetry.sql`

**Interfaces:**
- Produces:
  - Table `public.app_releases` (`version_code` int PK, `version_name` text, `apk_url` text, `changelog` text, `is_mandatory` boolean, `created_at` timestamptz)
  - Table `public.app_crashes` (`id` bigint identity PK, `install_id` text, `version_code` int, `version_name` text, `os_version` int, `device_model` text, `exception_type` text, `message` text, `stack_trace` text, `crashed_at` timestamptz, `received_at` timestamptz)
  - Table `public.app_events` (`id` bigint identity PK, `install_id` text, `version_code` int, `event_name` text, `properties` jsonb, `occurred_at` timestamptz, `received_at` timestamptz)
  - Indexes: `app_events_name_time` on `(event_name, occurred_at)`, `app_crashes_time` on `(crashed_at)`
  - Cron cleanup schedules: `saathi-events-cleanup` (30 days), `saathi-crashes-cleanup` (60 days)
  - RLS enabled on all three tables with access restricted to `service_role`.

- [ ] **Step 1: Write migration SQL file**

Create `supabase/migrations/20261021000000_updater_and_telemetry.sql`:

```sql
-- Saathi: In-App Updater & Telemetry tables

-- 1. App Releases
create table if not exists public.app_releases (
  version_code   integer primary key,
  version_name   text    not null,
  apk_url        text    not null,
  changelog      text    not null default '',
  is_mandatory   boolean not null default false,
  created_at     timestamptz not null default now()
);

alter table public.app_releases enable row level security;
revoke all on public.app_releases from anon, authenticated;
grant select, insert, update, delete on public.app_releases to service_role;

-- 2. App Crashes
create table if not exists public.app_crashes (
  id             bigint generated always as identity primary key,
  install_id     text    not null,
  version_code   integer not null,
  version_name   text    not null,
  os_version     integer not null,
  device_model   text    not null,
  exception_type text    not null,
  message        text,
  stack_trace    text    not null,
  crashed_at     timestamptz not null,
  received_at    timestamptz not null default now()
);

create index if not exists app_crashes_time on public.app_crashes (crashed_at);

alter table public.app_crashes enable row level security;
revoke all on public.app_crashes from anon, authenticated;
grant select, insert, delete on public.app_crashes to service_role;

-- 3. App Events
create table if not exists public.app_events (
  id             bigint generated always as identity primary key,
  install_id     text    not null,
  version_code   integer not null,
  event_name     text    not null,
  properties     jsonb   not null default '{}'::jsonb,
  occurred_at    timestamptz not null,
  received_at    timestamptz not null default now()
);

create index if not exists app_events_name_time on public.app_events (event_name, occurred_at);

alter table public.app_events enable row level security;
revoke all on public.app_events from anon, authenticated;
grant select, insert, delete on public.app_events to service_role;

-- 4. Auto-purge cron jobs (safe if pg_cron is available)
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.unschedule('saathi-events-cleanup');
    perform cron.unschedule('saathi-crashes-cleanup');
    perform cron.schedule('saathi-events-cleanup', '23 3 * * *',
      'delete from public.app_events where received_at < now() - interval ''30 days''');
    perform cron.schedule('saathi-crashes-cleanup', '29 3 * * *',
      'delete from public.app_crashes where received_at < now() - interval ''60 days''');
  end if;
exception when others then
  raise notice 'pg_cron not active; skipping cron registration';
end;
$$;
```

- [ ] **Step 2: Verify SQL syntax**

Inspect file contents and verify table definitions match the design specification.

- [ ] **Step 3: Commit migration**

```bash
git add supabase/migrations/20261021000000_updater_and_telemetry.sql
git commit -m "feat(backend): add releases, crashes, and events schema migration"
```

---

### Task 2: Backend Store Methods for Releases and Telemetry

**Files:**
- Modify: `supabase/functions/api/store.ts`

**Interfaces:**
- Consumes: none
- Produces:
  - `ReleaseRow` interface: `{ version_code: number; version_name: string; apk_url: string; changelog: string; is_mandatory: boolean }`
  - `CrashRow` interface: `{ install_id: string; version_code: number; version_name: string; os_version: number; device_model: string; exception_type: string; message: string | null; stack_trace: string; crashed_at: string }`
  - `EventRow` interface: `{ install_id: string; version_code: number; event_name: string; properties: Record<string, unknown>; occurred_at: string }`
  - `Store` methods: `latestRelease(): Promise<ReleaseRow | null>`, `addCrash(c: CrashRow): Promise<void>`, `addEvent(e: EventRow): Promise<void>`
  - Implementations in both `MemoryStore` and `PgStore`

- [ ] **Step 1: Update `supabase/functions/api/store.ts` with new interfaces and implementations**

In `supabase/functions/api/store.ts`:
1. Export `ReleaseRow`, `CrashRow`, and `EventRow`.
2. Add `latestRelease()`, `addCrash()`, `addEvent()` to `Store` interface.
3. In `MemoryStore`:
   - Add fields `releases: ReleaseRow[] = []`, `crashes: CrashRow[] = []`, `events: EventRow[] = []`.
   - `latestRelease()`: returns latest release sorted by `version_code desc` or null if empty.
   - `addCrash(c)`: pushes to `this.crashes`.
   - `addEvent(e)`: pushes to `this.events`.
4. In `PgStore`:
   - `latestRelease()`: queries `this.db.from("app_releases").select("version_code,version_name,apk_url,changelog,is_mandatory").order("version_code", { ascending: false }).limit(1).maybeSingle()` and unwraps via `must(...)`.
   - `addCrash(c)`: executes `must(await this.db.from("app_crashes").insert(c))`.
   - `addEvent(e)`: executes `must(await this.db.from("app_events").insert(e))`.

- [ ] **Step 2: Commit store updates**

```bash
git add supabase/functions/api/store.ts
git commit -m "feat(backend): implement release and telemetry store operations"
```

---

### Task 3: Backend Edge Function Endpoints (`/version` & `/telemetry`)

**Files:**
- Modify: `supabase/functions/api/app.ts`
- Modify: `supabase/functions/api/api_test.ts`

**Interfaces:**
- Consumes: `store.latestRelease()`, `store.addCrash()`, `store.addEvent()`
- Produces:
  - `GET /api/version` -> `{ "latest": { "versionCode": number, "versionName": string, "apkUrl": string, "changelog": string, "isMandatory": boolean } | null }`
  - `POST /api/telemetry` -> `{ "ok": true, "crashes": number, "events": number }` with validation of `installId`, batch capping at 50, and stack trace truncation to 8000 chars.

- [ ] **Step 1: Write tests in `supabase/functions/api/api_test.ts`**

Add tests for:
1. `GET /api/version` returns `{ latest: null }` when no releases exist.
2. `GET /api/version` returns newest release when rows are present in store.
3. `POST /api/telemetry` validates `installId` is required (returns 400 when missing).
4. `POST /api/telemetry` saves crashes and events, truncating stack trace > 8000 chars, and returns `{ ok: true, crashes: N, events: N }`.
5. `POST /api/telemetry` rejects or caps payloads over 50 items.

- [ ] **Step 2: Implement endpoints in `supabase/functions/api/app.ts`**

In `supabase/functions/api/app.ts`:
Add routes:
```typescript
if (m("GET", /^\/version$/)) {
  const rel = await d.store.latestRelease();
  return json({
    latest: rel ? {
      versionCode: rel.version_code,
      versionName: rel.version_name,
      apkUrl: rel.apk_url,
      changelog: rel.changelog,
      isMandatory: rel.is_mandatory,
    } : null,
  });
}

if (m("POST", /^\/telemetry$/)) {
  const b = await body(req);
  const installId = String(b.installId ?? "").trim().slice(0, 64);
  if (!installId) return json({ error: "missing installId" }, 400);

  const versionCode = Number(b.versionCode) || 0;
  const versionName = String(b.versionName ?? "").slice(0, 32);
  const osVersion = Number(b.osVersion) || 0;
  const deviceModel = String(b.deviceModel ?? "").slice(0, 64);

  const rawCrashes = Array.isArray(b.crashes) ? b.crashes.slice(0, 50) : [];
  const rawEvents = Array.isArray(b.events) ? b.events.slice(0, 50) : [];

  if (rawCrashes.length === 0 && rawEvents.length === 0) {
    return json({ error: "empty telemetry batch" }, 400);
  }

  for (const c of rawCrashes) {
    await d.store.addCrash({
      install_id: installId,
      version_code: versionCode,
      version_name: versionName,
      os_version: osVersion,
      device_model: deviceModel,
      exception_type: String(c.exceptionType ?? "Unknown").slice(0, 100),
      message: c.message ? String(c.message).slice(0, 1000) : null,
      stack_trace: String(c.stackTrace ?? "").slice(0, 8000),
      crashed_at: String(c.crashedAt || new Date(now()).toISOString()),
    });
  }

  for (const e of rawEvents) {
    await d.store.addEvent({
      install_id: installId,
      version_code: versionCode,
      event_name: String(e.name ?? "").slice(0, 64),
      properties: (e.properties && typeof e.properties === "object") ? e.properties as Record<string, unknown> : {},
      occurred_at: String(e.occurredAt || new Date(now()).toISOString()),
    });
  }

  return json({ ok: true, crashes: rawCrashes.length, events: rawEvents.length });
}
```

- [ ] **Step 3: Commit backend endpoints**

```bash
git add supabase/functions/api/app.ts supabase/functions/api/api_test.ts
git commit -m "feat(backend): add /version and /telemetry Edge Function endpoints"
```

---

### Task 4: Android Install ID Provider

**Files:**
- Create: `android/app/src/main/java/app/saathi/data/InstallId.kt`
- Create: `android/app/src/test/java/app/saathi/data/InstallIdTest.kt`

**Interfaces:**
- Produces: `InstallId.get(ctx: Context): String` -> generates and persists a random UUID v4 in SharedPreferences `saathi_telemetry`.
- Consumes: Android `Context`

- [ ] **Step 1: Write the failing unit test**

Create `android/app/src/test/java/app/saathi/data/InstallIdTest.kt`:
```kotlin
package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class InstallIdTest {
    @Test
    fun generatesValidUuidFormat() {
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val generated = InstallId.newUuid()
        assertTrue("Generated ID should match UUID pattern: $generated", uuidRegex.matches(generated))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.InstallIdTest"`
Expected: FAIL with "Unresolved reference: InstallId"

- [ ] **Step 3: Implement `InstallId.kt`**

Create `android/app/src/main/java/app/saathi/data/InstallId.kt`:
```kotlin
package app.saathi.data

import android.content.Context
import java.util.UUID

object InstallId {
    private const val PREFS_NAME = "saathi_telemetry"
    private const val KEY_INSTALL_ID = "install_id"

    @Volatile
    private var cachedId: String? = null

    internal fun newUuid(): String = UUID.randomUUID().toString()

    fun get(ctx: Context): String {
        cachedId?.let { return it }
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (existing != null) {
            cachedId = existing
            return existing
        }
        val generated = newUuid()
        prefs.edit().putString(KEY_INSTALL_ID, generated).apply()
        cachedId = generated
        return generated
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.InstallIdTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/saathi/data/InstallId.kt android/app/src/test/java/app/saathi/data/InstallIdTest.kt
git commit -m "feat(android): add anonymous InstallId generator"
```

---

### Task 5: `Api.kt` Integration for Version Check & Telemetry

**Files:**
- Modify: `android/app/src/main/java/app/saathi/data/Api.kt`
- Create: `android/app/src/test/java/app/saathi/data/ApiSerializationTest.kt`

**Interfaces:**
- Produces:
  - `Api.Release(val versionCode: Int, val versionName: String, val apkUrl: String, val changelog: String, val isMandatory: Boolean)`
  - `Api.latestRelease(): Api.Release?`
  - `Api.postTelemetry(bodyJson: String): Boolean`
  - `Api.parseRelease(jsonStr: String): Api.Release?` (helper for testability and parsing)

- [ ] **Step 1: Write unit tests for JSON parsing and telemetry payload structure**

Create `android/app/src/test/java/app/saathi/data/ApiSerializationTest.kt`:
```kotlin
package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSerializationTest {
    @Test
    fun parseReleaseReturnsNullWhenLatestIsNull() {
        val json = """{"latest": null}"""
        assertNull(Api.parseRelease(json))
    }

    @Test
    fun parseReleaseParsesValidReleaseJson() {
        val json = """
        {
            "latest": {
                "versionCode": 2,
                "versionName": "0.2",
                "apkUrl": "https://github.com/example/saathi.apk",
                "changelog": "Fixes and improvements",
                "isMandatory": true
            }
        }
        """.trimIndent()
        val rel = Api.parseRelease(json)
        assertNotNull(rel)
        assertEquals(2, rel?.versionCode)
        assertEquals("0.2", rel?.versionName)
        assertEquals("https://github.com/example/saathi.apk", rel?.apkUrl)
        assertEquals("Fixes and improvements", rel?.changelog)
        assertEquals(true, rel?.isMandatory)
    }

    @Test
    fun formatTelemetryBatchBuildsValidJson() {
        val body = Api.buildTelemetryJson(
            installId = "test-uuid-123",
            versionCode = 1,
            versionName = "0.1",
            osVersion = 34,
            deviceModel = "Pixel 7",
            crashesJson = """[{"exceptionType":"NullPointerException","message":"oops","stackTrace":"at Foo.kt:10","crashedAt":"2026-10-21T00:00:00Z"}]""",
            eventsJson = """[{"name":"trip_started","properties":{"mode":"uber"},"occurredAt":"2026-10-21T00:00:00Z"}]"""
        )
        assertTrue(body.contains("\"installId\":\"test-uuid-123\""))
        assertTrue(body.contains("\"crashes\":["))
        assertTrue(body.contains("\"events\":["))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.ApiSerializationTest"`
Expected: FAIL with "Unresolved reference: parseRelease"

- [ ] **Step 3: Add `Release` model and methods to `Api.kt`**

In `android/app/src/main/java/app/saathi/data/Api.kt`:
Add:
```kotlin
data class Release(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val isMandatory: Boolean
)

fun parseRelease(jsonStr: String): Release? = runCatching {
    val o = Json.parseToJsonElement(jsonStr).jsonObject
    val latest = o["latest"]?.jsonObject ?: return null
    Release(
        versionCode = latest["versionCode"]?.jsonPrimitive?.intOrNull ?: 0,
        versionName = latest["versionName"]?.jsonPrimitive?.content ?: "",
        apkUrl = latest["apkUrl"]?.jsonPrimitive?.content ?: "",
        changelog = latest["changelog"]?.jsonPrimitive?.content ?: "",
        isMandatory = latest["isMandatory"]?.jsonPrimitive?.booleanOrNull ?: false
    )
}.getOrNull()

fun latestRelease(): Release? = runCatching {
    val req = Request.Builder().url("${BuildConfig.API_URL}/version").build()
    http.newCall(req).execute().use { r ->
        if (r.isSuccessful) parseRelease(r.body!!.string()) else null
    }
}.getOrNull()

fun buildTelemetryJson(
    installId: String,
    versionCode: Int,
    versionName: String,
    osVersion: Int,
    deviceModel: String,
    crashesJson: String = "[]",
    eventsJson: String = "[]"
): String {
    return """{"installId":${q(installId)},"versionCode":$versionCode,"versionName":${q(versionName)},"osVersion":$osVersion,"deviceModel":${q(deviceModel)},"crashes":$crashesJson,"events":$eventsJson}"""
}

fun postTelemetry(bodyJson: String): Boolean = runCatching {
    val req = Request.Builder().url("${BuildConfig.API_URL}/telemetry").post(bodyJson.toRequestBody(jsonType)).build()
    http.newCall(req).execute().use { it.isSuccessful }
}.getOrDefault(false)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.ApiSerializationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/saathi/data/Api.kt android/app/src/test/java/app/saathi/data/ApiSerializationTest.kt
git commit -m "feat(android): add latestRelease and postTelemetry to Api"
```

---

### Task 6: `CrashTracker` Component (Exception Handling & Crash Delivery)

**Files:**
- Create: `android/app/src/main/java/app/saathi/data/CrashTracker.kt`
- Create: `android/app/src/test/java/app/saathi/data/CrashTrackerTest.kt`

**Interfaces:**
- Produces:
  - `CrashTracker.install(ctx: Context)`: Registers default uncaught exception handler
  - `CrashTracker.flushPending(ctx: Context)`: Sends cached crash files to `/api/telemetry` and cleans up files
  - `CrashTracker.formatCrashRecord(throwable: Throwable, maxTraceLength: Int = 8000): CrashRecord`
  - `CrashRecord(val exceptionType: String, val message: String?, val stackTrace: String, val crashedAt: String)`

- [ ] **Step 1: Write unit test for crash formatting and truncation**

Create `android/app/src/test/java/app/saathi/data/CrashTrackerTest.kt`:
```kotlin
package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashTrackerTest {
    @Test
    fun formatsExceptionCorrectly() {
        val ex = IllegalStateException("Test exception message")
        val record = CrashTracker.formatCrashRecord(ex)
        assertEquals("IllegalStateException", record.exceptionType)
        assertEquals("Test exception message", record.message)
        assertTrue(record.stackTrace.contains("IllegalStateException"))
        assertTrue(record.crashedAt.isNotBlank())
    }

    @Test
    fun truncatesOversizedStackTrace() {
        val longString = "A".repeat(10000)
        val truncated = CrashTracker.truncateStackTrace(longString, 8000)
        assertEquals(8000, truncated.length)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.CrashTrackerTest"`
Expected: FAIL with "Unresolved reference: CrashTracker"

- [ ] **Step 3: Implement `CrashTracker.kt`**

Create `android/app/src/main/java/app/saathi/data/CrashTracker.kt`:
```kotlin
package app.saathi.data

import android.content.Context
import android.os.Build
import app.saathi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CrashRecord(
    val exceptionType: String,
    val message: String?,
    val stackTrace: String,
    val crashedAt: String
)

object CrashTracker {
    private const val CRASH_DIR = "crashes"
    private const val MAX_TRACE_LEN = 8000

    fun formatCrashRecord(t: Throwable): CrashRecord {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val trace = t.stackTraceToString()
        return CrashRecord(
            exceptionType = t.javaClass.simpleName.ifBlank { t.javaClass.name },
            message = t.message,
            stackTrace = truncateStackTrace(trace, MAX_TRACE_LEN),
            crashedAt = isoFormat.format(Date())
        )
    }

    fun truncateStackTrace(trace: String, maxLen: Int = MAX_TRACE_LEN): String {
        return if (trace.length > maxLen) trace.substring(0, maxLen) else trace
    }

    fun install(ctx: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                saveCrashSync(ctx, throwable)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashSync(ctx: Context, t: Throwable) {
        val record = formatCrashRecord(t)
        val dir = File(ctx.filesDir, CRASH_DIR).apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.json")
        val json = JSONObject().apply {
            put("exceptionType", record.exceptionType)
            put("message", record.message ?: JSONObject.NULL)
            put("stackTrace", record.stackTrace)
            put("crashedAt", record.crashedAt)
        }
        file.writeText(json.toString())
    }

    fun flushPending(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val dir = File(ctx.filesDir, CRASH_DIR)
                if (!dir.exists()) return@launch
                val files = dir.listFiles { f -> f.extension == "json" } ?: return@launch
                if (files.isEmpty()) return@launch

                val installId = InstallId.get(ctx)
                val crashesArray = StringBuilder("[")
                files.forEachIndexed { index, file ->
                    if (index > 0) crashesArray.append(",")
                    crashesArray.append(file.readText())
                }
                crashesArray.append("]")

                val payload = Api.buildTelemetryJson(
                    installId = installId,
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    osVersion = Build.VERSION.SDK_INT,
                    deviceModel = Build.MODEL,
                    crashesJson = crashesArray.toString(),
                    eventsJson = "[]"
                )

                if (Api.postTelemetry(payload)) {
                    files.forEach { it.delete() }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.CrashTrackerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/saathi/data/CrashTracker.kt android/app/src/test/java/app/saathi/data/CrashTrackerTest.kt
git commit -m "feat(android): implement CrashTracker uncaught exception handler and flush"
```

---

### Task 7: `TelemetryManager` Component (Milestone Tracking & Event Queue)

**Files:**
- Create: `android/app/src/main/java/app/saathi/data/TelemetryManager.kt`
- Create: `android/app/src/test/java/app/saathi/data/TelemetryManagerTest.kt`

**Interfaces:**
- Produces:
  - `TelemetryManager.track(ctx: Context, eventName: String, properties: Map<String, Any> = emptyMap())`
  - `TelemetryManager.flush(ctx: Context)`
  - `TelemetryEvent(val name: String, val properties: Map<String, Any>, val occurredAt: String)`
  - Queue cap: in-memory / persistent disk buffer capped at 200 events, dropping oldest when exceeded.
  - Flush batch cap: up to 10 events immediately or periodically on flush triggers.

- [ ] **Step 1: Write unit tests for batching and queue capping**

Create `android/app/src/test/java/app/saathi/data/TelemetryManagerTest.kt`:
```kotlin
package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryManagerTest {
    @Test
    fun formatsEventRecordWithIsoTimestamp() {
        val event = TelemetryManager.createEventRecord("trip_started", mapOf("mode" to "auto"))
        assertEquals("trip_started", event.name)
        assertEquals("auto", event.properties["mode"])
        assertTrue(event.occurredAt.endsWith("Z"))
    }

    @Test
    fun capsQueueToMaxCapacityDroppingOldest() {
        val list = (1..250).map { i ->
            TelemetryEvent("event_$i", emptyMap(), "2026-10-21T00:00:00Z")
        }
        val capped = TelemetryManager.enforceCap(list, maxCapacity = 200)
        assertEquals(200, capped.size)
        assertEquals("event_51", capped.first().name)
        assertEquals("event_250", capped.last().name)
    }

    @Test
    fun serializesEventsToJsonArray() {
        val events = listOf(
            TelemetryEvent("test_event", mapOf("count" to 42, "flag" to true), "2026-10-21T00:00:00Z")
        )
        val json = TelemetryManager.serializeEvents(events)
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"name\":\"test_event\""))
        assertTrue(json.contains("\"count\":42"))
        assertTrue(json.contains("\"flag\":true"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.TelemetryManagerTest"`
Expected: FAIL with "Unresolved reference: TelemetryManager"

- [ ] **Step 3: Implement `TelemetryManager.kt`**

Create `android/app/src/main/java/app/saathi/data/TelemetryManager.kt`:
```kotlin
package app.saathi.data

import android.content.Context
import android.os.Build
import app.saathi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TelemetryEvent(
    val name: String,
    val properties: Map<String, Any>,
    val occurredAt: String
)

object TelemetryManager {
    private const val QUEUE_FILE = "telemetry_queue.json"
    private const val MAX_CAPACITY = 200
    private const val BATCH_THRESHOLD = 10

    private val inMemoryBuffer = mutableListOf<TelemetryEvent>()
    private val lock = Any()

    fun createEventRecord(name: String, properties: Map<String, Any>): TelemetryEvent {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return TelemetryEvent(name, properties, isoFormat.format(Date()))
    }

    fun enforceCap(events: List<TelemetryEvent>, maxCapacity: Int = MAX_CAPACITY): List<TelemetryEvent> {
        return if (events.size > maxCapacity) events.takeLast(maxCapacity) else events
    }

    fun serializeEvents(events: List<TelemetryEvent>): String {
        val array = JSONArray()
        for (e in events) {
            val obj = JSONObject().apply {
                put("name", e.name)
                put("occurredAt", e.occurredAt)
                val props = JSONObject()
                e.properties.forEach { (k, v) -> props.put(k, v) }
                put("properties", props)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun track(ctx: Context, name: String, properties: Map<String, Any> = emptyMap()) {
        val record = createEventRecord(name, properties)
        var shouldFlush = false
        synchronized(lock) {
            inMemoryBuffer.add(record)
            if (inMemoryBuffer.size >= BATCH_THRESHOLD) {
                shouldFlush = true
            }
        }
        if (shouldFlush) {
            flush(ctx)
        }
    }

    fun flush(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val toSend: List<TelemetryEvent>
            synchronized(lock) {
                if (inMemoryBuffer.isEmpty()) {
                    loadQueueFromDisk(ctx)
                }
                if (inMemoryBuffer.isEmpty()) return@launch
                val capped = enforceCap(inMemoryBuffer, MAX_CAPACITY)
                inMemoryBuffer.clear()
                toSend = capped.toList()
            }

            val installId = InstallId.get(ctx)
            val eventsJson = serializeEvents(toSend)
            val payload = Api.buildTelemetryJson(
                installId = installId,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                osVersion = Build.VERSION.SDK_INT,
                deviceModel = Build.MODEL,
                crashesJson = "[]",
                eventsJson = eventsJson
            )

            val success = Api.postTelemetry(payload)
            if (!success) {
                saveQueueToDisk(ctx, toSend)
            } else {
                clearDiskQueue(ctx)
            }
        }
    }

    private fun loadQueueFromDisk(ctx: Context) {
        val file = File(ctx.filesDir, QUEUE_FILE)
        if (!file.exists()) return
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val occurredAt = obj.getString("occurredAt")
                val propsObj = obj.optJSONObject("properties") ?: JSONObject()
                val props = mutableMapOf<String, Any>()
                propsObj.keys().forEach { k -> props[k] = propsObj.get(k) }
                inMemoryBuffer.add(TelemetryEvent(name, props, occurredAt))
            }
        }
    }

    private fun saveQueueToDisk(ctx: Context, failedBatch: List<TelemetryEvent>) {
        runCatching {
            synchronized(lock) {
                inMemoryBuffer.addAll(0, failedBatch)
                val capped = enforceCap(inMemoryBuffer, MAX_CAPACITY)
                inMemoryBuffer.clear()
                inMemoryBuffer.addAll(capped)
                val file = File(ctx.filesDir, QUEUE_FILE)
                file.writeText(serializeEvents(capped))
            }
        }
    }

    private fun clearDiskQueue(ctx: Context) {
        runCatching {
            val file = File(ctx.filesDir, QUEUE_FILE)
            if (file.exists()) file.delete()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.TelemetryManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/saathi/data/TelemetryManager.kt android/app/src/test/java/app/saathi/data/TelemetryManagerTest.kt
git commit -m "feat(android): implement TelemetryManager with event batching and disk fallback"
```

---

### Task 8: `SaathiApp`, Manifest & `FileProvider` Configuration

**Files:**
- Create: `android/app/src/main/java/app/saathi/SaathiApp.kt`
- Create: `android/app/src/main/res/xml/filepaths.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `CrashTracker.install(ctx)`, `CrashTracker.flushPending(ctx)`, `TelemetryManager.flush(ctx)`
- Produces:
  - `SaathiApp : Application` class
  - `FileProvider` configuration exposing `cache-path updates/`
  - Manifest permission `android.permission.REQUEST_INSTALL_PACKAGES`

- [ ] **Step 1: Create `filepaths.xml`**

Create `android/app/src/main/res/xml/filepaths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="updates" path="updates/" />
</paths>
```

- [ ] **Step 2: Create `SaathiApp.kt`**

Create `android/app/src/main/java/app/saathi/SaathiApp.kt`:
```kotlin
package app.saathi

import android.app.Application
import app.saathi.data.CrashTracker
import app.saathi.data.TelemetryManager

class SaathiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register uncaught crash handler before anything else runs
        CrashTracker.install(this)
        // Flush any crashes recorded from previous run
        CrashTracker.flushPending(this)
        // Flush pending telemetry events if any
        TelemetryManager.flush(this)
    }
}
```

- [ ] **Step 3: Update `AndroidManifest.xml`**

In `android/app/src/main/AndroidManifest.xml`:
1. Add `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`
2. Add `android:name=".SaathiApp"` to the `<application>` tag.
3. Add inside `<application>`:
```xml
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

- [ ] **Step 4: Verify build compilation**

Run: `cmd.exe /c ".\gradlew.bat compileDebugSources"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/saathi/SaathiApp.kt android/app/src/main/res/xml/filepaths.xml android/app/src/main/AndroidManifest.xml
git commit -m "feat(android): add SaathiApp application class, FileProvider, and install permission"
```

---

### Task 9: `AppUpdateManager` Component

**Files:**
- Create: `android/app/src/main/java/app/saathi/data/AppUpdateManager.kt`
- Create: `android/app/src/test/java/app/saathi/data/AppUpdateManagerTest.kt`

**Interfaces:**
- Produces:
  - `UpdateCheckResult(val isAvailable: Boolean, val release: Api.Release?)`
  - `DownloadState`: `Idle`, `Checking`, `Downloading(progress: Float)`, `Ready(apkFile: File)`, `Failed(message: String)`
  - `AppUpdateManager.isUpdateAvailable(currentVersionCode: Int, release: Api.Release?): Boolean`
  - `AppUpdateManager.downloadApk(ctx: Context, apkUrl: String, onProgress: (Float) -> Unit): File?`
  - `AppUpdateManager.createInstallIntent(ctx: Context, apkFile: File): Intent`

- [ ] **Step 1: Write unit tests for version comparison and install intent creation**

Create `android/app/src/test/java/app/saathi/data/AppUpdateManagerTest.kt`:
```kotlin
package app.saathi.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun updateIsAvailableWhenReleaseVersionIsGreater() {
        val release = Api.Release(
            versionCode = 5,
            versionName = "0.5",
            apkUrl = "https://example.com/saathi.apk",
            changelog = "New features",
            isMandatory = false
        )
        assertTrue(AppUpdateManager.isUpdateAvailable(currentVersionCode = 4, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 5, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 6, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 4, release = null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.AppUpdateManagerTest"`
Expected: FAIL with "Unresolved reference: AppUpdateManager"

- [ ] **Step 3: Implement `AppUpdateManager.kt`**

Create `android/app/src/main/java/app/saathi/data/AppUpdateManager.kt`:
```kotlin
package app.saathi.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.saathi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    object Checking : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Ready(val file: File) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

object AppUpdateManager {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isUpdateAvailable(currentVersionCode: Int, release: Api.Release?): Boolean {
        if (release == null) return false
        return release.versionCode > currentVersionCode
    }

    suspend fun checkUpdate(ctx: Context, force: Boolean = false): Api.Release? = withContext(Dispatchers.IO) {
        val prefs = ctx.getSharedPreferences("saathi_updater", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check_ms", 0L)
        val now = System.currentTimeMillis()
        val sixHoursMs = 6 * 60 * 60 * 1000L

        if (!force && (now - lastCheck < sixHoursMs)) {
            return@withContext null
        }

        val release = Api.latestRelease()
        prefs.edit().putLong("last_check_ms", now).apply()

        if (isUpdateAvailable(BuildConfig.VERSION_CODE, release)) {
            TelemetryManager.track(ctx, "update_prompted", mapOf(
                "from_version" to BuildConfig.VERSION_CODE,
                "to_version" to (release?.versionCode ?: 0)
            ))
            release
        } else {
            null
        }
    }

    suspend fun downloadApk(
        ctx: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val updatesDir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "saathi-update.apk")
        val startTime = System.currentTimeMillis()

        runCatching {
            val req = Request.Builder().url(apkUrl).build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    apkFile.delete()
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }

            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            TelemetryManager.track(ctx, "update_completed", mapOf(
                "download_time_s" to elapsedSec
            ))
            apkFile
        }.getOrElse {
            apkFile.delete()
            null
        }
    }

    fun createInstallIntent(ctx: Context, apkFile: File): Intent {
        val authority = "${ctx.packageName}.provider"
        val uri = FileProvider.getUriForFile(ctx, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest --tests app.saathi.data.AppUpdateManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/saathi/data/AppUpdateManager.kt android/app/src/test/java/app/saathi/data/AppUpdateManagerTest.kt
git commit -m "feat(android): implement AppUpdateManager download and install orchestration"
```

---

### Task 10: In-App Update UI & `MainActivity` Integration

**Files:**
- Create: `android/app/src/main/java/app/saathi/ui/UpdateDialog.kt`
- Modify: `android/app/src/main/java/app/saathi/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `AppUpdateManager`, `TripService.isRunning`
- Produces:
  - Compose `UpdateDialog(release: Api.Release, onDismiss: () -> Unit)` with progress indicator, retry on failure, and launch install intent.
  - Integration in `MainActivity.onCreate()` / Compose root.

- [ ] **Step 1: Create `UpdateDialog.kt`**

Create `android/app/src/main/java/app/saathi/ui/UpdateDialog.kt`:
```kotlin
package app.saathi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.saathi.data.Api
import app.saathi.data.AppUpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    release: Api.Release,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!release.isMandatory && !isDownloading) onDismiss()
        },
        title = {
            Text(text = "Update Available (v${release.versionName})", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (release.changelog.isNotBlank()) {
                    Text(text = release.changelog, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (isDownloading) {
                    Text(text = "Downloading update... ${(downloadProgress * 100).toInt()}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (downloadFailed) {
                    Text(
                        text = "Download failed. Please check internet connection and try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = {
                        isDownloading = true
                        downloadFailed = false
                        downloadProgress = 0f
                        scope.launch {
                            val apkFile = AppUpdateManager.downloadApk(ctx, release.apkUrl) { progress ->
                                downloadProgress = progress
                            }
                            isDownloading = false
                            if (apkFile != null) {
                                val intent = AppUpdateManager.createInstallIntent(ctx, apkFile)
                                ctx.startActivity(intent)
                                onDismiss()
                            } else {
                                downloadFailed = true
                            }
                        }
                    }
                ) {
                    Text(if (downloadFailed) "Retry" else "Update Now")
                }
            }
        },
        dismissButton = {
            if (!release.isMandatory && !isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}
```

- [ ] **Step 2: Wire Update Check into `MainActivity.kt`**

In `android/app/src/main/java/app/saathi/ui/MainActivity.kt`:
1. Check if `TripService.isRunning` is active; if false, trigger `AppUpdateManager.checkUpdate(this)`.
2. Hold `availableUpdate: Api.Release?` in Compose state.
3. If non-null, display `UpdateDialog(release = availableUpdate, onDismiss = { availableUpdate = null })`.

- [ ] **Step 3: Verify build compilation**

Run: `cmd.exe /c ".\gradlew.bat compileDebugSources"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/app/saathi/ui/UpdateDialog.kt android/app/src/main/java/app/saathi/ui/MainActivity.kt
git commit -m "feat(android): add UpdateDialog Compose UI and wire into MainActivity"
```

---

### Task 11: Milestone Telemetry Instrumentation Across App Flows

**Files:**
- Modify: `android/app/src/main/java/app/saathi/trip/TripService.kt`
- Modify: `android/app/src/main/java/app/saathi/ui/PlateCheckActivity.kt`
- Modify: `android/app/src/main/java/app/saathi/ui/CheckInActivity.kt`
- Modify: `android/app/src/main/java/app/saathi/alert/AlertSender.kt`

**Interfaces:**
- Consumes: `TelemetryManager.track(ctx, eventName, properties)`
- Produces: Milestone telemetry events emitted from key user actions:
  - `trip_started`: `mode`, `auto_detected`
  - `plate_scanned`: `success`
  - `checkin_triggered`: `reason`
  - `checkin_responded`: `response`
  - `alert_sent`: `contacts_count`, `offline`

- [ ] **Step 1: Instrument `trip_started` and `checkin_triggered` in `TripService.kt`**

In `TripService.kt`:
- When starting trip (in `startTrip` or service start command):
  `TelemetryManager.track(this, "trip_started", mapOf("mode" to tripMode, "auto_detected" to autoDetected))`
- When a check-in is triggered (e.g. detour or long stop):
  `TelemetryManager.track(this, "checkin_triggered", mapOf("reason" to reason))`

- [ ] **Step 2: Instrument `plate_scanned` in `PlateCheckActivity.kt`**

In `PlateCheckActivity.kt`:
- When a plate recognition finishes:
  `TelemetryManager.track(this, "plate_scanned", mapOf("success" to (detectedPlate != null)))`

- [ ] **Step 3: Instrument `checkin_responded` in `CheckInActivity.kt`**

In `CheckInActivity.kt`:
- On safe confirmation or alert escalation:
  `TelemetryManager.track(this, "checkin_responded", mapOf("response" to if (isSafe) "safe" else "alert"))`

- [ ] **Step 4: Instrument `alert_sent` in `AlertSender.kt`**

In `AlertSender.kt`:
- When alert broadcast completes:
  `TelemetryManager.track(ctx, "alert_sent", mapOf("contacts_count" to contactsCount, "offline" to isOffline))`

- [ ] **Step 5: Run all unit tests and verify build**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest"`
Expected: BUILD SUCCESSFUL, all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/app/saathi/trip/TripService.kt android/app/src/main/java/app/saathi/ui/PlateCheckActivity.kt android/app/src/main/java/app/saathi/ui/CheckInActivity.kt android/app/src/main/java/app/saathi/alert/AlertSender.kt
git commit -m "feat(android): instrument safety milestone telemetry across trip and alert flows"
```

---

### Task 12: Full System Build & Packaging Verification

**Files:**
- Test verification across all created and modified components.

- [ ] **Step 1: Execute all unit tests**

Run: `cmd.exe /c ".\gradlew.bat testDebugUnitTest"`
Expected: All tests PASS with zero failures.

- [ ] **Step 2: Build debug APK**

Run: `cmd.exe /c ".\gradlew.bat assembleDebug"`
Expected: BUILD SUCCESSFUL, APK generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Commit all remaining cleanups**

```bash
git status
git commit -m "chore: complete in-app updater and telemetry subsystem implementation"
```
