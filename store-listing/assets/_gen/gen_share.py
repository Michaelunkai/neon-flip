# Share-score card template 1080x1350
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artlib import *  # noqa

ROOT = r"F:\study\Platforms\Android\Applications\Games\HyperCasual\neon-flip\neon-flip"
W, H = 1080, 1350


def main():
    out = os.path.join(ROOT, "store-listing", "assets", "share-card.png")
    img = dark_bg(W, H, corner=True)
    img = grid_floor(img, horizon=1030, vp_x=540, alpha=34, rows=6, cols=12)

    # brand lockup
    fb = F("black", 88)
    w1 = fb.getlength("NEON")
    w2 = fb.getlength("FLIP")
    gapc = 30
    startx = 540 - (w1 + gapc + w2) / 2
    lay = Image.new("RGB", (W, H), (0, 0, 0))
    ld = ImageDraw.Draw(lay)
    ttext(ld, (startx, 132), "NEON", fb, CYAN, 0.05, "l")
    ttext(ld, (startx + w1 + gapc, 132), "FLIP", fb, PINK, 0.05, "c" if False else "l")
    img = glow_pass(img, lay, (4, 12, 26), (1.0, 0.55, 0.3))
    d = ImageDraw.Draw(img)
    ttext(d, (startx, 132), "NEON", fb, mix(CYAN, WHITE, 0.6), 0.05, "l")
    ttext(d, (startx + w1 + gapc, 132), "FLIP", fb, mix(PINK, WHITE, 0.6), 0.05, "l")

    o, d = overlay(img)
    for i in range(24):
        x = 300 + i * 20
        col = mix(CYAN, PINK, i / 23)
        d.rectangle([x, 208, x + 12, 214], fill=rgba(col, 220))
    img = merge(img, o)

    # score
    d = ImageDraw.Draw(img)
    ttext(d, (540, 356), "SCORE", F("bahn", 54), DIM, 0.42, "c")
    img = neon_text(img, (540, 512), "12 847", F("black", 236), CYAN, tracking=0.03, glow_scale=1.05)
    img = pill_text(img, (322, 672, 758, 740), "NEW PERSONAL BEST", F("bahn", 38), PINK,
                    fill=(48, 8, 30, 215), outline=rgba(PINK, 200), width=3, glow_color=PINK)

    # flip motif
    def painter(d):
        d.line([(300, 906), (780, 884)], fill=rgba(CYAN), width=12)
        d.line([(300, 1022), (780, 1044)], fill=rgba(PINK), width=12)
        for p in ((300, 906), (780, 884)):
            d.ellipse([p[0] - 7, p[1] - 7, p[0] + 7, p[1] + 7], fill=rgba(CYAN))
        for p in ((300, 1022), (780, 1044)):
            d.ellipse([p[0] - 7, p[1] - 7, p[0] + 7, p[1] + 7], fill=rgba(PINK))
        d.ellipse([540 - 52, 964 - 52, 540 + 52, 964 + 52], fill=rgba(CYAN))
        sparkle(d, 820, 948, 15, CYAN, 220)
        sparkle(d, 262, 990, 11, PINK, 220)
    img = glow_shapes(img, painter, (5, 14, 30), (0.95, 0.5, 0.25))
    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(core)
    d.line([(300, 906), (780, 884)], fill=rgba(mix(CYAN, WHITE, 0.8)), width=5)
    d.line([(300, 1022), (780, 1044)], fill=rgba(mix(PINK, WHITE, 0.8)), width=5)
    img.paste(core, (0, 0), core)
    img = paste_c(img, orb(96, CYAN), 540, 964)
    o, d = overlay(img)
    sparkle(d, 820, 948, 13, mix(CYAN, WHITE, 0.5), 255)
    sparkle(d, 262, 990, 9, mix(PINK, WHITE, 0.5), 255)
    img = merge(img, o)

    # CTA
    box = (170, 1148, 910, 1252)
    o, d = overlay(img)
    d.rounded_rectangle(box, radius=52, fill=(13, 17, 34, 230))
    img = merge(img, o)
    lay = Image.new("RGB", (W, H), (0, 0, 0))
    ImageDraw.Draw(lay).rounded_rectangle(box, radius=52, outline=CYAN, width=5)
    img = glow_pass(img, lay, (5, 14), (0.8, 0.4))
    o, d = overlay(img)
    d.rounded_rectangle(box, radius=52, outline=rgba(mix(CYAN, WHITE, 0.55), 220), width=3)
    d.ellipse([196, 1164, 268, 1236], fill=rgba(CYAN))
    d.polygon([(222, 1182), (222, 1218), (256, 1200)], fill=(8, 12, 24))
    img = merge(img, o)
    d = ImageDraw.Draw(img)
    ttext(d, (592, 1188), "PLAY FREE ON GOOGLE PLAY", F("black", 38), WHITE, 0.02, "c")
    d = ImageDraw.Draw(img)
    ttext(d, (540, 1310), "CAN YOU BEAT THIS SCORE?", F("bahn", 30), DIM, 0.14, "c")

    img = vignette(img, strength=0.5)
    img = sheen(img, W // 2, H // 2, 1500, alpha=9)
    img.save(out, optimize=True)
    print("share-card.png", img.size)


if __name__ == "__main__":
    main()
