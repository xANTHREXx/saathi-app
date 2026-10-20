import { assert, assertEquals, assertMatch } from "jsr:@std/assert@1";
import { makeHandler, type Deps } from "./app.ts";
import { chat, companionTurn, pick, providers, type Gen } from "./ai.ts";
import { hmacWith } from "./crypto.ts";
import { normPlate, plateKey, toPoint, vehicleSummary } from "./community.ts";
import { places, type PlaceHit } from "./places.ts";
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
    route: () => Promise.resolve({ points: [], duration_s: null }), places: () => Promise.resolve([]), routesConfigured: false,
    livePageUrl: "https://example.github.io/saathi/live/", sample: false, now: () => clock, ...over,
  };
  const handle = makeHandler(deps);
  const call = (method: string, path: string, body?: unknown, device = "dev-1") =>
    handle(new Request(`http://x/functions/v1/api${path}`, {
      method, headers: { "content-type": "application/json", "x-saathi-device": device },
      body: body === undefined ? undefined : JSON.stringify(body),
    }));
  const raw = (path: string, text: string) =>
    handle(new Request(`http://x/functions/v1/api${path}`, { method: "POST", body: text }));
  return { store, call, raw, tick: (ms: number) => (clock += ms) };
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

Deno.test("chatTriesProvidersAndModelsInOrder", async () => {
  const tried: string[] = [];
  const fetcher = ((url: string, init: RequestInit) => {
    const model = JSON.parse(String(init.body)).model;
    tried.push(model);
    return Promise.resolve(url.includes("groq") && model === "g2"
      ? new Response(JSON.stringify({ choices: [{ message: { content: '{"reply":"hi","raise_alert":false}' } }] }))
      : new Response("quota", { status: 429 }));
  }) as typeof fetch;
  const ps = providers((k) => ({ GEMINI_API_KEY: "a", GEMINI_MODELS: "m1,m2", GROQ_API_KEY: "b", GROQ_MODELS: "g1,g2,g3" } as Record<string, string>)[k]);
  assertEquals(ps.map((p) => p.name), ["groq", "gemini"]);
  const out = await chat(ps, fetcher)("s", "u");
  assertEquals(tried, ["g1", "g2"]);
  assertMatch(out, /hi/);
});

Deno.test("slowModelGetsCompanyAndFastestAnswerWins", async () => {
  const fetcher = ((_url: string, init: RequestInit) => {
    const model = JSON.parse(String(init.body)).model;
    if (model === "slow") {
      // Never answers; must be cancelled once the other one does.
      return new Promise<Response>((_, reject) => init.signal!.addEventListener("abort", () => reject(new Error("aborted"))));
    }
    return Promise.resolve(new Response(JSON.stringify({ choices: [{ message: { content: '{"reply":"fast","raise_alert":false}' } }] })));
  }) as typeof fetch;
  const ps = providers((k) => ({ GROQ_API_KEY: "a", GROQ_MODELS: "slow", GEMINI_API_KEY: "b", GEMINI_MODELS: "quick" } as Record<string, string>)[k]);
  const t0 = Date.now();
  const out = await chat(ps, fetcher)("s", "u");
  assertMatch(out, /fast/);
  assert(Date.now() - t0 < 6_000);
});

Deno.test("pickPrefersWhatTheProviderServes", () => {
  assertEquals(pick(["a", "b", "c"], new Set(["c", "b", "z"])), ["b", "c"]);
  assertEquals(pick(["a", "b", "c"], new Set(["z"])), ["a", "b"]); // listing knows none: try preferences anyway
  assertEquals(pick(["a", "b", "c"], null), ["a", "b"]); // listing failed
});

Deno.test("unpinnedProviderAsksWhichModelsExist", async () => {
  const tried: string[] = [];
  const fetcher = ((url: string, init?: RequestInit) => {
    if (url.endsWith("/models")) {
      return Promise.resolve(new Response(JSON.stringify({ data: [{ id: "openai/gpt-oss-20b" }, { id: "whisper-large-v3" }] })));
    }
    tried.push(JSON.parse(String(init!.body)).model);
    return Promise.resolve(new Response(JSON.stringify({ choices: [{ message: { content: '{"reply":"ok","raise_alert":false}' } }] })));
  }) as typeof fetch;
  const ps = providers((k) => ({ GROQ_API_KEY: "b" } as Record<string, string>)[k]);
  await chat(ps, fetcher)("s", "u");
  assertEquals(tried, ["openai/gpt-oss-20b"]);
});

Deno.test("customProviderGoesFirstOnlyWhenComplete", () => {
  const env = (o: Record<string, string>) => (k: string) => o[k];
  assertEquals(providers(env({ AI_API_KEY: "k", GEMINI_API_KEY: "g" })).map((p) => p.name), ["gemini"]);
  assertEquals(providers(env({ AI_API_KEY: "k", AI_BASE_URL: "https://x/", AI_MODELS: "m", GEMINI_API_KEY: "g", GROQ_API_KEY: "q" })).map((p) => p.name), ["custom", "groq", "gemini"]);
});

Deno.test("chatWithoutKeysFallsBack", async () => {
  const r = await companionTurn({ text: "help" }, chat([]));
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

// --- places

Deno.test("placesParsesOrsAndSkipsBadFeatures", async () => {
  let sent = "";
  const fetcher = ((url: string) => {
    sent = String(url);
    return Promise.resolve(new Response(JSON.stringify({
      features: [
        { geometry: { coordinates: [77.6, 12.9] }, properties: { name: "Indiranagar Metro", neighbourhood: "Indiranagar", region: "Karnataka" } },
        { geometry: { coordinates: [77.5, 12.8] }, properties: { name: "Hosur Road", county: "Bengaluru South", region: "Karnataka" } },
        { geometry: { coordinates: [77.62, 13.01] }, properties: { name: "Hoysala Nagar Park", neighbourhood: "Hoysala Nagar", locality: "Bengaluru", region: "Karnataka" } },
        { geometry: {}, properties: { name: "No coordinates", region: "Karnataka" } },
      ],
    })));
  }) as typeof fetch;
  const hits = await places("  indira  ", { lat: 12.97, lng: 77.59 }, "key", fetcher);
  assertEquals(hits, [
    { label: "Indiranagar Metro", sub: "Indiranagar, Karnataka", lat: 12.9, lng: 77.6 },
    { label: "Hosur Road", sub: "Bengaluru South, Karnataka", lat: 12.8, lng: 77.5 },
    { label: "Hoysala Nagar Park", sub: "Hoysala Nagar, Bengaluru", lat: 13.01, lng: 77.62 },
  ]);
  const u = new URL(sent);
  assertEquals(u.origin + u.pathname, "https://api.openrouteservice.org/geocode/autocomplete");
  assertEquals(u.searchParams.get("boundary.country"), "IN");
  assertEquals(u.searchParams.get("size"), "5");
  assertEquals(u.searchParams.get("text"), "indira");
  assertEquals(u.searchParams.get("focus.point.lat"), "12.97");
  assertEquals(u.searchParams.get("focus.point.lon"), "77.59");
});

Deno.test("placesShortQueryNeverCallsOrs", async () => {
  let called = false;
  const fetcher = (() => { called = true; return Promise.resolve(new Response("{}")); }) as typeof fetch;
  assertEquals(await places("ab", null, "key", fetcher), []);
  assertEquals(called, false);
});

Deno.test("placesEmptyOnErrorOrRefusal", async () => {
  const broken = (() => Promise.reject(new Error("offline"))) as typeof fetch;
  assertEquals(await places("indiranagar", null, "key", broken), []);
  const refused = (() => Promise.resolve(new Response("forbidden", { status: 403 }))) as typeof fetch;
  assertEquals(await places("indiranagar", null, "key", refused), []);
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

Deno.test("placesEndpointIgnoresShortQueries", async () => {
  assertEquals(await (await app().call("GET", "/places?q=ab")).json(), { places: [] });
});

Deno.test("placesEndpointPassesQueryAndNearThrough", async () => {
  const seen: unknown[][] = [];
  const hit: PlaceHit = { label: "A", sub: "B", lat: 1, lng: 2 };
  const a = app({ places: (q, near) => { seen.push([q, near]); return Promise.resolve([hit]); } });
  assertEquals(await (await a.call("GET", "/places?q=indira&lat=12.97&lng=77.59")).json(), { places: [hit] });
  await a.call("GET", "/places?q=indira");
  assertEquals(seen, [["indira", { lat: 12.97, lng: 77.59 }], ["indira", null]]);
});

Deno.test("corsPreflight", async () => {
  const r = await app().call("OPTIONS", "/live/abc/state");
  assertEquals(r.status, 204);
  assertEquals(r.headers.get("access-control-allow-origin"), "*");
});

Deno.test("versionEndpointReturnsNullWhenNoReleases", async () => {
  const a = app();
  const res = await a.call("GET", "/version");
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { latest: null });
});

Deno.test("versionEndpointReturnsLatestRelease", async () => {
  const a = app();
  a.store.releases.push(
    { version_code: 1, version_name: "0.1", apk_url: "https://x/1.apk", apk_sha256: "1".repeat(64), changelog: "first", is_mandatory: false },
    { version_code: 2, version_name: "0.2", apk_url: "https://x/2.apk", apk_sha256: "ab".repeat(32), changelog: "second", is_mandatory: true },
  );
  const res = await a.call("GET", "/version");
  assertEquals(res.status, 200);
  assertEquals(await res.json(), {
    latest: {
      versionCode: 2,
      versionName: "0.2",
      apkUrl: "https://x/2.apk",
      apkSha256: "ab".repeat(32),
      changelog: "second",
      isMandatory: true,
    },
  });
});

const IID = "123e4567-e89b-42d3-a456-426614174000";
const CLOCK = 1_760_800_000_000; // the app() helper's fixed clock
const HOUR_MS = 3_600_000;
const startOfHour = (ms: number) => new Date(Math.floor(ms / HOUR_MS) * HOUR_MS).toISOString();
const ev = (over: Record<string, unknown> = {}) => ({ name: "trip_started", ...over });

Deno.test("telemetryEndpointValidatesInstallId", async () => {
  const a = app();
  for (const installId of ["", "uuid-123", "ABCDEFAB-e89b-42d3-a456-426614174000", `${IID}0`, 42, undefined]) {
    const res = await a.call("POST", "/telemetry", { installId, crashes: [], events: [ev()] });
    assertEquals(res.status, 400, String(installId));
  }
  assertEquals(a.store.events.length, 0);
});

Deno.test("telemetryRejectsBadJson", async () => {
  const a = app();
  const send = (body: string) => a.raw("/telemetry", body);
  assertEquals((await send("{not json")).status, 400);
  assertEquals((await send("[1,2]")).status, 400);
  assertEquals((await send("null")).status, 400);
});

Deno.test("telemetryRejectsOversizedBodyWith413", async () => {
  const a = app();
  const res = await a.call("POST", "/telemetry", { installId: IID, events: [ev()], pad: "x".repeat(64_000) });
  assertEquals(res.status, 413);
  assertEquals(await res.json(), { error: "too large" });
  assertEquals(a.store.events.length, 0);
});

Deno.test("telemetryJunkOnlyBatchIs200WithZeroCounts", async () => {
  const a = app();
  const res = await a.call("POST", "/telemetry", {
    installId: IID,
    crashes: ["nope", null, 5],
    events: [ev({ name: "sos_triggered" }), ev({ name: 5 }), "x", null, {}],
  });
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { ok: true, crashes: 0, events: 0 });
  const empty = await a.call("POST", "/telemetry", { installId: IID, crashes: [], events: [] });
  assertEquals(empty.status, 200);
  assertEquals(await empty.json(), { ok: true, crashes: 0, events: 0 });
  assertEquals(a.store.crashes.length + a.store.events.length, 0);
});

Deno.test("telemetryDropsUnknownEventsAndKeepsAllowlisted", async () => {
  const a = app();
  const names = ["trip_started", "checkin_triggered", "checkin_answered", "checkin_timed_out", "plate_scanned", "alert_sent", "update_prompted", "update_completed"];
  const res = await a.call("POST", "/telemetry", { installId: IID, events: [...names.map((name) => ev({ name })), ev({ name: "checkin_responded" }), ev({ name: "pwn" })] });
  assertEquals(await res.json(), { ok: true, crashes: 0, events: 8 });
  assertEquals(a.store.events.map((e) => e.event_name), names);
});

Deno.test("telemetryCapsEventsAt50AndCrashesAt20", async () => {
  const a = app();
  const res = await a.call("POST", "/telemetry", {
    installId: IID,
    events: Array.from({ length: 80 }, () => ev()),
    crashes: Array.from({ length: 30 }, () => ({ exceptionType: "E", stackTrace: "s" })),
  });
  assertEquals(await res.json(), { ok: true, crashes: 20, events: 50 });
});

Deno.test("telemetryPropertyRules", async () => {
  const a = app();
  const many = Object.fromEntries("abcdefghijkl".split("").map((c, i) => [`key_${c}`, i]));
  const props = {
    ok_bool: true,
    ok_num: 3.5,
    ok_str: "  cab  ",
    long_str: "x".repeat(100),
    nested: { a: 1 },
    list: [1, 2],
    nothing: null,
    inf: 1e999, // JSON turns this into null
    BadKey: "dropped",
    "has space": "dropped",
    ["k".repeat(33)]: "dropped",
  };
  await a.call("POST", "/telemetry", { installId: IID, events: [ev({ properties: props }), ev({ properties: many }), ev({ properties: "str" }), ev({ properties: [1] })] });
  assertEquals(a.store.events.length, 4);
  assertEquals(a.store.events[0].properties, { ok_bool: true, ok_num: 3.5, ok_str: "cab", long_str: "x".repeat(40) });
  assertEquals(Object.keys(a.store.events[1].properties).length, 8);
  assertEquals(a.store.events[2].properties, {});
  assertEquals(a.store.events[3].properties, {});
});

Deno.test("telemetryTimestampsAreTruncatedToTheHour", async () => {
  const a = app();
  const t = new Date(CLOCK - 5 * HOUR_MS - 123_456).toISOString(); // not on the hour
  await a.call("POST", "/telemetry", {
    installId: IID,
    events: [ev({ occurredAt: t })],
    crashes: [{ exceptionType: "E", stackTrace: "s", crashedAt: t }],
  });
  const want = startOfHour(CLOCK - 5 * HOUR_MS - 123_456);
  assertEquals(a.store.events[0].occurred_at, want);
  assertEquals(a.store.crashes[0].crashed_at, want);
  assertMatch(want, /:00:00\.000Z$/);
});

Deno.test("telemetryReplacesInvalidFutureAndAncientTimestampsWithNow", async () => {
  const a = app();
  const now = startOfHour(CLOCK);
  const bad: unknown[] = [
    "not a date", undefined, null, {},
    new Date(CLOCK + 2 * 86_400_000).toISOString(),
    new Date(CLOCK - 31 * 86_400_000).toISOString(),
  ];
  await a.call("POST", "/telemetry", {
    installId: IID,
    events: bad.map((occurredAt) => ev({ occurredAt })),
    crashes: bad.map((crashedAt) => ({ exceptionType: "E", stackTrace: "s", crashedAt })),
  });
  assertEquals(a.store.events.length, bad.length);
  assertEquals(a.store.crashes.length, bad.length);
  for (const e of a.store.events) assertEquals(e.occurred_at, now);
  for (const c of a.store.crashes) assertEquals(c.crashed_at, now);
  // Edges that are still accepted: just under 1 day ahead, just under 30 days old.
  const b = app();
  await b.call("POST", "/telemetry", {
    installId: IID,
    events: [ev({ occurredAt: new Date(CLOCK + 86_400_000 - HOUR_MS).toISOString() }), ev({ occurredAt: new Date(CLOCK - 29 * 86_400_000).toISOString() })],
  });
  assertEquals(b.store.events.map((e) => e.occurred_at), [startOfHour(CLOCK + 86_400_000 - HOUR_MS), startOfHour(CLOCK - 29 * 86_400_000)]);
});

Deno.test("telemetryScrubsCrashMessageAndStackTrace", async () => {
  const a = app();
  await a.call("POST", "/telemetry", {
    installId: IID,
    crashes: [{
      exceptionType: "IllegalStateException",
      message: "bad contact +91 98765 43210 for riya@example.com near 12.97167,77.59456",
      stackTrace: "java.lang.IllegalStateException: call 9876543210\n\tat app.saathi.ui.MainActivity.onCreate(MainActivity.kt:123)",
    }],
  });
  const c = a.store.crashes[0];
  assertEquals(c.message, "bad contact [number] for [email] near [place]");
  assertEquals(c.stack_trace, "java.lang.IllegalStateException: call [number]\n\tat app.saathi.ui.MainActivity.onCreate(MainActivity.kt:123)");
});

Deno.test("telemetryEndpointStoresCrashesAndEventsWithTruncation", async () => {
  const a = app();
  const longTrace = "e".repeat(10000);
  const res = await a.call("POST", "/telemetry", {
    installId: IID,
    versionCode: 1,
    versionName: "0.1",
    osVersion: 34,
    deviceModel: "Pixel 7",
    crashes: [
      { exceptionType: "NullPointerException", message: "boom", stackTrace: longTrace },
    ],
    events: [
      { name: "trip_started", properties: { mode: "cab" } },
    ],
  });
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { ok: true, crashes: 1, events: 1 });
  assertEquals(a.store.crashes.length, 1);
  assertEquals(a.store.crashes[0].stack_trace.length, 8000);
  assertEquals(a.store.crashes[0].install_id, IID);
  assertEquals(a.store.events.length, 1);
  assertEquals(a.store.events[0].event_name, "trip_started");
  assertEquals(a.store.events[0].properties, { mode: "cab" });
});

