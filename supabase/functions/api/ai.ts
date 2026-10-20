/**
 * The companion's model. Any OpenAI-compatible chat API works. Set whichever keys you have; they are tried in
 * order (your own provider, then Groq, then Gemini), and within each, models in order. One that is busy, out of
 * quota or retired falls through. When none answers, the companion still keeps her talking and raises the alert
 * on words for help.
 */
export type Gen = (system: string, user: string) => Promise<string>;

/** `pinned`: models the deployer named, used as given. Otherwise `models` is a preference list, narrowed to what
 * the provider says it serves, because providers retire model names every few months. */
export interface Provider { name: string; baseUrl: string; apiKey: string; models: string[]; pinned?: boolean }

const list = (s: string | undefined) => (s || "").split(",").map((x) => x.trim()).filter(Boolean);

// Preference order, best first. Checked against the provider's model list at run time. Groq goes first because
// it answers in under a second, and on a call speed matters more than polish; Gemini is often busy.
const GEMINI_PREFS = ["gemini-3.8-flash", "gemini-flash-latest", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-flash-lite-latest"];
const GROQ_PREFS = ["openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant"];

export function providers(env: (k: string) => string | undefined): Provider[] {
  const out: Provider[] = [];
  const custom = env("AI_API_KEY");
  if (custom && env("AI_BASE_URL") && list(env("AI_MODELS")).length) {
    out.push({ name: "custom", baseUrl: env("AI_BASE_URL")!, apiKey: custom, models: list(env("AI_MODELS")), pinned: true });
  }
  const groq = env("GROQ_API_KEY");
  if (groq) {
    const pinned = list(env("GROQ_MODELS"));
    out.push({ name: "groq", baseUrl: "https://api.groq.com/openai/v1", apiKey: groq,
      models: pinned.length ? pinned : GROQ_PREFS, pinned: pinned.length > 0 });
  }
  const gemini = env("GEMINI_API_KEY");
  if (gemini) {
    const pinned = list(env("GEMINI_MODELS"));
    out.push({ name: "gemini", baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai", apiKey: gemini,
      models: pinned.length ? pinned : GEMINI_PREFS, pinned: pinned.length > 0 });
  }
  return out.map((p) => ({ ...p, baseUrl: p.baseUrl.replace(/\/$/, "") }));
}

/** Preferences the provider actually serves, best first, at most two. Unknown listing → the first two preferences. */
export function pick(prefs: string[], served: Set<string> | null): string[] {
  if (!served) return prefs.slice(0, 2);
  const hit = prefs.filter((m) => served.has(m));
  return (hit.length ? hit : prefs).slice(0, 2);
}

const TOTAL_MS = 15_000; // she is waiting on a call; after this the fixed reply is better than silence
const HEDGE_MS = 4_000; // a candidate that hasn't answered by then gets company from the next one

/** Reasoning models think before answering; on a call, a quick answer beats a considered one. */
const quick = (p: Provider, model: string) =>
  p.name === "gemini" || /^openai\/gpt-oss|^qwen/.test(model) ? { reasoning_effort: "low" } : {};

export function chat(ps: Provider[], fetcher: typeof fetch = fetch): Gen {
  const listed = new Map<string, Promise<Set<string> | null>>();
  const served = (p: Provider) => {
    if (!listed.has(p.name)) {
      listed.set(p.name, fetcher(`${p.baseUrl}/models`, { headers: { Authorization: `Bearer ${p.apiKey}` }, signal: AbortSignal.timeout(3_000) })
        .then(async (r) => (r.ok ? new Set<string>(((await r.json())?.data ?? []).map((m: { id: string }) => String(m.id).replace(/^models\//, ""))) : null))
        .catch(() => null));
    }
    return listed.get(p.name)!;
  };

  const ask = async (p: Provider, model: string, system: string, user: string, signal: AbortSignal) => {
    const started = Date.now();
    const r = await fetcher(`${p.baseUrl}/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${p.apiKey}` },
      body: JSON.stringify({
        model,
        messages: [{ role: "system", content: system }, { role: "user", content: user }],
        response_format: { type: "json_object" },
        ...quick(p, model),
      }),
      signal,
    });
    if (!r.ok) throw new Error(`${p.name} ${model}: ${r.status} ${(await r.text()).slice(0, 200)}`);
    const text = (await r.json())?.choices?.[0]?.message?.content;
    if (typeof text !== "string" || !text.trim()) throw new Error(`${p.name} ${model}: empty reply`);
    console.log(`companion answered by ${p.name} ${model} in ${Date.now() - started} ms`);
    return text;
  };

  return async (system, user) => {
    const candidates = (await Promise.all(ps.map(async (p) =>
      (p.pinned ? p.models : pick(p.models, await served(p))).map((model) => ({ p, model }))))).flat();
    if (!candidates.length) throw new Error("No AI provider configured");

    // Candidates start in preference order: the next one when the last fails, or after HEDGE_MS of silence.
    // The first answer wins and the rest are cancelled.
    const stop = new AbortController();
    const deadline = AbortSignal.timeout(TOTAL_MS);
    return await new Promise<string>((resolve, reject) => {
      let next = 0, running = 0, done = false;
      let timer: ReturnType<typeof setTimeout> | undefined;
      const finish = (f: () => void) => { if (done) return; done = true; clearTimeout(timer); stop.abort(); f(); };
      deadline.addEventListener("abort", () => finish(() => reject(new Error("companion out of time"))));
      const launch = () => {
        clearTimeout(timer);
        if (done) return;
        if (next >= candidates.length) {
          if (running === 0) finish(() => reject(new Error("every companion model failed")));
          return;
        }
        const { p, model } = candidates[next++];
        running++;
        ask(p, model, system, user, AbortSignal.any([stop.signal, deadline]))
          .then((t) => finish(() => resolve(t)))
          .catch((e) => {
            running--;
            if (!done) console.warn("companion model failed:", String(e));
            launch();
          });
        timer = setTimeout(launch, HEDGE_MS);
      };
      launch();
    });
  };
}

const parseJson = (t: string) => JSON.parse(String(t).replace(/^\s*```(json)?\s*|\s*```\s*$/g, ""));

const SAFE = /\byou('| a)re (now )?safe\b/gi;
// Used only when no model answers: English, Hindi and Hinglish words for asking for help.
export const HELP_WORDS = /(\b(help|scared|afraid|follow(ing|ed)?|touch(ed|ing)?|unsafe|police|madad|bachao|darr?|peecha)\b|पीछा|मदद|बचाओ)/i;

const COMPANION_SYSTEM = (lang: string) => `You are Saathi, on a phone call with a woman travelling alone in India who feels uneasy.
Speak like a calm elder sister, in the language ${lang}. Short sentences, out loud.
Ask her what she can see around her, and give one cautious next step at a time, such as moving towards an open shop or other people.
Set raise_alert to true when she asks for help, says she is scared, or describes being followed, touched or threatened.
When you raise it, tell her her contacts have been told.
Never say she is safe. Never promise that help is coming. Never ask what happened to her.
Return JSON only: {"reply": string, "raise_alert": boolean}.`;

export interface Turn { text?: string; lang?: string; history?: { role: string; text: string }[] }

export async function companionTurn({ text = "", lang = "en-IN", history = [] }: Turn, gen: Gen) {
  try {
    const convo = history.slice(-6).map((h) => `${h.role}: ${h.text}`).join("\n");
    const r = parseJson(await gen(COMPANION_SYSTEM(lang), `${convo}\nShe says: ${text}`));
    const reply = String(r.reply || "").replace(SAFE, "I'm here with you");
    if (!reply) throw new Error("empty");
    return { reply, raise_alert: Boolean(r.raise_alert) || HELP_WORDS.test(text), places: [] };
  } catch {
    // No model: keep her talking and treat words for help as a request for help.
    return HELP_WORDS.test(text)
      ? { reply: "I'm here with you. I've told your contacts. Keep the phone with you and move towards people.", raise_alert: true, places: [] }
      : { reply: "I'm here with you. Tell me where you are and what you can see around you.", raise_alert: false, places: [] };
  }
}
