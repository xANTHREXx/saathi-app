/**
 * Expected route and travel time from OpenRouteService (free key, no card). The phone's watcher uses it for
 * off-route and late-arrival checks; without it the phone still checks for long stops.
 */
export interface LatLng { lat: number; lng: number }
export interface Route { points: LatLng[]; duration_s: number | null }

export const NO_ROUTE: Route = { points: [], duration_s: null };

/** ORS has no transit or two-wheeler profile: autos, cabs and bike taxis use the car profile; bus and train get none. */
export function profileFor(mode: unknown): string | null {
  switch (String(mode || "").toUpperCase()) {
    case "WALK": return "foot-walking";
    case "AUTO": case "CAB": case "BIKE": return "driving-car";
    default: return null;
  }
}

/** Free-flow times run short in Indian city traffic. Scale road travel so "late" means late. */
export const TRAFFIC_FACTOR = 1.5;
const MAX_POINTS = 800;

const valid = (p: unknown): p is LatLng =>
  !!p && Number.isFinite((p as LatLng).lat) && Number.isFinite((p as LatLng).lng);

export async function route(body: Record<string, unknown>, key: string | undefined, fetcher: typeof fetch = fetch): Promise<Route> {
  const { from, to, mode } = body;
  const profile = profileFor(mode);
  if (!key || !profile || !valid(from) || !valid(to)) return NO_ROUTE;
  try {
    const r = await fetcher(`https://api.openrouteservice.org/v2/directions/${profile}/geojson`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: key },
      // Saved places can sit inside a campus or gated society, far from a public road: snap within 1 km.
      body: JSON.stringify({ coordinates: [[from.lng, from.lat], [to.lng, to.lat]], radiuses: [1000, 1000] }),
      signal: AbortSignal.timeout(10_000),
    });
    if (!r.ok) {
      console.warn(`route failed: OpenRouteService ${r.status} ${(await r.text()).slice(0, 200)}`);
      return NO_ROUTE;
    }
    const f = (await r.json())?.features?.[0];
    const coords: [number, number][] = f?.geometry?.coordinates ?? [];
    const secs = Number(f?.properties?.summary?.duration);
    if (!coords.length) return NO_ROUTE;
    const step = Math.ceil(coords.length / MAX_POINTS);
    const points = coords.filter((_, i) => i % step === 0 || i === coords.length - 1).map(([lng, lat]) => ({ lat, lng }));
    const factor = profile === "foot-walking" ? 1 : TRAFFIC_FACTOR;
    return { points, duration_s: Number.isFinite(secs) ? Math.round(secs * factor) : null };
  } catch (e) {
    console.warn("route failed:", String(e));
    return NO_ROUTE;
  }
}
