# Generates all NeonFlip launcher icon assets (adaptive layers, legacy mipmaps, store 512)
import os
import sys
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artlib import *  # noqa

ROOT = r"F:\study\Platforms\Android\Applications\Games\HyperCasual\neon-flip\neon-flip"
RES = os.path.join(ROOT, "app", "src", "main", "res")
ST = os.path.join(ROOT, "store-listing", "assets")

# Design in a 432x432 design-space (adaptive 108dp @ xxxhdpi); safe-zone radius 128
TOP_LINE = [(118, 152), (314, 140)]
BOT_LINE = [(118, 292), (314, 304)]
BALL = (217, 222)
BALL_R = 46
GHOST = (150, 143)
SPARKS = [((302, 168), 15, CYAN), ((128, 272), 11, PINK)]


def _lines(d, u, colored=True, core=False):
    if core:
        top, bot, w = mix(CYAN, WHITE, 0.8), mix(PINK, WHITE, 0.8), 5
    else:
        top, bot, w = CYAN, PINK, 11
    w = max(2, int(w * u))
    d.line([(TOP_LINE[0][0] * u, TOP_LINE[0][1] * u), (TOP_LINE[1][0] * u, TOP_LINE[1][1] * u)],
           fill=rgba(top), width=w)
    d.line([(BOT_LINE[0][0] * u, BOT_LINE[0][1] * u), (BOT_LINE[1][0] * u, BOT_LINE[1][1] * u)],
           fill=rgba(bot), width=w)
    for (p, col) in ((TOP_LINE[0], top), (TOP_LINE[1], top), (BOT_LINE[0], bot), (BOT_LINE[1], bot)):
        r = w / 2 - (0 if core else u * 1.5)
        if r > 0:
            d.ellipse([p[0] * u - r, p[1] * u - r, p[0] * u + r, p[1] * u + r], fill=rgba(col))


def _extras(d, u, color=CYAN):
    # dotted flip arc from ghost ball toward ball
    dotted_bezier(d, (GHOST[0] * u + 8 * u, GHOST[1] * u + 10 * u),
                  (BALL[0] * u - 30 * u, BALL[1] * u - 34 * u),
                  (BALL[0] * u - 46 * u, BALL[1] * u - 62 * u), n=6, r=max(1.5, 3.5 * u),
                  color=color, alpha=170)
    # speed ticks left of ball
    for (x0, x1, y) in ((146, 184, 214), (136, 180, 240)):
        d.line([(x0 * u, y * u), (x1 * u, y * u)], fill=rgba(mix(CYAN, WHITE, 0.6), 200),
               width=max(2, int(6 * u)))


def build_full(S):
    """Opaque icon: dark bg + neon motif. Returns RGB."""
    u = S / 432.0
    img = dark_bg(S, S, corner=True)
    # glow layer
    def painter(d):
        _lines(d, u, colored=True)
        d.ellipse([BALL[0] * u - BALL_R * u, BALL[1] * u - BALL_R * u,
                   BALL[0] * u + BALL_R * u, BALL[1] * u + BALL_R * u], fill=rgba(CYAN))
        d.ellipse([GHOST[0] * u - 16 * u, GHOST[1] * u - 16 * u,
                   GHOST[0] * u + 16 * u, GHOST[1] * u + 16 * u], fill=rgba(CYAN, 95))
        for (p, r, col) in SPARKS:
            sparkle(d, p[0] * u, p[1] * u, r * u, col, 200)
        _extras(d, u)
    img = glow_shapes(img, painter, (u * 4, u * 13, u * 30), (1.0, 0.55, 0.28))
    # crisp cores
    core = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(core)
    _lines(d, u, core=True)
    _extras(d, u, color=WHITE)
    img.paste(core, (0, 0), core)
    img = paste_c(img, orb(int(2 * BALL_R * u), CYAN), BALL[0] * u, BALL[1] * u)
    o, d2 = overlay(img)
    d2.ellipse([GHOST[0] * u - 13 * u, GHOST[1] * u - 13 * u,
                GHOST[0] * u + 13 * u, GHOST[1] * u + 13 * u], fill=rgba(CYAN, 85))
    merge(img, o)
    for (p, r, col) in SPARKS:
        o, d2 = overlay(img)
        sparkle(d2, p[0] * u, p[1] * u, r * u, col, 235)
        merge(img, o)
    return img


def build_foreground(S):
    """Transparent adaptive foreground with baked soft glow."""
    u = S / 432.0
    colored = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(colored)
    _lines(d, u, colored=True)
    d.ellipse([BALL[0] * u - BALL_R * u, BALL[1] * u - BALL_R * u,
               BALL[0] * u + BALL_R * u, BALL[1] * u + BALL_R * u], fill=rgba(CYAN))
    d.ellipse([GHOST[0] * u - 16 * u, GHOST[1] * u - 16 * u,
               GHOST[0] * u + 16 * u, GHOST[1] * u + 16 * u], fill=rgba(CYAN, 95))
    _extras(d, u)
    for (p, r, col) in SPARKS:
        sparkle(d, p[0] * u, p[1] * u, r * u, col, 220)
    fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fg = Image.alpha_composite(fg, soft(colored, u * 26, 0.35))
    fg = Image.alpha_composite(fg, soft(colored, u * 10, 0.75))
    core = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(core)
    _lines(d, u, core=True)
    _extras(d, u, color=WHITE)
    core = Image.alpha_composite(core, orb(int(2 * BALL_R * u), CYAN).transform(
        core.size, Image.AFFINE, (1, 0, (BALL[0] - BALL_R) * u, 0, 1, (BALL[1] - BALL_R) * u)))
    fg = Image.alpha_composite(fg, core)
    return fg


def build_background(S):
    return dark_bg(S, S, corner=True).convert("RGBA")


def save(img, path, size=None):
    if size is not None:
        img = img.resize((size, size), Image.LANCZOS)
    img.save(path, optimize=True)
    print(os.path.relpath(path, ROOT), img.size)


def main():
    for d in (["mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi",
               "mipmap-xxxhdpi", "mipmap-anydpi-v26"]):
        os.makedirs(os.path.join(RES, d), exist_ok=True)
    os.makedirs(ST, exist_ok=True)

    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    full1024 = build_full(1024)
    fg432 = build_foreground(432 * 3)  # 1296 master, downscale per density
    bg_master = build_background(432 * 3)

    for name, k in dens.items():
        # legacy launcher icons (48dp)
        save(full1024, os.path.join(RES, "mipmap-" + name, "ic_launcher.png"), int(48 * k))
        # adaptive layers (108dp)
        side = int(108 * k)
        save(fg432, os.path.join(RES, "mipmap-" + name, "ic_launcher_foreground.png"), side)
        save(bg_master, os.path.join(RES, "mipmap-" + name, "ic_launcher_background.png"), side)

    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@mipmap/ic_launcher_background"/>\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
           '</adaptive-icon>\n')
    for fn in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(RES, "mipmap-anydpi-v26", fn), "w", encoding="utf-8") as f:
            f.write(xml)
        print("mipmap-anydpi-v26/" + fn)

    save(full1024, os.path.join(ST, "icon-512.png"), 512)


if __name__ == "__main__":
    main()
