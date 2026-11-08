# Adhan recordings we may redistribute

Research for ticket #12. Researched 2026-11-08. Terms follow [`GLOSSARY.md`](../../GLOSSARY.md): **Full Adhan**, **Short Adhan**, **Adhan Alert**.

> **Status: incomplete, and the reason matters.** This session's network allowed only GitHub. Wikimedia Commons (`commons.wikimedia.org`, `upload.wikimedia.org`), Freesound (`freesound.org`), the Internet Archive (`archive.org`) and Cambridge's repository (`www.repository.cam.ac.uk`) were all refused by the egress proxy. Those are the places where most openly licensed adhan recordings live.
>
> - Licences marked **verified** below were read from the rights holder's own published licence file.
> - Everything marked **unverified** comes from search-engine snippets or from another project's notes. It still has to be confirmed on the source page before anything ships.
>
> To close this ticket, re-run the "verify" steps in [Open questions](#open-questions-for-the-maintainer) with those hosts allowed.

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

There are two tiers. **Tier A** is the only set whose licence was verified at the source. Its weakness is audio quality and style. **Tier B** is better-sounding and more varied, but unverified. If Tier B verifies, it should replace or join Tier A.

### Tier A: verified licence (CC BY 4.0), shippable now, after a listen

| Rank | Slot | Recording | Licence |
|---|---|---|---|
| A1 | **Fajr** | `Developer_Athan-3_Fajr.mp3`, an original recording by the developer of the open-source app *Athan* ([nandxorandor/Athan](https://github.com/nandxorandor/Athan)) | **CC BY 4.0**, verified |
| A2 | Regular | `Developer_Athan-1.mp3`, same author | **CC BY 4.0**, verified |
| A3 | Regular | `Developer_Athan-2.mp3`, same author | **CC BY 4.0**, verified |

The licence is the rights holder's own statement, in the repo's [`LICENSE`](https://github.com/nandxorandor/Athan/blob/main/LICENSE) (commit `ad2bf1799add`):

> "Developer_Athan-1.mp3, Developer_Athan-2.mp3 and Developer_Athan-3_Fajr.mp3 are original recordings by Ahmed Khalaf, released under the Creative Commons Attribution 4.0 International licence (CC BY 4.0)"

The README repeats it: "The three `Developer_Athan-*` recordings are mine, under CC BY 4.0."

Caveats:

- **One voice, home-recording quality.** All three appear to be the same person. That gives no variety of reciter or style.
- **Low, uneven loudness.** A2 and A1 are quiet (−27 and −29 LUFS) and need loudness normalisation.
- **Fajr phrase not confirmed.** «الصلاة خير من النوم» in A1 is inferred from the filename and the author's README ("a *Fajr* recording carrying *aṣ-ṣalātu khayrun min an-nawm*"). It has not been checked by ear.

### Tier B: better candidates, licence UNVERIFIED (confirm on the source page)

| Rank | Slot | Recording | Claimed licence |
|---|---|---|---|
| B1 | **Fajr** | Commons: [*Eid al-Fitr Fajr azan at Malmö Mosque - 19 August 2012.webm*](https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm). Muezzin Besim Azemi, published by Islamic Center Malmö on its own YouTube channel | **CC BY 3.0**, reportedly license-reviewed on Commons |
| B2 | Regular | Commons: [*Beautiful adhan.ogg*](https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg), by Commons user Adam-synagda, "Own work", 29 April 2022 | **CC0 1.0** |
| B3 | Regular | Commons: [*Azan.ogg*](https://commons.wikimedia.org/wiki/File:Azan.ogg), by Commons user Andrewler, "Own work", 3 September 2022 | **CC BY-SA 4.0**, per a search snippet of its structured data |
| B4 | Regular, field recording | Freesound [#255231](https://freesound.org/s/255231/), "Islamic Call to Prayer (Dhuhr Adhan Audiophile Field Recording from Hamtramck, MI 96 kHz 24 Bit Stereo)", by RJStefanski | **CC BY 3.0** |

B1 is the strongest Fajr lead: the mosque is the natural rights holder, and it published the recording itself. B2 is the strongest regular lead.

**If both tiers verify, the suggested final set is:**

- Fajr: B1, with A1 as fallback.
- Regular: B2, then A2, then B3 or B4.

None of these is in a Makkah, Madinah or classic Egyptian style; see [Open questions](#open-questions-for-the-maintainer).

## Candidate table

Measured files were cloned into the session scratchpad and measured with `ffprobe` and `ffmpeg`'s `ebur128` filter (integrated loudness). Nothing was added to this repo.

**Tier A** (verified):

| ID | Title / file | Reciter, origin | Source | Licence (quoted) | Attribution | Fajr phrase | Duration | Format, bitrate, size | Quality notes | Provenance doubts |
|---|---|---|---|---|---|---|---|---|---|---|
| A1 | `Developer_Athan-3_Fajr.mp3` | The repo author ("my own"), recorded by him; the voice is presumably his own | [nandxorandor/Athan `app/src/main/assets/athan/fajr/`](https://github.com/nandxorandor/Athan/tree/main/app/src/main/assets/athan/fajr) | "released under the Creative Commons Attribution 4.0 International licence (CC BY 4.0)" ([LICENSE](https://github.com/nandxorandor/Athan/blob/main/LICENSE)) | See [Attribution](#attribution-text-for-about) | Claimed by author; not ear-checked | 99.5 s | MP3, 44.1 kHz mono, 96 kbps CBR, 1,194,414 B. SHA-1 `deb91424…` | −28.9 LUFS, peak −13.5 dBFS: quiet, needs about +13 dB normalisation. Noise floor about −50 dB. 14 sung phrase blocks detected. Short for a Fajr adhan. Edited in WavePad, then re-encoded by the author's script to 96 kbps; the original master is not public | The author asserts he is the recordist. Whether he is also the voice is not stated outright. If someone else sang, that performer's consent is needed |
| A2 | `Developer_Athan-1.mp3` | Same author | [`…/athan/developer/`](https://github.com/nandxorandor/Athan/tree/main/app/src/main/assets/athan/developer) | Same | Same | No (regular) | 144.7 s | MP3, 44.1 kHz mono, 96 kbps, 1,736,351 B. SHA-1 `b59ea280…` | −12.2 LUFS, peak −1.1 dBFS, LRA 10.5 LU. Clean phrase gaps (12 long phrases, slow melodic style), noise floor about −52 dB. Best-sounding of the three | Same |
| A3 | `Developer_Athan-2.mp3` | Same author | Same folder | Same | Same | No (regular) | 132.2 s | MP3, 44.1 kHz mono, 96 kbps, 1,586,564 B. SHA-1 `fac15435…` | −26.6 LUFS, peak −4.3 dBFS: quiet. Choppier level profile than A2 | Same |

**Tier B** (unverified):

| ID | Title / file | Reciter, origin | Source | Licence (quoted) | Attribution | Fajr phrase | Duration | Format, bitrate, size | Quality notes | Provenance doubts |
|---|---|---|---|---|---|---|---|---|---|---|
| B1 | *Eid al-Fitr Fajr azan at Malmö Mosque - 19 August 2012.webm* | Besim Azemi, at Islamic Center Malmö. Eid al-Fitr Fajr, 2012-08-19 | [Commons](https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm), from the mosque's [YouTube video](https://www.youtube.com/watch?v=0IHfm4Y82xA) | **Unverified.** Another app's register says "CC BY 3.0, license reviewed on Commons" ([rabaya-bagum/prayertime `docs/AUDIO_LICENSES.md`](https://github.com/rabaya-bagum/prayertime/blob/main/docs/AUDIO_LICENSES.md)) | "Besim Azemi, Islamic Center Malmö, CC BY 3.0, via Wikimedia Commons" (to be confirmed) | Very likely: that project counted a Fajr phrase structure from levels, not by ear | Source over 241 s (adhan from 11.5 s to about 235 s, then a du'a) | WebM video; audio needs extracting. Source SHA-1 per that register: `bc75d5ce…`. A trimmed derivative measured 223.6 s | Mosque interior. The derivative, normalised to −19 LUFS, has a clean noise floor (about −49 dB p10) | Low: the publisher is the mosque itself. Check that the YouTube licence field really says CC BY, and that Commons' licence-review stamp is present |
| B2 | *Beautiful adhan.ogg* | Not named. Uploader Adam-synagda, "Own work", 29 Apr 2022 | [Commons](https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg) | **Unverified.** Snippet: "Creative Commons CC0 1.0 Universal Public Domain Dedication"; several apps credit it as CC0 | None required under CC0. Credit as courtesy: "“Beautiful adhan” by Adam-synagda, Wikimedia Commons, CC0 1.0" | No | 2 min 34 s (154.0 s measured) | Ogg Vorbis, 64 kbps, about 1.17 MB per Commons. A copy bundled by [rahmanSaaed/qibla-mobile](https://github.com/rahmanSaaed/qibla-mobile) measured 44.1 kHz stereo, 1,229,032 B, SHA-1 `a1fa4fd9…`; compare it with the Commons original | −8.8 LUFS, peak −2.2 dBFS: very loud, likely heavily compressed. 64 kbps is low but adequate for a phone speaker | **Medium.** An anonymous "own work" claim. Check that it is not a rip of a broadcast or known reciter (Commons edit history, deletion discussions, reverse audio search) |
| B3 | *Azan.ogg* | Not named. Uploader Andrewler, "Own work", 3 Sep 2022 | [Commons](https://commons.wikimedia.org/wiki/File:Azan.ogg) | **Unverified.** Snippet: "Creative Commons Attribution-ShareAlike 4.0 International" | "“Azan” by Andrewler, Wikimedia Commons, CC BY-SA 4.0" (to be confirmed) | Unknown | about 3 min 3 s | Ogg Vorbis; bitrate unknown | Not measured | Medium, same checks as B2. Share-alike; see [below](#share-alike-cc-by-sa-on-a-bundled-asset) |
| B4 | Freesound #255231, Hamtramck, MI | Not named. Dhuhr adhan in Hamtramck, Michigan, recorded by RJStefanski | [freesound.org/s/255231](https://freesound.org/s/255231/) | **Unverified.** Snippet: "Attribution 3.0"; uploader: "please cite me as a source if possible" | "Islamic Call to Prayer (Dhuhr…) by RJStefanski, freesound.org, CC BY 3.0" | No | Unknown | WAV, 96 kHz / 24-bit stereo (per title) | Field recording, so expect ambience and distance; listen first | Low for the recording itself; the recordist uploaded it |

**Further unverified leads** (worth opening once the hosts are reachable):

| Lead | Licence per snippet | Notes |
|---|---|---|
| Freesound [#28775](https://freesound.org/people/sazman/sounds/28775/), sazman | CC BY 4.0 | Dawn ezan, Patara village, south-west Turkey, summer 1992. A Turkish Fajr adhan includes the tathweeb (the «الصلاة خير من النوم» line), so this could be a Fajr fallback. Probably distant and lo-fi |
| Freesound [#807484](https://freesound.org/people/iainmccurdy/sounds/807484/), iainmccurdy | CC BY 4.0 | Aroumd, Morocco; field recording |
| Freesound [#583206](https://freesound.org/people/Redalemage/sounds/583206/), Redalemage | CC0 | Marrakech; field recording |
| Freesound [#260102](https://freesound.org/s/260102) | CC BY 3.0 | Adhan at first light, heard from the slopes of Mt Merapi, Java; likely very distant |
| Freesound [#185172](https://freesound.org/s/185172) | CC BY 4.0 | Evening adhan, Old Delhi |

## Rejected candidates and why

| Candidate | Why rejected |
|---|---|
| The other 26 recordings in nandxorandor/Athan (Egyptian, Emirati, Kuwaiti, Turkish, Fajr sets), all from IslamWeb | Bundled under IslamWeb's permission ([fatwa 379009](https://www.islamweb.net/en/fatwa/379009/)), which that repo summarises as "non-commercial purposes provided the source is named". That is **NC**. The repo itself warns they "are NOT relicensed" and "remain the property of their reciters and of IslamWeb". Also, IslamWeb hosts but does not own many of them. |
| SammyNashed/Emran: Mishary Alafasy (full and Fajr), Saad Al-Ghamdi, Ahmad Al-Nafees | Famous named reciters. The repo is GPL-3.0 (code), and it gives no licence, source or permission for the audio. |
| NedaaDevs/nedaa: `makkah_athan1`, `medina_athan`, `yasser_aldosari`, `athan2/3` | Named reciters and Haram recordings. No audio licence; the repo is GPL-3.0 (code only). |
| MihrabHQ/Mihrab: 17 adhans (Makkah, Madina, Egypt, Abdul Basit, Minshawi, Aqsa, Yusuf Islam, and others) | The repo's NOTICE says third-party content is "the property of their respective authors and … used under their own terms". No licence is shown for us. |
| meypod/al-azan `masjid_an_nabawi.mp3`, and [meypod/audio_files](https://github.com/meypod/audio_files) (Abdul Basit, Ghalwash, and others) | No licence at all; the README says only that it serves "audio files for other projects". |
| [Kiwifu/adhan-mp3](https://github.com/Kiwifu/adhan-mp3) ("200+ recordings… Free for Islamic apps") | "Free" with no licence, and named muezzins uploaded by a third party. Explicitly excluded by the ticket. |
| Makkah and Madinah official adhans (any copy: Internet Archive "Makkah Azan", "AZAN (ATHAN AL FAJR) : ME ONLY", aladhan.com, assabile.com) | Saudi state broadcasts. No open licence found, and third-party uploads carry no rights. Only usable with written permission from the rights holder. |
| Commons *Call to prayer by Sabah Fakhry.mp3* | Tagged public domain because the adhan dates from 622 CE. That covers the *text*, not a 1985 performance taken from YouTube. (Reason as recorded by [rabaya-bagum/prayertime](https://github.com/rabaya-bagum/prayertime/blob/main/docs/AUDIO_LICENSES.md); not re-checked here.) |
| Commons *The Adhan - Muslim Call to Prayer - Aaqib Azeez.mp3* (CC BY-SA 4.0, "own work" by Atcovi) | **Held, not shipped.** A named reciter, uploaded as "own work" by someone who may not be the performer or the rights holder. Usable only if the uploader confirms the performer agreed. The derivative measured 86.6 s. |
| Internet Archive *Adhan Recordings from Doha, Qatar* (Public Domain Mark 1.0, one file per prayer, including Fajr) | **Held.** The uploader is listed as "Unknown". A Public Domain Mark on a 2013–14 recording is just the uploader's claim, and the recordist and mosque are not named. Needs provenance before use. |
| Freesound [#639494](https://freesound.org/people/sonically_sound/sounds/639494/), sonically_sound (CC0) | Per the snippet, "extracted from a YouTube video and processed". The uploader is not the rights holder. |
| Freesound [#178382](https://freesound.org/people/kraskland/sounds/178382/), Istanbul (CC BY-NC 3.0) | NC, and per the uploader it is the *sela*, not the adhan. |
| Commons *Adhan wiki.oga* (2007, CC BY-SA 2.5) | 27 s spoken reading, not a Full Adhan. |
| Commons *Adhan.ogg* (CC0, 42 s) | Too short to be a Full Adhan; unverified. At most a Short Adhan source. |
| Commons *Oldest recording of the Quran 1885* | The Commons page itself disputes the date, and the quality is unusable. |
| Cambridge repository, *Voices and Faces of the Adhan: Cairo* (Masgid Mahmoudeya, Sheikh Hamed, Fajr) | Could not be reached, so its licence is unknown. Worth checking: it is a documented Egyptian Fajr recording. |

### Why NC is still risky on Play, even for a free app

- **"Commercial" is undefined.** CC NC bars use "primarily intended for or directed towards commercial advantage or monetary compensation". It does not define the term for app stores.
- **Distribution runs through a commercial platform.** Google Play is one.
- **Rights holders read it strictly.** Islamic content publishers often treat any store distribution as commercial.
- **Future changes are blocked.** Donations, a paid tier, ads or sponsorship would breach NC.
- **Forks are blocked.** NC stops anyone who forks this public repo from shipping commercially, which conflicts with an OSI-style project licence.
- **F-Droid flags it.** F-Droid treats NC assets as non-free (it flags them).

### Why ND is out

Cutting the Short Adhan, trimming and fading are adaptations. ND permits only format shifting.

### Share-alike (CC BY-SA) on a bundled asset

Applies to B3 and the held Aaqib Azeez file.

- **Derivatives must be BY-SA too.** Our Short Adhan clip, any trim and any normalised file must be shared under BY-SA (4.0, or a compatible later version).
- **The app's code is not affected.** The app is a collection containing the file, not an adaptation of it.
- **No restrictive technical measures.** BY-SA 4.0 §2(a)(5)(B) forbids "effective technological measures" that restrict the licensed material. Plain asset files inside an APK or AAB are generally fine. Do not encrypt the assets.
- **Keep it simple.** Prefer CC0 or CC BY where there is a choice. If BY-SA is used, keep the audio and its licence in a separate folder with its own `LICENSE`, as [rabaya-bagum/prayertime](https://github.com/rabaya-bagum/prayertime/blob/main/docs/AUDIO_LICENSES.md) does.

## Attribution text for About

CC BY 4.0 §3(a) requires:

- the creator's name, as they asked to be credited
- a notice referring to the licence, with a link
- a note that the material was modified, if it was

For the Short Adhan and any trimmed or normalised version, add "excerpted" or "edited". In Arabic-first UI, show the Arabic line with the English licence name and the link.

**Tier A (verified).** The credit uses the name the licensor chose in his public `LICENSE`.

> Adhan recordings (regular and Fajr) by Ahmed Khalaf, from the open-source app *Athan* (github.com/nandxorandor/Athan), licensed under CC BY 4.0 (creativecommons.org/licenses/by/4.0/). The Short Adhan is an excerpt, and the recordings were loudness-normalised and re-encoded by MyPrayers.
>
> تسجيلات الأذان (العادي والفجر) من تسجيل Ahmed Khalaf من تطبيق Athan مفتوح المصدر، مرخّصة بموجب CC BY 4.0. الأذان القصير مقتطع منها، وقد عُدّلت شدة الصوت وأُعيد ترميزها.

**Tier B, if verified.** Replace each placeholder with the exact credit line shown on the source page.

> Fajr adhan: Besim Azemi, Islamic Center Malmö, via Wikimedia Commons, CC BY 3.0 (creativecommons.org/licenses/by/3.0/). Trimmed and normalised.
> "Beautiful adhan" by Adam-synagda, Wikimedia Commons, CC0 1.0 (credit not required).
> "Azan" by Andrewler, Wikimedia Commons, CC BY-SA 4.0 (creativecommons.org/licenses/by-sa/4.0/). Excerpts and edits are shared under the same licence.

## Open questions for the maintainer

1. **Allow the blocked hosts and finish verification.** Allow `commons.wikimedia.org`, `upload.wikimedia.org`, `freesound.org` and `archive.org` (and optionally `www.repository.cam.ac.uk`). For each Tier B file, then:
   - read the Licensing and Source sections
   - check the uploader's history and talk page
   - check the licence-review stamp (B1)
   - download the original and record duration, bitrate and SHA-1
2. **Listen to every shortlisted Fajr file.** Confirm «الصلاة خير من النوم» is present (A1, B1). Nothing here was checked by ear; phrase counts came from audio levels only.
3. **Are amateur or field recordings acceptable?** Tier A is a single home-recorded voice, and two of its files are quiet. Is that good enough for v1?
4. **Accept CC BY-SA?** It is workable, but it adds a licence boundary inside the app (see above).
5. **Who sings in Tier A?** The author says the recordings are "mine". Confirming that he is also the performer, by an issue on his repo, would close the last provenance gap.
6. **Makkah, Madinah or Egyptian style.** No openly licensed recording in these styles was found. The options:
   - **Ask the rights holders** for a written app-redistribution permission, through their official public contact pages: the General Authority for the Affairs of the Grand Mosque and the Prophet's Mosque (Haramain adhans), the Saudi Broadcasting Authority, and Egypt's Ministry of Awqaf or Egyptian Radio (Holy Quran Radio). Expect slow or no replies.
   - **Ask a mosque that already publishes openly** (as Islamic Center Malmö appears to) for a regular adhan by the same muezzin, to pair with B1.
   - **Commission a recording.** Ask a local muezzin for a regular and a Fajr adhan, with a short written release signed by the performer and the recordist. The release should grant CC0 or CC BY 4.0, consent to edits including the Short Adhan cut, and permit app-store distribution. Record in a quiet room at 48 kHz / 24-bit or better. This is the only route to a guaranteed-clean set in a chosen style.
7. **Encoding target.** All candidates should be re-encoded consistently and loudness-normalised (for example to −16 LUFS, true peak −1.5 dBTP). The Short Adhan clip length should be chosen per recording. That belongs in the implementation ticket.

## Sources

Primary sources, read directly. Repos were shallow-cloned at the commits given.

- nandxorandor/Athan @ `ad2bf1799add`: [LICENSE](https://github.com/nandxorandor/Athan/blob/main/LICENSE), [README "About the athan recordings" and "Licence"](https://github.com/nandxorandor/Athan#about-the-athan-recordings), `app/src/main/assets/athan/index.tsv`, `CreditsActivity.kt`
- rabaya-bagum/prayertime @ `d4ba082c1faf`: [docs/AUDIO_LICENSES.md](https://github.com/rabaya-bagum/prayertime/blob/main/docs/AUDIO_LICENSES.md), `tools/make_adhan_audio.sh`. This is a secondary source for B1, the Aaqib Azeez file and Sabah Fakhry.
- rahmanSaaed/qibla-mobile @ `025959c7ed9a`: README "Adhan recordings". Secondary source for B2.
- SammyNashed/Emran @ `5b314b482ed9`; NedaaDevs/nedaa @ `950703c206fb`; MihrabHQ/Mihrab @ `bec2350d7a72` (NOTICE); meypod/al-azan @ `4e7af099b9e1`; meypod/audio_files @ `99f81f1d397d`; Kiwifu/adhan-mp3 @ `b9180a2bb769`
- Licence texts: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/legalcode), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/legalcode), [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/), [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)

Unverified: search-engine snippets only. The hosts were blocked in this session.

- Commons: [Beautiful adhan.ogg](https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg), [Azan.ogg](https://commons.wikimedia.org/wiki/File:Azan.ogg), [Eid al-Fitr Fajr azan at Malmö Mosque](https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm), [Aaqib Azeez](https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3), [Adhan wiki.oga](https://commons.wikimedia.org/wiki/File:Adhan_wiki.oga), [Category:Adhan](https://commons.wikimedia.org/wiki/Category:Adhan)
- Freesound: [255231](https://freesound.org/s/255231/), [28775](https://freesound.org/people/sazman/sounds/28775/), [807484](https://freesound.org/people/iainmccurdy/sounds/807484/), [583206](https://freesound.org/people/Redalemage/sounds/583206/), [260102](https://freesound.org/s/260102), [185172](https://freesound.org/s/185172), [639494](https://freesound.org/people/sonically_sound/sounds/639494/), [178382](https://freesound.org/people/kraskland/sounds/178382/)
- Internet Archive: [Adhan Recordings from Doha, Qatar](https://archive.org/details/adhan.recordings.from.doha.qatar)
- Cambridge repository: [Masgid Mahmoudeya: Sheikh Hamed: Fajr Adhan](https://www.repository.cam.ac.uk/handle/1810/237237)
- IslamWeb permission as quoted by nandxorandor/Athan: [fatwa 379009](https://www.islamweb.net/en/fatwa/379009/)
