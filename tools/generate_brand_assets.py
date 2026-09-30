#!/usr/bin/env python3
"""Regenerate Lunara's launcher icons and in-app logo from the master artwork.

Usage (from the repository root, needs Pillow):

    python3 tools/generate_brand_assets.py logo.png

`logo.png` is the square master artwork (it is git-ignored; only the derived assets are
committed). The script writes:

* `mipmap-<density>/ic_launcher.png` and `ic_launcher_round.png` for the legacy launcher icon
* `drawable-<density>/ic_launcher_foreground.png` for the adaptive icon (108dp canvas, artwork
  kept inside the 66dp safe zone so no launcher mask clips it)
* `drawable-<density>/splash_logo.png` for the launch screen at an exact dp size, which is what
  keeps the platform splash from cropping the artwork
* `drawable-nodpi/lunara_logo.png` used by the in-app branding

Everything is a **density-correct** bitmap on purpose: a density-less (`drawable-nodpi`) image has
no dp size of its own, so the system draws it at its raw pixel size and clips whatever does not fit.
That is what used to cut the logo in half during launch.
"""

import os
import sys

from PIL import Image, ImageDraw

LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
# Splash artwork keeps the same visual size as the Compose splash logo (132dp).
SPLASH_DP = 132
DENSITY_SCALE = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
RES_DIR = os.path.join("app", "src", "main", "res")


def fit(source: Image.Image, size: int, inset: float = 0.0) -> Image.Image:
    """Centre the artwork on a transparent square canvas, optionally inset by a fraction."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = max(1, int(round(size * (1.0 - inset))))
    scaled = source.resize((inner, inner), Image.LANCZOS)
    offset = (size - inner) // 2
    canvas.paste(scaled, (offset, offset), scaled)
    return canvas


def circle(source: Image.Image, size: int) -> Image.Image:
    """Round variant of the legacy icon."""
    base = fit(source, size)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out


def main() -> int:
    master = sys.argv[1] if len(sys.argv) > 1 else "logo.png"
    if not os.path.isfile(master):
        print(f"master artwork not found: {master}", file=sys.stderr)
        return 1

    source = Image.open(master).convert("RGBA")

    for density, size in LEGACY_SIZES.items():
        directory = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(directory, exist_ok=True)
        fit(source, size).save(os.path.join(directory, "ic_launcher.png"))
        circle(source, size).save(os.path.join(directory, "ic_launcher_round.png"))

    for density, size in ADAPTIVE_SIZES.items():
        directory = os.path.join(RES_DIR, f"drawable-{density}")
        os.makedirs(directory, exist_ok=True)
        fit(source, size, inset=0.28).save(os.path.join(directory, "ic_launcher_foreground.png"))

    nodpi = os.path.join(RES_DIR, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    logo_path = os.path.join(nodpi, "lunara_logo.png")
    fit(source, 512).save(logo_path)

    # Splash art is derived from the committed in-app logo so both screens always match.
    splash_source = Image.open(logo_path).convert("RGBA")
    for density, scale in DENSITY_SCALE.items():
        directory = os.path.join(RES_DIR, f"drawable-{density}")
        os.makedirs(directory, exist_ok=True)
        pixels = int(round(SPLASH_DP * scale))
        fit(splash_source, pixels).save(os.path.join(directory, "splash_logo.png"))

    print("brand assets written")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
