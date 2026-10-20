/**
 * The companion's model. Any OpenAI-compatible chat API works: Gemini (free AI Studio key, the default),
 * Groq, or your own provider. Models are tried in order; one that is busy or retired falls through.
 * When none answers, the companion still keeps her talking and raises the alert on words for help.
 */
export type Gen = (system: string, user: string) => Promise<string>;

export interface AiConfig { baseUrl: string; apiKey?: string; models: string[] }

export const DEFAULT_AI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai";
export const DEFAULT_MODELS = ["gemini-flash-latest", "gemini-flash-lite-latest"];

export function aiConfig(env: (k: string) => string | undefined): AiConfig {
  const models = (env("AI_MODELS") || "").split(",").map((s) => s.trim()).filter(Boolean);
  return { baseUrl: (env("AI_BASE_URL") || DEFAULT_AI_BASE).replace(/\/$/, ""), apiKey: env("AI_API_KEY"), models: models.length ? models : DEFAULT_MODELS };
}

export function chat(cfg: AiConfig, fetcher: typeof fetch = fetch): Gen {
  return async (system, user) => {
    if (!cfg.apiKey) throw new Error("No AI key");
    let last: unknown;
    for (const model of cfg.models) {
      try {
        const r = await fetcher(`${cfg.baseUrl}/chat/completions`, {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${cfg.apiKey}` },
          body: JSON.stringify({
            model,
            messages: [{ role: "system", content: system }, { role: "user", content: user }],
            response_format: { type: "json_object" },
          }),
          signal: AbortSignal.timeout(12_000),
        });
        if (!r.ok) throw new Error(`${model}: ${r.status}`);
        const j = await r.json();
        const text = j?.choices?.[0]?.message?.content;
        if (typeof text === "string" && text.trim()) return text;
        throw new Error(`${model}: empty reply`);
      } catch (e) {
        last = e;
      }
    }
    throw last ?? new Error("No model available");
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
