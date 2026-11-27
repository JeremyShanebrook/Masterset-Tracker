#!/usr/bin/env python3
"""Build the Masterset Tracker web app: compact the data JSONs and inject
them into app-template.html, writing the final index.html into the Android
project's assets dir."""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, 'data')
OUT = os.path.join(HERE, 'android', 'app', 'src', 'main', 'assets', 'index.html')

ORDER = [
    ('perfect-order', 'perfect-order.json'),
    ('chaos-rising', 'chaos-rising.json'),
    ('pitch-black', 'pitch-black.json'),
    ('30th-celebration', '30th-celebration.json'),
    ('delta-reign', 'delta-reign.json'),
    ('ditto', 'ditto.json'),
]

def compact_card(c, is_ditto):
    out = {
        'n': c.get('num'),
        'name': c.get('name'),
        'r': c.get('rarity'),
        's': c.get('section'),
        'v': c.get('variant'),
        'm': 1 if c.get('inMaster') else 0,
        'g': 1 if c.get('inGrandMaster') else 0,
        'img': c.get('image'),
    }
    if c.get('language'):
        out['lang'] = c['language']
    if c.get('group'):
        out['grp'] = c['group']
    if c.get('set'):
        out['set'] = c['set']
    # promo if it applies: Promos section, MEP numbers, or a promo set/variant
    sec = c.get('section') or ''
    num = c.get('num') or ''
    var = c.get('variant') or ''
    setname = c.get('set') or ''
    if sec == 'Promos' or num.startswith('MEP') or 'Promo' in var or 'Promo' in setname:
        out['promo'] = 1
    # divider label shown when the group changes in binder order. For Ditto
    # the rows carry an explicit 'div' (the set name for main cards, the
    # section name for Cameos / Topps / Other) so languages are never
    # separated by a language label.
    out['div'] = (c.get('div') or c.get('group')) if is_ditto else (c.get('section') or None)
    return out

binders = []
for bid, fname in ORDER:
    d = json.load(open(os.path.join(DATA, fname)))
    is_ditto = (bid == 'ditto')
    cards = [compact_card(c, is_ditto) for c in d['cards']]
    m = sum(c['m'] for c in cards)
    g = sum(c['g'] for c in cards)
    assert m == d['masterTotal'], f"{bid}: master {m} != {d['masterTotal']}"
    assert g == d['grandMasterTotal'], f"{bid}: grand {g} != {d['grandMasterTotal']}"
    binders.append({
        'id': bid,
        'name': d.get('name'),
        'series': d.get('series') or d.get('setCode') or '',
        'releaseDate': d.get('releaseDate'),
        'masterTotal': d['masterTotal'],
        'grandMasterTotal': d['grandMasterTotal'],
        'cover': d.get('cover'),
        'cards': cards,
    })
    print(f"{bid}: {len(cards)} rows, master {m}, grand {g}, "
          f"images {sum(1 for c in cards if c['img'])}")

data = {'binders': binders}
tpl = open(os.path.join(HERE, 'app-template.html')).read()
assert '/*__DATA__*/' in tpl
html = tpl.replace('/*__DATA__*/', json.dumps(data, separators=(',', ':')))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w').write(html)
print(f"wrote {OUT} ({len(html):,} bytes)")
