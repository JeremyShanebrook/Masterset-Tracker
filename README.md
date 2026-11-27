# Masterset Tracker

Android app for tracking Pokémon TCG masterset progress — interactive
checklists in binder placement order, with card pictures.

## Sets covered

| Binder | Master | Grand Master |
| --- | --- | --- |
| Perfect Order (ME03) | 124 | 263 |
| Chaos Rising (ME04) | 122 | 234 |
| Pitch Black (ME05) | 120 | 222 |
| 30th Celebration (30C) | 199 | 283 |
| Delta Reign (ME06) | 141 | 250 |
| Ditto — Every Card (#132) | 86 | 200 |

Master = one of each card number, including secret rares. Grand Master adds
the holo and reverse-holo versions, promos and special variants. The Ditto binder covers the PokeCottage Ditto list
(English / Japanese / Simplified Chinese / Korean) plus cameo cards, Topps cards,
Japanese vending prints, plus sticker and
non-TCG Ditto items from the Elite Fourum Dittologist thread.

## Features

- Binder-order checklists — regular card, then its reverse holo, then
  secret rares, promos and variants last
- One page at a time, full width — the cards are big enough to tell apart
  from the picture alone. No headers or divider rows: every page is a
  clean grid of cards, and every page is the same height, so turning never
  shifts the layout or leaves empty slots
- Per-binder page-size picker: 4, 9, 12 or 16 pockets per page
- Master / Grand Master toggle per binder
- Card thumbnail per row; tap for the card info, long-press to multi-select
- In the card details, swipe left or right to move to the next or previous
  card in the binder; closing the details lands the binder on the page of
  the card you were last viewing, not the page you opened it from
- Missing cards are darkened; a card you add slides in lit up
- A card whose picture does not show its variation is marked with an
  asterisk at the start of its description and the note "Correct card
  image not found" — 7 Ditto rows are flagged this way (151 Master Ball /
  Poké Ball holos, Fossil + Koga's Ditto 1st Ed., Flight of Legends
  Unlimited). The Delta Species variation rows are NOT flagged: the image
  shows the right card number and art, only the finish / stamp / printed
  code is not captured
- 30th Celebration: the three First Partner sets each get their own page
  (each generation's starters share a row, so the nine cards complete one
  picture), and the Classic Collection and Promos each start on a fresh page
- Multi-select: Add to collection / Remove from collection also close
  multi-select; the Done button is now Cancel
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
Elite Fourum Dittologist thread, dexli.st for Korean cards, Poplex for old
Japanese, PK.TCG for Simplified Chinese, and Cardboard Tens for the 30th
Celebration Classic Collection); pictures are cached on the phone after the
first view, so turning pages is instant. 26 Ditto cards have no verified
image yet and show a placeholder; another 38 show a picture of the base
card instead of their variation and are flagged with an asterisk in the
card details.

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
