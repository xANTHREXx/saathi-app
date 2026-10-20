/**
 * Address suggestions for saved places, from OpenRouteService (same free key as routes). What she types in the
 * search box is sent to OpenRouteService; nothing is stored here.
 */
export interface PlaceHit { label: string; sub: string; lat: number; lng: number }

const str = (v: unknown): string => (typeof v === "string" ? v.trim() : "");

export async function places(
  q: string,
  near: { lat: number; lng: number } | null,
  key: string | undefined,
  fetcher: typeof fetch = fetch,
): Promise<PlaceHit[]> {
  const text = q.trim().slice(0, 100);
  if (!key || text.length < 3) return [];
  try {
    const params = new URLSearchParams({ api_key: key, text, "boundary.country": "IN", size: "5" });
    if (near) {
      params.set("focus.point.lat", String(near.lat));
      params.set("focus.point.lon", String(near.lng));
    }
    const r = await fetcher(`https://api.openrouteservice.org/geocode/autocomplete?${params}`, {
      signal: AbortSignal.timeout(6_000),
    });
    if (!r.ok) {
      console.warn(`places failed: OpenRouteService ${r.status} ${(await r.text()).slice(0, 200)}`);
      return [];
    }
    const features: unknown[] = (await r.json())?.features ?? [];
    const out: PlaceHit[] = [];
    for (const f of features as Record<string, any>[]) {
      const p = f?.properties ?? {};
      const label = str(p.name);
      const [lng, lat] = Array.isArray(f?.geometry?.coordinates) ? f.geometry.coordinates : [];
      if (!label || !Number.isFinite(lng) || !Number.isFinite(lat)) continue;
      const area = [p.neighbourhood, p.locality, p.county].map(str).find(Boolean);
      const parts: string[] = [];
      for (const s of [area, str(p.region)]) {
        if (s && s !== label && !parts.includes(s)) parts.push(s);
      }
      out.push({ label, sub: parts.join(", "), lat, lng });
    }
    return out;
  } catch (e) {
    console.warn("places failed:", String(e));
    return [];
  }
}
