# Verify dimensions of every generated asset
import os
from PIL import Image

ROOT = r"F:\study\Platforms\Android\Applications\Games\HyperCasual\neon-flip\neon-flip"
RES = os.path.join(ROOT, "app", "src", "main", "res")
ST = os.path.join(ROOT, "store-listing", "assets")

rows, bad = [], []

# legacy launcher icons
for name, k in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    p = os.path.join(RES, "mipmap-" + name, "ic_launcher.png")
    exp = int(48 * k)
    s = Image.open(p).size
    rows.append((os.path.relpath(p, ROOT), s, s == (exp, exp)))
    if s != (exp, exp):
        bad.append(p)

# adaptive layers
for name, k in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    for layer in ("ic_launcher_foreground.png", "ic_launcher_background.png"):
        p = os.path.join(RES, "mipmap-" + name, layer)
        exp = int(108 * k)
        s = Image.open(p).size
        rows.append((os.path.relpath(p, ROOT), s, s == (exp, exp)))
        if s != (exp, exp):
            bad.append(p)

# store-listing assets
expect_store = {"icon-512.png": (512, 512), "feature-graphic.png": (1024, 500),
                "share-card.png": (1080, 1350)}
for fn in sorted(os.listdir(ST)):
    if not fn.endswith(".png"):
        continue
    if fn.startswith("screenshot-"):
        exp = (1080, 2340)
    else:
        exp = expect_store.get(fn)
    p = os.path.join(ST, fn)
    s = Image.open(p).size
    rows.append((os.path.relpath(p, ROOT), s, exp is None or s == exp))
    if exp is not None and s != exp:
        bad.append(p)

print(f"{'FILE':70} {'SIZE':>13}  OK")
for name, s, ok in rows:
    print(f"{name:70} {str(s):>13}  {'PASS' if ok else 'FAIL'}")
print()
print("TOTAL:", len(rows), "| FAILS:", len(bad))
