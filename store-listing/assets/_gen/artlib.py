# NeonFlip store-art shared style library (dark #0B0E1A / cyan #00E5FF / pink #FF2E97)
import math
import os
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont

FONTS = r"C:\Windows\Fonts"
_FONT_FILES = {"black": "seguibl.ttf", "blackit": "seguibli.ttf",
               "bahn": "bahnschrift.ttf", "arial": "arialbd.ttf"}
_cache = {}

CYAN = (0, 229, 255)
PINK = (255, 46, 151)
VIOLET = (154, 84, 255)
GOLD = (255, 198, 64)
GREEN = (64, 255, 170)
ICE = (170, 225, 255)
WHITE = (238, 246, 255)
DIM = (148, 158, 192)
NAVY = (11, 14, 26)

def F(name, size, bold_var=True):
    key = (name, size)
    if key not in _cache:
        f = ImageFont.truetype(os.path.join(FONTS, _FONT_FILES[name]), size)
        if name == "bahn" and bold_var:
            try:
                f.set_variation_by_name("Bold")
            except Exception:
                pass
        _cache[key] = f
    return _cache[key]

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def rgba(c, a=255):
    return (c[0], c[1], c[2], a)

def vgrad(size, top, bottom):
    m = Image.linear_gradient("L").resize(size)
    return Image.composite(Image.new("RGB", size, top), Image.new("RGB", size, bottom), m)

def hgrad(size, left, right):
    m = Image.linear_gradient("L").rotate(90, expand=True).resize(size)
    return Image.composite(Image.new("RGB", size, left), Image.new("RGB", size, right), m)

def glow_pass(base, layer, radii, strengths):
    for r, s in zip(radii, strengths):
        b = layer.filter(ImageFilter.GaussianBlur(r))
        if s < 1.0:
            b = b.point(lambda p, s=s: int(p * s))
        base = ImageChops.screen(base, b)
    return base

def glow_shapes(base, painter, radii=(6, 16, 36), strengths=(1.0, 0.55, 0.3)):
    """painter(draw) paints colored shapes on a black RGB layer; additive screen."""
    lay = Image.new("RGB", base.size, (0, 0, 0))
    painter(ImageDraw.Draw(lay))
    return glow_pass(base, lay, radii, strengths)

def overlay(base):
    o = Image.new("RGBA", base.size, (0, 0, 0, 0))
    return o, ImageDraw.Draw(o)

def merge(base, o):
    base.paste(o, (0, 0), o)
    return base

def ttext(d, xy, text, font, fill, tracking=0.0, anchor="c"):
    """Tracked text, y = middle line. anchor: 'c' center, 'l' left, 'r' right."""
    widths = [font.getlength(ch) for ch in text]
    tr = tracking * font.size
    total = sum(widths) + tr * max(0, len(text) - 1)
    x, y = xy
    if anchor[0] == "c":
        x -= total / 2
    elif anchor[0] == "r":
        x -= total
    for ch, w in zip(text, widths):
        d.text((x, y), ch, font=font, fill=fill, anchor="lm")
        x += w + tr
    return total

def neon_text(base, xy, text, font, color, tracking=0.04, anchor="c",
              core_t=0.62, glow_scale=1.0):
    """Glow passes + near-white core. Returns base."""
    s = font.size
    lay = Image.new("RGB", base.size, (0, 0, 0))
    ttext(ImageDraw.Draw(lay), xy, text, font, color, tracking, anchor)
    base = glow_pass(base, lay,
                     (max(2, int(s * 0.05 * glow_scale)),
                      max(4, int(s * 0.14 * glow_scale)),
                      max(8, int(s * 0.30 * glow_scale))),
                     (1.0, 0.6, 0.34))
    mask = Image.new("L", base.size, 0)
    ttext(ImageDraw.Draw(mask), xy, text, font, 255, tracking, anchor)
    base.paste(Image.new("RGB", base.size, mix(color, (255, 255, 255), core_t)), (0, 0), mask)
    return base

def text_dim(base, xy, text, font, color=DIM, tracking=0.04, anchor="c", alpha=255):
    d = ImageDraw.Draw(base)
    ttext(d, xy, text, font, mix(color, base.getpixel((2, 2)), 1 - alpha / 255) if alpha < 255 else color,
          tracking, anchor)
    return base

def orb(diameter, color, core_white=0.9):
    """Smooth radial-gradient neon ball, RGBA tile."""
    S = diameter * 3
    grad = Image.radial_gradient("L").resize((S, S))
    hot = grad.point(lambda p: int(max(0, min(255, (78 - p) * 3.2))))
    body = Image.composite(Image.new("RGB", (S, S), (255, 255, 255)),
                           Image.new("RGB", (S, S), color), hot)
    # tint the mid zone toward color for richer falloff
    midtint = Image.new("RGB", (S, S), color)
    body = Image.blend(body, Image.composite(
        Image.new("RGB", (S, S), mix(color, (255, 255, 255), 0.45)),
        midtint, grad.point(lambda p: max(0, min(255, (140 - p) * 2)))),
        0.35)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([S * 0.02, S * 0.02, S * 0.98, S * 0.98], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(S * 0.012))
    body.putalpha(mask)
    return body.resize((diameter, diameter), Image.LANCZOS)

def ring(diameter, color, width, alpha=255):
    S = max(4, diameter * 3)
    a = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    w = max(2, width * 3)
    ImageDraw.Draw(a).ellipse([w, w, S - w, S - w], outline=rgba(color, alpha), width=w)
    return a.resize((diameter, diameter), Image.LANCZOS)

def paste_c(base, tile, cx, cy):
    base.paste(tile, (int(cx - tile.width / 2), int(cy - tile.height / 2)), tile)
    return base

def sparkle(d, cx, cy, r, color, alpha=255):
    k = r * 0.16
    c = rgba(color, alpha)
    d.polygon([(cx, cy - r), (cx + k, cy), (cx, cy + r), (cx - k, cy)], fill=c)
    d.polygon([(cx - r, cy), (cx, cy - k), (cx + r, cy), (cx, cy + k)], fill=c)

def dotted_bezier(d, p0, p1, ctrl, n=8, r=4, color=WHITE, alpha=150):
    for i in range(n):
        t = i / (n - 1)
        mt = 1 - t
        x = mt * mt * p0[0] + 2 * mt * t * ctrl[0] + t * t * p1[0]
        y = mt * mt * p0[1] + 2 * mt * t * ctrl[1] + t * t * p1[1]
        d.ellipse([x - r, y - r, x + r, y + r], fill=rgba(color, alpha))

def vignette(base, strength=0.55, start=80, spread=1.28):
    w, h = base.size
    m = Image.radial_gradient("L")
    m = m.resize((int(w * spread * 2), int(h * spread * 2)))
    mw, mh = m.size
    m = m.crop(((mw - w) // 2, (mh - h) // 2, (mw - w) // 2 + w, (mh - h) // 2 + h))
    m = m.point(lambda p: int(max(0, min(255, (p - start)) * (255.0 / (255 - start)) * strength)))
    base.paste(Image.new("RGB", (w, h), (0, 0, 0)), (0, 0), m)
    return base

def corner_glows(base, glows):
    """glows: list of (cx, cy, radius, color, strength) additive soft light."""
    g = Image.new("RGB", base.size, (0, 0, 0))
    d = ImageDraw.Draw(g)
    for cx, cy, r, col, s in glows:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=tuple(int(c * s) for c in col))
    g = g.filter(ImageFilter.GaussianBlur(min(base.size) * 0.10))
    return ImageChops.screen(base, g)

def sheen(base, cx, cy, length, alpha=12):
    o, d = overlay(base)
    d.line([(cx - length / 2, cy + length / 2), (cx + length / 2, cy - length / 2)],
           fill=(255, 255, 255, alpha), width=int(min(base.size) * 0.16))
    o = o.filter(ImageFilter.GaussianBlur(70))
    return merge(base, o)

def grid_floor(base, horizon, vp_x, color=CYAN, alpha=44, rows=9, cols=16):
    w, h = base.size
    o, d = overlay(base)
    for k in range(1, rows + 1):
        t = (k / rows) ** 1.75
        y = horizon + (h - horizon) * t
        d.line([(0, y), (w, y)], fill=rgba(color, int(alpha * (1 - t * 0.7))), width=2)
    for i in range(cols + 1):
        xb = -w * 0.5 + i * (w * 2.0 / cols)
        d.line([(vp_x + (xb - vp_x) * 0.02, horizon), (xb, h)],
               fill=rgba(color, int(alpha * 0.8)), width=2)
    return merge(base, o)

def neon_panel(base, box, radius=48, border=CYAN, fill=(15, 19, 38), fill_a=236,
               bw=3, glow=1.0):
    o, d = overlay(base)
    d.rounded_rectangle(box, radius=radius, fill=rgba(fill, fill_a))
    merge(base, o)
    lay = Image.new("RGB", base.size, (0, 0, 0))
    ImageDraw.Draw(lay).rounded_rectangle(box, radius=radius, outline=border, width=bw + 3)
    base = glow_pass(base, lay, (max(2, int(6 * glow)), max(4, int(18 * glow))), (0.8, 0.38))
    o, d = overlay(base)
    d.rounded_rectangle(box, radius=radius, outline=rgba(border, 150), width=bw)
    merge(base, o)
    return base

def pill(base, box, fill=None, outline=None, width=3, radius=None):
    o, d = overlay(base)
    r = radius if radius is not None else (box[3] - box[1]) // 2
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)
    return merge(base, o)

def pill_text(base, box, text, font, fg, fill=None, outline=None, width=3,
              tracking=0.05, glow_color=None):
    pill(base, box, fill=fill, outline=outline, width=width)
    if glow_color:
        lay = Image.new("RGB", base.size, (0, 0, 0))
        ttext(ImageDraw.Draw(lay), ((box[0] + box[2]) // 2, (box[1] + box[3]) // 2),
              text, font, glow_color, tracking, "c")
        base = glow_pass(base, lay, (4, 12), (0.8, 0.35))
    d = ImageDraw.Draw(base)
    ttext(d, ((box[0] + box[2]) // 2, (box[1] + box[3]) // 2 - 2), text, font, fg, tracking, "c")
    return base

def progress_bar(base, box, frac, c1=CYAN, c2=PINK):
    o, d = overlay(base)
    d.rounded_rectangle(box, radius=(box[3] - box[1]) // 2, fill=(255, 255, 255, 28))
    merge(base, o)
    w, h = int((box[2] - box[0]) * frac) - 6, (box[3] - box[1]) - 6
    if w > 2:
        bar = hgrad((w, h), c2, c1)
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=h // 2, fill=255)
        base.paste(bar, (box[0] + 3, box[1] + 3), mask)
    return base

def coin(base, cx, cy, d_):
    t = ring(d_, GOLD, max(3, d_ // 9))
    base.paste(t, (int(cx - d_ / 2), int(cy - d_ / 2)), t)
    dd = d_ * 0.30
    o, dr = overlay(base)
    dr.polygon([(cx, cy - dd), (cx + dd, cy), (cx, cy + dd), (cx - dd, cy)], fill=rgba(GOLD))
    return merge(base, o)

def diamond(d, cx, cy, r, color):
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=rgba(color))

def check_mark(d, cx, cy, s, color, w):
    d.line([(cx - s * 0.5, cy + s * 0.05), (cx - s * 0.12, cy + s * 0.42),
            (cx + s * 0.58, cy - s * 0.38)], fill=rgba(color), width=w, joint="curve")

def chevron(d, cx, cy, s, color, w, direction="left"):
    if direction == "left":
        pts = [(cx + s * 0.4, cy - s * 0.7), (cx - s * 0.4, cy), (cx + s * 0.4, cy + s * 0.7)]
    else:
        pts = [(cx - s * 0.4, cy - s * 0.7), (cx + s * 0.4, cy), (cx - s * 0.4, cy + s * 0.7)]
    d.line(pts, fill=rgba(color), width=w, joint="curve")

def status_bar(base, time_str="21:47"):
    d = ImageDraw.Draw(base)
    c = mix(DIM, (255, 255, 255), 0.30)
    d.text((58, 54), time_str, font=F("bahn", 34), fill=c, anchor="lm")
    x, y = 1005, 54
    for i, bh in enumerate([12, 19, 26]):
        d.rectangle([x - 170 + i * 15, y + 13 - bh, x - 161 + i * 15, y + 13], fill=rgba(c, 170))
    d.rounded_rectangle([x - 80, y - 14, x - 14, y + 14], radius=8, outline=rgba(c, 200), width=3)
    d.rectangle([x - 74, y - 8, x - 42, y + 8], fill=rgba(c, 200))
    d.rectangle([x - 10, y - 5, x - 4, y + 5], fill=rgba(c, 200))
    return base

def new_screen(w=1080, h=2340, glow=True):
    base = vgrad((w, h), (10, 13, 27), (15, 19, 40))
    if glow:
        base = corner_glows(base, [
            (w * 0.12, h * 0.08, w * 0.55, (0, 46, 58), 0.55),
            (w * 0.92, h * 0.9, w * 0.6, (58, 8, 34), 0.5),
        ])
    return base

def dark_bg(w, h, corner=True):
    base = vgrad((w, h), (13, 17, 34), (9, 12, 23))
    g = Image.new("RGB", (w, h), (0, 0, 0))
    ImageDraw.Draw(g).ellipse([w * 0.14, h * 0.10, w * 0.86, h * 0.92], fill=(22, 18, 42))
    g = g.filter(ImageFilter.GaussianBlur(min(w, h) * 0.16))
    base = ImageChops.screen(base, g)
    if corner:
        base = corner_glows(base, [
            (w * 0.08, h * 0.06, w * 0.42, (0, 56, 70), 0.6),
            (w * 0.95, h * 0.95, w * 0.45, (66, 9, 42), 0.6),
        ])
    return base

def soft(rgba_img, radius, amul):
    b = rgba_img.filter(ImageFilter.GaussianBlur(radius))
    a = b.getchannel("A").point(lambda p: int(p * amul))
    b.putalpha(a)
    return b
