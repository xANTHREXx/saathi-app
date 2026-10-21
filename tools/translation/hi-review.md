# Hindi review: Saathi app strings

For a native Hindi speaker to check quickly. Every string the app shows is in the tables below, grouped by screen. Rows marked ⚠ are the ones I am least sure of; the reason is in the Notes column. The Hindi file is `android/app/src/main/res/values-hi/strings.xml`; the English is `res/values/strings.xml`. Fix a string there and it is live in the app.

Counts: 413 entries (14 are plurals with a "one" and an "other" form, so some entries have two Hindi sentences).

## Not translated, on purpose

- The alert SMS and the "I'm okay" SMS to contacts stay English for now (`core/AlertText.kt`). Three Hindi strings quote or sit beside that English text: `settings_you_hint`, `setup_name_hint` and `ready_told_message`.
- The reporting guide body (`res/raw/guide_en.txt`) and the complaint draft stay English. Their screen titles and buttons are translated, and a one-line note tells her the guide is in English.
- Speak for me's spoken phrases (`assets/phrases.json`) are untouched. The three phrase labels in Settings reuse the same Hindi.
- The hidden demo panel, log lines, telemetry names, and the companion's server replies are untouched.
- The brand name "Saathi" is not translated (see below).

## Word choices

| English | Hindi used | Why |
|---|---|---|
| Saathi (the app) | Saathi, in Latin script, everywhere | One spelling that matches the launcher icon and the English "Saathi alert:" SMS her contacts still get. The name is provisional, so one Latin token is easy to replace. "साथी" is also an ordinary word (companion), which could read as "a companion" instead of the app's name. Verbs for Saathi are masculine because ऐप is masculine; change them all if Saathi should read as "she". |
| trip | सफ़र | Plain and warm. "यात्रा" is formal; "ट्रिप" is used only inside ride-app words. |
| check-in | चेक-इन | The loanword people already say. The question on the check-in screen is the full "क्या आप ठीक हैं?". |
| alert | अलर्ट | Common loanword. |
| contacts (her people) | कॉन्टैक्ट | Same word as her phone's Contacts app. "People to text" is "जिन्हें मैसेज जाए". |
| text / SMS | मैसेज (SMS where the SIM or permission is meant) | How people say it. |
| Speak for me | मेरी तरफ़ से बोलें | ⚠ Short and literal ("speak on my behalf"). The feature name needs a native ear. |
| Hold for help | मदद के लिए दबाए रखें | |
| I'm okay | मैं ठीक हूँ | |
| sealed record / fingerprint | सील किया हुआ रिकॉर्ड / फ़िंगरप्रिंट | Literal. Flagged where used. |
| watching (the trip) | नज़र रखना / "…पर नज़र है" | Avoids the formal "निगरानी". |
| Settings, Quick Settings | सेटिंग्स, क्विक सेटिंग्स | |
| Permissions | अनुमतियाँ | |
| She / you | आप. Feminine verb forms only where Hindi forces one ("जा रही हैं", "देतीं") | The app speaks to her. Most lines use imperatives, which carry no gender. |
| Punctuation | Hindi sentences end with "।" | Digits stay 0-9, as on Indian phones. |
| Phone-maker menu names (Autostart, Battery saver…) | Left in English inside quotes | Most Indian phones show them in English. A phone set to Hindi may show Hindi names. |

## App and shared labels

| String name | English | Hindi | Notes |
|---|---|---|---|
| `app_name` | Saathi | (not translated) | Brand name. Kept in Latin script everywhere, not translated. |
| `widget_description` | SOS speaks for you and alerts your contacts. Slide to start a trip. | SOS आपकी तरफ़ से बोलता है और आपके कॉन्टैक्ट को अलर्ट भेजता है। सफ़र शुरू करने के लिए स्लाइड करें। |  |
| `lang_name` | English | हिन्दी | The app's own language name, shown in Settings > Language. Not a sentence. |
| `action_cancel` | Cancel | रद्द करें |  |
| `action_close` | Close | बंद करें |  |
| `action_done` | Done | हो गया |  |
| `action_save` | Save | सेव करें |  |
| `action_add` | Add | जोड़ें |  |
| `action_back` | Back | वापस | Screen-reader label for the back arrow. |
| `action_remove` | Remove %1$s | %1$s को हटाएँ | Screen-reader label for a remove (x) button. |
| `action_help` | Help | मदद |  |
| `im_okay` | I'm okay | मैं ठीक हूँ |  |
| `names_none` | your contacts | आपके कॉन्टैक्ट | Used when there is nobody to name, inside a sentence. |
| `names_pair` | %1$s and %2$s | %1$s और %2$s | Joins the last two names; earlier names are joined with a comma. |

## Navigation

| String name | English | Hindi | Notes |
|---|---|---|---|
| `nav_trip` | Trip | सफ़र |  |
| `nav_my_trips` | My trips | मेरे सफ़र |  |
| `nav_map` | Map | मैप |  |
| `nav_settings` | Settings | सेटिंग्स |  |

## Home: ready, status and mode chips

| String name | English | Hindi | Notes |
|---|---|---|---|
| `ready_title_rides` | Uber, Ola and Rapido rides start on their own | Uber, Ola और Rapido की राइड अपने-आप शुरू हो जाती हैं |  |
| `ready_title_manual` | How are you travelling? | आप कैसे जा रही हैं? | Feminine verb form ("जा रही हैं"): the app speaks to her. |
| `home_ready_snackbar` | Saathi is ready. Uber, Ola and Rapido trips start on their own. | Saathi तैयार है। Uber, Ola और Rapido के सफ़र अपने-आप शुरू होंगे। |  |
| `status_ready` | Watching is on. Location, texts and check-ins are ready. | नज़र रखना चालू है। लोकेशन, SMS और चेक-इन तैयार हैं। |  |
| `status_fix` | Fix | ठीक करें |  |
| `perm_off_location` | Location is off. Saathi can't see your trip. | लोकेशन बंद है। Saathi आपका सफ़र नहीं देख सकता। | Verbs for Saathi are masculine throughout (ऐप is masculine). Change if Saathi should read as "she". |
| `perm_off_location_always` | Location isn't allowed all the time. Saathi stops watching when the screen is off. | लोकेशन की अनुमति हर समय के लिए नहीं है। स्क्रीन बंद होने पर Saathi नज़र रखना छोड़ देता है। |  |
| `perm_off_sms` | Texts are off. Saathi can't text your contacts. | SMS की अनुमति बंद है। Saathi आपके कॉन्टैक्ट को मैसेज नहीं भेज सकता। |  |
| `perm_off_notifications` | Notifications are off. Check-ins can't reach you. | नोटिफ़िकेशन बंद हैं। चेक-इन आप तक नहीं पहुँच सकते। |  |
| `perm_off_lock_screen` | Check-ins can't show over the lock screen. | लॉक स्क्रीन पर चेक-इन नहीं दिख सकते। |  |
| `perm_off_background` | Battery saving may stop Saathi in the middle of a trip. | बैटरी सेविंग सफ़र के बीच में Saathi को बंद कर सकती है। |  |
| `status_keep_done` | I've done this | मैंने यह कर लिया |  |
| `perm_off_keep_running` | Your phone may stop Saathi in the background, in the middle of a trip. | आपका फ़ोन सफ़र के बीच बैकग्राउंड में Saathi को बंद कर सकता है। |  |
| `perm_off_rides` | Rides won't start on their own yet. | राइड अभी अपने-आप शुरू नहीं होंगी। |  |
| `mode_walk` | Walk | पैदल |  |
| `mode_bus` | Bus | बस |  |
| `mode_train` | Train | ट्रेन |  |
| `mode_auto` | Auto | ऑटो |  |
| `mode_cab` | Cab | कैब |  |
| `mode_bike` | Bike | बाइक |  |
| `ready_plate_label` | Auto plate, if you can see it | ऑटो की प्लेट, अगर दिख रही हो |  |
| `ready_start` | Start | शुरू करें |  |

## Home: Two more things

| String name | English | Hindi | Notes |
|---|---|---|---|
| `ready_card_title` | Two more things | दो काम और |  |
| `ready_card_not_now` | Not now | अभी नहीं |  |
| `ready_card_done` | Done | हो गया |  |
| `ready_told_title` | Tell %1$s you added them | %1$s को बताएँ कि आपने उन्हें जोड़ा है |  |
| `ready_told_names_two` | %1$s and %2$s | %1$s और %2$s |  |
| `ready_told_names_more` | one: %1$s and %2$d other<br>other: %1$s and %2$d others | one: %1$s और %2$d अन्य<br>other: %1$s और %2$d अन्य | Was a plain string; now a plural. |
| `ready_told_names_none` | your contacts | आपके कॉन्टैक्ट |  |
| `ready_told_sub` | So a text from Saathi doesn't surprise them. | ताकि Saathi का मैसेज आने पर वे चौंकें नहीं। |  |
| `ready_told_write` | Write message | मैसेज लिखें |  |
| ⚠ `ready_told_message` | Hi, I've added you as a contact in Saathi, my travel safety app. If a message from my number ever says I need help, it's real. Please call me and open the location link. | नमस्ते, मैंने आपको Saathi में कॉन्टैक्ट के तौर पर जोड़ा है। यह मेरा सफ़र की सुरक्षा वाला ऐप है। अगर कभी मेरे नंबर से ऐसा मैसेज आए कि मुझे मदद चाहिए, तो वह सच है। कृपया मुझे कॉल करें और लोकेशन का लिंक खोलें। | This text goes to her contacts, who will still get the alert SMS in English. Decide whether this intro should stay English until the alert SMS is translated. |
| `ready_tile_title` | Add the help tile | मदद वाली टाइल जोड़ें |  |
| `ready_tile_sub` | Pull down from the top, even when locked, and tap Saathi help. | ऊपर से नीचे खींचें, लॉक होने पर भी, और Saathi मदद पर टैप करें। |  |
| `ready_tile_add` | Add | जोड़ें |  |
| `ready_tile_how` | How | कैसे |  |
| `ready_tile_steps` | Edit Quick Settings (the pencil) and drag Saathi help in. | क्विक सेटिंग्स में एडिट (पेंसिल) दबाएँ और Saathi मदद को खींचकर जोड़ दें। |  |
| `help_tile_label` | Saathi help | Saathi मदद | Also the Quick Settings tile name. |

## Home: trip

| String name | English | Hindi | Notes |
|---|---|---|---|
| `trip_on_your_way` | On your way | आप रास्ते में हैं |  |
| `trip_with_driver` | With %1$s | %1$s के साथ |  |
| `trip_watching_auto` | Watching your auto | आपके ऑटो पर नज़र है |  |
| `trip_watching_cab` | Watching your cab | आपकी कैब पर नज़र है |  |
| `trip_watching_bike` | Watching your bike taxi | आपकी बाइक टैक्सी पर नज़र है |  |
| `trip_watching_bus` | Watching your bus | आपकी बस पर नज़र है |  |
| `trip_watching_train` | Watching your train | आपकी ट्रेन पर नज़र है |  |
| `trip_watching_walk` | Watching your walk | आपके पैदल सफ़र पर नज़र है |  |
| `trip_watching_trip` | Watching your trip | आपके सफ़र पर नज़र है |  |
| `trip_watching_auto_app` | Watching your %1$s auto | आपके %1$s ऑटो पर नज़र है | Separate strings per vehicle because Hindi "आपके/आपकी" follows the noun's gender. |
| `trip_watching_cab_app` | Watching your %1$s cab | आपकी %1$s कैब पर नज़र है |  |
| `trip_watching_bike_app` | Watching your %1$s bike taxi | आपकी %1$s बाइक टैक्सी पर नज़र है |  |
| `trip_watching_bus_app` | Watching your %1$s bus | आपकी %1$s बस पर नज़र है |  |
| `trip_watching_train_app` | Watching your %1$s train | आपकी %1$s ट्रेन पर नज़र है |  |
| `trip_watching_walk_app` | Watching your %1$s walk | आपके %1$s पैदल सफ़र पर नज़र है |  |
| `trip_watching_trip_app` | Watching your %1$s trip | आपके %1$s सफ़र पर नज़र है |  |
| `trip_minutes` | one: %1$d min<br>other: %1$d min | one: %1$d मिनट<br>other: %1$d मिनट |  |
| `trip_watched` | watched | नज़र रखी | Small label under the minutes figure. |
| `trip_dest_unsure` | Not sure where you're going: watching for long stops | आप कहाँ जा रही हैं, पक्का नहीं: लंबे ठहराव पर नज़र है |  |
| `trip_dest_no_route` | Going to %1$s · no route found, watching for long stops | %1$s जा रही हैं · रास्ता नहीं मिला, लंबे ठहराव पर नज़र है |  |
| `trip_dest_finding` | Going to %1$s · finding the route | %1$s जा रही हैं · रास्ता ढूँढ रहे हैं |  |
| `trip_dest_watching` | Going to %1$s · watching for detours | %1$s जा रही हैं · रास्ता बदलने पर नज़र है |  |
| `trip_dest_pick` | Pick | चुनें |  |
| `trip_dest_change` | Change | बदलें |  |
| `trip_dest_not_sure` | Not sure | पक्का नहीं |  |
| `check_plate` | Check plate | प्लेट जाँचें |  |
| `end_trip` | End trip | सफ़र खत्म करें |  |
| `end_trip_armed` | Tap again to end | खत्म करने के लिए फिर टैप करें |  |
| `thread_earlier` | one: %1$d min earlier<br>other: %1$d min earlier | one: %1$d मिनट पहले<br>other: %1$d मिनट पहले | Was a plain string; now a plural. |
| `line_started` | Started %1$s | %1$s पर शुरू हुआ |  |
| `line_started_plain` | Started | शुरू हुआ |  |
| `line_plate_matched` | plate matched | प्लेट मेल खाई |  |
| `line_plate_mismatch` | plate didn't match | प्लेट मेल नहीं खाई |  |
| `line_you` | You | आप |  |
| `line_you_at` | You · %1$s | आप · %1$s |  |
| `line_checking_in` | Checking in | चेक-इन हो रहा है |  |
| `line_min_watched_quiet` | one: %1$d min watched, all quiet<br>other: %1$d min watched, all quiet | one: %1$d मिनट नज़र रखी, सब शांत<br>other: %1$d मिनट नज़र रखी, सब शांत |  |
| `line_min_watched` | one: %1$d min watched<br>other: %1$d min watched | one: %1$d मिनट नज़र रखी<br>other: %1$d मिनट नज़र रखी |  |
| `line_ends_with_ride` | Ends on its own when the %1$s ride ends. | %1$s की राइड खत्म होते ही यह अपने-आप बंद हो जाएगा। |  |
| `event_long_stop` | one: stopped %1$d min<br>other: stopped %1$d min | one: %1$d मिनट से रुके हैं<br>other: %1$d मिनट से रुके हैं | English starts lowercase; the app capitalises where it needs to. |
| `event_off_route` | %1$d m off route | रास्ते से %1$d मीटर दूर |  |
| `event_late` | one: %1$d min past arrival time<br>other: %1$d min past arrival time | one: पहुँचने के तय समय से %1$d मिनट देर<br>other: पहुँचने के तय समय से %1$d मिनट देर |  |

## Home: help

| String name | English | Hindi | Notes |
|---|---|---|---|
| ⚠ `tile_speak` | Speak for me | मेरी तरफ़ से बोलें | Feature name. "Speak for me" means it speaks on her behalf; "मेरी तरफ़ से बोलें" is the closest short phrase. Check with a native speaker. |
| `tile_call` | Call Saathi | Saathi को कॉल करें |  |
| `help_title` | Hold for help | मदद के लिए दबाए रखें |  |
| `help_holding` | Keep holding… | दबाए रखें… |  |
| `help_sub` | 2 seconds · texts %1$s | 2 सेकंड · %1$s को मैसेज जाएगा |  |
| `help_sub_none` | 2 seconds · add a contact in Settings | 2 सेकंड · सेटिंग्स में कॉन्टैक्ट जोड़ें |  |
| `help_spoken` | Raise help. Texts %1$s. | मदद माँगें। %1$s को मैसेज जाएगा। | Screen-reader text. |
| `help_spoken_none` | Raise help. No contacts yet, add one in Settings. | मदद माँगें। अभी कोई कॉन्टैक्ट नहीं है, सेटिंग्स में जोड़ें। | Screen-reader text. |
| `help_action` | Raise help now | अभी मदद माँगें | Screen-reader action label. |
| `help_dialog_title` | Send help now? | अभी मदद भेजें? |  |
| `help_dialog_text` | Saathi will text %1$s your live location. | Saathi %1$s को आपकी लाइव लोकेशन भेजेगा। |  |
| `help_dialog_text_none` | Saathi will start recording and sound the alarm. You have no contacts yet. | Saathi रिकॉर्डिंग शुरू करेगा और अलार्म बजाएगा। अभी आपका कोई कॉन्टैक्ट नहीं है। |  |
| `help_dialog_send` | Send help | मदद भेजें |  |
| `help_dialog_cancel` | Cancel | रद्द करें |  |
| `raised_title` | Help already raised | मदद पहले ही माँगी जा चुकी है |  |
| `raised_told` | Your contacts were told | आपके कॉन्टैक्ट को बता दिया गया |  |
| `raised_failed` | Couldn't reach %1$s | %1$s तक नहीं पहुँच पाए |  |
| `raised_retry` | Try again | फिर कोशिश करें |  |
| `told_telling` | Telling your contacts… | आपके कॉन्टैक्ट को बताया जा रहा है… |  |
| `told_names` | one: %1$s was told<br>other: %1$s were told | one: %1$s को बता दिया गया<br>other: %1$s को बता दिया गया |  |
| `told_link` | Live location link sent | लाइव लोकेशन का लिंक भेज दिया गया |  |
| `told_recording` | recording on this phone | इस फ़ोन पर रिकॉर्डिंग चल रही है |  |
| `told_failed_retrying` | Couldn't send to %1$s. Retrying. | %1$s को नहीं भेज पाए। फिर कोशिश कर रहे हैं। |  |
| `no_112` | Saathi doesn't call 112 | Saathi 112 पर कॉल नहीं करता |  |
| `no_112_full` | Saathi doesn't call 112. Call it yourself if you need to. | Saathi 112 पर कॉल नहीं करता। ज़रूरत हो तो खुद कॉल करें। |  |
| `alert_none_reached` | No one was reached | किसी तक मैसेज नहीं पहुँचा |  |

## Home: after a trip

| String name | English | Hindi | Notes |
|---|---|---|---|
| `consent_title` | Help other women? | दूसरी महिलाओं की मदद करें? |  |
| `consent_body` | Count this ride anonymously, and add your answer to the community map. Never your name or number. You can change this in Settings. | इस राइड को बिना नाम के गिना जाएगा और आपका जवाब कम्युनिटी मैप में जुड़ेगा। आपका नाम या नंबर कभी नहीं। इसे आप सेटिंग्स में बदल सकती हैं। |  |
| `consent_stats` | Also send anonymous usage stats: which features get used, never where you go. | साथ ही बिना नाम के इस्तेमाल के आँकड़े भेजें: कौन से फ़ीचर इस्तेमाल हुए, आप कहाँ गईं यह कभी नहीं। |  |
| `consent_yes` | Yes, share | हाँ, शेयर करें |  |
| `consent_no` | No thanks | नहीं, रहने दें |  |
| `felt_title` | Anything felt off on that trip? | उस सफ़र में कुछ ठीक नहीं लगा? |  |
| `felt_sub` | You don't need to say what happened. | क्या हुआ, यह बताना ज़रूरी नहीं। |  |
| `felt_nothing` | Nothing | कुछ नहीं |  |
| `felt_off` | Something felt off, nothing major | कुछ ठीक नहीं लगा, पर कुछ बड़ा नहीं |  |
| `felt_happened` | Something happened | कुछ हुआ |  |

## My trips

| String name | English | Hindi | Notes |
|---|---|---|---|
| `trips_title` | My trips | मेरे सफ़र |  |
| ⚠ `trips_empty` | Trips Saathi watched will appear here, each with its sealed record. | जिन सफ़र पर Saathi ने नज़र रखी, वे यहाँ दिखेंगे, हर एक के साथ उसका सील किया हुआ रिकॉर्ड। | "sealed record" is a product term. "सील किया हुआ रिकॉर्ड" is literal; check it reads clearly. |
| `trips_plate_matched` | Plate check: matched (%1$s) | प्लेट जाँच: मेल खाई (%1$s) |  |
| `trips_plate_mismatch` | Plate check: saw %1$s, booked %2$s | प्लेट जाँच: दिखी %1$s, बुक थी %2$s |  |
| `trips_plate_other` | Plate check: %1$s | प्लेट जाँच: %1$s | Fallback for an old record whose note can't be read. |
| `trips_alert_sent` | Alert sent at %1$s. Told: %2$s | %1$s पर अलर्ट भेजा गया। बताया गया: %2$s |  |
| `trips_alert_none` | No one was reached at %1$s. | %1$s पर किसी तक मैसेज नहीं पहुँचा। |  |
| `trips_alert_retry` | Tried again at %1$s. Told: %2$s | %1$s पर फिर कोशिश की गई। बताया गया: %2$s |  |
| `trips_sealed` | Sealed · fingerprints | सील · फ़िंगरप्रिंट | "fingerprint" here means the SHA-256 hash of a file. |
| `trips_evidence_location` | location log | लोकेशन लॉग |  |
| `trips_evidence_audio` | audio %1$s | ऑडियो %1$s | %1$s is the recording's file name. |
| `trips_guide` | Reporting options and draft | रिपोर्ट के विकल्प और ड्राफ़्ट |  |
| `trips_delete` | Delete | डिलीट करें |  |
| `trips_delete_armed` | Tap again to delete | डिलीट करने के लिए फिर टैप करें |  |

## Settings

| String name | English | Hindi | Notes |
|---|---|---|---|
| `settings_title` | Settings | सेटिंग्स |  |
| `settings_you` | You | आप |  |
| `settings_you_hint` | Shown in alerts, like "%1$s hasn't answered a check-in." | अलर्ट में आपका नाम ऐसे दिखता है: "%1$s hasn't answered a check-in." | The quoted example is the English alert SMS (it stays English for now), so it stays English here. |
| `settings_name_label` | Your first name | आपका पहला नाम |  |
| `settings_name_problem` | Your name is shown in alerts. | आपका नाम अलर्ट में दिखाया जाता है। |  |
| `settings_language` | Language | भाषा |  |
| `settings_language_follow` | Saathi follows your phone's language. | Saathi आपके फ़ोन की भाषा के हिसाब से चलता है। |  |
| `settings_people` | People to text | जिन्हें मैसेज जाए |  |
| `contacts_hint` | If you don't answer a check-in, Saathi texts these people from your own SIM. | अगर आप चेक-इन का जवाब नहीं देतीं, तो Saathi इन लोगों को आपके अपने SIM से मैसेज करता है। |  |
| `contacts_empty_problem` | Add at least one person, or alerts can't go anywhere. | कम से कम एक व्यक्ति जोड़ें, वरना अलर्ट किसी तक नहीं पहुँचेगा। |  |
| `settings_write_contacts` | Write them a message about Saathi | उन्हें Saathi के बारे में मैसेज लिखें |  |
| `settings_places_hint` | Home and work, say. Saathi uses them to notice a detour or a late arrival. | जैसे घर और ऑफ़िस। Saathi इनसे रास्ता बदलने या देर से पहुँचने का पता लगाता है। |  |
| `settings_help_options` | Help options | मदद के विकल्प |  |
| `settings_speak_says` | Speak for me says | "मेरी तरफ़ से बोलें" यह कहता है |  |
| `settings_phrase_touched` | "This man touched me. He's still here. Please help me." | "इस आदमी ने मुझे छुआ। वह अभी भी यहीं है। कृपया मेरी मदद करें।" | Same Hindi as the spoken phrase in phrases.json (not edited here). |
| `settings_phrase_following` | "This man is following me. Please help me." | "यह आदमी मेरा पीछा कर रहा है। कृपया मेरी मदद करें।" | Same Hindi as phrases.json. |
| `settings_phrase_help` | "I need help. Please stay with me and call for help." | "मुझे मदद चाहिए। कृपया मेरे साथ रहें और मदद बुलाएँ।" | Same Hindi as phrases.json. |
| `settings_tile` | Quick Settings tile | क्विक सेटिंग्स टाइल |  |
| `settings_tile_body` | Pull down from the top of the screen, even when locked, and tap Saathi help. You get 5 seconds to tap again if it was a mistake. | स्क्रीन के ऊपर से नीचे खींचें, लॉक होने पर भी, और Saathi मदद पर टैप करें। गलती से दब गया हो तो 5 सेकंड में दोबारा टैप करके रोक सकती हैं। |  |
| `settings_tile_add` | Add to Quick Settings | क्विक सेटिंग्स में जोड़ें |  |
| `settings_tile_manual` | Edit your Quick Settings (the pencil icon) and drag Saathi help in. | क्विक सेटिंग्स में एडिट (पेंसिल का निशान) दबाएँ और Saathi मदद को खींचकर जोड़ दें। |  |
| `settings_power` | Power button | पावर बटन |  |
| `settings_presses` | one: %1$d press<br>other: %1$d presses | one: %1$d बार<br>other: %1$d बार |  |
| `settings_off` | Off | बंद |  |
| `settings_power_hint` | Off at first, because many phones already use quick presses for the camera or Emergency SOS. If yours doesn't, pick a number and try it. If presses open your camera, set it back to Off and use the Quick Settings tile. | शुरू में यह बंद रहता है, क्योंकि कई फ़ोन तेज़ प्रेस पर कैमरा या Emergency SOS खोल देते हैं। आपके फ़ोन में ऐसा नहीं होता, तो कोई संख्या चुनकर आज़माएँ। प्रेस करने पर कैमरा खुले, तो इसे फिर बंद कर दें और क्विक सेटिंग्स टाइल इस्तेमाल करें। |  |
| `settings_community` | Community sharing | कम्युनिटी शेयरिंग |  |
| `settings_community_body` | Share anonymously: count my rides by vehicle plate and add my reports to the community map. Never my name or number. | बिना नाम के शेयर करें: मेरी राइड को गाड़ी की प्लेट से गिनें और मेरी रिपोर्ट कम्युनिटी मैप में जोड़ें। मेरा नाम या नंबर कभी नहीं। |  |
| `settings_improve` | Help improve Saathi | Saathi को बेहतर बनाने में मदद करें |  |
| `settings_crash_title` | Send crash reports | क्रैश रिपोर्ट भेजें |  |
| `settings_crash_body` | If Saathi crashes, send what went wrong. Numbers, names and places are removed first. | अगर Saathi क्रैश हो, तो बताएँ कि क्या गड़बड़ हुई। नंबर, नाम और जगहें पहले हटा दी जाती हैं। |  |
| `settings_usage_title` | Send usage stats | इस्तेमाल के आँकड़े भेजें |  |
| `settings_usage_body` | Which features get used, rounded to the hour. Never where you go or who you text. | कौन से फ़ीचर इस्तेमाल हुए, घंटे के हिसाब से। आप कहाँ जाती हैं या किसे मैसेज करती हैं, यह कभी नहीं। |  |
| `settings_permissions` | Permissions | अनुमतियाँ |  |
| `settings_permissions_hint` | Tap Allow on anything that's missing. | जो अनुमति बाकी है, उस पर "अनुमति दें" दबाएँ। |  |
| `settings_practice` | Practice | अभ्यास |  |
| `settings_practice_button` | Practise a check-in | चेक-इन का अभ्यास करें |  |
| `settings_practice_hint` | Lock your phone. Saathi rings in 5 seconds. | अपना फ़ोन लॉक करें। Saathi 5 सेकंड में बजेगा। |  |
| `settings_app` | App | ऐप |  |
| `settings_version` | Version %1$s | वर्शन %1$s |  |
| `settings_check_updates` | Check for updates | अपडेट देखें |  |
| `settings_checking` | Checking… | देख रहे हैं… |  |
| `settings_up_to_date` | You're on the latest version. | आपके पास सबसे नया वर्शन है। |  |
| `settings_update_failed` | Couldn't check for updates. Try again later. | अपडेट नहीं देख पाए। बाद में फिर कोशिश करें। |  |

## Setup: flow

| String name | English | Hindi | Notes |
|---|---|---|---|
| `setup_step` | Step %1$d of %2$d | स्टेप %1$d / %2$d |  |
| `setup_continue` | Continue | आगे बढ़ें |  |
| `setup_finish` | Finish | पूरा करें |  |
| `setup_start_practice` | Start practice | अभ्यास शुरू करें |  |
| `setup_skip` | Skip | छोड़ें |  |
| `setup_skip_practice` | Skip practice | अभ्यास छोड़ें |  |
| `setup_later` | Set up later | बाद में सेट करें |  |
| `setup_skip_title` | Skip for now? | अभी छोड़ें? |  |
| `setup_skip_body` | Without these, Saathi can't watch trips or send alerts. You can finish this in Settings. | इनके बिना Saathi न सफ़र पर नज़र रख सकता है, न अलर्ट भेज सकता है। आप इसे सेटिंग्स में पूरा कर सकती हैं। |  |
| `setup_go_back` | Go back | वापस जाएँ |  |
| `setup_skip_anyway` | Skip anyway | फिर भी छोड़ें |  |

## Setup: people to text

| String name | English | Hindi | Notes |
|---|---|---|---|
| `setup_contacts_title` | Who should we tell? | किसे बताएँ? |  |
| `setup_name_hint` | Shown in alerts: "%1$s hasn't answered a check-in." | अलर्ट में दिखता है: "%1$s hasn't answered a check-in." | Example is the English alert SMS, so it stays English. |
| `contacts_heading` | People to text | जिन्हें मैसेज जाए |  |
| `contacts_empty` | No one yet. Pick at least one person you'd want to know. | अभी कोई नहीं। कम से कम एक ऐसा व्यक्ति चुनें जिसे आप बताना चाहेंगी। |  |
| `contacts_choose` | Choose from contacts | कॉन्टैक्ट में से चुनें |  |
| `contacts_type` | Type a number instead | नंबर खुद लिखें |  |
| `contacts_name` | Name | नाम |  |
| `contacts_phone` | Phone number | फ़ोन नंबर |  |
| `contacts_phone_hint` | With country code, like +91 98765 43210 | देश के कोड के साथ, जैसे +91 98765 43210 |  |
| `contacts_city_anywhere` | Texted wherever you are | आप जहाँ भी हों, मैसेज जाएगा |  |
| `contacts_city_in` | Texted when you're in %1$s | आप %1$s में हों तो मैसेज जाएगा |  |
| `contacts_anywhere` | Anywhere | कहीं भी |  |
| `contacts_other_city` | Other city… | दूसरा शहर… |  |
| `contacts_which_city` | Which city? | कौन सा शहर? |  |
| `contacts_city` | City | शहर |  |
| `problem_phone_missing` | Add a phone number for %1$s. | %1$s का फ़ोन नंबर जोड़ें। |  |
| `problem_phone_incomplete` | This number looks incomplete. Add the country code, like +91. | यह नंबर अधूरा लगता है। देश का कोड जोड़ें, जैसे +91। |  |
| `problem_name_missing` | Add a name for this number. | इस नंबर के लिए नाम जोड़ें। |  |
| `problem_your_name` | Add your first name, so contacts know who the alert is from. | अपना पहला नाम जोड़ें, ताकि कॉन्टैक्ट को पता चले कि अलर्ट किसका है। |  |
| `problem_no_contacts` | Add at least one person to text. | मैसेज के लिए कम से कम एक व्यक्ति जोड़ें। |  |

## Setup: permissions

| String name | English | Hindi | Notes |
|---|---|---|---|
| `perm_heading` | Let Saathi watch your trips | Saathi को अपने सफ़र पर नज़र रखने दें |  |
| `perm_subheading` | Android asks once for each. Saathi uses them only on trips and alerts. | Android हर एक के लिए एक बार पूछता है। Saathi इन्हें सिर्फ़ सफ़र और अलर्ट में इस्तेमाल करता है। |  |
| `perm_optional` | Optional | ज़रूरी नहीं |  |
| `perm_location` | Location | लोकेशन |  |
| `perm_location_why` | To know where your trip goes. | ताकि पता रहे कि आपका सफ़र कहाँ जा रहा है। |  |
| `perm_location_always` | Location all the time | हर समय लोकेशन |  |
| `perm_location_always_why` | So Saathi keeps watching with the screen off. | ताकि स्क्रीन बंद होने पर भी Saathi नज़र रखे। |  |
| `perm_sms` | Text messages | टेक्स्ट मैसेज |  |
| `perm_sms_why` | Alerts go from your own SIM, even with no data. | अलर्ट आपके अपने SIM से जाते हैं, डेटा न हो तब भी। |  |
| `perm_notifications` | Notifications | नोटिफ़िकेशन |  |
| `perm_notifications_why` | For check-ins and the trip card. | चेक-इन और सफ़र कार्ड के लिए। |  |
| `perm_lock_screen` | Check-ins over the lock screen | लॉक स्क्रीन पर चेक-इन |  |
| `perm_lock_screen_why` | So 'Are you okay?' shows even when your phone is locked. | ताकि फ़ोन लॉक होने पर भी "क्या आप ठीक हैं?" दिखे। |  |
| `perm_background` | Keep running in the background | बैकग्राउंड में चलता रहे |  |
| `perm_background_why` | So Android doesn't stop Saathi mid-trip. | ताकि Android सफ़र के बीच Saathi को बंद न करे। |  |
| `perm_rides` | Notice Uber, Ola and Rapido rides | Uber, Ola और Rapido की राइड पहचानें |  |
| `perm_rides_why` | Trips start on their own when a ride is booked. | राइड बुक होते ही सफ़र अपने-आप शुरू हो जाता है। |  |
| `perm_microphone` | Microphone | माइक्रोफ़ोन |  |
| `perm_microphone_why` | Records only after help is raised. | सिर्फ़ मदद माँगने के बाद रिकॉर्ड करता है। |  |
| `perm_allow_label` | Allow %1$s | %1$s की अनुमति दें | Screen-reader label; %1$s is the permission title. |
| `perm_allow` | Allow | अनुमति दें |  |
| `perm_allowed` | Allowed | अनुमति मिल गई |  |
| ⚠ `perm_always_steps` | Tap Allow, then Permissions › Location › Allow all the time. | "अनुमति दें" दबाएँ, फिर अनुमतियाँ › लोकेशन › हमेशा अनुमति दें चुनें। | Android's own Hindi menu names differ by phone ("जगह की जानकारी" for Location on some). Check on a phone set to Hindi. |
| `perm_after_first` | After the first one | पहली अनुमति के बाद |  |
| `perm_needed` | Needed so alerts can reach your contacts. | ज़रूरी है, ताकि अलर्ट आपके कॉन्टैक्ट तक पहुँच सकें। |  |

## Setup and Settings: keep Saathi running

| String name | English | Hindi | Notes |
|---|---|---|---|
| `perm_keep_title` | Keep Saathi running | Saathi को चालू रखें |  |
| `perm_keep_why` | Your phone stops apps in the background. Saathi has to keep watching during a trip. | आपका फ़ोन बैकग्राउंड में ऐप बंद कर देता है। सफ़र में Saathi को नज़र रखते रहना है। |  |
| `perm_keep_open` | Open settings | सेटिंग्स खोलें |  |
| `perm_keep_needed` | Needed so Saathi keeps watching during a trip. | ज़रूरी है, ताकि सफ़र में Saathi नज़र रखता रहे। |  |
| ⚠ `oem_xiaomi_1` | Turn on Autostart for Saathi. | Saathi के लिए Autostart चालू करें। | Phone-maker menu names are left in English, as they appear on most Indian phones. A phone set to Hindi may show them translated. |
| `oem_xiaomi_2` | In Battery saver, choose No restrictions. | Battery saver में "No restrictions" चुनें। |  |
| `oem_xiaomi_3` | In recent apps, hold Saathi and tap the lock. | रीसेंट ऐप्स में Saathi को दबाए रखें और लॉक पर टैप करें। |  |
| `oem_vivo_1` | Turn on Autostart for Saathi. | Saathi के लिए Autostart चालू करें। |  |
| `oem_vivo_2` | In Battery, allow Saathi to run in the background. | Battery में Saathi को बैकग्राउंड में चलने दें। |  |
| `oem_vivo_3` | In recent apps, lock Saathi. | रीसेंट ऐप्स में Saathi को लॉक करें। |  |
| `oem_oppo_1` | Turn on Auto launch for Saathi. | Saathi के लिए Auto launch चालू करें। |  |
| `oem_oppo_2` | In Battery, allow background activity for Saathi. | Battery में Saathi को बैकग्राउंड एक्टिविटी की इजाज़त दें। |  |
| `oem_oppo_3` | In recent apps, lock Saathi. | रीसेंट ऐप्स में Saathi को लॉक करें। |  |
| `oem_oneplus_1` | Turn on Auto launch for Saathi. | Saathi के लिए Auto launch चालू करें। |  |
| `oem_oneplus_2` | In Battery, choose Don't optimise for Saathi. | Battery में Saathi के लिए "Don't optimise" चुनें। |  |
| `oem_oneplus_3` | In recent apps, lock Saathi. | रीसेंट ऐप्स में Saathi को लॉक करें। |  |
| `oem_samsung_1` | In Battery, open Background usage limits. | Battery में "Background usage limits" खोलें। |  |
| `oem_samsung_2` | Take Saathi out of Sleeping apps and Deep sleeping apps. | Saathi को "Sleeping apps" और "Deep sleeping apps" से हटाएँ। |  |
| `oem_samsung_3` | Add Saathi to Never sleeping apps. | Saathi को "Never sleeping apps" में जोड़ें। |  |
| `oem_huawei_1` | Open App launch and set Saathi to Manage manually. | "App launch" खोलें और Saathi को "Manage manually" पर रखें। |  |
| `oem_huawei_2` | Turn on Auto-launch, Secondary launch and Run in background. | "Auto-launch", "Secondary launch" और "Run in background" चालू करें। |  |
| `oem_huawei_3` | In recent apps, lock Saathi. | रीसेंट ऐप्स में Saathi को लॉक करें। |  |

## Home: the phone stopped Saathi

| String name | English | Hindi | Notes |
|---|---|---|---|
| `stopped_text` | Your phone stopped Saathi at %1$s, during a trip. It wasn't watching after that. | आपके फ़ोन ने सफ़र के दौरान %1$s पर Saathi को बंद कर दिया। उसके बाद वह नज़र नहीं रख रहा था। |  |
| `stopped_text_yesterday` | Your phone stopped Saathi yesterday at %1$s, during a trip. It wasn't watching after that. | आपके फ़ोन ने सफ़र के दौरान कल %1$s पर Saathi को बंद कर दिया। उसके बाद वह नज़र नहीं रख रहा था। |  |
| `stopped_text_date` | Your phone stopped Saathi on %1$s at %2$s, during a trip. It wasn't watching after that. | आपके फ़ोन ने सफ़र के दौरान %1$s को %2$s पर Saathi को बंद कर दिया। उसके बाद वह नज़र नहीं रख रहा था। | %1$s is a short localised date such as "21 Oct"; %2$s is the time. |
| `stopped_fix` | Fix this | इसे ठीक करें |  |
| `stopped_dismiss` | Dismiss | हटाएँ |  |

## Setup: places

| String name | English | Hindi | Notes |
|---|---|---|---|
| `places_title` | Places you go often | जहाँ आप अक्सर जाती हैं |  |
| `places_hint_setup` | With two places, Saathi knows where a trip from one is going and notices detours. You can skip this. | दो जगहें हों तो Saathi जान लेता है कि एक से निकला सफ़र किधर जा रहा है, और रास्ता बदलने पर पकड़ लेता है। आप इसे छोड़ भी सकती हैं। |  |
| `place_home` | Home | घर |  |
| ⚠ `place_work` | Work | ऑफ़िस | "Work" as a place. "ऑफ़िस" suits most commuters; "काम की जगह" is the alternative. |
| `place_college` | College | कॉलेज |  |
| `place_other` | Other | दूसरी जगह |  |
| `places_add` | Add a place | जगह जोड़ें |  |
| `places_name_label` | Name this place | इस जगह का नाम रखें |  |
| `places_name_placeholder` | Gym | जिम |  |
| `places_name_problem` | Give this place a name first. | पहले इस जगह का नाम रखें। |  |
| `places_search` | Search an address or place | पता या जगह खोजें |  |
| `places_search_note` | Addresses you type are looked up with OpenRouteService. | आप जो पता लिखती हैं, उसे OpenRouteService से खोजा जाता है। |  |
| `places_clear` | Clear | मिटाएँ |  |
| `places_use_current` | Use my current location | मेरी मौजूदा लोकेशन इस्तेमाल करें |  |
| `places_searching` | Searching… | खोज रहे हैं… |  |
| `places_no_matches` | No matches. Try a landmark or area name. | कुछ नहीं मिला। कोई लैंडमार्क या इलाके का नाम आज़माएँ। |  |
| `places_locate_failed` | Couldn't find where you are. Try again outside, or search instead. | आपकी लोकेशन नहीं मिली। बाहर खुले में फिर कोशिश करें, या खोजकर चुनें। |  |
| `places_allow_location` | Allow location to use where you are, or search instead. | अपनी जगह इस्तेमाल करने के लिए लोकेशन की अनुमति दें, या खोजकर चुनें। |  |
| `places_near_here` | Near your current location | आपकी मौजूदा लोकेशन के पास |  |
| `places_saved` | Saved place | सेव की हुई जगह |  |
| `places_from_location` | From your location | आपकी लोकेशन से |  |

## Setup: practice

| String name | English | Hindi | Notes |
|---|---|---|---|
| `practice_title` | One practice, so the real one isn't a surprise. | एक अभ्यास, ताकि असली चेक-इन चौंकाए नहीं। |  |
| `practice_body` | On a trip, if something looks wrong, Saathi rings loudly and asks if you're okay. Let's try it once. Nothing is sent. | सफ़र में कुछ गड़बड़ लगे तो Saathi ज़ोर से बजता है और पूछता है कि आप ठीक हैं या नहीं। चलिए, एक बार आज़माते हैं। कुछ भेजा नहीं जाएगा। |  |
| `practice_fact_rings` | It rings, even on silent | साइलेंट पर भी बजता है |  |
| `practice_fact_lock` | It shows over your lock screen | लॉक स्क्रीन के ऊपर दिखता है |  |
| `practice_fact_tap` | Tap I'm okay to stop it | रोकने के लिए "मैं ठीक हूँ" दबाएँ |  |
| `practice_lock_now` | Lock your phone now | अब अपना फ़ोन लॉक करें |  |
| `practice_will_check` | Saathi will check in, like on a trip. | सफ़र की तरह Saathi चेक-इन करेगा। |  |
| `practice_tap_okay` | Tap I'm okay on the check-in. | चेक-इन पर "मैं ठीक हूँ" दबाएँ। |  |
| `practice_done` | Practice done | अभ्यास पूरा हुआ |  |
| `practice_done_body` | That's what a check-in feels like. You can practise again from Settings. | चेक-इन ऐसा लगता है। आप सेटिंग्स से फिर अभ्यास कर सकती हैं। |  |

## Check-in screen

| String name | English | Hindi | Notes |
|---|---|---|---|
| `checkin_title` | Are you okay? | क्या आप ठीक हैं? |  |
| `checkin_pill_practice` | Practice | अभ्यास |  |
| `checkin_body` | If you don't answer, Saathi will text your contacts with your location. | अगर आप जवाब नहीं देतीं, तो Saathi आपके कॉन्टैक्ट को आपकी लोकेशन के साथ मैसेज करेगा। |  |
| `checkin_body_practice` | This is a practice. Nothing will be sent. | यह अभ्यास है। कुछ भेजा नहीं जाएगा। |  |
| `checkin_seconds` | seconds before your contacts get a text | सेकंड में आपके कॉन्टैक्ट को मैसेज जाएगा | Sits under a big countdown number. |
| `checkin_seconds_practice` | seconds left in the practice | सेकंड बचे हैं अभ्यास में | Sits under a big countdown number. |
| `checkin_fact_alarm` | Alarm on | अलार्म चालू |  |
| `checkin_fact_vibrating` | Vibrating | वाइब्रेशन चालू |  |
| `checkin_fact_lock` | Over lock screen | लॉक स्क्रीन पर |  |
| ⚠ `checkin_stalled` | The thread has stopped moving. | धागा आगे नहीं बढ़ रहा। | "The thread" is the trip line of stars. The image may not carry in Hindi; a plainer line is "सफ़र रुक गया है।". |
| `checkin_practice_over` | Practice over | अभ्यास खत्म |  |
| `checkin_practice_over_body` | On a real trip, your contacts would get a text now with where you are. | असली सफ़र में अब आपके कॉन्टैक्ट को आपकी लोकेशन के साथ मैसेज जाता। |  |
| `checkin_nothing_sent` | Nothing was sent. | कुछ भेजा नहीं गया। |  |
| `checkin_recording` | Recording on this phone | इस फ़ोन पर रिकॉर्डिंग चल रही है |  |
| `checkin_none` | No check-in right now | अभी कोई चेक-इन नहीं |  |

## Speak for me

| String name | English | Hindi | Notes |
|---|---|---|---|
| `speak_speaking` | %1$s · speaking | %1$s · बोल रहा है |  |
| `speak_not_sent` | Not sent. Speak for me is still on screen. | भेजा नहीं गया। "मेरी तरफ़ से बोलें" अभी स्क्रीन पर है। |  |
| `speak_again` | Say it again | फिर से बोलें |  |
| `speak_told_sub` | Live location sent · recording on this phone · %1$s | लाइव लोकेशन भेजी गई · इस फ़ोन पर रिकॉर्डिंग चल रही है · %1$s | %1$s is the "Saathi doesn't call 112" line. |
| `speak_texting` | Texting %1$s in %2$d… | %2$d सेकंड में %1$s को मैसेज जाएगा… |  |
| `speak_hint` | Tap Don't text if you opened this by mistake. | अगर यह गलती से खुल गया हो, तो "मैसेज न भेजें" दबाएँ। |  |
| `speak_dont_text` | Don't text | मैसेज न भेजें |  |

## Plate check

| String name | English | Hindi | Notes |
|---|---|---|---|
| `plate_title` | Check the plate | प्लेट जाँचें |  |
| `plate_title_match` | It matches your booking | यह आपकी बुकिंग से मेल खाती है |  |
| `plate_title_mismatch` | This isn't the plate you booked | यह वह प्लेट नहीं है जो आपने बुक की थी |  |
| `plate_body` | Snap the number plate of the vehicle in front of you. Saathi compares it with your booking. | अपने सामने खड़ी गाड़ी की नंबर प्लेट की फ़ोटो लें। Saathi उसे आपकी बुकिंग से मिलाएगा। |  |
| `plate_body_match` | The plate in front of you is the one your ride app booked. | आपके सामने की प्लेट वही है जो आपके राइड ऐप ने बुक की थी। |  |
| `plate_body_mismatch` | The vehicle in front of you has a different number from your booking. | आपके सामने की गाड़ी का नंबर आपकी बुकिंग से अलग है। |  |
| `plate_in_front` | In front of you | आपके सामने |  |
| `plate_booked` | Booked | बुक की हुई |  |
| `plate_none_shown` | No plate shown | प्लेट नहीं दिख रही |  |
| `plate_unreadable` | Couldn't read the plate. Type it below. | प्लेट पढ़ नहीं पाए। नीचे लिखें। |  |
| `plate_type_label` | Or type the plate you see | या जो प्लेट दिख रही है, वह लिखें |  |
| `plate_compare` | Compare | मिलाएँ |  |
| `plate_reading` | Reading the plate… | प्लेट पढ़ रहे हैं… |  |
| `plate_snap` | Snap the plate | प्लेट की फ़ोटो लें |  |
| `plate_dont_get_in` | Don't get in | गाड़ी में न बैठें |  |
| `plate_tell_contacts` | Tell my contacts | मेरे कॉन्टैक्ट को बताएँ |  |
| `plate_its_fine` | It's fine | सब ठीक है |  |
| `plate_notif_title` | Your ride is here. Check the plate | आपकी राइड आ गई है। प्लेट जाँचें |  |
| `plate_notif_text` | Snap the number plate and Saathi will compare it with %1$s. | नंबर प्लेट की फ़ोटो लें, Saathi उसे %1$s से मिलाएगा। |  |
| `plate_notif_text_booking` | Snap the number plate and Saathi will compare it with your booking. | नंबर प्लेट की फ़ोटो लें, Saathi उसे आपकी बुकिंग से मिलाएगा। |  |

## Companion call

| String name | English | Hindi | Notes |
|---|---|---|---|
| `companion_title` | Saathi is on the call | Saathi कॉल पर है |  |
| `companion_online` | Speak out loud, so anyone near you hears you're on a call. | ज़ोर से बोलें, ताकि पास के लोग सुनें कि आप कॉल पर हैं। |  |
| `companion_offline` | No connection. Use Help or Speak for me. | इंटरनेट नहीं है। मदद या "मेरी तरफ़ से बोलें" इस्तेमाल करें। |  |
| `companion_told` | Your contacts were told: %1$s | आपके कॉन्टैक्ट को बता दिया गया: %1$s |  |
| `companion_type` | Or type | या लिखें |  |
| `companion_send` | Send | भेजें |  |
| `companion_listening` | Listening… | सुन रहे हैं… |  |
| `companion_hold` | Hold to talk | बोलने के लिए दबाए रखें |  |
| `companion_end` | End call | कॉल खत्म करें |  |

## Community map

| String name | English | Hindi | Notes |
|---|---|---|---|
| `map_title` | Community map | कम्युनिटी मैप |  |
| `map_sample` | Sample data | सैंपल डेटा |  |
| `map_sub` | Where women reported something, even small things, in the last 90 days. Never names, never exact places. | पिछले 90 दिनों में महिलाओं ने कहाँ कुछ रिपोर्ट किया, छोटी बातें भी। नाम कभी नहीं, सटीक जगह कभी नहीं। |  |
| `map_after_9` | After 9 pm | रात 9 बजे के बाद |  |
| `map_all_day` | All day | पूरे दिन |  |
| `map_offline` | No connection. The map needs data. | इंटरनेट नहीं है। मैप के लिए डेटा चाहिए। |  |
| `map_note` | one: %1$d report within %2$d m of here in the last 90 days.<br>other: %1$d reports within %2$d m of here in the last 90 days. | one: पिछले 90 दिनों में यहाँ से %2$d मीटर के अंदर %1$d रिपोर्ट।<br>other: पिछले 90 दिनों में यहाँ से %2$d मीटर के अंदर %1$d रिपोर्ट। |  |
| `map_note_night` | one: %1$d report after 9 pm within %2$d m of here in the last 90 days.<br>other: %1$d reports after 9 pm within %2$d m of here in the last 90 days. | one: पिछले 90 दिनों में यहाँ से %2$d मीटर के अंदर रात 9 बजे के बाद %1$d रिपोर्ट।<br>other: पिछले 90 दिनों में यहाँ से %2$d मीटर के अंदर रात 9 बजे के बाद %1$d रिपोर्ट। |  |

## Reporting guide (chrome only; the guide text stays English)

| String name | English | Hindi | Notes |
|---|---|---|---|
| `guide_title` | What you can do next | आगे आप क्या कर सकती हैं |  |
| `guide_english_note` | The guide below is in English for now. | नीचे की जानकारी अभी सिर्फ़ अंग्रेज़ी में है। | Shown only when the app is not in English. The legal guide text needs a qualified review before it is translated. |
| `guide_draft_heading` | Draft complaint | शिकायत का ड्राफ़्ट |  |
| `guide_copy` | Copy the draft, to edit and send yourself | ड्राफ़्ट कॉपी करें, खुद बदलकर भेजें |  |
| `guide_no_record` | No trip record yet, so there's no draft. Trips Saathi watches leave one. | अभी कोई सफ़र रिकॉर्ड नहीं है, इसलिए ड्राफ़्ट भी नहीं है। जिन सफ़र पर Saathi नज़र रखता है, उनका रिकॉर्ड बनता है। |  |

## Update dialog

| String name | English | Hindi | Notes |
|---|---|---|---|
| `update_important` | Important update | ज़रूरी अपडेट |  |
| `update_available` | Update available (v%1$s) | अपडेट उपलब्ध है (v%1$s) |  |
| `update_mandatory` | This fixes something important. Please update soon. | इसमें कोई ज़रूरी चीज़ ठीक की गई है। कृपया जल्दी अपडेट करें। |  |
| `update_downloading` | Downloading… %1$d%% | डाउनलोड हो रहा है… %1$d%% |  |
| `update_metered` | This is a large download (about 100 MB). Use Wi‑Fi if you can. | यह बड़ा डाउनलोड है (लगभग 100 MB)। हो सके तो Wi‑Fi इस्तेमाल करें। |  |
| `update_allow_install` | Allow Saathi to install updates, then come back and tap Install. | Saathi को अपडेट इंस्टॉल करने की इजाज़त दें, फिर वापस आकर "इंस्टॉल करें" दबाएँ। |  |
| `update_err_installer` | Couldn't open the installer. Try again later. | इंस्टॉलर नहीं खुल पाया। बाद में फिर कोशिश करें। |  |
| `update_err_mismatch` | The download didn't check out, so it wasn't installed. Try again later. | डाउनलोड की जाँच पूरी नहीं हुई, इसलिए इंस्टॉल नहीं किया गया। बाद में फिर कोशिश करें। |  |
| `update_err_failed` | Couldn't download the update. Check your connection and try again. | अपडेट डाउनलोड नहीं हो पाया। इंटरनेट देखें और फिर कोशिश करें। |  |
| `update_button` | Update | अपडेट करें |  |
| `update_install` | Install | इंस्टॉल करें |  |
| `update_later` | Later | बाद में |  |

## Notifications

| String name | English | Hindi | Notes |
|---|---|---|---|
| `notif_channel_guard` | Saathi is ready | Saathi तैयार है | Notification channel name, shown in the phone's notification settings. |
| `notif_channel_checkin` | Check-ins | चेक-इन | Notification channel name. |
| `notif_channel_alert` | Alerts sent | भेजे गए अलर्ट | Notification channel name. |
| `notif_guard_ready` | Saathi is ready | Saathi तैयार है |  |
| `notif_guard_alert` | Alert sent to %1$s | %1$s को अलर्ट भेजा गया |  |
| `guard_text_power` | Press the power button to get help | मदद के लिए पावर बटन दबाएँ |  |
| `guard_text_lock` | Help and Speak for me work from the lock screen | लॉक स्क्रीन से भी मदद और "मेरी तरफ़ से बोलें" काम करते हैं |  |
| `notif_action_stop` | Stop watching | नज़र रखना बंद करें |  |
| `notif_checkin_practice_title` | Practice check-in | चेक-इन का अभ्यास |  |
| `notif_checkin_practice_text` | This is a practice. Nothing will be sent. Tap I'm okay. | यह अभ्यास है। कुछ भेजा नहीं जाएगा। "मैं ठीक हूँ" दबाएँ। |  |
| `notif_checkin_text` | one: Your trip: %1$s. Your contacts will be told in %2$d second.<br>other: Your trip: %1$s. Your contacts will be told in %2$d seconds. | one: आपका सफ़र: %1$s। %2$d सेकंड में आपके कॉन्टैक्ट को बताया जाएगा।<br>other: आपका सफ़र: %1$s। %2$d सेकंड में आपके कॉन्टैक्ट को बताया जाएगा। |  |
| `notif_alert_none` | No SMS went out. | कोई SMS नहीं गया। |  |
| `notif_alert_told` | Told: %1$s. | बताया गया: %1$s। |  |
| `notif_alert_failed` | Failed: %1$s. | नहीं पहुँचा: %1$s। |  |
| `notif_alert_no_112` | Saathi doesn't call 112. | Saathi 112 पर कॉल नहीं करता। |  |
| `vehicle_notif_title` | Before you get in: %1$s | गाड़ी में बैठने से पहले: %1$s |  |
| `vehicle_flag_text` | one: This vehicle has %1$d report from other women in the last 90 days.<br>other: This vehicle has %1$d reports from other women in the last 90 days. | one: पिछले 90 दिनों में दूसरी महिलाओं ने इस गाड़ी की %1$d रिपोर्ट की है।<br>other: पिछले 90 दिनों में दूसरी महिलाओं ने इस गाड़ी की %1$d रिपोर्ट की हैं। |  |
| `vehicle_flag_more` | You can continue, or cancel and rebook in your ride app. | आप जा सकती हैं, या रद्द करके अपने राइड ऐप में दोबारा बुक कर सकती हैं। |  |
| `vehicle_sample` | (Sample data) | (सैंपल डेटा) |  |
| `vehicle_rides` | one: %1$d ride with Saathi users<br>other: %1$d rides with Saathi users | one: Saathi इस्तेमाल करने वालों के साथ %1$d राइड<br>other: Saathi इस्तेमाल करने वालों के साथ %1$d राइड |  |
| ⚠ `vehicle_none_raised` | none raised a concern | किसी ने कोई दिक्कत नहीं बताई | "concern" is rendered as "दिक्कत" (a problem). Must never read as "safe". |
| `ride_watcher_label` | Saathi ride watcher | Saathi राइड वॉचर | Name of the notification-access entry in the phone's Settings. |

## Home-screen widget and Quick Settings tile

| String name | English | Hindi | Notes |
|---|---|---|---|
| `widget_help` | Help | मदद |  |
| `widget_sub` | One tap speaks for you and texts your contacts | एक टैप में आपकी तरफ़ से बोलता है और कॉन्टैक्ट को मैसेज भेजता है |  |
| `widget_sub_trip` | Watching your trip · one tap speaks for you and texts your contacts | आपके सफ़र पर नज़र है · एक टैप में आपकी तरफ़ से बोलता है और कॉन्टैक्ट को मैसेज भेजता है |  |
| `widget_slide` | Slide for Walk, Bus, Train | पैदल, बस, ट्रेन के लिए स्लाइड करें |  |
| `widget_back` | Back to SOS | SOS पर वापस |  |
| `tile_contacts_told` | Contacts told | कॉन्टैक्ट को बता दिया गया |  |
| `tile_texting_soon` | Texting in 5 s · tap to stop | 5 सेकंड में मैसेज · रोकने के लिए टैप करें |  |
| `tile_finish_setup` | Finish setup first | पहले सेटअप पूरा करें |  |
| `tile_tap_text_name` | Tap to text %1$s | %1$s को मैसेज करने के लिए टैप करें |  |
| `tile_tap_text_contacts` | Tap to text your contacts | अपने कॉन्टैक्ट को मैसेज करने के लिए टैप करें |  |

## New on 2026-10-22: alerting additions (not yet reviewed)

Added with the "Check on me" timer, arrival, back-tap, earphone button, shake, Call 112 and the one-tap intro text. All are drafts. `ready_told_message`, `no_112_full` and `settings_intro_contacts` replace earlier wording in the tables above; `ready_told_write` and `settings_write_contacts` were removed.

| String name | English | Hindi | Notes |
|---|---|---|---|
| `ready_told_sub` | One text from your SIM, so a Saathi alert doesn't surprise them. | आपकी SIM से एक मैसेज, ताकि Saathi का अलर्ट आने पर वे चौंकें नहीं। |  |
| `ready_told_send` | Text them | उन्हें मैसेज भेजें |  |
| `ready_told_message` | Hi, I've added you as a contact in Saathi, my travel safety app. If you ever get a text from my number starting "Saathi alert", it's real. Please call me straight away and open the location link. | नमस्ते, मैंने आपको Saathi में कॉन्टैक्ट के तौर पर जोड़ा है। यह मेरा सफ़र की सुरक्षा वाला ऐप है। अगर कभी मेरे नंबर से "Saathi alert" से शुरू होने वाला मैसेज आए, तो वह सच है। कृपया तुरंत मुझे कॉल करें और लोकेशन का लिंक खोलें। | Sent to her contacts from her SIM, in the app's language. Keeps the English "Saathi alert": the alert SMS itself is English. |
| `intro_sent` | Sent to %1$s | %1$s को भेज दिया |  |
| `intro_failed` | Couldn't send to %1$s. Try again. | %1$s को नहीं भेज पाए। फिर से कोशिश करें। |  |
| `no_112_full` | Saathi doesn't call 112 for you. Tap Call 112 to dial it. | Saathi आपके लिए 112 पर कॉल नहीं करता। डायल करने के लिए "112 पर कॉल करें" दबाएँ। |  |
| `call_112` | Call 112 | 112 पर कॉल करें |  |
| `settings_intro_contacts` | Text %1$s that you added them | %1$s को मैसेज भेजें कि आपने उन्हें जोड़ा है |  |
| `settings_dying_title` | Low battery and switched-off texts | बैटरी कम होने और फ़ोन बंद होने पर मैसेज |  |
| `settings_dying_body` | During a trip, text your contacts your last location if the battery reaches 5% or the phone is switched off. Some phones switch off too fast for the text to go. | सफ़र के दौरान बैटरी 5% पर पहुँचे या फ़ोन बंद हो, तो आपके कॉन्टैक्ट को आपकी आख़िरी लोकेशन मैसेज की जाएगी। कुछ फ़ोन इतनी जल्दी बंद होते हैं कि मैसेज नहीं जा पाता। |  |
| `check_title` | Check on me | मेरा हाल पूछें | ⚠ Feature name. "Check on me" rendered as "ask how I am". |
| `check_sub` | No ride app? Saathi asks if you're okay when the time runs out. | राइड ऐप नहीं है? समय पूरा होने पर Saathi पूछेगा कि आप ठीक हैं या नहीं। |  |
| `check_set` | Set a time | समय चुनें |  |
| `check_minutes` | one: %1$d min<br>other: %1$d min | one: %1$d मिनट<br>other: %1$d मिनट |  |
| `check_running` | Checking on you at %1$s | %1$s बजे आपका हाल पूछेंगे | ⚠ Word order around the time. |
| `check_add` | Add 10 min | 10 मिनट और |  |
| `check_cancel` | Cancel check | जाँच रद्द करें |  |
| `event_timer` | one: your %1$d-minute check ran out<br>other: your %1$d-minute check ran out | one: आपका %1$d मिनट का समय पूरा हुआ<br>other: आपका %1$d मिनट का समय पूरा हुआ |  |
| `line_ends_at_place` | Ends on its own when you reach %1$s. | %1$s पहुँचते ही यह अपने-आप बंद हो जाएगा। |  |
| `settings_arrival_title` | Text when I arrive | पहुँचने पर मैसेज |  |
| `settings_arrival_body` | A trip ends on its own when you reach the saved place it was headed to. Turn this on to also text your contacts then, one text each trip. | सफ़र जिस सेव की हुई जगह की ओर था, वहाँ पहुँचते ही सफ़र अपने-आप खत्म हो जाता है। इसे चालू करें तो उसी समय आपके कॉन्टैक्ट को भी मैसेज जाएगा, हर सफ़र में एक। |  |
| `help_now_label` | Saathi Help | Saathi मदद | Shows in the app drawer beside Saathi; keep it short. |
| `help_now_short` | Get help | मदद लें |  |
| `help_now_title` | Getting help | मदद माँगी जा रही है |  |
| `help_now_not_sent` | Not sent. Nobody was texted. | नहीं भेजा। किसी को मैसेज नहीं गया। |  |
| `notif_pending_title` | Texting your contacts in %1$d seconds | %1$d सेकंड में आपके कॉन्टैक्ट को मैसेज जाएगा |  |
| `notif_pending_text` | You pressed the earphone button 3 times. | आपने ईयरफ़ोन का बटन 3 बार दबाया। |  |
| `event_shake` | phone shaken hard | फ़ोन ज़ोर से हिलाया गया |  |
| `settings_headset_title` | Earphone button for help | मदद के लिए ईयरफ़ोन का बटन |  |
| `settings_headset_body` | On a trip, press your earphone button 3 times quickly. Your contacts are texted after 5 seconds unless you tap Don't text. Works while no music or video is playing. | सफ़र के दौरान ईयरफ़ोन का बटन जल्दी-जल्दी 3 बार दबाएँ। 5 सेकंड बाद आपके कॉन्टैक्ट को मैसेज जाएगा, जब तक आप "मैसेज न भेजें" न दबाएँ। तभी काम करता है जब कोई गाना या वीडियो न चल रहा हो। | Quotes `speak_dont_text`. |
| `settings_shake_title` | Shake for a check-in | हिलाने पर हाल पूछें |  |
| `settings_shake_body` | On a trip, shake the phone hard 3 times and Saathi asks "Are you okay?". Bumpy rides may set it off. | सफ़र के दौरान फ़ोन को ज़ोर से 3 बार हिलाएँ, तो Saathi पूछेगा "क्या आप ठीक हैं?"। ऊबड़-खाबड़ रास्ते पर यह अपने-आप भी हो सकता है। |  |
| `settings_backtap_title` | Back-tap for help | पीछे टैप करके मदद |  |
| `settings_backtap_body` | Some phones open an app when you tap the back twice. Pick Saathi Help. Pixel: Settings › System › Gestures › Quick Tap. Samsung: Good Lock › RegiStar. You get 5 seconds to undo. Long-press Saathi's icon for the same Get help shortcut. | कुछ फ़ोन में पीछे दो बार टैप करने पर कोई ऐप खुलता है। उसमें Saathi मदद चुनें। Pixel: सेटिंग्स › सिस्टम › जेस्चर › क्विक टैप। Samsung: Good Lock › RegiStar। रद्द करने के लिए 5 सेकंड मिलेंगे। Saathi के आइकन को दबाकर रखने पर भी "मदद लें" मिलता है। | ⚠ The menu paths are the phone makers' own; on a Hindi phone their labels may differ. |
