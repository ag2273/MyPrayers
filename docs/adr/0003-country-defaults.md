# Country defaults: our own country → Calculation Method and Asr Convention table

After the user sets a location, the app suggests a Calculation Method and Asr Convention for the country of the nearest bundled city, and says why («نقترح الهيئة المصرية العامة للمساحة لأنك في مصر»). No reusable, licensed, machine-readable country → method mapping exists in primary sources (docs/research/prayer-time-calculation.md §5), so we keep our own small table, keyed by ISO 3166-1 alpha-2 code, in `core/.../CountryDefaults.kt`. It is only a suggestion: the user can accept it or choose anything else.

## The table

| Countries | Calculation Method | Basis |
|---|---|---|
| SA | Umm al-Qura | The official Saudi calendar |
| AE | Dubai | Adhan METHODS.md: "used in the UAE" |
| KW | Kuwait | National authority's method |
| QA | Qatar | National authority's method |
| EG, every African country, SY, LB, JO, PS | Egyptian General Authority of Survey | PrayTimes.org region column ("Africa, Syria, Lebanon"); the Levant follows its neighbours |
| PK, IN, BD, AF | University of Islamic Sciences, Karachi | PrayTimes.org region column; Adhan hints |
| TR | Diyanet (Turkey) | Adhan: "less accurate outside the region of Turkey" |
| SG, MY, ID | Singapore | Adhan: "used in Singapore, Malaysia, and Indonesia" |
| US, CA, GB | Moonsighting Committee | Adhan: "recommended for North America and the UK" |
| Any other country | Muslim World League | The common international default |

**Asr Convention:** Standard everywhere, except **Hanafi** for PK, IN, BD, AF, TR and Central Asia (KZ, UZ, TM, KG, TJ), where the Hanafi school predominates.

## Consequences

- The country comes from the nearest bundled city (GeoNames country code), including for typed coordinates, so near a border the suggestion may name the neighbouring country. That is acceptable for a suggestion the user confirms.
- Gulf states without their own adhan method (BH, OM, YE) and Iraq get Muslim World League. Revisit when a national authority's parameters are verified.
- `CountryDefaultsTest` encodes this table; change this ADR and the table together.
