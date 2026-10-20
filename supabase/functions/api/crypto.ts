const enc = new TextEncoder();
const hex = (b: ArrayBuffer) => [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");

/** HMAC-SHA256 as hex. The key lives only on the server, so a stored key can't be turned back into a plate. */
export type Hmac = (s: string) => Promise<string>;

export function hmacWith(secret: string): Hmac {
  const key = crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return async (s) => hex(await crypto.subtle.sign("HMAC", await key, enc.encode(s)));
}

export const randomToken = () => hex(crypto.getRandomValues(new Uint8Array(16)).buffer);
