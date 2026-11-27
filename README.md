# Masterset Tracker

Android app for tracking Pokémon TCG masterset progress — interactive
checklists in binder placement order, with card pictures.

## Sets covered

| Binder | Master | Grand Master |
| --- | --- | --- |
| Perfect Order (ME03) | 124 | 263 |
| Chaos Rising (ME04) | 122 | 234 |
| Pitch Black (ME05) | 120 | 222 |
| 30th Celebration (30C) | 199 | 292 |
| Delta Reign (ME06) | 141 | 250 |
| Ditto — Every Card (#132) | 85 | 333 |

Master = one of each card number, including secret rares. Grand Master adds
the holo and reverse-holo versions, promos and special variants. The Ditto binder covers the PokeCottage Ditto list
(English / Japanese / Simplified Chinese / Korean) plus cameo cards, Topps cards,
Japanese vending prints, and other-language printings, plus sticker and
non-TCG Ditto items from the Elite Fourum Dittologist thread.

## Features

- Binder-order checklists — regular card, then its reverse holo, then
  secret rares, promos and variants last
- Two-page spread: pages sit either side of the centre binding, and a page
  turns over the binding like a real binder — the next page is already
  underneath and is revealed as the leaf lifts
- Per-binder page-size picker: 4, 9, 12 or 16 pockets per page
- Master / Grand Master toggle per binder
- Card thumbnail per row; tap for the card info, long-press to multi-select
- Missing cards are darkened; a card you add slides in lit up
- Big progress bar per binder ("N% complete"), Rare Candy style
- Graded cards: pick the company (PSA / CGC / BGS / SGC) and grade — the
  pocket shows a slab matching the company and grade
- Scan a card: in-app camera with a card outline — the reader only looks
  at what's inside the frame
- Progress saved on the phone (localStorage); search + "missing only" filter
- In-app updates: checks GitHub releases on launch and offers to download
  and install a newer version

## Data

Checklists come from [PokeCottage](https://pokecottage.com) interactive
checklists, cross-checked against the Pokémon TCG API / TCGdex. Card images
load from the internet (scrydex / images.pokemontcg.io mirrors, plus the
Elite Fourum Dittologist thread and dexli.st for Korean cards); pictures are
cached on the phone after the first view, so turning pages is instant. A few
cards have no verified image yet and show a placeholder.

Source data lives in `data/*.json` (one file per binder). The web app is
generated from `app-template.html` by `build.py`, which embeds the data
into `android/app/src/main/assets/index.html`.

## Build

```sh
python3 build.py
cd android
# with ANDROID_HOME set and JDK 17:
gradle clean assembleDebug --offline
```

The app is a WebView shell (single `index.html` + bundled data). Debug
builds are signed with the developer's debug keystore so new versions
install over old ones.

## Updates

New versions ship as GitHub releases. The app checks
`api.github.com/repos/JeremyShanebrook/Masterset-Tracker/releases/latest`
on launch; when a newer tag exists it offers to download and install the
APK from that release.
