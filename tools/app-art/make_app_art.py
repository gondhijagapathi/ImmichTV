#!/usr/bin/env python3
"""
Makes ImmichTV's launcher icon and TV banner from Immich's official logo, as vector drawables.

    python3 tools/app-art/make_app_art.py

Downloads the logo from Immich's repository (design/), then writes:
- app/src/main/res/drawable/ic_launcher_foreground.xml: the flower, 48dp across, centred in the
  108dp adaptive icon so any launcher mask leaves room around it.
- app/src/main/res/drawable/banner.xml: the "immich" logo for dark backgrounds with "TV" drawn in
  round strokes matching its lettering, on the app's dark background.
"""

import re
import urllib.request
from pathlib import Path

LOGO_URL = "https://raw.githubusercontent.com/immich-app/immich/main/design/{}"
DRAWABLES = Path(__file__).resolve().parents[2] / "app/src/main/res/drawable"
ANDROID = 'xmlns:android="http://schemas.android.com/apk/res/android"'

BACKGROUND = "#1C1B1F"  # the app's dark background
TEXT = "#ACCBFA"  # the lettering colour of Immich's logo for dark backgrounds
STROKE = 21  # thickness of the logo's letters


def shapes(file_name):
    """The (fill colour, path data) of each shape in one of Immich's logo SVGs."""
    svg = urllib.request.urlopen(LOGO_URL.format(file_name), timeout=30).read().decode()
    colours = dict(re.findall(r"\.(st\d+)\{fill:(#[0-9A-Fa-f]{6});\}", svg))
    return [(colours[cls], " ".join(d.split())) for cls, d in re.findall(r'<path class="(st\d+)" d="([^"]*)"', svg)]


def vector(width, height, group, paths, header=()):
    lines = [*header, f"<vector {ANDROID}", f'    android:width="{width}dp"', f'    android:height="{height}dp"',
             f'    android:viewportWidth="{width}"', f'    android:viewportHeight="{height}">']
    lines += paths[0]
    scale, tx, ty = group
    lines += ["    <group", f'        android:scaleX="{scale:.5f}"', f'        android:scaleY="{scale:.5f}"',
              f'        android:translateX="{tx:.3f}"', f'        android:translateY="{ty:.3f}">']
    lines += ["    " + line for line in paths[1]]
    lines += ["    </group>", "</vector>", ""]
    return "\n".join(lines)


def fill(colour, data):
    return ["    <path", f'        android:fillColor="{colour}"', f'        android:pathData="{data}" />']


def stroke(colour, data):
    return ["    <path", f'        android:pathData="{data}"', f'        android:strokeColor="{colour}"',
            '        android:strokeLineCap="round"', '        android:strokeLineJoin="round"',
            f'        android:strokeWidth="{STROKE}" />']


def fit(bounds, width, height, size, centre):
    """Scale and offset that make the bounds [size] units wide (or tall), centred on [centre]."""
    x0, y0, x1, y1 = bounds
    scale = size / max(x1 - x0, y1 - y0) if width is None else size / (x1 - x0)
    return scale, centre[0] - (x0 + x1) / 2 * scale, centre[1] - (y0 + y1) / 2 * scale


def banner():
    # In the logo's 792x266 canvas the shapes span x 29.5-759 and y 43-221. The lettering's
    # ascenders start at y 70 and its baseline is at 182.5, so "TV" uses the same height.
    top, bottom = 70 + STROKE / 2, 182.5 - STROKE / 2
    t_left = 759 + 45 + STROKE / 2
    t_right = t_left + 66
    v_left = t_right + STROKE + 24
    v_right = v_left + 74
    tv = [f"M{t_left},{top}H{t_right}M{(t_left + t_right) / 2},{top}V{bottom}",
          f"M{v_left},{top}L{(v_left + v_right) / 2},{bottom}L{v_right},{top}"]
    bounds = (29.5, 43, v_right + STROKE / 2, 221)
    group = fit(bounds, 320, 180, 320 * 0.74, (160, 90))
    paths = [line for colour, data in shapes("immich-logo-inline-dark.svg") for line in fill(colour, data)]
    paths += [line for data in tv for line in stroke(TEXT, data)]
    header = ["<!-- Made by tools/app-art/make_app_art.py. The launcher draws this banner once, so its size",
              "     doesn't cost anything, and as a vector it stays sharp on any TV. -->"]
    xml = vector(320, 180, group, (fill(BACKGROUND, "M0,0h320v180h-320z"), paths), header)
    return xml.replace(f"<vector {ANDROID}", f'<vector {ANDROID}\n    xmlns:tools="http://schemas.android.com/tools"\n'
                                           f'    tools:ignore="VectorRaster"', 1)


def icon_foreground():
    # In the logo's 792x792 canvas the flower spans x 100-690.5 and y 95-672.
    group = fit((100, 95, 690.5, 672), None, None, 48, (54, 54))
    paths = [line for colour, data in shapes("immich-logo.svg") for line in fill(colour, data)]
    return vector(108, 108, group, ([], paths), ["<!-- Made by tools/app-art/make_app_art.py. -->"])


(DRAWABLES / "banner.xml").write_text(banner())
(DRAWABLES / "ic_launcher_foreground.xml").write_text(icon_foreground())
print(f"Wrote banner.xml and ic_launcher_foreground.xml in {DRAWABLES}")
