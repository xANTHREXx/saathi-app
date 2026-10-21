import type { Hmac } from "./crypto.ts";

export const DAY = 86400;
export const FLAG_ACCOUNTS = 3;
export const WINDOW = 90 * DAY;

/** Same rule as the phone: case, spaces and dashes don't matter; O/0 and I/1 are one character. */
export const normPlate = (p: unknown) =>
  p ? String(p).toUpperCase().replace(/[^A-Z0-9]/g, "").replace(/O/g, "0").replace(/I/g, "1") : null;

export const plateKey = async (h: Hmac, p: unknown) => {
  const n = normPlate(p);
  return n ? await h("plate:" + n) : null;
};
export const accountKey = (h: Hmac, device: string) => h("acct:" + device);
export const caseKey = (h: Hmac, device: string, caseId: unknown) => (caseId ? h(`case:${device}:${caseId}`) : null);

/** 0..7, 3-hour bands in IST. */
export const istBand = (t: number) => Math.floor((((t + 19800) % DAY) / 3600) / 3);
export const round3 = (x: number) => Math.round(x * 1000) / 1000;

export interface PointRow {
  lat: number; lng: number; t: number; band: number; kind: string;
  plate_key: string | null; account: string; case_key: string | null; sample: boolean; test: boolean;
}
export interface RideRow { plate_key: string; account: string; t: number; case_key: string | null; sample: boolean; test: boolean }

/** Anonymous point: rounded place, time band, kind, keyed plate. No names, no numbers. Null when the input is junk. `test` marks a test phone's row. */
export async function toPoint(h: Hmac, body: Record<string, unknown>, device: string, sample = false, test = false): Promise<PointRow | null> {
  const lat = Number(body.lat), lng = Number(body.lng), t = Number(body.t);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || !Number.isFinite(t)) return null;
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
  return {
    lat: round3(lat), lng: round3(lng), t: Math.floor(t), band: istBand(t),
    kind: String(body.kind || "report").slice(0, 20),
    plate_key: await plateKey(h, body.plate),
    account: await accountKey(h, device),
    case_key: await caseKey(h, device, body.case),
    sample,
    test,
  };
}

/**
 * What she sees about a vehicle. `reports` counts distinct accounts that raised a concern, `rides` distinct
 * accounts that rode it, both within 90 days. Flagged at 3 or more accounts reporting.
 */
export function vehicleSummary(reportAccounts: string[], rideAccounts: string[]) {
  const reports = new Set(reportAccounts).size;
  return { rides: new Set(rideAccounts).size, reports, flagged: reports >= FLAG_ACCOUNTS };
}
