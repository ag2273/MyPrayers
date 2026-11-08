# Adhan recordings we may redistribute

Research for ticket #12. Researched 2026-11-08 in two passes. Terms follow [`GLOSSARY.md`](../../GLOSSARY.md): **Full Adhan**, **Short Adhan**, **Adhan Alert**.

**How each source was checked**

- **Wikimedia Commons**: licence and author read from the Commons API (`extmetadata`) and from each file page's wikitext. Page history, uploader contributions, talk pages and noticeboard archives were checked for provenance.
- **Internet Archive**: item metadata read from `archive.org/metadata/<id>`.
- **GitHub repos**: shallow-cloned at the commits listed under [Sources](#sources).
- **Not reachable from this environment**:
  - Freesound: the site itself answers 403, and its API needs a key.
  - Internet Archive file servers (`*.archive.org` mirrors): refused by the proxy, so IA audio could not be downloaded or measured. Durations and sizes come from IA's metadata.
  - `upload.wikimedia.org`: answered 429 on every download attempt, so durations and sizes for Commons files come from the Commons API. Where a byte-identical copy (matching SHA-1) or a documented derivative existed in a GitHub repo, that copy was measured instead.
- **Ear checks**: none. No recording was checked by ear, and «الصلاة خير من النوم» is unconfirmed in every Fajr candidate.

**What changed since the first pass**

- **Two "CC0 / CC BY-SA own work" Commons files are now rejected.** *Beautiful adhan.ogg* and *Azan.ogg* were uploaded by two sockpuppet accounts of a user Commons has blocked for repeated copyright violations. A sibling upload by one of them was deleted as "clearly not a recording of own voice". Several open-source prayer apps on GitHub ship *Beautiful adhan.ogg* (qibla-mobile among them).
- **Aaqib Azeez moves from "held" to recommended.** The uploader states on his Commons talk page that he is the reciter and the rights holder.
- **The Malmö Fajr adhan is verified.** It is CC BY 3.0, its author is Islamic Center Malmö, and a Commons reviewer confirmed the YouTube licence.
- **New finds**:
  - Makkah adhans recorded by visitors, CC BY 3.0, licence-reviewed
  - a Madinah field recording, CC BY 3.0, via Freesound
  - a CC0 adhan uploaded by the muezzin himself to the Internet Archive
  - a CC BY-SA 4.0 adhan with a Wikimedia VRT permission ticket
- **The Internet Archive "Doha, Qatar" Fajr set is rejected.** Its uploader also marks famous reciters' adhans as public domain.

## Task

Find adhan recordings that MyPrayers, a free, open-source Android app on Google Play, may legally bundle:

- **1 Fajr Full Adhan.** It must include «الصلاة خير من النوم».
- **2–3 regular Full Adhan recordings.** Ideally by different reciters, in different styles.
- **Short Adhan.** It will be cut from each recording (the opening «الله أكبر، الله أكبر»), so the licence must allow adaptations.

Acceptable licences:

- public domain or CC0
- CC BY
- CC BY-SA
- explicit published permission to redistribute in apps

Not acceptable:

- NC (non-commercial) licences
- ND (no-derivatives) licences
- "free to download" with no licence
- uploads of a named reciter by someone who does not hold the rights

## Recommended shortlist

Every Fajr entry still needs the maintainer to **listen and confirm «الصلاة خير من النوم»**. Every entry needs a quality listen.

### Fajr

| Rank | Recording | Licence | Why this rank | Maintainer must listen for |
|---|---|---|---|---|
| **F1** | *Eid al-Fitr Fajr azan at Malmö Mosque - 19 August 2012.webm*. Muezzin Besim Azemi, published by Islamic Center Malmö on its own YouTube channel | **CC BY 3.0**, verified. Commons `{{YouTube CC-BY|Islamic Center Malmö}}` plus `{{YouTubeReview}}` dated 2020-02-12, with an archived copy of the YouTube page | The publisher is the mosque itself, the natural rights holder. It is a real mosque Fajr adhan (about 3 min 45 s of adhan, then a du'a), and the licence was reviewed | The tathweeb («الصلاة خير من النوم») after «حي على الفلاح»; the end point before the du'a (about 235 s); the room sound |
| F2 | `Developer_Athan-3_Fajr.mp3` by Ahmed Khalaf ([nandxorandor/Athan](https://github.com/nandxorandor/Athan)) | **CC BY 4.0**, verified in the author's own `LICENSE` | A fallback. Clean licence, but a short (99.5 s), quiet, home-made recording | The tathweeb, and the overall quality |

No other openly licensed Fajr adhan with credible provenance was found. The Freesound dawn ezan [#28775](https://freesound.org/people/sazman/sounds/28775/) (reportedly CC BY 4.0) remains a lead that cannot be verified from this environment.

### Regular

| Rank | Recording | Style | Licence | Why this rank |
|---|---|---|---|---|
| **R1** | *The Adhan - Muslim Call to Prayer - Aaqib Azeez.mp3*, uploaded by Commons user Atcovi | Plain, unembellished. He describes this version as "an Adhan that adheres to the Sunnah and stays away from overexaggeration" | **CC BY-SA 4.0**, verified | The reciter is the uploader. Asked whether his uploads were his own, Atcovi answered "That is my voice" (March 2022). In September 2026 he wrote: "I confirm I am the original reciter and the rights holder of the CC0 recordings for every recitation that is published under this account". He asks to be credited by his real name, Aaqib Azeez. Downside: share-alike, and it is short (86.5 s) |
| **R2** | *Adhan, Great Mosque of Mecca - Jan 21, 2013.webm*, YouTube video by "Seyfula Islam" | **Makkah**, the Haram adhan heard live in the mosque | **CC BY 3.0**, verified: `{{YouTube CC-BY}}` plus `{{LicenseReview}}` by a Commons reviewer, 2018-07-19 | The only openly licensed Makkah recording with a reviewed licence. **Caveat:** a visitor's recording of a live performance by the Haram muezzin. The recordist licensed his recording, but the muezzin's performer rights were not licensed by anyone (see [Open questions](#open-questions-for-the-maintainer)). Expect crowd and room noise |
| R3 | `Developer_Athan-1.mp3` by Ahmed Khalaf | Slow, melodic; amateur | **CC BY 4.0**, verified | The cleanest licence chain and the best of the three Khalaf files (−12 LUFS, clean phrase gaps). One amateur voice |
| R4 | Internet Archive *ICR Friday Prayer 1st Adhan 17th May 2024 … By Asim Javed* | Mosque adhan; the mosque's location is not stated | **CC0 1.0**, as stated on the item | IA metadata names Asim Javed both as the caller ("Called by: Asim Javed") and as the item's creator, so the muezzin dedicated his own recording. The account archives a whole mosque's ("ICR") prayers; its newer items are CC0. A lossless WAV original exists (219.7 s). Could not be downloaded or measured here |

**Suggested set:**

- **Fajr:** F1
- **Regular:** R1 and R2, plus R3 or R4 as a third.

That gives three different voices and one Makkah-style recording. If the maintainer rejects R2 over performer rights, use R1, R3 and R4. No openly licensed Madinah studio recording or classic Egyptian (Cairo radio-style) recording was found. The nearest is a Madinah field recording, listed below.

## Candidate table

"Verified" means the licence was read at the source. Durations, formats and sizes are from the Commons API or IA metadata unless marked *measured*.

**Shortlisted**:

| ID | Title / file | Reciter, origin | Source | Licence (quoted) | Attribution | Fajr phrase | Duration | Format, size | Quality notes | Provenance doubts |
|---|---|---|---|---|---|---|---|---|---|---|
| F1 | Eid al-Fitr Fajr azan at Malmö Mosque - 19 August 2012.webm | Besim Azemi; Islamic Center Malmö, Sweden; Eid al-Fitr Fajr, 2012-08-19 | [Commons](https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm), from [YouTube 0IHfm4Y82xA](https://www.youtube.com/watch?v=0IHfm4Y82xA) | "CC BY 3.0" (`{{YouTube CC-BY\|Islamic Center Malmö}}`, `{{YouTubeReview\|…\|date=2020-02-12}}`), [licence](https://creativecommons.org/licenses/by/3.0/) | Required | Not yet confirmed by ear. A Fajr upload by its title; [another project](https://github.com/rabaya-bagum/prayertime/blob/main/docs/AUDIO_LICENSES.md) counted a Fajr phrase structure from audio levels | 247.6 s total; adhan about 11.5–235 s | WebM video, 78,385,315 B. SHA-1 `bc75d5ce…f103f`. Audio must be extracted | *Measured* on a trimmed, normalised derivative: 223.6 s, −19.1 LUFS, clean noise floor (about −49 dB). Mosque interior | Low. The muezzin is the mosque's own, and the mosque published the recording |
| F2 | Developer_Athan-3_Fajr.mp3 | Ahmed Khalaf (recordist; probably also the voice) | [nandxorandor/Athan](https://github.com/nandxorandor/Athan/tree/main/app/src/main/assets/athan/fajr) | "released under the Creative Commons Attribution 4.0 International licence (CC BY 4.0)" ([LICENSE](https://github.com/nandxorandor/Athan/blob/main/LICENSE)) | Required | Claimed by the author; not ear-checked | *Measured* 99.5 s | MP3 44.1 kHz mono 96 kbps, 1,194,414 B | −28.9 LUFS, peak −13.5 dBFS: quiet, about +13 dB needed. Edited in WavePad | Low. Whether he is also the singer is not stated outright |
| R1 | The Adhan - Muslim Call to Prayer - Aaqib Azeez.mp3 | Aaqib Azeez (Commons user Atcovi), recorded 2020-12-23. Current version uploaded 2025-12-11 | [Commons](https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3) | "CC BY-SA 4.0" (`{{self\|cc-by-sa-4.0}}`), [licence](https://creativecommons.org/licenses/by-sa/4.0/) | Required; reciter's preferred credit is "Aaqib Azeez" | No (regular) | 86.5 s | MP3, 1,448,294 B. SHA-1 `971bced4…`. An earlier 2022 version is 3.77 MB | *Measured* on a normalised derivative: −18.8 LUFS, clean noise floor | Very low. Reciter and rights holder confirmed on [User talk:Atcovi](https://commons.wikimedia.org/wiki/User_talk:Atcovi). He has granted further commercial-use requests there |
| R2 | Adhan, Great Mosque of Mecca - Jan 21, 2013.webm | Haram muezzin (not named), heard live; filmed by YouTube user "Seyfula Islam" | [Commons](https://commons.wikimedia.org/wiki/File:Adhan,_Great_Mosque_of_Mecca_-_Jan_21,_2013.webm), from YouTube `KYi-PuOqVnA` | "CC BY 3.0" (`{{YouTube CC-BY\|Seyfula Islam}}`, `{{LicenseReview\|…\|date=2018-07-19}}`) | Required | No | 197.1 s | WebM video, 9,359,941 B. SHA-1 `818becd5…` | Not measured. Phone video in the mosque; expect crowd and reverb | **Performer rights** of the Haram muezzin, and whether the Saudi authority treats the adhan as its broadcast. The recordist's own rights are licensed |
| R3 | Developer_Athan-1.mp3 | Ahmed Khalaf | [nandxorandor/Athan](https://github.com/nandxorandor/Athan/tree/main/app/src/main/assets/athan/developer) | Same as F2 | Required | No | *Measured* 144.7 s | MP3 mono 96 kbps, 1,736,351 B | −12.2 LUFS, peak −1.1 dBFS, clean gaps | Same as F2 |
| R4 | ICR Friday Prayer 1st Adhan 17th May 2024 9th Dhul Qadah 1445 By Asim Javed | Asim Javed, at "ICR" (mosque not otherwise identified) | [IA item](https://archive.org/details/icr-friday-prayer-1st-adhan-17th-may-2024-9th-dhul-qadah-1445-by-asim-javed) | `licenseurl`: [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Not required; courtesy credit "Asim Javed (ICR), CC0" | No (Friday first adhan, a regular adhan) | 219.7 s | WAV original 77,525,766 B; FLAC 26.9 MB; MP3 2.18 MB | Not measured: IA file servers blocked | Low to medium. A self-upload by the caller; the mosque's identity is unknown. Its 2nd adhan (74.9 s) is the same muezzin |

**Other licence-verified options**:

| ID | Title / file | Origin | Licence (verified) | Duration, size | Notes |
|---|---|---|---|---|---|
| O1 | [33937 ejaz215 call-to-prayer-from-the-prophet-s-mo.ogg](https://commons.wikimedia.org/wiki/File:33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg) | **Madinah**, the Prophet's Mosque; field recording by Freesound user ejaz215, imported to Commons in 2014 | "CC BY 3.0" on Commons. The source, [Freesound 33937](http://www.freesound.org/people/ejaz215/sounds/33937/), is unverifiable from here and has no Commons licence review | 186.6 s, Ogg, 2,974,689 B | Used on 13 Wikipedias. Same performer-rights caveat as R2. A good Madinah-style option if Freesound confirms the licence |
| O2 | [Time of Adhan, Great Mosque of Mecca - Jan 6, 2018 .webm](https://commons.wikimedia.org/wiki/File:Time_of_Adhan,_Great_Mosque_of_Mecca_-_Jan_6,_2018_.webm) | Makkah; YouTube user "M SOHAIL Ahmad" | CC BY 3.0, `{{YouTubeReview\|date=2019-12-07}}` | 189.2 s, WebM, 278.9 MB | Alternative to R2 |
| O3 | [Maghrib Adhan at the Masjid al Haram, Mecca - 25 Feb, 2012.webm](https://commons.wikimedia.org/wiki/File:Maghrib_Adhan_at_the_Masjid_al_Haram,_Mecca_-_25_Feb,_2012.webm) | Makkah, Maghrib; YouTube user "3omar Faruq" | CC BY 3.0, `{{YouTubeReview\|date=2020-02-14}}` | 301.1 s (includes waiting before the adhan), WebM, 32.3 MB | Alternative to R2 |
| O4 | [Adhan in Shalqar mosque.webm](https://commons.wikimedia.org/wiki/File:Adhan_in_Shalqar_mosque.webm) | Matey Qoqanuly mosque, Shalkar, Kazakhstan; own work by Commons user Esetok, 2014 | CC BY-SA 4.0 (`{{self}}`) | 155.3 s, WebM 720p, 31.5 MB | Credible uploader (he files copyright notices himself); used on 12 Wikipedias. Central Asian style; share-alike |
| O5 | [اذان حسین صبحدل.webm](https://commons.wikimedia.org/wiki/File:%D8%A7%D8%B0%D8%A7%D9%86_%D8%AD%D8%B3%DB%8C%D9%86_%D8%B5%D8%A8%D8%AD%D8%AF%D9%84.webm) | "Adhan of Master Hussein Sobhdel" (Iran) | CC BY-SA 4.0, with **VRT permission ticket 2020110810007063** (the strongest formal permission found) | 510.0 s, WebM, 248 MB | Very long. Probably a Persian, possibly Shia-form adhan (extra phrases); listen before considering it for an Arabic-first default |
| O6 | [Muslim calling to prayer.ogg](https://commons.wikimedia.org/wiki/File:Muslim_calling_to_prayer.ogg) | Field recording, Nigeria; own work by Aishatu98, 2026 | CC0 | 102.4 s, Ogg, 820 KB | New account doing everyday-life field recordings. Quality unknown |
| O7 | IA [Isha Azan By Moazzambeg Mirza (07 09 2013)](https://archive.org/details/IshaAzanByMoazzambegMirza07092013) | Ahmedabad; apparently self-uploaded by the muezzin | Public Domain *Mark* 1.0 | 275 s, MP3 4.4 MB | PDM is meant for works already out of copyright, not as a waiver. Ask for CC0 before use |
| O8 | IA [Azan Zhur Masjid KLCC](https://archive.org/details/AzanZhurMasjidKlcc) | Kuala Lumpur; iPhone field recording, "birds chirping and heavy reverb" | CC0 | 186.9 s, MP3 1.87 MB | Anonymous recordist; field quality |

## Rejected candidates and why

| Candidate | Why rejected |
|---|---|
| Commons [*Beautiful adhan.ogg*](https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg) (CC0, "own work" by Adam-synagda, 154 s) | The uploader was blocked indefinitely on 2022-11-27 as "another sock of Hadysylmy", with "Blocked, most files deleted" ([AN/B archive 34](https://commons.wikimedia.org/wiki/Commons:Administrators%27_noticeboard/Blocks_and_protections/Archive_34)). Several of his uploads were flagged as copyright violations. An anonymous "own work" claim from a serial copyright violator does not establish rights. Its SHA-1 `a1fa4fd9…` matches the copies shipped by [rahmanSaaed/qibla-mobile](https://github.com/rahmanSaaed/qibla-mobile) and other apps. |
| Commons [*Azan.ogg*](https://commons.wikimedia.org/wiki/File:Azan.ogg) (CC BY-SA 4.0, "own work" by Andrewler, 183.5 s) | The same pattern: Andrewler was blocked on 2022-11-08 for "Abusing multiple accounts". His *Iqama.ogg* was flagged as "clearly not a recording of own voice; no permission", and other uploads as copyright violations ([User talk:Andrewler](https://commons.wikimedia.org/wiki/User_talk:Andrewler)). |
| The 26 IslamWeb recordings in nandxorandor/Athan | NC: IslamWeb permits only "non-commercial purposes provided the source is named" ([fatwa 379009](https://www.islamweb.net/en/fatwa/379009/), as quoted by that repo). |
| SammyNashed/Emran (Alafasy, Al-Ghamdi, Al-Nafees); NedaaDevs/nedaa (Makkah, Madinah, Al-Dosari); MihrabHQ/Mihrab (17 adhans); meypod/al-azan and meypod/audio_files; Kiwifu/adhan-mp3 | Named reciters and Haram broadcasts with no audio licence. Code licences (GPL, AGPL, MIT) do not cover audio. |
| IA [*Adhan Recordings from Doha, Qatar*](https://archive.org/details/adhan.recordings.from.doha.qatar) (Public Domain Mark; includes a 217 s Fajr) | Creator given only as "Doha, Qatar"; no recordist. The same uploader's [*Adhan Notifications*](https://archive.org/details/adhan.notifications) applies the Public Domain Mark to Mishary Alafasy, Nasser al-Qatami and others, so its public-domain claims are not reliable. |
| IA third-party uploads of famous reciters marked PD or CC0 (*Adzan Kota Kuwait (Misyari Rasyid)*, *Adzan Misyari Rasyid*, *28MayFajrAdhanMakkah*, *Makkah Azan*, *AZAN (ATHAN AL FAJR)*, *Adhan From Egypt Abdulnasser Harak*, *Azaan of World Mosques*, al-Suraihi clip, and others) | Uploaded by people who are not the reciter or the broadcaster. |
| IA items under NC or ND licences (*Adhan-Tounsi* BY-NC-SA, *TvQuran.com 01.athan* BY-ND, *1azan* BY-NC, the Jordan field recordings BY-NC-ND, and others) | NC or ND. |
| IA *Athan* by "abathar al_halwaji" (CC BY 3.0, 325 s) | No information on who is performing or recording. Possibly a Shia adhan (Karbala identifier). Not pursued. |
| Commons *Call to prayer by Sabah Fakhry.mp3* | Tagged "Public domain" with the source given as YouTube. A 1985 performance by a famous singer is not public domain because the adhan text is old. |
| Commons *Oración Al-Azzan.ogg* (PD, 30 s) | 30 s, from "the evening liturgy of Madeenah" (likely a broadcast). A public-domain claim by an uploader is not credible for that. |
| Commons *Adhan.ogg* (CC0, Aishatu98, 42 s) | Too short for a Full Adhan. (It is byte-identical, SHA-1 `b86432f1…`, to qibla-mobile's `adhan_short.ogg`.) |
| Commons *Adhan wiki.oga*, *Maliki doctrine.oga*, *Islamic call to worship.oga* | A 27 s spoken reading, a 57 s talk on the Maliki conditions of the adhan, and a 210 s talk on the adhan's definition and legitimacy (per their descriptions). Not adhans. |
| Commons Jaffa, Kfar Kama, Melaka, Tripoli and Istanbul clips; *Tour guide … Isfahan*; *Hminsec150 Konya*; *The sound of Syria*; *Llamada a oración Mezquita Hassan II*; Somaliland hotel recording | Fragments (7–50 s), or ambience and travel videos with the adhan in the background. Not usable as a Full Adhan. |
| Freesound #639494 (CC0, sonically_sound) and #178382 (BY-NC, a *sela*, not the adhan) | The first was "extracted from a YouTube video"; the second is NC. |
| Freesound #255231 (Hamtramck), #28775 (Patara dawn), #807484, #583206, #260102, #185172 | **Unverifiable from this environment** (Freesound answers 403). Not rejected on the merits. |
| Cambridge repository *Voices and Faces of the Adhan: Cairo* (Egyptian Fajr) | Its host was blocked, so its licence is unknown. Still worth checking as the only documented Egyptian Fajr lead. |

### Why NC is still risky on Play, even for a free app

- **"Commercial" is undefined.** CC NC bars use "primarily intended for or directed towards commercial advantage or monetary compensation", without defining it for app stores.
- **Distribution runs through a commercial platform.** Google Play is one.
- **Rights holders read it strictly.** Islamic content publishers often treat store distribution as commercial.
- **Future changes are blocked.** Donations, ads or a paid tier would breach NC.
- **Forks are blocked.** NC stops forks of this public repo from shipping commercially.
- **F-Droid flags it.** F-Droid treats NC assets as non-free.

### Why ND is out

Cutting the Short Adhan, trimming and fading are adaptations. ND permits only format shifting.

### Share-alike (CC BY-SA) on a bundled asset

Applies to R1, O4 and O5.

- **Derivatives must be BY-SA too.** Our Short Adhan clip and any trimmed or normalised file must be shared under BY-SA 4.0.
- **The app's code is not affected.** The app is a collection that contains the files, not an adaptation of them.
- **No restrictive technical measures.** BY-SA 4.0 §2(a)(5)(B) forbids them. Plain assets in an APK or AAB are fine; do not encrypt them.
- **Keep the boundary visible.** Keep BY-SA audio and its `LICENSE` in a separate folder.
- **Or avoid it.** The Aaqib Azeez Quran recordings are CC0, so it is worth asking him whether he would also release the adhan as CC0 (see [Open questions](#open-questions-for-the-maintainer)).

## Attribution text for About

CC BY and CC BY-SA require:

- the credit the creator asked for
- the licence name and link
- the source
- a note when the material was modified

Every bundled file is modified (trimmed, normalised, re-encoded), and the Short Adhan is an excerpt. Show the Arabic line, with the licence name and link in Latin script.

> **Fajr adhan:** Besim Azemi, Islamic Center Malmö, from Wikimedia Commons ("Eid al-Fitr Fajr azan at Malmö Mosque - 19 August 2012"), CC BY 3.0, creativecommons.org/licenses/by/3.0/. Trimmed and loudness-normalised.
> أذان الفجر: بسيم عزمي، المركز الإسلامي في مالمو، عبر ويكيميديا كومنز، بترخيص CC BY 3.0. قُصّ وعُدّلت شدة صوته.
>
> **Adhan:** Aaqib Azeez, from Wikimedia Commons ("The Adhan - Muslim Call to Prayer - Aaqib Azeez"), CC BY-SA 4.0, creativecommons.org/licenses/by-sa/4.0/. The Short Adhan is an excerpt; edited versions are shared under CC BY-SA 4.0.
> الأذان: عاقب عزيز، عبر ويكيميديا كومنز، بترخيص CC BY-SA 4.0. الأذان القصير مقتطع منه، والنسخ المعدّلة متاحة بالترخيص نفسه.
>
> **Adhan (Makkah):** recorded by Seyfula Islam at the Great Mosque of Mecca, 21 January 2013, from Wikimedia Commons, CC BY 3.0, creativecommons.org/licenses/by/3.0/. Trimmed and loudness-normalised.
> الأذان (مكة المكرمة): تسجيل Seyfula Islam في المسجد الحرام، عبر ويكيميديا كومنز، بترخيص CC BY 3.0.
>
> **Adhan (alternative):** Ahmed Khalaf, from the open-source app *Athan* (github.com/nandxorandor/Athan), CC BY 4.0, creativecommons.org/licenses/by/4.0/. Edited.
>
> **Adhan (alternative):** Asim Javed (ICR), from the Internet Archive, CC0 1.0. No credit is required; it is given as a courtesy.

## Open questions for the maintainer

1. **Listen before shipping.**
   - F1 and F2: confirm «الصلاة خير من النوم», and choose F1's end point before the du'a.
   - R2 and O1–O3: judge crowd and room noise.
   - O5: check the adhan form.
2. **Performer rights for the Makkah and Madinah field recordings (R2, O1–O3).** CC BY covers the recordist's rights, but the Haram muezzin did not license his performance, and some jurisdictions give performers rights in fixations of live performances. Accept the risk (Wikipedia projects use these files widely), or ship only R1, R3 and R4 plus F1?
3. **Accept CC BY-SA (R1)?** Alternatively, ask Aaqib Azeez on his public Commons talk page ([User talk:Atcovi](https://commons.wikimedia.org/wiki/User_talk:Atcovi)) to:
   - release the adhan under CC0, as he does for his Quran recordings
   - ideally, record a Fajr adhan in the same voice. That would give a matched regular and Fajr pair from one consenting reciter.
4. **Verify the Freesound leads.** These are Hamtramck #255231, Patara dawn ezan #28775 (a possible second Fajr), and the source of O1 (#33937). Freesound needs a browser or an API key; this environment gets 403.
5. **R4's provenance.** Which mosque is "ICR", and can Asim Javed (via the mosque's public contact page) confirm the CC0 dedication? A short confirmation would make R4 a clean, lossless option.
6. **Egyptian style.** No openly licensed Egyptian adhan was found. The options:
   - Check the Cambridge *Voices and Faces of the Adhan: Cairo* collection (www.repository.cam.ac.uk), which needs network access.
   - Ask Egypt's Ministry of Awqaf or Egyptian Radio through their official public contact pages.
   - Commission a recording with a written release from both the performer and the recordist. The release should grant CC0 or CC BY 4.0, consent to edits including the Short Adhan cut, and allow app-store distribution.
7. **Already-shipping apps.** Several open-source apps ship *Beautiful adhan.ogg* as CC0. If MyPrayers mentions this anywhere public, describe it neutrally: it is a provenance finding, not an accusation against those projects.
8. **Encoding target** (for the implementation ticket): extract the audio, downmix to mono, loudness-normalise (for example −16 LUFS, −1.5 dBTP), and choose the Short Adhan cut point per recording.

## Sources

Primary sources, read in this session:

- **Commons API and file pages**, read 2026-11-08: the `extmetadata`, wikitext, history, global usage and links-here of each file named above.
  - [Category:Adhan](https://commons.wikimedia.org/wiki/Category:Adhan) and [Category:Muezzins](https://commons.wikimedia.org/wiki/Category:Muezzins)
  - Commons full-text search for adhan, azan, athan, ezan, muezzin, «أذان» and "call to prayer" (audio and video files)
- **Commons user pages**:
  - [User talk:Atcovi](https://commons.wikimedia.org/wiki/User_talk:Atcovi) (reciter confirmation)
  - [User talk:Adam-synagda](https://commons.wikimedia.org/wiki/User_talk:Adam-synagda) and [User talk:Andrewler](https://commons.wikimedia.org/wiki/User_talk:Andrewler) (blocks and copyright-violation notices)
  - [AN/B Archive 34](https://commons.wikimedia.org/wiki/Commons:Administrators%27_noticeboard/Blocks_and_protections/Archive_34)
- **Internet Archive**:
  - `archive.org/metadata/<id>` for the IA items named above
  - `archive.org/advancedsearch.php` for (adhan OR azan OR athan OR adzan OR ezan) audio items with a licence URL, 134 hits reviewed
- **GitHub repos**, shallow-cloned:
  - nandxorandor/Athan @ `ad2bf1799add` ([LICENSE](https://github.com/nandxorandor/Athan/blob/main/LICENSE), README)
  - rabaya-bagum/prayertime @ `d4ba082c1faf` ([docs/AUDIO_LICENSES.md](https://github.com/rabaya-bagum/prayertime/blob/main/docs/AUDIO_LICENSES.md))
  - rahmanSaaed/qibla-mobile @ `025959c7ed9a`
  - SammyNashed/Emran @ `5b314b482ed9`
  - NedaaDevs/nedaa @ `950703c206fb`
  - MihrabHQ/Mihrab @ `bec2350d7a72`
  - meypod/al-azan @ `4e7af099b9e1`
  - meypod/audio_files @ `99f81f1d397d`
  - Kiwifu/adhan-mp3 @ `b9180a2bb769`
- **Licence texts**: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/legalcode), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/legalcode), [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/legalcode), [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/legalcode), [Public Domain Mark 1.0](https://creativecommons.org/publicdomain/mark/1.0/)

Unverifiable from this environment:

- Freesound sound pages: [255231](https://freesound.org/s/255231/), [28775](https://freesound.org/people/sazman/sounds/28775/), [33937](https://freesound.org/people/ejaz215/sounds/33937/), [807484](https://freesound.org/people/iainmccurdy/sounds/807484/), [583206](https://freesound.org/people/Redalemage/sounds/583206/)
- [Cambridge repository item 1810/237237](https://www.repository.cam.ac.uk/handle/1810/237237)
- Internet Archive download servers
