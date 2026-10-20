/** Live-location shares: open while an alert is active, gone after an hour without an update. */
export const LIVE_TTL_MS = 60 * 60 * 1000;

export interface LiveShare {
  token: string; name: string; plate: string | null;
  last: { lat: number; lng: number; t: number } | null;
  events: string[]; touched: number;
}

export const newShare = (token: string, body: Record<string, unknown>, now: number): LiveShare => ({
  token,
  name: String(body.name || "She").slice(0, 40),
  plate: body.plate ? String(body.plate).slice(0, 16) : null,
  last: null,
  events: [],
  touched: now,
});

export function applyUpdate(s: LiveShare, body: Record<string, unknown>, now: number): LiveShare | null {
  const lat = Number(body.lat), lng = Number(body.lng), t = Number(body.t);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return {
    ...s,
    last: { lat, lng, t: Number.isFinite(t) ? t : Math.floor(now / 1000) },
    events: Array.isArray(body.events) ? body.events.slice(-10).map((e) => String(e).slice(0, 120)) : s.events,
    touched: now,
  };
}

export const isLive = (s: LiveShare | null, now: number): s is LiveShare => !!s && now - s.touched <= LIVE_TTL_MS;
export const publicState = (s: LiveShare) => ({ name: s.name, plate: s.plate, last: s.last, events: s.events });
export const validToken = (t: string) => /^[0-9a-f]{32}$/.test(t);
