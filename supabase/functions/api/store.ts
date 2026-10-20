import type { LiveShare } from "./live.ts";
import type { PointRow, RideRow } from "./community.ts";

/** Where the backend keeps state. Postgres on Supabase; memory for tests and running locally without Docker. */
export interface Store {
  getLive(token: string): Promise<LiveShare | null>;
  putLive(s: LiveShare): Promise<void>;
  addPoint(p: PointRow): Promise<void>;
  addRide(r: RideRow): Promise<void>;
  /** Public heatmap data: place, time band and kind only. */
  points(includeSample: boolean): Promise<{ lat: number; lng: number; band: number; kind: string }[]>;
  /** Accounts that reported (points with this plate) and rode (rides with this plate) since `since`. */
  vehicle(plateKey: string, since: number, includeSample: boolean): Promise<{ reports: string[]; rides: string[] }>;
  hasSample(): Promise<boolean>;
  deleteMine(account: string, caseKey: string): Promise<number>;
}

export class MemoryStore implements Store {
  live = new Map<string, LiveShare>();
  pts: PointRow[] = [];
  rides: RideRow[] = [];

  getLive(token: string) { return Promise.resolve(this.live.get(token) ?? null); }
  putLive(s: LiveShare) { this.live.set(s.token, s); return Promise.resolve(); }
  addPoint(p: PointRow) { this.pts.push(p); return Promise.resolve(); }
  addRide(r: RideRow) { this.rides.push(r); return Promise.resolve(); }
  points(includeSample: boolean) {
    return Promise.resolve(this.pts.filter((p) => includeSample || !p.sample).map(({ lat, lng, band, kind }) => ({ lat, lng, band, kind })));
  }
  vehicle(plateKey: string, since: number, includeSample: boolean) {
    const ok = (r: { plate_key: string | null; t: number; sample: boolean }) => r.plate_key === plateKey && r.t >= since && (includeSample || !r.sample);
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
}

// Minimal slice of supabase-js we use, so this file type-checks without pulling the client into tests.
// deno-lint-ignore no-explicit-any
type Db = { from(table: string): any };

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
  async points(includeSample: boolean) {
    let q = this.db.from("community_points").select("lat,lng,band,kind").order("t", { ascending: false }).limit(5000);
    if (!includeSample) q = q.eq("sample", false);
    return must(await q);
  }
  async vehicle(plateKey: string, since: number, includeSample: boolean) {
    const pick = async (table: string) => {
      let q = this.db.from(table).select("account").eq("plate_key", plateKey).gte("t", since).limit(10000);
      if (!includeSample) q = q.eq("sample", false);
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
}
