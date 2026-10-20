import { assert, assertEquals, assertMatch } from "jsr:@std/assert@1";
import { makeHandler, type Deps } from "./app.ts";
import { chat, companionTurn, type Gen } from "./ai.ts";
import { hmacWith } from "./crypto.ts";
import { normPlate, plateKey, toPoint, vehicleSummary } from "./community.ts";
import { profileFor, route, TRAFFIC_FACTOR } from "./route.ts";
import { MemoryStore } from "./store.ts";

const h = hmacWith("test-secret");
const fakeJson = (obj: unknown): Gen => () => Promise.resolve(JSON.stringify(obj));
const failing: Gen = () => Promise.reject(new Error("quota"));

function app(over: Partial<Deps> = {}) {
  const store = new MemoryStore();
  let clock = 1_760_800_000_000;
  const deps: Deps = {
    store, hmac: h, gen: failing, aiConfigured: false,
    route: () => Promise.resolve({ points: [], duration_s: null }), routesConfigured: false,
    livePageUrl: "https://example.github.io/saathi/live/", sample: false, now: () => clock, ...over,
  };
  const handle = makeHandler(deps);
  const call = (method: string, path: string, body?: unknown, device = "dev-1") =>
    handle(new Request(`http://x/functions/v1/api${path}`, {
      method, headers: { "content-type": "application/json", "x-saathi-device": device },
      body: body === undefined ? undefined : JSON.stringify(body),
    }));
  return { store, call, tick: (ms: number) => (clock += ms) };
}

// --- companion (ported from the earlier Express server's tests)

Deno.test("replyNeverSaysSafe", async () => {
  const r = await companionTurn({ text: "hello" }, fakeJson({ reply: "You're safe now, I'm with you.", raise_alert: false }));
  assertEquals(/you('| a)re safe/i.test(r.reply), false);
});

Deno.test("raiseAlertFlagPassesThrough", async () => {
  const r = await companionTurn({ text: "a man is near me" }, fakeJson({ reply: "I've told Riya and Amma.", raise_alert: true }));
  assertEquals(r.raise_alert, true);
});

Deno.test("helpWordsRaiseEvenIfModelDoesNot", async () => {
  const r = await companionTurn({ text: "bachao" }, fakeJson({ reply: "Tell me more.", raise_alert: false }));
  assertEquals(r.raise_alert, true);
});

Deno.test("noModelOnlyRaisesForHelpWords", async () => {
  assertEquals((await companionTurn({ text: "hi, just checking in" }, failing)).raise_alert, false);
  assertEquals((await companionTurn({ text: "bachao, koi peecha kar raha hai", lang: "hi-IN" }, failing)).raise_alert, true);
  assertEquals((await companionTurn({ text: "मदद करो" }, failing)).raise_alert, true);
  const r = await companionTurn({ text: "help" }, failing);
  assert(r.reply.length > 0);
});

Deno.test("chatTriesModelsInOrder", async () => {
  const tried: string[] = [];
  const fetcher = ((_url: string, init: RequestInit) => {
    const model = JSON.parse(String(init.body)).model;
    tried.push(model);
    return Promise.resolve(model === "b"
      ? new Response(JSON.stringify({ choices: [{ message: { content: '{"reply":"hi","raise_alert":false}' } }] }))
      : new Response("busy", { status: 503 }));
  }) as typeof fetch;
  const out = await chat({ baseUrl: "https://ai", apiKey: "k", models: ["a", "b", "c"] }, fetcher)("s", "u");
  assertEquals(tried, ["a", "b"]);
  assertMatch(out, /hi/);
});

Deno.test("chatWithoutKeyFallsBack", async () => {
  const r = await companionTurn({ text: "help" }, chat({ baseUrl: "https://ai", models: ["a"] }));
  assertEquals(r.raise_alert, true);
});

// --- community (ported from the earlier Express server's tests)

Deno.test("pointRoundsLocationAndHashesPlate", async () => {
  const p = (await toPoint(h, { lat: 12.971234, lng: 77.594987, t: 1760800000, kind: "felt_off", plate: "KA 05 AB 1234" }, "acc1"))!;
  assertEquals([p.lat, p.lng], [12.971, 77.595]);
  const raw = JSON.stringify(p);
  assertEquals(raw.includes("KA05AB1234") || raw.includes("KA 05 AB 1234") || raw.includes("acc1"), false);
});

Deno.test("junkPointRejected", async () => {
  assertEquals(await toPoint(h, { lat: "x", lng: 1, t: 1 }, "a"), null);
  assertEquals(await toPoint(h, { lat: 100, lng: 1, t: 1 }, "a"), null);
});

Deno.test("plateVariantsMatch", async () => {
  assertEquals(normPlate("ka-05-xy-9876"), "KA05XY9876");
  assertEquals(await plateKey(h, "KA O5 XY 9876"), await plateKey(h, "KA05XY9876"));
});

Deno.test("flaggedAt3DistinctAccounts", () => {
  assertEquals(vehicleSummary(["same", "same", "same"], []), { rides: 0, reports: 1, flagged: false });
  assertEquals(vehicleSummary(["a", "b", "c"], ["a", "x", "x"]), { rides: 2, reports: 3, flagged: true });
});

// --- route

Deno.test("routeProfiles", () => {
  assertEquals(profileFor("AUTO"), "driving-car");
  assertEquals(profileFor("BIKE"), "driving-car");
  assertEquals(profileFor("WALK"), "foot-walking");
  assertEquals(profileFor("BUS"), null);
  assertEquals(profileFor("TRAIN"), null);
});

Deno.test("routeParsesOrsAndScalesForTraffic", async () => {
  const fetcher = (() => Promise.resolve(new Response(JSON.stringify({
    features: [{ geometry: { coordinates: [[77.6, 12.9], [77.61, 12.91], [77.62, 12.92]] }, properties: { summary: { duration: 600 } } }],
  })))) as typeof fetch;
  const r = await route({ from: { lat: 12.9, lng: 77.6 }, to: { lat: 12.92, lng: 77.62 }, mode: "AUTO" }, "key", fetcher);
  assertEquals(r.points[0], { lat: 12.9, lng: 77.6 });
  assertEquals(r.points.length, 3);
  assertEquals(r.duration_s, 600 * TRAFFIC_FACTOR);
});

Deno.test("routeEmptyWithoutKeyOrOnError", async () => {
  const body = { from: { lat: 1, lng: 1 }, to: { lat: 2, lng: 2 }, mode: "CAB" };
  assertEquals((await route(body, undefined)).points, []);
  const broken = (() => Promise.reject(new Error("offline"))) as typeof fetch;
  assertEquals((await route(body, "key", broken)).duration_s, null);
});

// --- HTTP surface

Deno.test("health", async () => {
  const r = await app().call("GET", "/health");
  assertEquals((await r.json()).ok, true);
});

Deno.test("liveCreateUpdateStateExpire", async () => {
  const a = app();
  const { token, url } = await (await a.call("POST", "/live", { name: "Priya", plate: "KA05AB1234" })).json();
  assertMatch(token, /^[0-9a-f]{32}$/);
  assertEquals(url, `https://example.github.io/saathi/live/?t=${token}`);
  assertEquals((await a.call("POST", `/live/${token}`, { lat: 12.9, lng: 77.6, t: 5, events: ["stopped 12 min"] })).status, 200);
  const s = await (await a.call("GET", `/live/${token}/state`)).json();
  assertEquals(s, { name: "Priya", plate: "KA05AB1234", last: { lat: 12.9, lng: 77.6, t: 5 }, events: ["stopped 12 min"] });
  a.tick(61 * 60 * 1000);
  assertEquals((await a.call("GET", `/live/${token}/state`)).status, 410);
  assertEquals((await a.call("POST", `/live/${token}`, { lat: 1, lng: 1, t: 1 })).status, 410);
  assertEquals((await a.call("GET", `/live/not-a-token/state`)).status, 410);
});

Deno.test("vehicleRidesAndReports", async () => {
  const a = app();
  const t = 1_760_800_000 - 86400;
  for (const dev of ["d1", "d2", "d2"]) await a.call("POST", "/community/ride", { plate: "KA05AB1234", t }, dev);
  let v = await (await a.call("GET", "/community/vehicle?plate=ka-05-ab-1234")).json();
  assertEquals(v, { rides: 2, reports: 0, flagged: false, sample: false });
  for (const dev of ["d1", "d3", "d4"]) await a.call("POST", "/community/point", { lat: 1, lng: 1, t, kind: "alert", plate: "KA05AB1234" }, dev);
  v = await (await a.call("GET", "/community/vehicle?plate=KA05AB1234")).json();
  assertEquals(v, { rides: 2, reports: 3, flagged: true, sample: false });
});

Deno.test("oldRidesAndReportsDontCount", async () => {
  const a = app();
  const old = 1_760_800_000 - 91 * 86400;
  for (const dev of ["a", "b", "c"]) {
    await a.call("POST", "/community/point", { lat: 1, lng: 1, t: old, kind: "alert", plate: "KA01AA0001" }, dev);
    await a.call("POST", "/community/ride", { plate: "KA01AA0001", t: old }, dev);
  }
  assertEquals(await (await a.call("GET", "/community/vehicle?plate=KA01AA0001")).json(), { rides: 0, reports: 0, flagged: false, sample: false });
});

Deno.test("deleteMineRemovesOnlyThatCase", async () => {
  const a = app();
  const t = 1_760_800_000;
  await a.call("POST", "/community/point", { lat: 1, lng: 1, t, kind: "felt_off", plate: "KA05AB1234", case: "SA-1" }, "me");
  await a.call("POST", "/community/ride", { plate: "KA05AB1234", t, case: "SA-1" }, "me");
  await a.call("POST", "/community/ride", { plate: "KA05AB1234", t, case: "SA-2" }, "me");
  await a.call("POST", "/community/ride", { plate: "KA05AB1234", t, case: "SA-1" }, "someone-else");
  const r = await (await a.call("DELETE", "/community/mine?case=SA-1", undefined, "me")).json();
  assertEquals(r.deleted, 2);
  assertEquals(a.store.pts.length + a.store.rides.length, 2);
});

Deno.test("pointsHideSampleUnlessDemo", async () => {
  const plain = await (await app().call("GET", "/community/points")).json();
  assertEquals(plain, { sample: false, points: [] });
  const demo = app({ sample: true });
  const d = await (await demo.call("GET", "/community/points")).json();
  assertEquals(d.sample, true);
  assert(d.points.length > 100);
  assertEquals(Object.keys(d.points[0]).sort(), ["band", "kind", "lat", "lng"]);
  const v = await (await demo.call("GET", "/community/vehicle?plate=KA05XY9876")).json();
  assertEquals(v.flagged, true);
  const ok = await (await demo.call("GET", "/community/vehicle?plate=KA05AB1234")).json();
  assertEquals(ok, { rides: 13, reports: 0, flagged: false, sample: true });
});

Deno.test("communityWritesNeedDevice", async () => {
  const a = app();
  assertEquals((await a.call("POST", "/community/point", { lat: 1, lng: 1, t: 1 }, "")).status, 400);
});

Deno.test("routeEndpointPassesThrough", async () => {
  const a = app({ route: () => Promise.resolve({ points: [{ lat: 1, lng: 2 }], duration_s: 60 }) });
  assertEquals(await (await a.call("POST", "/route", {})).json(), { points: [{ lat: 1, lng: 2 }], duration_s: 60 });
});

Deno.test("corsPreflight", async () => {
  const r = await app().call("OPTIONS", "/live/abc/state");
  assertEquals(r.status, 204);
  assertEquals(r.headers.get("access-control-allow-origin"), "*");
});
