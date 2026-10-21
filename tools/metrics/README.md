# Saathi metrics dashboard

A private page with charts and funnels of the anonymous app metrics: active installs, trips, check-ins, alerts,
plate photos, updates, community rides and crashes. Only you can read it. It needs a secret key.

The page holds no data. It is one HTML file, and each time you connect or refresh it asks your own server
(`GET /reports` on the `api` edge function) for the numbers. Nothing is stored in the file. This folder is not
under `docs/`, so GitHub Pages never publishes it.

## Switch it on

1. Make a key (at least 32 characters). In a terminal:

   ```
   openssl rand -hex 32
   ```

   or in PowerShell:

   ```
   -join ((1..32) | % { '{0:x2}' -f (Get-Random -Max 256) })
   ```

2. Supabase dashboard > Edge Functions > Secrets: add `REPORTS_TOKEN` with that value. (Or
   `supabase secrets set REPORTS_TOKEN=...`.) Keep the key in your password manager.
3. Apply the migration `20261021000003_reports_api.sql`, then deploy the function:
   `supabase functions deploy api --no-verify-jwt`.

Until `REPORTS_TOKEN` is set, `/reports` answers 404 and the page says reports aren't switched on.

## Open it

Double-click `index.html`, or serve the folder with any static server. Paste the API address (it is prefilled) and
your key, then press Connect. Tick "Remember on this computer" only on a computer you trust: the key is then kept in
that browser's local storage. Without it, the key lasts for the browser tab. "Forget key" clears it.

Use the buttons at the top for 7, 30 or 90 days, and to switch between real data and test phones.

## Good to know

- Days are India time. Raw events are kept 30 days and crashes 60 days. Counts and funnels reach further back through
  the daily history; active installs, the breakdowns and the crash table only cover what is still kept.
- Charts use Chart.js 4.4.1 from cdnjs. Offline, the figures, funnels and tables still show.
- Anonymous counts only. No names, numbers, places or plates.
