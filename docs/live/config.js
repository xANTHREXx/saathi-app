// Where the live page reads her location from. Set this to your Supabase project's function URL:
//   https://<project-ref>.supabase.co/functions/v1/api
// On localhost it talks to the backend run with `deno run` (see README).
window.SAATHI_API = location.hostname === "localhost" || location.hostname === "127.0.0.1"
  ? "http://localhost:8000/api"
  : "https://psxkhdxqhzhpfhwxucpz.supabase.co/functions/v1/api";
