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
* `drawable-<density>/ic_notification.png` for the media notification / status bar small icon
  (a one-colour cut-out: the system tints that icon and reads only its alpha channel)
* `drawable-nodpi/lunara_logo.png` and `lunara_logo_white.png` used by the in-app branding
* `drawable/small_icon.png` for the notification small icon and for the spots that draw the mark
  beside text
* `mipmap-<density>/ic_launcher_static.png` for the pre-adaptive launcher fallback, drawn on the
  launcher-tile colour instead of on transparency
* `mipmap-<density>/ic_launcher_fg_lunara.png` for the adaptive-icon foreground referenced by both
  `drawable/ic_launcher_foreground_inset.xml` and the static `mipmap-anydpi` adaptive icon
* `mipmap-xhdpi/tv_banner.png` for the Android TV launcher tile
* `app/src/main/ic_launcher-playstore.png` and `fastlane/metadata/android/en-US/images/icon.png`
  for the store listings, plus `featureGraphic.png` for the listing banner

Everything is a **density-correct** bitmap on purpose: a density-less (`drawable-nodpi`) image has
no dp size of its own, so the system draws it at its raw pixel size and clips whatever does not fit.
That is what used to cut the logo in half during launch.
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
# Splash artwork keeps the same visual size as the Compose splash logo (132dp).
SPLASH_DP = 132
DENSITY_SCALE = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
# Small icons are drawn at 24dp by the system (status bar and notification shade).
NOTIFICATION_DP = 24
# Alpha that separates the artwork from the soft glow around it in the master PNG.
SILHOUETTE_ALPHA = 128
# Breathing room so the cut-out does not touch the edges of its 24dp box.
SILHOUETTE_INSET = 0.08
RES_DIR = os.path.join("app", "src", "main", "res")
# The master artwork already carries roughly a tenth of its canvas as margin,
# so these are insets on top of that, not the final size of the mark.
# Adaptive foreground: keeps the glyph inside the 66dp safe zone even before
# the extra 12dp the inset layer-list adds.
FOREGROUND_INSET = 0.19
# Legacy icons are masked by the launcher, so the mark keeps a margin.
LEGACY_INSET = 0.22
# Store listings: a square icon on the launcher-tile colour.
STORE_ICON_PX = 512
STORE_ICON_INSET = 0.12
# Android TV launcher tile (320x180dp at xhdpi).
TV_BANNER_SIZE = (640, 360)
# Matches `ic_launcher_background` in res/values/ic_launcher_background.xml.
TILE_BACKGROUND = (255, 255, 255, 255)
# Matches `lunara_background` in res/values/colors.xml.
TILE_DARK_BACKGROUND = (13, 15, 18, 255)
# Present on this machine and on any Debian/Ubuntu box with fonts-dejavu.
WORDMARK_FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def fit(source: Image.Image, size: int, inset: float = 0.0) -> Image.Image:
    """Centre the artwork on a transparent square canvas, optionally inset by a fraction."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = max(1, int(round(size * (1.0 - inset))))
    scaled = source.resize((inner, inner), Image.LANCZOS)
    offset = (size - inner) // 2
    canvas.paste(scaled, (offset, offset), scaled)
    return canvas


def circle(source: Image.Image, size: int, inset: float = 0.0) -> Image.Image:
    """Round variant of the legacy icon."""
    base = fit(source, size, inset=inset)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out


def on_background(
    source: Image.Image,
    size: int,
    background: tuple,
    inset: float = 0.0,
) -> Image.Image:
    """Composite the artwork onto an opaque square, for icons that are never transparent."""
    canvas = Image.new("RGBA", (size, size), background)
    canvas.alpha_composite(fit(source, size, inset=inset))
    return canvas


def white_mark(source: Image.Image, size: int) -> Image.Image:
    """Flat white cut-out of the mark, keeping the artwork's antialiased edges."""
    white = Image.new("RGBA", source.size, (255, 255, 255, 0))
    white.putalpha(source.getchannel("A"))
    return fit(white, size)


def tv_banner(source: Image.Image, size: tuple) -> Image.Image:
    """Android TV launcher tile: the mark on the dark brand backdrop."""
    width, height = size
    canvas = Image.new("RGBA", size, TILE_DARK_BACKGROUND)
    art = fit(source, height, inset=0.16)
    canvas.alpha_composite(art, ((width - art.width) // 2, 0))
    return canvas.convert("RGB")


def feature_graphic(source: Image.Image, directory: str, name: str = "Lunara") -> None:
    """1024x500 store banner: the mark and the wordmark on the dark backdrop."""
    width, height = 1024, 500
    canvas = Image.new("RGB", (width, height), TILE_DARK_BACKGROUND[:3])
    art = fit(source, int(height * 0.6))
    canvas.paste(art, (96, (height - art.height) // 2), art)
    if os.path.isfile(WORDMARK_FONT):
        draw = ImageDraw.Draw(canvas)
        font = ImageFont.truetype(WORDMARK_FONT, 132)
        text_x = 96 + art.width + 64
        box = draw.textbbox((text_x, 0), name, font=font)
        draw.text(
            (text_x, (height - (box[3] - box[1])) // 2 - box[1]),
            name,
            font=font,
            fill=(255, 255, 255),
        )
    canvas.save(os.path.join(directory, "featureGraphic.png"))


def silhouette(source: Image.Image, size: int) -> Image.Image:
    """Flat one-colour cut-out of the artwork, for the notification small icon.

    The system tints a small icon and reads only its alpha channel, so the shading of the master
    PNG is dropped and the shape alone has to carry the brand.
    """
    art = source.convert("RGBA")
    bounds = art.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("artwork is fully transparent")
    art = art.crop(bounds)
    mask = art.getchannel("A").point(lambda value: 255 if value >= SILHOUETTE_ALPHA else 0)
    side = max(art.width, art.height)
    square = Image.new("L", (side, side), 0)
    square.paste(mask, ((side - art.width) // 2, (side - art.height) // 2))
    flat = Image.new("RGBA", (side, side), (255, 255, 255, 0))
    flat.putalpha(square)
    return fit(flat, size, inset=SILHOUETTE_INSET)


def main() -> int:
    master = sys.argv[1] if len(sys.argv) > 1 else "logo.png"
    if not os.path.isfile(master):
        print(f"master artwork not found: {master}", file=sys.stderr)
        return 1

    source = Image.open(master).convert("RGBA")

    for density, size in LEGACY_SIZES.items():
        directory = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(directory, exist_ok=True)
        fit(source, size, inset=LEGACY_INSET).save(os.path.join(directory, "ic_launcher.png"))
        circle(source, size, inset=LEGACY_INSET).save(
            os.path.join(directory, "ic_launcher_round.png")
        )
        # Pre-adaptive fallback: drawn on the launcher-tile colour, never transparent.
        on_background(source, size, TILE_BACKGROUND, inset=LEGACY_INSET).save(
            os.path.join(directory, "ic_launcher_static.png")
        )
        # Adaptive foreground, referenced by the inset layer-list and the static anydpi icon.
        fit(source, ADAPTIVE_SIZES[density], inset=FOREGROUND_INSET).save(
            os.path.join(directory, "ic_launcher_fg_lunara.png")
        )

    for density, size in ADAPTIVE_SIZES.items():
        directory = os.path.join(RES_DIR, f"drawable-{density}")
        os.makedirs(directory, exist_ok=True)
        fit(source, size, inset=0.28).save(os.path.join(directory, "ic_launcher_foreground.png"))

    nodpi = os.path.join(RES_DIR, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    logo_path = os.path.join(nodpi, "lunara_logo.png")
    fit(source, 512).save(logo_path)

    # The mark is legible on light and dark backdrops alike, but several screens sit on album
    # artwork, so a flat white cut-out of the same shape is kept alongside the colour one.
    white_mark(source, 512).save(os.path.join(nodpi, "lunara_logo_white.png"))

    # Splash art is derived from the committed in-app logo so both screens always match.
    splash_source = Image.open(logo_path).convert("RGBA")
    for density, scale in DENSITY_SCALE.items():
        directory = os.path.join(RES_DIR, f"drawable-{density}")
        os.makedirs(directory, exist_ok=True)
        pixels = int(round(SPLASH_DP * scale))
        fit(splash_source, pixels).save(os.path.join(directory, "splash_logo.png"))

    # The notification small icon is the same artwork reduced to a single flat colour.
    for density, scale in DENSITY_SCALE.items():
        directory = os.path.join(RES_DIR, f"drawable-{density}")
        os.makedirs(directory, exist_ok=True)
        pixels = int(round(NOTIFICATION_DP * scale))
        silhouette(splash_source, pixels).save(os.path.join(directory, "ic_notification.png"))

    # `small_icon` is a density-less drawable used both as a notification small icon and as a
    # mark drawn beside text, so it keeps the artwork's colour.
    fit(source, 192).save(os.path.join(RES_DIR, "drawable", "small_icon.png"))

    # Android TV launcher tile.
    banner_dir = os.path.join(RES_DIR, "mipmap-xhdpi")
    os.makedirs(banner_dir, exist_ok=True)
    tv_banner(source, TV_BANNER_SIZE).save(os.path.join(banner_dir, "tv_banner.png"))

    # Store listings.
    store_icon = on_background(source, STORE_ICON_PX, TILE_BACKGROUND, inset=STORE_ICON_INSET)
    store_icon.convert("RGB").save(os.path.join("app", "src", "main", "ic_launcher-playstore.png"))
    fastlane_images = os.path.join("fastlane", "metadata", "android", "en-US", "images")
    if os.path.isdir(fastlane_images):
        store_icon.convert("RGB").save(os.path.join(fastlane_images, "icon.png"))
        feature_graphic(source, fastlane_images)

    print("brand assets written")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
