# Six 1080x2340 store screenshots — procedural in-game mockups
import os
import sys
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artlib import *  # noqa

ROOT = r"F:\study\Platforms\Android\Applications\Games\HyperCasual\neon-flip\neon-flip"
OUT = os.path.join(ROOT, "store-listing", "assets")
W, H = 1080, 2340
VP = (540, 880)


def finish(img, name):
    img = vignette(img, strength=0.5)
    status_bar(img)
    img.save(os.path.join(OUT, name), optimize=True)
    print(name, img.size)


def ghost_orb(d_, color, alpha):
    t = orb(d_, color)
    a = t.getchannel("A").point(lambda p: int(p * alpha / 255))
    t.putalpha(a)
    return t


def corridor(img, with_core=True):
    def painter(d):
        for xb, c in ((-380, PINK), (1460, PINK)):
            d.line([(VP[0], VP[1]), (xb, H)], fill=rgba(c, 170), width=11)
        for xb, c in ((-40, CYAN), (1120, CYAN)):
            d.line([(VP[0], VP[1]), (xb, H)], fill=rgba(c, 130), width=7)
        for k in range(1, 10):
            t = (k / 9) ** 1.7
            y = VP[1] + (H - VP[1]) * t
            half = 60 + 860 * t
            d.line([(VP[0] - half, y), (VP[0] + half, y)], fill=rgba(CYAN, 105), width=6)
    img = glow_shapes(img, painter, (5, 14, 30), (0.9, 0.5, 0.25))
    if with_core:
        o, d = overlay(img)
        for xb, c in ((-380, PINK), (1460, PINK), (-40, CYAN), (1120, CYAN)):
            d.line([(VP[0], VP[1]), (xb, H)], fill=rgba(mix(c, WHITE, 0.65), 225), width=3)
        img = merge(img, o)
    return img


def coin_neon(base, cx, cy, d_, color, glow=True):
    if glow:
        def painter(d):
            d.ellipse([cx - d_ / 2, cy - d_ / 2, cx + d_ / 2, cy + d_ / 2],
                      fill=rgba(color, 170))
        base = glow_shapes(base, painter, (4, 10), (0.9, 0.5))
    base = paste_c(base, ring(d_, color, max(4, d_ // 9), 255), cx, cy)
    o, d = overlay(base)
    diamond(d, cx, cy, d_ * 0.22, mix(color, WHITE, 0.5))
    return merge(base, o)


def button(base, box, color, text, tfont, filled=True):
    """Primary/secondary neon button."""
    x0, y0, x1, y1 = box
    if filled:
        lay = Image.new("RGB", base.size, (0, 0, 0))
        ImageDraw.Draw(lay).rounded_rectangle(box, radius=(y1 - y0) // 2, fill=color)
        base = glow_pass(base, lay, (6, 18, 36), (0.9, 0.5, 0.25))
        bw, bh = x1 - x0, y1 - y0
        grad = vgrad((bw, bh), mix(color, WHITE, 0.25), mix(color, (0, 0, 0), 0.25))
        mask = Image.new("L", (bw, bh), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, bw - 1, bh - 1], radius=(bh) // 2, fill=255)
        base.paste(grad, (x0, y0), mask)
        o, d = overlay(base)
        d.rounded_rectangle(box, radius=(y1 - y0) // 2, outline=rgba(mix(color, WHITE, 0.6), 200), width=3)
        base = merge(base, o)
        d = ImageDraw.Draw(base)
        ttext(d, ((x0 + x1) // 2, (y0 + y1) // 2 - 2), text, tfont, (8, 6, 16), 0.08, "c")
    else:
        lay = Image.new("RGB", base.size, (0, 0, 0))
        ImageDraw.Draw(lay).rounded_rectangle(box, radius=(y1 - y0) // 2, outline=color, width=6)
        base = glow_pass(base, lay, (5, 14), (0.8, 0.4))
        o, d = overlay(base)
        d.rounded_rectangle(box, radius=(y1 - y0) // 2, outline=rgba(mix(color, WHITE, 0.5), 220), width=4)
        base = merge(base, o)
        d = ImageDraw.Draw(base)
        ttext(d, ((x0 + x1) // 2, (y0 + y1) // 2 - 2), text, tfont, mix(color, WHITE, 0.55), 0.08, "c")
    return base


# ---------------------------------------------------------------- s1 gameplay
def s1_gameplay():
    img = new_screen()
    img = corridor(img)

    lay = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.line([(0, 1700), (W, 1700)], fill=CYAN, width=14)
    for x in (690, 790, 890):
        d.polygon([(x - 34, 1447), (x + 34, 1447), (x, 1358)], fill=PINK)
    img = glow_shapes(img, lambda dd: (
        dd.line([(0, 1700), (W, 1700)], fill=CYAN, width=14),
        [dd.polygon([(x - 34, 1447), (x + 34, 1447), (x, 1358)], fill=PINK)
         for x in (690, 790, 890)]),
        (5, 14, 28), (0.9, 0.5, 0.25))
    o, d = overlay(img)
    d.line([(0, 1700), (W, 1700)], fill=rgba(mix(CYAN, WHITE, 0.75)), width=6)
    for x in (690, 790, 890):
        d.polygon([(x - 22, 1440), (x + 22, 1440), (x, 1372)], fill=rgba(mix(PINK, WHITE, 0.55)))
    img = merge(img, o)

    for (cx, cy) in ((640, 1296), (760, 1166), (560, 1058)):
        img = coin_neon(img, cx, cy, 54, CYAN)
    img = coin_neon(img, 905, 1560, 62, GOLD)

    for gx, gd, ga in ((300, 88, 0.45), (200, 58, 0.28), (128, 36, 0.16)):
        img = paste_c(img, ghost_orb(gd, CYAN, ga), gx, 1638)

    def ball_glow(d):
        d.ellipse([430 - 74, 1638 - 74, 430 + 74, 1638 + 74], fill=rgba(CYAN, 220))
    img = glow_shapes(img, ball_glow, (6, 18, 40), (1.0, 0.55, 0.3))
    img = paste_c(img, orb(124, CYAN), 430, 1638)

    # HUD
    img = neon_text(img, (540, 300), "4 826", F("black", 118), CYAN, tracking=0.05)
    o, d = overlay(img)
    d.rounded_rectangle([382, 388, 698, 456], radius=34, fill=(46, 8, 28, 215))
    img = merge(img, o)
    lay = Image.new("RGB", (W, H), (0, 0, 0))
    ttext(ImageDraw.Draw(lay), (540, 420), "COMBO ×12", F("bahn", 42), PINK, 0.08, "c")
    img = glow_pass(img, lay, (4, 12), (0.9, 0.4))
    d = ImageDraw.Draw(img)
    ttext(d, (540, 420), "COMBO ×12", F("bahn", 42), mix(PINK, WHITE, 0.55), 0.08, "c")

    progress_bar(img, (70, 152, 370, 176), 0.62)
    d = ImageDraw.Draw(img)
    ttext(d, (70, 222), "WAVE 4", F("bahn", 36), DIM, 0.10, "l")
    d.rounded_rectangle([964, 138, 988, 192], radius=8, fill=rgba(WHITE, 210))
    d.rounded_rectangle([1002, 138, 1026, 192], radius=8, fill=rgba(WHITE, 210))

    o, d = overlay(img)
    chevron(d, 190, 2150, 56, rgba(CYAN, 160), 12, "left")
    chevron(d, 890, 2150, 56, rgba(PINK, 160), 12, "right")
    img = merge(img, o)

    finish(img, "screenshot-1-gameplay.png")


# ---------------------------------------------------------------- s2 results
def s2_results():
    img = new_screen()
    img = corridor(img, with_core=False)
    img = img.filter(ImageFilter.GaussianBlur(7))
    o, d = overlay(img)
    d.rectangle([0, 0, W, H], fill=(5, 6, 14, 165))
    img = merge(img, o)

    img = neon_panel(img, (140, 560, 940, 1760), radius=64, border=CYAN, glow=1.2)
    o, d = overlay(img)
    d.line([(300, 636), (780, 636)], fill=rgba(CYAN, 140), width=2)
    img = merge(img, o)

    img = neon_text(img, (540, 720), "GAME OVER", F("black", 92), PINK, tracking=0.05, glow_scale=0.9)
    d = ImageDraw.Draw(img)
    ttext(d, (540, 850), "SCORE", F("bahn", 42), DIM, 0.35, "c")
    img = neon_text(img, (540, 982), "12 847", F("black", 164), CYAN, tracking=0.04)
    img = pill_text(img, (392, 1092, 688, 1156), "BEST 18 220", F("bahn", 38), DIM,
                    outline=rgba(DIM, 160), width=3)

    cx, cy, r = 540, 1330, 88

    def ring_painter(d):
        d.arc([cx - r, cy - r, cx + r, cy + r], start=-90, end=195, fill=PINK, width=14)
    img = glow_shapes(img, ring_painter, (5, 14), (0.9, 0.5))
    o, d = overlay(img)
    d.arc([cx - r, cy - r, cx + r, cy + r], start=-90, end=195,
          fill=rgba(mix(PINK, WHITE, 0.55)), width=8)
    img = merge(img, o)
    img = neon_text(img, (cx, cy), "5", F("black", 84), PINK, glow_scale=0.55, core_t=0.8)

    img = button(img, (250, 1520, 530, 1614), PINK, "REVIVE", F("black", 50))
    img = button(img, (552, 1520, 830, 1614), CYAN, "END RUN", F("black", 44), filled=False)
    finish(img, "screenshot-2-results.png")


# ---------------------------------------------------------------- s3 shop
SKINS = [("PULSE", CYAN, None), ("SOLARIS", GOLD, "1 200"), ("NOVA", VIOLET, "900"),
         ("TOXIN", GREEN, "750"), ("FROST", ICE, "600"), ("DUALITY", PINK, "1 500")]


def s3_shop():
    img = new_screen()
    img = neon_text(img, (500, 212), "SKINS", F("black", 96), CYAN, tracking=0.08, glow_scale=0.95)
    o, d = overlay(img)
    d.rounded_rectangle([770, 142, 1012, 214], radius=36, outline=rgba(GOLD, 190), width=3)
    img = merge(img, o)
    img = coin(img, 818, 178, 40)
    d = ImageDraw.Draw(img)
    ttext(d, (852, 178), "2 450", F("bahn", 40), mix(GOLD, WHITE, 0.4), 0.04, "l")

    cw, ch, gap, mx = 455, 350, 30, 70
    for i, (name, col, price) in enumerate(SKINS):
        r_, c_ = divmod(i, 2)
        x0 = mx + c_ * (cw + gap)
        y0 = 320 + r_ * (ch + gap)
        box = (x0, y0, x0 + cw, y0 + ch)
        img = neon_panel(img, box, radius=36, border=col, bw=2, glow=0.7)
        cx = x0 + cw // 2

        def glow_ball(d, cx=cx, y0=y0, col=col):
            d.ellipse([cx - 62, y0 + 56, cx + 62, y0 + 180], fill=rgba(col, 190))
        img = glow_shapes(img, glow_ball, (5, 14), (0.9, 0.45))
        img = paste_c(img, orb(112, col), cx, y0 + 118)
        d = ImageDraw.Draw(img)
        ttext(d, (cx, y0 + 212), name, F("black", 40), WHITE, 0.06, "c")
        if price is None:
            o, d2 = overlay(img)
            d2.rounded_rectangle([cx - 96, y0 + 248, cx + 96, y0 + 300], radius=26, fill=col)
            img = merge(img, o)
            d = ImageDraw.Draw(img)
            ttext(d, (cx, y0 + 273), "EQUIPPED", F("bahn", 32), (6, 20, 26), 0.08, "c")
        else:
            img = coin(img, cx - 52, y0 + 274, 34)
            d = ImageDraw.Draw(img)
            ttext(d, (cx - 24, y0 + 274), price, F("bahn", 38), DIM, 0.04, "l")

    img = button(img, (290, 1576, 790, 1676), PINK, "EQUIP", F("black", 58))
    finish(img, "screenshot-3-skins.png")


# ---------------------------------------------------------------- s4 quests
def s4_quests():
    img = new_screen()
    img = neon_text(img, (540, 200), "DAILY QUESTS", F("black", 86), CYAN, tracking=0.06, glow_scale=0.9)

    img = neon_panel(img, (80, 292, 1000, 614), radius=44, border=GOLD, bw=2, glow=0.8)
    img = neon_text(img, (196, 452), "6", F("black", 148), GOLD, glow_scale=0.9, core_t=0.7)
    d = ImageDraw.Draw(img)
    ttext(d, (306, 382), "DAY STREAK", F("bahn", 42), WHITE, 0.10, "l")
    for i in range(7):
        cx = 452 + i * 78
        if i < 5:
            col = mix(CYAN, PINK, i / 4)
            d.ellipse([cx - 24, 428, cx + 24, 476], fill=rgba(col))
        elif i == 5:
            t = ring(56, CYAN, 6)
            img.paste(t, (cx - 28, 424), t)
        else:
            d.ellipse([cx - 22, 430, cx + 22, 474], outline=rgba(DIM, 140), width=4)
    img = coin(img, 330, 552, 36)
    d = ImageDraw.Draw(img)
    ttext(d, (362, 552), "TOMORROW  +250", F("bahn", 36), mix(GOLD, WHITE, 0.35), 0.06, "l")

    quests = [("Collect 150 coins", "120/150", 0.80, CYAN),
              ("Flip 500 times", "500/500", 1.00, PINK),
              ("Reach wave 8", "5/8", 0.625, VIOLET)]
    for i, (title, counter, frac, col) in enumerate(quests):
        y0 = 668 + i * 252
        img = neon_panel(img, (80, y0, 1000, y0 + 184), radius=40, border=col, bw=2, glow=0.6)
        o, d = overlay(img)
        d.ellipse([116, y0 + 46, 180, y0 + 110], outline=rgba(col, 220), width=5)
        if i == 0:
            diamond(d, 148, y0 + 78, 18, col)
        elif i == 1:
            check_mark(d, 148, y0 + 76, 46, col, 8)
        else:
            chevron(d, 148, y0 + 78, 30, col, 8, "right")
        img = merge(img, o)
        d = ImageDraw.Draw(img)
        ttext(d, (216, y0 + 62), title, F("black", 44), WHITE, 0.02, "l")
        ttext(d, (940, y0 + 62), counter, F("bahn", 40), DIM, 0.04, "r")
        progress_bar(img, (216, y0 + 108, 940, y0 + 134), frac, c1=col, c2=mix(col, WHITE, 0.3))

    img = button(img, (290, 1452, 790, 1552), PINK, "CLAIM 500", F("black", 54))
    finish(img, "screenshot-4-quests.png")


# ---------------------------------------------------------------- s5 leaderboard
def s5_leaderboard():
    img = new_screen()
    img = neon_text(img, (540, 196), "LEADERBOARD", F("black", 84), CYAN, tracking=0.05, glow_scale=0.9)
    img = pill_text(img, (438, 262, 642, 322), "WEEK 42", F("bahn", 34), DIM,
                    outline=rgba(DIM, 160), width=3)

    podium = [(215, 930, 475, ICE, "2", "GLITCHKING", "23 410", 345),
              (605, 970, 865, mix(GOLD, (200, 90, 40), 0.45), "3", "ZEROLAGG", "22 150", 735)]
    for x0, y0, x1, col, rank, name, score, cx in podium:
        img = neon_panel(img, (x0, y0, x1, 1150), radius=22, border=col, bw=3, glow=0.7)
    img = neon_panel(img, (410, 830, 670, 1150), radius=22, border=GOLD, bw=4, glow=1.1)

    # winner (center)
    d = ImageDraw.Draw(img)
    ttext(d, (540, 930), "24 880", F("bahn", 40), mix(GOLD, WHITE, 0.35), 0.04, "c")
    img = neon_text(img, (540, 1010), "1", F("black", 104), GOLD, glow_scale=0.6, core_t=0.7)
    img = paste_c(img, orb(104, GOLD), 540, 756)
    o, d = overlay(img)
    d.polygon([(508, 676), (522, 640), (540, 668), (558, 640), (572, 676)], fill=rgba(GOLD))
    img = merge(img, o)
    d = ImageDraw.Draw(img)
    ttext(d, (540, 608), "NOVAQUEEN", F("black", 40), WHITE, 0.04, "c")

    # runners-up (names drawn last so they sit above the winner block edge)
    for x0, y0, x1, col, rank, name, score, cx in podium:
        d = ImageDraw.Draw(img)
        ttext(d, (cx, 1030), score, F("bahn", 36), DIM, 0.04, "c")
        img = neon_text(img, (cx, 1102), rank, F("black", 84), col, glow_scale=0.6, core_t=0.7)
        img = paste_c(img, orb(96, col), cx, y0 - 66)
        d = ImageDraw.Draw(img)
        ttext(d, (cx, y0 - 142), name, F("black", 38), WHITE, 0.04, "c")

    rows = [("4", "VOIDRUNNER", "21 300"), ("5", "PIXELWITCH", "19 070"),
            ("6", "NEONHAWK", "17 540"), ("7", "KILOX", "16 220")]
    y = 1288
    d = ImageDraw.Draw(img)
    for rank, name, score in rows:
        ttext(d, (118, y), rank, F("bahn", 44), DIM, 0.02, "l")
        ttext(d, (196, y), name, F("black", 46), WHITE, 0.03, "l")
        ttext(d, (962, y), score, F("bahn", 44), DIM, 0.04, "r")
        d.line([(118, y + 52), (962, y + 52)], fill=rgba(DIM, 60), width=2)
        y += 105
    img = neon_panel(img, (80, 1668, 1000, 1782), radius=32, border=CYAN, bw=3, glow=1.0)
    d = ImageDraw.Draw(img)
    ttext(d, (130, 1725), "8", F("bahn", 50), CYAN, 0.02, "l")
    ttext(d, (208, 1725), "YOU", F("black", 50), WHITE, 0.06, "l")
    ttext(d, (950, 1725), "12 847", F("bahn", 50), mix(CYAN, WHITE, 0.5), 0.04, "r")
    finish(img, "screenshot-5-leaderboard.png")


# ---------------------------------------------------------------- s6 menu
def s6_menu():
    img = new_screen()
    img = pill_text(img, (400, 150, 680, 218), "BEST 18 220", F("bahn", 38), DIM,
                    outline=rgba(DIM, 150), width=3)

    def ball_glow(d):
        d.ellipse([540 - 66, 420 - 66, 540 + 66, 420 + 66], fill=rgba(CYAN, 220))
        dotted_bezier(d, (424, 348), (470, 296), (452, 320), n=6, r=5, color=WHITE, alpha=150)
        sparkle(d, 664, 356, 18, CYAN, 220)
        sparkle(d, 416, 500, 13, PINK, 220)
    img = glow_shapes(img, ball_glow, (5, 16, 34), (1.0, 0.55, 0.28))
    img = paste_c(img, orb(100, CYAN), 540, 420)
    o, d = overlay(img)
    sparkle(d, 664, 356, 15, mix(CYAN, WHITE, 0.5), 255)
    sparkle(d, 416, 500, 11, mix(PINK, WHITE, 0.5), 255)
    img = merge(img, o)

    img = neon_text(img, (540, 628), "NEON", F("black", 198), CYAN, tracking=0.05, glow_scale=1.05)
    img = neon_text(img, (540, 832), "FLIP", F("black", 198), PINK, tracking=0.05, glow_scale=1.05)

    pc = (540, 1290)

    def play_glow(d):
        for r, a in ((258, 60), (222, 95)):
            d.ellipse([pc[0] - r, pc[1] - r, pc[0] + r, pc[1] + r], outline=rgba(CYAN, a), width=8)
        d.rounded_rectangle([340, 1200, 740, 1380], radius=90, fill=CYAN)
    img = glow_shapes(img, play_glow, (6, 18, 40), (1.0, 0.55, 0.3))
    bw, bh = 400, 180
    grad = vgrad((bw, bh), mix(CYAN, WHITE, 0.28), mix(CYAN, (0, 120, 170), 0.5))
    mask = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, bw - 1, bh - 1], radius=90, fill=255)
    img.paste(grad, (pc[0] - bw // 2, pc[1] - bh // 2), mask)
    d = ImageDraw.Draw(img)
    ttext(d, pc, "PLAY", F("black", 92), (7, 11, 25), 0.08, "c")

    icons = [(250, CYAN, "SHOP"), (540, PINK, "QUESTS"), (830, VIOLET, "RANKS")]
    for cx, col, label in icons:
        def ring_glow(d, cx=cx, col=col):
            d.ellipse([cx - 90, 1568, cx + 90, 1748], fill=mix(col, (0, 0, 0), 0.80))
        img = glow_shapes(img, ring_glow, (4, 12), (0.7, 0.35))
        o, d = overlay(img)
        d.ellipse([cx - 88, 1570, cx + 88, 1746], outline=rgba(mix(col, WHITE, 0.55), 235), width=5)
        if label == "SHOP":
            d.ellipse([cx - 34, 1626, cx + 34, 1694], fill=rgba(col))
        elif label == "QUESTS":
            check_mark(d, cx, 1660, 56, col, 10)
        else:
            for i, bh2 in enumerate((26, 48, 72)):
                d.rounded_rectangle([cx - 32 + i * 24, 1694 - bh2, cx - 16 + i * 24, 1694],
                                    radius=5, fill=rgba(col))
        img = merge(img, o)
        d = ImageDraw.Draw(img)
        ttext(d, (cx, 1792), label, F("bahn", 36), DIM, 0.14, "c")

    d = ImageDraw.Draw(img)
    ttext(d, (540, 2262), "V1.0.0", F("bahn", 30), mix(DIM, (0, 0, 0), 0.35), 0.12, "c")
    finish(img, "screenshot-6-menu.png")


if __name__ == "__main__":
    s1_gameplay()
    s2_results()
    s3_shop()
    s4_quests()
    s5_leaderboard()
    s6_menu()
