# Saathi

A trip companion for women commuting in India. It works when she does nothing:

- It notices Uber, Ola and Rapido rides on its own.
- It watches for long stops, detours and late arrival, and checks in loudly when something is off.
- If she doesn't answer, it texts the right people from her own SIM, even with no mobile data.
- It takes help from the power button, the lock screen and a home-screen widget.
- It speaks for her when she can't, and talks her through an uneasy moment.
- Afterwards it leaves a fingerprinted record and a draft complaint she can file herself.

Built for the Google Cloud AI Builder Cup 2026, then reworked so anyone can run it for free.

```
android/    Kotlin + Jetpack Compose app (minSdk 29)
supabase/   backend: one Edge Function (Deno/TypeScript) + Postgres
docs/live/  the live-location page contacts open from the SMS (GitHub Pages)
tools/      demo track generator
```

## What works with no server at all

The safety loop runs entirely on the phone, with no AI and no network:

| On the phone, offline | Needs the backend (free tiers) |
|---|---|
| Ride pickup from Uber, Ola, Rapido notifications | Expected route for detour and late-arrival checks (cached per commute after the first trip) |
| Long-stop check, loud check-in over the lock screen | Live-location page for contacts |
| Alert SMS from her SIM to contacts in her current city | Vehicle history: "13 rides with Saathi users" and flags |
| Power button, lock-screen notification, widget, hold for help | Companion replies from a model |
| Speak for me in the local language, Hindi, English | Community heatmap |
| Plate check from a photo (ML Kit on the phone) | |
| Reporting guide and a draft complaint built from her record | |
| Companion raises the alert on words like "help" or "bachao" | |

Whether someone is alerted never depends on a model or a server.

## How it knows where she's going

At setup she saves the places she goes often, like home and work. When a trip starts at one of them,
Saathi guesses the destination without asking:

- With two saved places, it's the other one.
- With more, it's the place she has gone to at least twice from here.

It fetches the expected route once and caches it on the phone. Her regular commute is then checked for
detours and late arrival even offline. If it isn't sure, it doesn't guess; it watches for long stops and
she can pick a destination from the trip screen. A wrong guess would mean a false alarm.

## Run the backend

Everything runs on free tiers with no card: Supabase, OpenRouteService, and Gemini through Google AI Studio.

### Locally, without Docker

```bash
STORE=memory SAATHI_SECRET=dev SEED_DEMO=true deno run -A supabase/functions/api/index.ts   # http://localhost:8000/health
deno test --allow-net=none supabase/functions/api/                                          # 22 tests
```

The memory store forgets everything on restart. Serve the live page with
`deno run -A jsr:@std/http@1/file-server docs --port 8080`.

### On Supabase

```bash
supabase link --project-ref <ref>
supabase db push                       # tables, row-level security, hourly cleanup
supabase secrets set SAATHI_SECRET=<long random string> ORS_API_KEY=<key> AI_API_KEY=<key> \
  LIVE_PAGE_URL=https://<you>.github.io/<repo>/live/
supabase functions deploy api --no-verify-jwt
```

`--no-verify-jwt` matters: contacts open the live link from an SMS, with no key.

**Settings**

| Variable | What it does |
|---|---|
| `SAATHI_SECRET` | **Required.** Key for the hashes of plates, devices and records. Keep it fixed, or community data is orphaned. |
| `ORS_API_KEY` | Routes from [OpenRouteService](https://openrouteservice.org/) (free: 2,000 a day). Without it, no detour or late checks; long stops still work. |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_MODELS` | Companion model, any OpenAI-compatible API. Defaults to Gemini's endpoint; a free key comes from [AI Studio](https://aistudio.google.com/). For Groq: `AI_BASE_URL=https://api.groq.com/openai/v1`, `AI_MODELS=llama-3.3-70b-versatile`. Without a key, the fixed fallback replies are used. |
| `LIVE_PAGE_URL` | Where `docs/live/` is published. |
| `SEED_DEMO=true` | Adds sample community data, labelled "Sample data" in the app. Leave it off for real use. |

**Privacy:** free AI tiers may use prompts to improve the provider's products. For real users, use a paid
key that doesn't train on data, or leave the companion on its fallback replies.

**Live page:** publish `docs/` with GitHub Pages and set your function URL in `docs/live/config.js`.

**Staying awake:** Supabase pauses free projects after 7 days with no activity.
`.github/workflows/keepalive.yml` pings `/health` every 3 days. Set the repository variable `SAATHI_API`
to `https://<ref>.supabase.co/functions/v1/api`.

### What the backend stores

- **Live shares:** her name, the plate, her last location and trip events. Kept only while an alert is
  active, and deleted an hour after the last update.
- **Community points and rides:** location rounded to about 110 m, a 3-hour time band, and keyed hashes of
  the plate and device. No names, numbers or plates.
- **Access:** all tables have row-level security with no public access, so only the function reads them.
- **Deletion:** deleting a trip in the app deletes what it contributed.

## Run the app

Needs JDK 17 and the Android SDK. Set `android/local.properties` (not committed):

```
sdk.dir=/path/to/Android/Sdk
API_URL=https://<ref>.supabase.co/functions/v1/api
```

Without `API_URL`, the app talks to `http://10.0.2.2:8000/api`, which is the local Deno backend as seen
from the emulator. Release builds refuse plain HTTP.

```bash
cd android
./gradlew :app:testDebugUnitTest   # 57 tests
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Grant notification access for the ride watcher: Settings › Notifications › Device & app notifications,
or `adb shell cmd notification allow_listener app.saathi/app.saathi.trigger.RideNotificationListener`.

### Demo

Long-press the "Saathi" title on the home screen for the demo panel (sample data):

- **Replay: Bengaluru Uber auto, 30×**: the auto leaves its planned route. The detour check fires first,
  then the stop check, and with no answer the SMS goes out.
- **Fake ride-app notifications**: Booked (KA05AB1234 has 13 sample rides), Arrived (asks for the plate),
  Flagged auto (KA05XY9876 has sample reports), Ended (asks "Anything felt off?").
- **Pretend I'm in (city)**: Bengaluru or Pune changes who is alerted and the speak-for-me language.

## Verified so far

On the Android emulator, against the local backend with a stand-in route:

- Saved places set from the current location.
- A trip from Home guessed Work and fetched the route.
- Driving 1 km off it fired the check-in. With no answer, an SMS went to Riya (Bengaluru) and Amma
  (anywhere) with a live link.
- The live page updated in a browser.
- Changing the destination fetched a new route.
- Ending the trip counted the ride (13 → 14), and "something felt off" added a report.
- Deleting the trip removed both from the server.
- The guide showed a draft built from the record.

Before that version: the check-in over the lock screen, hold for help, the flagged-vehicle warning, and
the companion raising the alert on "help".

**Not yet verified:**

- The power-button trigger. The emulator merges quick presses; test on a real phone.
- The widget on a home screen.
- Plate reading from a real photo.
- SMS delivery to a real phone.
- A real OpenRouteService route.
- Anything on a deployed Supabase project.

## Limits

- Speak-for-me translations are drafts and need a native speaker's check.
- The reporting guide (`android/app/src/main/res/raw/guide_en.txt`) hasn't been reviewed by a qualified
  person yet.
- Travel times from OpenRouteService assume free-flowing traffic. Road trips are scaled by 1.5, so check
  that against real commutes.
- Vehicle history is only useful once many people in a city use Saathi. Until then, most plates show nothing.
- The emergency step (a call to 112) is simulated.
- The `server/` folder holds the earlier Express backend for Cloud Run, kept for reference.
