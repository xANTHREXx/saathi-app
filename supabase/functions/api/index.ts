// Saathi backend: one Supabase Edge Function. Deploy with
//   supabase functions deploy api --no-verify-jwt
// (contacts open the live link from an SMS, with no key). Locally without Docker:
//   STORE=memory SAATHI_SECRET=dev deno run -A supabase/functions/api/index.ts
import { createClient } from "npm:@supabase/supabase-js@2";
import { makeHandler } from "./app.ts";
import { chat, providers } from "./ai.ts";
import { hmacWith } from "./crypto.ts";
import { places } from "./places.ts";
import { route } from "./route.ts";
import { MemoryStore, PgStore } from "./store.ts";

const env = (k: string) => Deno.env.get(k) || undefined;

// The key behind plate and account hashes. Without a fixed one, every cold start would orphan the data.
const secret = env("SAATHI_SECRET");
if (!secret) throw new Error("Set SAATHI_SECRET (supabase secrets set SAATHI_SECRET=...)");

// The server-side key Supabase injects: the new secret key, or the legacy service-role key (retired end of 2026).
const adminKey = (() => {
  try { return JSON.parse(env("SUPABASE_SECRET_KEYS") ?? "{}").default as string | undefined; } catch { return undefined; }
})() ?? env("SUPABASE_SERVICE_ROLE_KEY");

const store = env("STORE") === "memory"
  ? new MemoryStore()
  : new PgStore(createClient(env("SUPABASE_URL")!, adminKey!, { auth: { persistSession: false } }));

const ai = providers(env);
const orsKey = env("ORS_API_KEY");

Deno.serve(makeHandler({
  store,
  hmac: hmacWith(secret),
  gen: chat(ai),
  aiConfigured: ai.length > 0,
  route: (b) => route(b, orsKey),
  places: (q, near) => places(q, near, orsKey),
  routesConfigured: Boolean(orsKey),
  livePageUrl: env("LIVE_PAGE_URL") || "http://localhost:8080/live/",
  sample: env("SEED_DEMO") === "true",
}));
