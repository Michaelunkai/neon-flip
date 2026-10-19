# Feature graphic 1024x500 — "NEON FLIP" glowing lettering + flip motif
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artlib import *  # noqa

ROOT = r"F:\study\Platforms\Android\Applications\Games\HyperCasual\neon-flip\neon-flip"
W, H = 1024, 500


def main():
    out = os.path.join(ROOT, "store-listing", "assets", "feature-graphic.png")
    img = dark_bg(W, H, corner=True)

    # neon ambience
    img = corner_glows(img, [
        (170, 240, 300, (0, 70, 90), 0.8),      # cyan wash behind title
        (830, 300, 280, (90, 12, 55), 0.8),     # pink wash behind motif
    ])

    # perspective floor
    img = grid_floor(img, horizon=378, vp_x=520, alpha=40, rows=7, cols=14)
    o, d = overlay(img)
    d.line([(0, 378), (W, 378)], fill=rgba(CYAN, 60), width=2)
    img = merge(img, o)

    # ---- title, stacked left ----
    fb = F("black", 168)
    img = neon_text(img, (66, 168), "NEON", fb, CYAN, tracking=0.055, anchor="l", glow_scale=1.15)
    img = neon_text(img, (66, 330), "FLIP", fb, PINK, tracking=0.055, anchor="l", glow_scale=1.15)
    # neon underline bar
    lay = Image.new("RGB", (W, H), (0, 0, 0))
    ImageDraw.Draw(lay).line([(74, 424), (430, 424)], fill=CYAN, width=8)
    img = glow_pass(img, lay, (4, 14), (1.0, 0.5))
    o, d = overlay(img)
    d.line([(74, 424), (430, 424)], fill=rgba(mix(CYAN, WHITE, 0.7)), width=4)
    img = merge(img, o)

    # ---- flip motif, right ----
    u = 1.0
    top, bot = [(636, 118), (972, 98)], [(636, 382), (972, 402)]
    bx, by, br = 806, 250, 56

    def painter(d):
        d.line([(top[0][0], top[0][1]), (top[1][0], top[1][1])], fill=rgba(CYAN), width=12)
        d.line([(bot[0][0], bot[0][1]), (bot[1][0], bot[1][1])], fill=rgba(PINK), width=12)
        for p in top + bot:
            d.ellipse([p[0] - 7, p[1] - 7, p[0] + 7, p[1] + 7],
                      fill=rgba(CYAN if p in top else PINK))
        d.ellipse([bx - br, by - br, bx + br, by + br], fill=rgba(CYAN))
        dotted_bezier(d, (700, 122), (bx - 38, by - 44), (bx - 62, by - 76),
                      n=6, r=4, color=WHITE, alpha=160)
        sparkle(d, 940, 168, 17, CYAN, 220)
        sparkle(d, 668, 330, 12, PINK, 220)
        sparkle(d, 890, 330, 10, WHITE, 180)
    img = glow_shapes(img, painter, (5, 16, 34), (1.0, 0.55, 0.28))

    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(core)
    d.line([(top[0][0], top[0][1]), (top[1][0], top[1][1])], fill=rgba(mix(CYAN, WHITE, 0.8)), width=5)
    d.line([(bot[0][0], bot[0][1]), (bot[1][0], bot[1][1])], fill=rgba(mix(PINK, WHITE, 0.8)), width=5)
    img.paste(core, (0, 0), core)
    img = paste_c(img, orb(2 * br, CYAN), bx, by)
    o, d = overlay(img)
    dotted_bezier(d, (700, 122), (bx - 38, by - 44), (bx - 62, by - 76), n=6, r=3, color=WHITE, alpha=210)
    sparkle(d, 940, 168, 15, mix(CYAN, WHITE, 0.5), 255)
    sparkle(d, 668, 330, 10, mix(PINK, WHITE, 0.5), 255)
    sparkle(d, 890, 330, 9, WHITE, 220)
    img = merge(img, o)

    img = vignette(img, strength=0.5)
    img = sheen(img, W // 2, H // 2, 1200, alpha=10)
    img.save(out, optimize=True)
    print("feature-graphic.png", img.size)


if __name__ == "__main__":
    main()
