# Merchant icons

The merchant icons in `app/src/main/res/drawable-nodpi/brand_*.png` were rendered from
the corresponding SVGs in [Simple Icons](https://github.com/simple-icons/simple-icons)
on 2026-10-09. Each icon retains the brand color published in its
[`simple-icons.json`](https://github.com/simple-icons/simple-icons/blob/develop/data/simple-icons.json)
entry. PNG assets are used because Android's VectorDrawable parser rejects some
compact SVG arc syntax, including the Starbucks path.

| Merchant | Source |
| --- | --- |
| Starbucks | https://github.com/simple-icons/simple-icons/blob/develop/icons/starbucks.svg |
| Carrefour | https://github.com/simple-icons/simple-icons/blob/develop/icons/carrefour.svg |
| McDonald's | https://github.com/simple-icons/simple-icons/blob/develop/icons/mcdonalds.svg |
| Netflix | https://github.com/simple-icons/simple-icons/blob/develop/icons/netflix.svg |
| Spotify | https://github.com/simple-icons/simple-icons/blob/develop/icons/spotify.svg |
| Uber | https://github.com/simple-icons/simple-icons/blob/develop/icons/uber.svg |
| KFC | https://github.com/simple-icons/simple-icons/blob/develop/icons/kfc.svg |

Simple Icons' repository uses CC0 for its own work, but [its disclaimer](https://github.com/simple-icons/simple-icons/blob/develop/DISCLAIMER.md)
notes that individual brand icons may have separate rights. Brand names and marks
remain with their owners. These icons identify merchants in the transaction UI;
their inclusion does not imply any partnership or endorsement.

# App font

`app/src/main/assets/fonts/ReadexPro.ttf` is Readex Pro from [google/fonts](https://github.com/google/fonts/tree/main/ofl/readexpro),
licensed under the SIL Open Font License 1.1 (`assets/fonts/OFL.txt`).

# App logo

The Follow Money mark (a path that follows money upward, with a start dot) is original to this project:
`res/drawable/ic_launcher.xml`, adaptive layers `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml`,
and the notification icon `ic_stat_notify.xml`.

## Expanded catalog — 2026-10-10

35 local PNG resources are integrated:11 banks and24 merchants/services. The original7 resources remain unchanged;28 were rendered from independently collected official assets or Simple Icons. No network logo lookup is used.

[Source catalog](docs/brand-catalog-2026-10-10/catalog.json) records aliases, original SHA-256, source pages and rights status. [Android mapping](docs/brand-catalog-2026-10-10/android-assets.json) records output hashes and preserved resources. The8 pending entries have no integrated resource. Brand terms still require review before commercial release; logo matching never merges financial accounts.
