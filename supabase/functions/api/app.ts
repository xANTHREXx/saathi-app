import { randomToken, type Hmac } from "./crypto.ts";
import { accountKey, caseKey, plateKey, toPoint, vehicleSummary, WINDOW } from "./community.ts";
import { applyUpdate, isLive, newShare, publicState, validToken } from "./live.ts";
import { companionTurn, type Gen } from "./ai.ts";
import type { PlaceHit } from "./places.ts";
import type { Route } from "./route.ts";
import { seedRows } from "./seed.ts";
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

      return json({ error: "not found" }, 404);
    } catch (e) {
      console.error(path, e);
      return json({ error: "server error" }, 500);
    }
  };
}
