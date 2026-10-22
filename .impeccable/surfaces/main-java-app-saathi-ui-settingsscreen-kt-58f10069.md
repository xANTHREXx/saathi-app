---
version: 1
slug: "main-java-app-saathi-ui-settingsscreen-kt-58f10069"
primary_target: "android/app/src/main/java/app/saathi/ui/SettingsScreen.kt"
related_targets: []
---

# Saathi Settings: the ready list

Scope: the Settings tab (SettingsScreen.kt) and its sub-pages. Mode: Operate. Platform: Android, Material 3, inside the Home scaffold's navigation bar.

Audience and job: the commuter checks, mostly before a trip or after moving city, whether Saathi can still reach her people, and fixes the one thing that isn't. Rarely: language, privacy, app version. Constraints: 48dp targets, light and dark, text grows at 130% font, every string in English and Hindi (StringsParityTest), madder only for help and alerts, never shame an Off she chose, safety copy rules from PRODUCT.md.

Chosen direction: Ready list (dealt lead), raised by five declined challengers. Memorable moment: when a protection becomes ready, its star stamps into the readiness row.

Unresolved: whether a Quick Settings tile is really added can't be read on Android, so the tile is listed under Ways, never counted.

## Direction contract

THESIS: Settings opens on one answer, how ready Saathi is to reach her people, counted in stamped ajrakh stars. Refuses the category default: one flat scroll where safety setup sits beside telemetry and the version number.

OWN-WORLD: DESIGN.md's Ajrakh system unchanged: night indigo ground, one surface-1 readiness plate (28dp, hairline), indigo stars for set protections, alum ring only for a required protection that is missing, a quiet star-off ring for an optional one not set yet. Figtree only, hierarchy by scale and weight (raise from the design annual). Rows divided by hairlines, never boxed.

STORY: She opens Settings, reads "6 of 7 ready" and which one is missing, taps that row, fixes it, and sees its star stamp in when she comes back. Everything else (language, privacy, app) waits in one quiet group below.

FIRST VIEWPORT: Title "Settings" (displaySmall). Readiness plate: count in headlineMedium, seven 22dp stars in a row, one line naming the next missing protection (alum only when required). Then seven numbered rows, each number, 22dp mark, title (titleMedium), summary (bodyMedium fg2), chevron or alum "Fix": People to text; They know you added them; Your name in alerts; Ways to ask for help; Keeps working; Ride apps start trips; Places you go often. Below: Practise a check-in, then Also (Language, Privacy and sharing, App). Each row opens a sub-page with a back arrow; system Back returns.

FORM: Ready list, position 5 of the ordered list, dealt lead. Raises: orienteering (a row is punched or not, no half-states), boarding pass (a missing required protection holds alum until fixed), design annual (one face, hairlines not cards), Crouwel (rows reflow whole at 130%), variable-font specimen (each choice shows its effect where she makes it: the chosen phrase is shown in full, the chosen press count states its gesture). Signature: a protection turning ready stamps its star (1.6x to 1x, 200ms, emphasized decelerate) and the count ticks; with Remove animations on, it appears without the stamp. seed:b1e16404cd52787d

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
