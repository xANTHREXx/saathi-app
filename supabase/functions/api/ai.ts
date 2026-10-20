/**
 * The companion's model. Any OpenAI-compatible chat API works. Set whichever keys you have; they are tried in
 * order (your own provider, then Gemini, then Groq), and within each, models in order. One that is busy, out of
 * quota or retired falls through. When none answers, the companion still keeps her talking and raises the alert
 * on words for help.
 */
export type Gen = (system: string, user: string) => Promise<string>;

export interface Provider { name: string; baseUrl: string; apiKey: string; models: string[] }

const list = (s: string | undefined) => (s || "").split(",").map((x) => x.trim()).filter(Boolean);

export function providers(env: (k: string) => string | undefined): Provider[] {
  const out: Provider[] = [];
  const custom = env("AI_API_KEY");
  if (custom && env("AI_BASE_URL") && list(env("AI_MODELS")).length) {
    out.push({ name: "custom", baseUrl: env("AI_BASE_URL")!, apiKey: custom, models: list(env("AI_MODELS")) });
  }
  const gemini = env("GEMINI_API_KEY");
  if (gemini) {
    out.push({
      name: "gemini", baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai", apiKey: gemini,
      models: list(env("GEMINI_MODELS")).length ? list(env("GEMINI_MODELS")) : ["gemini-flash-latest", "gemini-flash-lite-latest"],
    });
  }
  const groq = env("GROQ_API_KEY");
  if (groq) {
    out.push({
      name: "groq", baseUrl: "https://api.groq.com/openai/v1", apiKey: groq,
      models: list(env("GROQ_MODELS")).length ? list(env("GROQ_MODELS")) : ["llama-3.3-70b-versatile", "llama-3.1-8b-instant"],
    });
  }
  return out.map((p) => ({ ...p, baseUrl: p.baseUrl.replace(/\/$/, "") }));
}

export function chat(ps: Provider[], fetcher: typeof fetch = fetch): Gen {
  return async (system, user) => {
    let last: unknown = new Error("No AI provider configured");
    for (const p of ps) {
      for (const model of p.models) {
        try {
          const r = await fetcher(`${p.baseUrl}/chat/completions`, {
            method: "POST",
            headers: { "Content-Type": "application/json", Authorization: `Bearer ${p.apiKey}` },
            body: JSON.stringify({
              model,
              messages: [{ role: "system", content: system }, { role: "user", content: user }],
              response_format: { type: "json_object" },
            }),
            signal: AbortSignal.timeout(12_000),
          });
          if (!r.ok) throw new Error(`${p.name} ${model}: ${r.status}`);
          const j = await r.json();
          const text = j?.choices?.[0]?.message?.content;
          if (typeof text === "string" && text.trim()) return text;
          throw new Error(`${p.name} ${model}: empty reply`);
        } catch (e) {
          console.warn("companion model failed:", String(e));
          last = e;
        }
      }
    }
    throw last;
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
