import type { Hmac } from "./crypto.ts";
import { accountKey, plateKey, toPoint, type PointRow, type RideRow } from "./community.ts";

// Synthetic community data for the demo. Every screen that shows it says "Sample data".
// Fictional points along transit corridors; no real report, person or vehicle.

function rng(seed: number) {
  let s = seed;
  return () => ((s = (s * 1664525 + 1013904223) % 4294967296) / 4294967296);
}

const corridors = [
  { from: [12.9172, 77.6229], to: [12.9352, 77.6245] }, // Bengaluru, Silk Board to Koramangala
  { from: [12.9767, 77.5713], to: [12.9779, 77.5728] }, // Bengaluru, Majestic bus stand area
  { from: [12.9070, 77.6235], to: [12.8900, 77.6400] }, // Bengaluru, Hosur Road
  { from: [18.5018, 73.8636], to: [18.5308, 73.8475] }, // Pune, Swargate to Shivajinagar
  { from: [18.5089, 73.9260], to: [18.4966, 73.9416] }, // Pune, Hadapsar bus route
];

/** Plates the demo notifications use: reported ones get flagged, KA05AB1234 has rides and no concerns. */
const vehicles = [
  { plate: "KA05XY9876", reports: 4, rides: 9 },
  { plate: "KA01AB4321", reports: 3, rides: 5 },
  { plate: "MH12CD5678", reports: 5, rides: 7 },
  { plate: "KA05AB1234", reports: 0, rides: 13 },
];

export async function seedRows(h: Hmac, now = Math.floor(Date.now() / 1000)) {
  const r = rng(42);
  const points: PointRow[] = [];
  const rides: RideRow[] = [];
  let acct = 0;
  for (const c of corridors) {
    for (let i = 0; i < 32; i++) {
      const f = r();
      const evening = r() < 0.7; // most reports after 9 pm
      const hour = evening ? 21 + Math.floor(r() * 3) : 7 + Math.floor(r() * 12);
      const dayStart = now - Math.floor(r() * 80) * 86400 - ((now + 19800) % 86400);
      const p = await toPoint(h, {
        lat: c.from[0] + (c.to[0] - c.from[0]) * f + (r() - 0.5) * 0.002,
        lng: c.from[1] + (c.to[1] - c.from[1]) * f + (r() - 0.5) * 0.002,
        t: dayStart + hour * 3600 + Math.floor(r() * 3600),
        kind: r() < 0.6 ? "felt_off" : "alert",
      }, `seed-${acct++}`, true);
      if (p) points.push(p);
    }
  }
  for (const v of vehicles) {
    const key = (await plateKey(h, v.plate))!;
    for (let i = 0; i < v.reports; i++) {
      const p = await toPoint(h, { lat: 12.93 + r() * 0.02, lng: 77.6 + r() * 0.02, t: now - Math.floor(r() * 60) * 86400, kind: "alert", plate: v.plate }, `seed-v-${v.plate}-${i}`, true);
      if (p) points.push(p);
    }
    for (let i = 0; i < v.rides; i++) {
      rides.push({ plate_key: key, account: await accountKey(h, `seed-r-${v.plate}-${i}`), t: now - Math.floor(r() * 80) * 86400, case_key: null, sample: true, test: false });
    }
  }
  return { points, rides };
}
