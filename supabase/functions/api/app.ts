import { randomToken, type Hmac } from "./crypto.ts";
import { accountKey, caseKey, plateKey, toPoint, vehicleSummary, WINDOW } from "./community.ts";
import { applyUpdate, isLive, newShare, publicState, validToken } from "./live.ts";
import { companionTurn, type Gen } from "./ai.ts";
import type { PlaceHit } from "./places.ts";
import type { Route } from "./route.ts";
import { seedRows } from "./seed.ts";
import { scrub } from "./scrub.ts";
import type { Store } from "./store.ts";

export interface Deps {
  store: Store;
  hmac: Hmac;
  gen: Gen;
  aiConfigured: boolean;
  route: (body: Record<string, unknown>) => Promise<Route>;
  places: (q: string, near: { lat: number; lng: number } | null) => Promise<PlaceHit[]>;
  routesConfigured: boolean;
  livePageUrl: string;
  sample: boolean;
  now?: () => number;
}

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-saathi-device",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
const gone = () => new Response(null, { status: 410, headers: CORS });

async function body(req: Request): Promise<Record<string, unknown>> {
  try {
    const b = await req.json();
    return b && typeof b === "object" ? b : {};
  } catch {
    return {};
  }
}

// --- telemetry validation: anonymous, coarse, bounded

const TELEMETRY_MAX_BODY = 64_000;
const MAX_EVENTS = 50, MAX_CRASHES = 20, MAX_PROPS = 8;
const HOUR = 3_600_000, DAY = 86_400_000;
const INSTALL_ID = /^[0-9a-f-]{36}$/;
const PROP_KEY = /^[a-z_]{1,32}$/;
const EVENT_NAMES = new Set([
  "trip_started", "checkin_triggered", "checkin_answered", "checkin_timed_out",
  "plate_scanned", "alert_sent", "update_prompted", "update_completed",
]);

const isObj = (v: unknown): v is Record<string, unknown> => !!v && typeof v === "object" && !Array.isArray(v);
/** Cut, scrub, cut again: the first cut bounds the work scrub does, the second keeps the stored size within the limit. */
const clean = (v: unknown, max: number) => scrub(String(v).slice(0, max)).slice(0, max);
const int32 = (v: unknown) => {
  const n = Math.trunc(Number(v));
  return Number.isFinite(n) ? Math.min(Math.max(n, 0), 2_147_483_647) : 0;
};

/** Hour-granularity UTC time. Invalid, >1 day ahead or >30 days old falls back to `nowMs`. */
function hourOf(v: unknown, nowMs: number): string {
  let t = typeof v === "string" || typeof v === "number" ? new Date(v).getTime() : NaN;
  if (!Number.isFinite(t) || t > nowMs + DAY || t < nowMs - 30 * DAY) t = nowMs;
  return new Date(Math.floor(t / HOUR) * HOUR).toISOString();
}

function cleanProps(v: unknown): Record<string, boolean | number | string> {
  const out: Record<string, boolean | number | string> = {};
  if (!isObj(v)) return out;
  let n = 0;
  for (const [k, val] of Object.entries(v)) {
    if (n >= MAX_PROPS) break;
    if (!PROP_KEY.test(k) || k === "__proto__") continue;
    if (typeof val === "boolean" || (typeof val === "number" && Number.isFinite(val))) out[k] = val;
    else if (typeof val === "string") out[k] = val.trim().slice(0, 40);
    else continue;
    n++;
  }
  return out;
}

/** The whole API as one handler. Paths match the old Express server, so the app keeps its calls. */
export function makeHandler(d: Deps) {
  const now = d.now ?? Date.now;
  let seeded: Promise<void> | null = null;
  const ensureSeed = () =>
    (seeded ??= (async () => {
      if (!d.sample || (await d.store.hasSample())) return;
      const { points, rides } = await seedRows(d.hmac);
      for (const p of points) await d.store.addPoint(p);
      for (const r of rides) await d.store.addRide(r);
    })().catch((e) => { seeded = null; throw e; }));

  return async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    const url = new URL(req.url);
    // Supabase passes /api/<path>; `supabase functions serve` and plain Deno may pass /functions/v1/api/<path> or /<path>.
    const path = url.pathname.replace(/^(\/functions\/v1)?\/api(?=\/|$)/, "") || "/";
    const device = req.headers.get("x-saathi-device")?.slice(0, 64) || "";
    const m = (method: string, re: RegExp) => (req.method === method ? re.exec(path) : null);
    let hit: RegExpExecArray | null;

    try {
      if (m("GET", /^\/health$/)) {
        // Touches the database too, so the keep-alive ping counts as project activity.
        await d.store.hasSample();
        return json({ ok: true, ai: d.aiConfigured, routes: d.routesConfigured, sample: d.sample });
      }

      // --- live location: open only while an alert is active
      if (m("POST", /^\/live$/)) {
        const token = randomToken();
        await d.store.putLive(newShare(token, await body(req), now()));
        const sep = d.livePageUrl.includes("?") ? "&" : "?";
        return json({ token, url: `${d.livePageUrl}${sep}t=${token}` });
      }
      if ((hit = m("POST", /^\/live\/([^/]+)$/))) {
        if (!validToken(hit[1])) return gone();
        const s = await d.store.getLive(hit[1]);
        if (!isLive(s, now())) return gone();
        const next = applyUpdate(s, await body(req), now());
        if (!next) return json({ error: "bad location" }, 400);
        await d.store.putLive(next);
        return json({ ok: true });
      }
      if ((hit = m("GET", /^\/live\/([^/]+)\/state$/))) {
        if (!validToken(hit[1])) return gone();
        const s = await d.store.getLive(hit[1]);
        return isLive(s, now()) ? json(publicState(s)) : gone();
      }

      // --- community: anonymous, rounded, consented, deletable
      if (m("POST", /^\/community\/point$/)) {
        if (!device) return json({ error: "missing device" }, 400);
        const p = await toPoint(d.hmac, await body(req), device);
        if (!p) return json({ error: "bad point" }, 400);
        await d.store.addPoint(p);
        return json({ ok: true });
      }
      if (m("POST", /^\/community\/ride$/)) {
        if (!device) return json({ error: "missing device" }, 400);
        const b = await body(req);
        const key = await plateKey(d.hmac, b.plate);
        const t = Number(b.t);
        if (!key || !Number.isFinite(t)) return json({ error: "bad ride" }, 400);
        await d.store.addRide({ plate_key: key, account: await accountKey(d.hmac, device), t: Math.floor(t), case_key: await caseKey(d.hmac, device, b.case), sample: false });
        return json({ ok: true });
      }
      if (m("GET", /^\/community\/points$/)) {
        await ensureSeed();
        return json({ sample: d.sample, points: await d.store.points(d.sample) });
      }
      if (m("GET", /^\/community\/vehicle$/)) {
        await ensureSeed();
        const key = await plateKey(d.hmac, url.searchParams.get("plate"));
        if (!key) return json({ rides: 0, reports: 0, flagged: false, sample: d.sample });
        const v = await d.store.vehicle(key, Math.floor(now() / 1000) - WINDOW, d.sample);
        return json({ ...vehicleSummary(v.reports, v.rides), sample: d.sample });
      }
      if (m("DELETE", /^\/community\/mine$/)) {
        const ck = await caseKey(d.hmac, device, url.searchParams.get("case"));
        if (!device || !ck) return json({ error: "missing device or case" }, 400);
        return json({ deleted: await d.store.deleteMine(await accountKey(d.hmac, device), ck) });
      }

      // --- companion, route and places
      if (m("POST", /^\/companion\/turn$/)) {
        const b = await body(req);
        return json(await companionTurn({
          text: String(b.text ?? "").slice(0, 2000),
          lang: String(b.lang ?? "en-IN").slice(0, 10),
          history: Array.isArray(b.history) ? b.history.slice(-6).map((h) => ({ role: String(h?.role ?? ""), text: String(h?.text ?? "").slice(0, 500) })) : [],
        }, d.gen));
      }
      if (m("POST", /^\/route$/)) return json(await d.route(await body(req)));
      if (m("GET", /^\/places$/)) {
        const q = url.searchParams.get("q") ?? "";
        const lat = url.searchParams.get("lat"), lng = url.searchParams.get("lng");
        const near = lat && lng && Number.isFinite(Number(lat)) && Number.isFinite(Number(lng)) ? { lat: Number(lat), lng: Number(lng) } : null;
        if (q.trim().length < 3) return json({ places: [] });
        return json({ places: await d.places(q, near) });
      }

      // --- updater & telemetry
      if (m("GET", /^\/version$/)) {
        const rel = await d.store.latestRelease();
        return json({
          latest: rel ? {
            versionCode: rel.version_code,
            versionName: rel.version_name,
            apkUrl: rel.apk_url,
            apkSha256: rel.apk_sha256,
            changelog: rel.changelog,
            isMandatory: rel.is_mandatory,
          } : null,
        });
      }

      if (m("POST", /^\/telemetry$/)) {
        const text = await req.text();
        if (text.length > TELEMETRY_MAX_BODY) return json({ error: "too large" }, 413);
        let b: unknown;
        try { b = JSON.parse(text); } catch { return json({ error: "bad json" }, 400); }
        if (!isObj(b)) return json({ error: "bad json" }, 400);

        const installId = typeof b.installId === "string" ? b.installId : "";
        if (!INSTALL_ID.test(installId)) return json({ error: "bad installId" }, 400);

        const nowMs = now();
        const versionCode = int32(b.versionCode);
        const versionName = String(b.versionName ?? "").slice(0, 32);
        const osVersion = int32(b.osVersion);
        const deviceModel = String(b.deviceModel ?? "").slice(0, 64);

        const crashes = (Array.isArray(b.crashes) ? b.crashes.slice(0, MAX_CRASHES) : []).filter(isObj).map((c) => ({
          install_id: installId,
          version_code: versionCode,
          version_name: versionName,
          os_version: osVersion,
          device_model: deviceModel,
          exception_type: String(c.exceptionType ?? "Unknown").slice(0, 100),
          message: c.message ? clean(c.message, 500) : null,
          stack_trace: clean(c.stackTrace ?? "", 8000),
          crashed_at: hourOf(c.crashedAt, nowMs),
        }));

        const events = (Array.isArray(b.events) ? b.events.slice(0, MAX_EVENTS) : [])
          .filter((e): e is Record<string, unknown> => isObj(e) && typeof e.name === "string" && EVENT_NAMES.has(e.name))
          .map((e) => ({
            install_id: installId,
            version_code: versionCode,
            event_name: e.name as string,
            properties: cleanProps(e.properties),
            occurred_at: hourOf(e.occurredAt, nowMs),
          }));

        // Junk-only batches still get a 200 so clients don't retry them forever.
        if (crashes.length) await d.store.addCrashes(crashes);
        if (events.length) await d.store.addEvents(events);
        return json({ ok: true, crashes: crashes.length, events: events.length });
      }

      return json({ error: "not found" }, 404);
    } catch (e) {
      console.error(path, e);
      return json({ error: "server error" }, 500);
    }
  };
}
