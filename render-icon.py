#!/usr/bin/env python3
"""Render the Masterset Tracker Poké Ball launcher icon as PNG mipmaps
(and a 512px preview) from the same geometry as the vector drawables."""
from PIL import Image, ImageDraw

NAVY = (11, 31, 51)
BLACK = (0, 0, 0)
RED = (227, 53, 13)
WHITE = (255, 255, 255)
GRAY = (217, 217, 217)

def draw_ball(d, size):
    c = size / 2.0
    r_out = size * 42 / 108.0
    r_in = size * 39 / 108.0
    # black disc
    d.ellipse([c - r_out, c - r_out, c + r_out, c + r_out], fill=BLACK)
    # red top half / white bottom half (inset)
    box = [c - r_in, c - r_in, c + r_in, c + r_in]
    d.pieslice(box, 180, 360, fill=RED)
    d.pieslice(box, 0, 180, fill=WHITE)
    # black band across the middle
    band = max(1, round(size * 12 / 108.0))
    d.line([(c - r_in, c), (c + r_in, c)], fill=BLACK, width=band)
    # center button
    r_btn = size * 13 / 108.0
    w_btn = max(1, round(size * 4 / 108.0))
    d.ellipse([c - r_btn, c - r_btn, c + r_btn, c + r_btn], fill=WHITE, outline=BLACK, width=w_btn)
    r_dot = size * 5 / 108.0
    w_dot = max(1, round(size * 2 / 108.0))
    d.ellipse([c - r_dot, c - r_dot, c + r_dot, c + r_dot], fill=GRAY, outline=BLACK, width=w_dot)

def render(size, path):
    img = Image.new('RGB', (size, size), NAVY)
    draw_ball(ImageDraw.Draw(img), size)
    img.save(path)
    print('wrote', path)

import os
BASE = '/home/hatch/workspace/projects/masterset-tracker/android/app/src/main/res'
for dpi, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    d = os.path.join(BASE, 'mipmap-' + dpi)
    os.makedirs(d, exist_ok=True)
    render(size, os.path.join(d, 'ic_launcher.png'))
render(512, '/home/hatch/workspace/projects/masterset-tracker/icon-preview-512.png')
