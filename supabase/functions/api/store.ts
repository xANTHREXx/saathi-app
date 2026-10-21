import type { LiveShare } from "./live.ts";
import type { PointRow, RideRow } from "./community.ts";

export interface ReleaseRow {
  version_code: number;
  version_name: string;
  apk_url: string;
  apk_sha256: string;
  changelog: string;
  is_mandatory: boolean;
}

export interface CrashRow {
  install_id: string;
  version_code: number;
  version_name: string;
  os_version: number;
  device_model: string;
  exception_type: string;
  message: string | null;
  stack_trace: string;
  crashed_at: string;
  test: boolean;
}

export interface EventRow {
  install_id: string;
  version_code: number;
  event_name: string;
  properties: Record<string, unknown>;
  occurred_at: string;
  test: boolean;
}

/** Where the backend keeps state. Postgres on Supabase; memory for tests and running locally without Docker. */
export interface Store {
  getLive(token: string): Promise<LiveShare | null>;
  putLive(s: LiveShare): Promise<void>;
  addPoint(p: PointRow): Promise<void>;
  addRide(r: RideRow): Promise<void>;
  /** Public heatmap data: place, time band and kind only. Test-phone rows only show to test phones (`includeTest`). */
  points(includeSample: boolean, includeTest: boolean): Promise<{ lat: number; lng: number; band: number; kind: string }[]>;
  /** Accounts that reported (points with this plate) and rode (rides with this plate) since `since`. */
  vehicle(plateKey: string, since: number, includeSample: boolean, includeTest: boolean): Promise<{ reports: string[]; rides: string[] }>;
  hasSample(): Promise<boolean>;
  deleteMine(account: string, caseKey: string): Promise<number>;
  latestRelease(): Promise<ReleaseRow | null>;
  addCrashes(rows: CrashRow[]): Promise<void>;
  addEvents(rows: EventRow[]): Promise<void>;
  /** The owner's dashboard numbers for the last `days` days (India time), real data or test phones only. See saathi_reports in the migrations. */
  reports(test: boolean, days: number): Promise<unknown>;
}

export class MemoryStore implements Store {
  live = new Map<string, LiveShare>();
  pts: PointRow[] = [];
  rides: RideRow[] = [];
  releases: ReleaseRow[] = [];
  crashes: CrashRow[] = [];
  events: EventRow[] = [];

  getLive(token: string) { return Promise.resolve(this.live.get(token) ?? null); }
  putLive(s: LiveShare) { this.live.set(s.token, s); return Promise.resolve(); }
  addPoint(p: PointRow) { this.pts.push(p); return Promise.resolve(); }
  addRide(r: RideRow) { this.rides.push(r); return Promise.resolve(); }
  points(includeSample: boolean, includeTest: boolean) {
    return Promise.resolve(this.pts.filter((p) => (includeSample || !p.sample) && (includeTest || !p.test)).map(({ lat, lng, band, kind }) => ({ lat, lng, band, kind })));
  }
  vehicle(plateKey: string, since: number, includeSample: boolean, includeTest: boolean) {
    const ok = (r: { plate_key: string | null; t: number; sample: boolean; test: boolean }) =>
      r.plate_key === plateKey && r.t >= since && (includeSample || !r.sample) && (includeTest || !r.test);
    return Promise.resolve({ reports: this.pts.filter(ok).map((p) => p.account), rides: this.rides.filter(ok).map((r) => r.account) });
  }
  hasSample() { return Promise.resolve(this.pts.some((p) => p.sample)); }
  deleteMine(account: string, caseKey: string) {
    const keep = (r: { account: string; case_key: string | null }) => !(r.account === account && r.case_key === caseKey);
    const before = this.pts.length + this.rides.length;
    this.pts = this.pts.filter(keep);
    this.rides = this.rides.filter(keep);
    return Promise.resolve(before - this.pts.length - this.rides.length);
  }
  latestRelease() {
    if (this.releases.length === 0) return Promise.resolve(null);
    const sorted = [...this.releases].sort((a, b) => b.version_code - a.version_code);
    return Promise.resolve(sorted[0]);
  }
  addCrashes(rows: CrashRow[]) {
    this.crashes.push(...rows);
    return Promise.resolve();
  }
  addEvents(rows: EventRow[]) {
    this.events.push(...rows);
    return Promise.resolve();
  }
  /** A small stand-in for the SQL function: enough shape for tests, not the full report. */
  reports(test: boolean, days: number) {
    const ev = this.events.filter((e) => e.test === test);
    const count = (name: string) => ev.filter((e) => e.event_name === name).length;
    return Promise.resolve({
      generated_at: new Date().toISOString(), days, test,
      kpis: { active_installs: new Set(ev.map((e) => e.install_id)).size, trips: count("trip_started"), crashes: this.crashes.filter((c) => c.test === test).length },
      daily: [], funnels: [], breakdowns: {}, crashes: [],
    });
  }
}

// Minimal slice of supabase-js we use, so this file type-checks without pulling the client into tests.
// deno-lint-ignore no-explicit-any
type Db = { from(table: string): any; rpc(fn: string, args: Record<string, unknown>): any };

// deno-lint-ignore no-explicit-any
const must = ({ data, error }: { data: any; error: unknown }): any => {
  if (error) throw error;
  return data;
};

/** Postgres through the service-role client. Tables have RLS on and no public policies (see migrations). */
export class PgStore implements Store {
  constructor(private db: Db) {}

  async getLive(token: string) {
    const r = must(await this.db.from("live_shares").select("*").eq("token", token).maybeSingle());
    if (!r) return null;
    return { token: r.token, name: r.name, plate: r.plate, last: r.last, events: r.events ?? [], touched: new Date(r.touched).getTime() };
  }
  async putLive(s: LiveShare) {
    must(await this.db.from("live_shares").upsert({
      token: s.token, name: s.name, plate: s.plate, last: s.last, events: s.events, touched: new Date(s.touched).toISOString(),
    }));
  }
  async addPoint(p: PointRow) { must(await this.db.from("community_points").insert(p)); }
  async addRide(r: RideRow) { must(await this.db.from("vehicle_rides").insert(r)); }
  async points(includeSample: boolean, includeTest: boolean) {
    let q = this.db.from("community_points").select("lat,lng,band,kind").order("t", { ascending: false }).limit(5000);
    if (!includeSample) q = q.eq("sample", false);
    if (!includeTest) q = q.eq("test", false);
    return must(await q);
  }
  async vehicle(plateKey: string, since: number, includeSample: boolean, includeTest: boolean) {
    const pick = async (table: string) => {
      let q = this.db.from(table).select("account").eq("plate_key", plateKey).gte("t", since).limit(10000);
      if (!includeSample) q = q.eq("sample", false);
      if (!includeTest) q = q.eq("test", false);
      return (must(await q) as { account: string }[]).map((r) => r.account);
    };
    return { reports: await pick("community_points"), rides: await pick("vehicle_rides") };
  }
  async hasSample() {
    const { count, error } = await this.db.from("community_points").select("id", { count: "exact", head: true }).eq("sample", true);
    if (error) throw error;
    return (count ?? 0) > 0;
  }
  async deleteMine(account: string, caseKey: string) {
    let n = 0;
    for (const table of ["community_points", "vehicle_rides"]) {
      const { count, error } = await this.db.from(table).delete({ count: "exact" }).eq("account", account).eq("case_key", caseKey);
      if (error) throw error;
      n += count ?? 0;
    }
    return n;
  }
  async latestRelease() {
    const r = must(await this.db.from("app_releases").select("version_code,version_name,apk_url,apk_sha256,changelog,is_mandatory").order("version_code", { ascending: false }).limit(1).maybeSingle());
    if (!r) return null;
    return {
      version_code: r.version_code,
      version_name: r.version_name,
      apk_url: r.apk_url,
      apk_sha256: r.apk_sha256,
      changelog: r.changelog,
      is_mandatory: r.is_mandatory,
    };
  }
  async addCrashes(rows: CrashRow[]) {
    if (rows.length === 0) return;
    must(await this.db.from("app_crashes").insert(rows));
  }
  async addEvents(rows: EventRow[]) {
    if (rows.length === 0) return;
    must(await this.db.from("app_events").insert(rows));
  }
  async reports(test: boolean, days: number) {
    return must(await this.db.rpc("saathi_reports", { want_test: test, days }));
  }
}
