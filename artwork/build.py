#!/usr/bin/env python3
"""Build Odysseus logo parts + variants from the read-only source artwork.

Never writes to artwork/source/odysseus-art.svg.
Outputs:
  artwork/parts/*.svg      - the two groups + background square, tight viewBoxes
  artwork/variants/*.svg   - composed lockups / icons / banners
  artwork/preview/*.png    - rasterised checks (not deliverables)
"""
import copy
import os
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image

BASE = "/home/gabriele/Projects/tv-bro/artwork"
SRC = f"{BASE}/source/odysseus-art.svg"
PARTS = f"{BASE}/parts"
VARIANTS = f"{BASE}/variants"
PREVIEW = f"{BASE}/preview"
NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", NS)
ET.register_namespace("inkscape", "http://www.inkscape.org/namespaces/inkscape")
ET.register_namespace("sodipodi", "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd")

# palette (as found in the source)
AMBER = "#de8e0c"
INK = "#241511"
BLACK = "#1c1a1a"
RUST = "#b95d2e"
CREAM = "#f7ecd9"
GOLD = "#efa33c"

# recolour map for "light" artwork used on dark backgrounds
LIGHT_MAP = [("#241511", CREAM), ("#1c1a1a", AMBER), ("#b95d2e", GOLD)]


def num(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return s if s not in ("", "-0") else "0"


def dump(path, view_box, children):
    x, y, w, h = view_box
    svg = ET.Element(f"{{{NS}}}svg", {
        "version": "1.1",
        "width": num(w),
        "height": num(h),
        "viewBox": " ".join(num(v) for v in (x, y, w, h)),
    })
    for c in children:
        svg.append(c)
    with open(path, "w") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n')
        f.write(ET.tostring(svg, encoding="unicode"))
        f.write("\n")


def query_bbox(path):
    """Union bbox of every object in the document, as (x, y, w, h) in user units."""
    out = subprocess.run(["inkscape", "--query-all", path],
                         capture_output=True, text=True, check=True).stdout
    box = None
    for line in out.strip().splitlines():
        p = line.split(",")
        if len(p) != 5:
            continue
        x, y = float(p[1]), float(p[2])
        w, h = float(p[3]), float(p[4])
        if box is None:
            box = [x, y, x + w, y + h]
        else:
            box = [min(box[0], x), min(box[1], y),
                   max(box[2], x + w), max(box[3], y + h)]
    return (box[0], box[1], box[2] - box[0], box[3] - box[1])


def render(svg, png, width):
    subprocess.run(["inkscape", "--export-type=png", f"--export-filename={png}",
                    f"--export-width={width}", svg],
                   capture_output=True, check=True)


def load_children(path):
    return [copy.deepcopy(c) for c in ET.parse(path).getroot()]


def recolor(path_in, path_out, mapping):
    with open(path_in) as f:
        txt = f.read()
    for a, b in mapping:
        txt = txt.replace(a, b)
    with open(path_out, "w") as f:
        f.write(txt)


def tf_w(bb, x, y, w):
    bx, by, bw, bh = bb
    s = w / bw
    return "translate(%s %s) scale(%s)" % (num(x - bx * s), num(y - by * s), num(s))


def tf_h(bb, x, y, h):
    bx, by, bw, bh = bb
    s = h / bh
    return "translate(%s %s) scale(%s)" % (num(x - bx * s), num(y - by * s), num(s))


def tf_contain(bb, X, Y, W, H):
    bx, by, bw, bh = bb
    s = min(W / bw, H / bh)
    tx = X + (W - bw * s) / 2 - bx * s
    ty = Y + (H - bh * s) / 2 - by * s
    return "translate(%s %s) scale(%s)" % (num(tx), num(ty), num(s))


def gnode(transform, children):
    g = ET.Element(f"{{{NS}}}g", {"transform": transform})
    for c in children:
        g.append(c)
    return g


def rect(w, h, fill):
    return ET.Element(f"{{{NS}}}rect", {
        "x": "0", "y": "0", "width": num(w), "height": num(h), "fill": fill})


# --------------------------------------------------------------------------
# 1. extract parts with tight viewBoxes
# --------------------------------------------------------------------------
root = ET.parse(SRC).getroot()
by_id = {}
for child in root:
    if child.get("id"):
        by_id[child.get("id")] = child

raw_parts = {"square": "path2", "galley": "g1096", "lettering": "g1023"}
src_bb = {}    # bboxes in the ORIGINAL source coordinate space
part_bb = {}   # tight bboxes inside each standalone part file
part_files = {}

for name, el_id in raw_parts.items():
    tmp = f"{PARTS}/{name}.svg"
    dump(tmp, (0, 0, 2048, 1117), [copy.deepcopy(by_id[el_id])])
    src_bb[name] = query_bbox(tmp)
    bb = src_bb[name]
    # 1 unit of breathing room -> tight viewBox for the standalone part
    dump(tmp, (bb[0] - 1, bb[1] - 1, bb[2] - bb[0] + 2, bb[3] - bb[1] + 2),
         [copy.deepcopy(by_id[el_id])])
    part_bb[name] = query_bbox(tmp)
    part_files[name] = tmp

# recoloured copies for dark backgrounds (same geometry -> same bboxes)
for name in ("galley", "lettering"):
    out = f"{PARTS}/{name}-light.svg"
    recolor(f"{PARTS}/{name}.svg", out, LIGHT_MAP)
    part_files[name + "-light"] = out
    part_bb[name + "-light"] = part_bb[name]
    src_bb[name + "-light"] = src_bb[name]

print("== parts ==")
for k in ("square", "galley", "lettering"):
    s, p = src_bb[k], part_bb[k]
    print(f"  {k:12s} src x={s[0]:8.2f} y={s[1]:8.2f} w={s[2]:8.2f} h={s[3]:8.2f}"
          f"   tight w={p[2]:8.2f} h={p[3]:8.2f}")

GAL = src_bb["galley"]
LET = src_bb["lettering"]
SQ = src_bb["square"]
SQX, SQY, SQW, SQH = SQ
print("  square aspect %.4f  galley aspect %.4f  lettering aspect %.4f"
      % (SQW / SQH, GAL[2] / GAL[3], LET[2] / LET[3]))

# --------------------------------------------------------------------------
# 1b. VISIBLE (ink) bounds
#     The galley's geometry box extends ~159 user units to the left of any
#     visible pixel (a sub-3.5%-alpha hairline), which would push every derived
#     lockup ~10% off-centre. Layout therefore measures real alpha from a
#     background-free render of the source; `faithful()` keeps the geometry box
#     so it stays pixel-identical to the original composition.
# --------------------------------------------------------------------------
dump(f"{PREVIEW}/_source-nobg.svg", (0, 0, 2048, 1117),
     [copy.deepcopy(c) for c in root if c.get("id") != "path2"])
render(f"{PREVIEW}/_source-nobg.svg", f"{PREVIEW}/_source-nobg.png", 2048)
TRUTH = Image.open(f"{PREVIEW}/_source-nobg.png").convert("RGBA")


def ink_bb(bb, threshold=32):
    """Alpha-measured bounds (source coords) inside a geometry box."""
    x0, y0, w, h = bb
    crop = TRUTH.crop((round(x0), round(y0), round(x0 + w), round(y0 + h)))
    b = crop.getchannel("A").point(lambda v: 255 if v > threshold else 0).getbbox()
    return (x0 + b[0], y0 + b[1], b[2] - b[0], b[3] - b[1])


GAL_INK = ink_bb(src_bb["galley"])
LET_INK = ink_bb(src_bb["lettering"])
print("  visible    galley x=%.2f y=%.2f w=%.2f h=%.2f   lettering w=%.2f h=%.2f"
      % (GAL_INK + LET_INK[2:]))


def ink_of(key):
    base = key.replace("-light", "")
    if base == "galley":
        return GAL_INK
    if base == "lettering":
        return LET_INK
    return src_bb[key]


# --------------------------------------------------------------------------
# 2. layout helpers
#     Part files keep their ORIGINAL source coordinates, so every placement is
#     computed from the source-space bboxes (src_bb).
# --------------------------------------------------------------------------
def horizontal(h, gal="galley", let="lettering"):
    """Side-by-side; `h` = height of the galley's VISIBLE art."""
    gb = ink_of(gal)
    lb = ink_of(let)
    gh = h
    gw = gb[2] * (h / gb[3])
    lh = 0.30 * h
    gap = 0.12 * h
    lx = gw + gap
    ly = (h - lh) / 2
    W = lx + lb[2] * (lh / lb[3])
    return (W, h), [(part_files[gal], tf_h(gb, 0, 0, gh)),
                    (part_files[let], tf_h(lb, lx, ly, lh))]


def vertical(gal_w, gal="galley", let="lettering"):
    """Galley above lettering, source proportions; `gal_w` = VISIBLE art width."""
    gb = ink_of(gal)
    lb = ink_of(let)
    s = gal_w / gb[2]
    gh = gb[3] * s
    lw = lb[2] * s          # lettering keeps its source-relative width
    lh = lb[3] * s
    gap = ((LET[1] - (GAL[1] + GAL[3])) / GAL[3]) * gh   # source gap ratio
    H = gh + gap + lh
    W = max(gal_w, lw)
    return (W, H), [(part_files[gal], tf_w(gb, (W - gal_w) / 2, 0, gal_w)),
                    (part_files[let], tf_w(lb, (W - lw) / 2, gh + gap, lw))]


def faithful(size, gal="galley", let="lettering", square_fill=None):
    """Exact reproduction of the source composition (square + parts).

    square_fill=None keeps the original amber square path; a hex value drops
    the path and lets the caller paint the background instead.
    """
    s = size / SQ[2]
    items = []
    if square_fill is None:
        items.append((part_files["square"], tf_w(SQ, 0, 0, size)))
    for key in (gal, let):
        sb = src_bb[key]
        items.append((part_files[key],
                      tf_w(sb, (sb[0] - SQ[0]) * s, (sb[1] - SQ[1]) * s, sb[2] * s)))
    return (size, size), items


def build(name, size, items, bg=None, outer=None, bg_rect=None):
    W, H = size
    children = []
    if bg_rect:
        children.append(rect(W, H, bg_rect))
    groups = [gnode(tr, load_children(f)) for f, tr in items]
    if outer:
        groups = [gnode(outer, groups)]
    children.extend(groups)
    dump(f"{VARIANTS}/{name}.svg", (0, 0, W, H), children)
    return W, H


# --------------------------------------------------------------------------
# 3. variants
# --------------------------------------------------------------------------
made = []


def emit(name, size, items, bg=None, outer=None):
    build(name, size, items, bg_rect=bg, outer=outer)
    made.append((f"{name}.svg", size[0], size[1]))
    return size


# --- app icons (square) ---
sz, items = faithful(512)
emit("icon-full", sz, items, bg=AMBER)

sz, items = faithful(512, gal="galley-light", let="lettering-light", square_fill=INK)
emit("icon-ink", sz, items, bg=INK)

sz = (512, 512)
items = [(part_files["galley"], tf_contain(GAL_INK, 56, 56, 400, 400))]
emit("icon-galley", sz, items, bg=AMBER)

# --- lockups (transparent) ---
lock_h, h_items = horizontal(300)
emit("lockup-horizontal", lock_h, h_items)

lock_hl, hl_items = horizontal(300, gal="galley-light", let="lettering-light")
emit("lockup-horizontal-light", lock_hl, hl_items)

lock_v, v_items = vertical(600)
emit("lockup-stacked", lock_v, v_items)

lock_vl, vl_items = vertical(600, gal="galley-light", let="lettering-light")
emit("lockup-stacked-light", lock_vl, vl_items)

# --- banners (store art + readme + home page) ---
W, H = lock_h
emit("feature-graphic-amber", (1024, 500), h_items, bg=AMBER,
     outer=tf_contain((0, 0, W, H), 90, 90, 844, 320))
W, H = lock_hl
emit("feature-graphic-ink", (1024, 500), hl_items, bg=INK,
     outer=tf_contain((0, 0, W, H), 90, 90, 844, 320))
W, H = lock_h
emit("banner-leanback", (640, 360), h_items, bg=AMBER,
     outer=tf_contain((0, 0, W, H), 40, 40, 560, 280))
W, H = lock_vl
emit("banner-tv", (1280, 720), vl_items, bg=INK,
     outer=tf_contain((0, 0, W, H), 240, 120, 800, 480))
W, H = lock_h
emit("readme-banner", (1200, 300), h_items,
     outer=tf_contain((0, 0, W, H), 60, 50, 1080, 200))
W, H = lock_hl
emit("readme-banner-dark", (1200, 300), hl_items,
     outer=tf_contain((0, 0, W, H), 60, 50, 1080, 200))
W, H = lock_v
emit("home-logo", (400, 300), v_items,
     outer=tf_contain((0, 0, W, H), 30, 20, 340, 260))
W, H = lock_vl
emit("home-logo-dark", (400, 300), vl_items,
     outer=tf_contain((0, 0, W, H), 30, 20, 340, 260))

print("\n== variants ==")
for m in made:
    print("  %-28s %sx%s" % m)


# --------------------------------------------------------------------------
# 4. rasterised verification
# --------------------------------------------------------------------------
def ink_bbox(png):
    im = Image.open(png).convert("RGBA")
    a = im.getchannel("A")
    return im.size, a.getbbox(), sum(1 for p in a.getdata() if p > 8) / (im.size[0] * im.size[1])


print("\n== render checks ==")
for m in made:
    name, W, H = m
    png = f"{PREVIEW}/{name[:-4]}.png"
    render(f"{VARIANTS}/{name}", png, int(round(W)))
    size, bb, cov = ink_bbox(png)
    print("  %-28s %s ink=%s coverage=%.1f%%" % (name, size, bb, cov * 100))

# faithful check: icon-full vs the source cropped to its square
render(SRC, f"{PREVIEW}/_source.png", 2048)
src = Image.open(f"{PREVIEW}/_source.png").convert("RGBA")
crop = src.crop((int(SQX), int(SQY), int(round(SQX + SQW)), int(round(SQY + SQH)))).resize((512, 512), Image.LANCZOS)
icon = Image.open(f"{PREVIEW}/icon-full.png").convert("RGBA")
diff = 0.0
px_s, px_i = crop.load(), icon.load()
n = 512 * 512
for y in range(512):
    for x in range(512):
        a, b = px_s[x, y], px_i[x, y]
        diff += (abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])) / 3
print("  icon-full vs source-square mean channel diff = %.2f/255" % (diff / n))


# --------------------------------------------------------------------------
# 5. install app + store assets
#     The icon is the chosen "galley only" mark; everything else follows the
#     approved preview. Surfaces that have a dark theme get the light-on-dark
#     artwork (home page logo, README banner).
# --------------------------------------------------------------------------
APP = "/home/gabriele/Projects/tv-bro"
CHOSEN_ICON = "icon-galley"

# (variant, export width, export height, destination relative to the repo root)
INSTALL = [
    (CHOSEN_ICON, 48, 48, "app/src/main/res/drawable-mdpi/ic_launcher.png"),
    (CHOSEN_ICON, 72, 72, "app/src/main/res/drawable-hdpi/ic_launcher.png"),
    (CHOSEN_ICON, 96, 96, "app/src/main/res/drawable-xhdpi/ic_launcher.png"),
    (CHOSEN_ICON, 144, 144, "app/src/main/res/drawable-xxhdpi/ic_launcher.png"),
    (CHOSEN_ICON, 192, 192, "app/src/main/res/drawable-xxxhdpi/ic_launcher.png"),
    (CHOSEN_ICON, 192, 192, "metadata/en-US/images/icon.png"),
    # TV launcher banner (manifest android:banner)
    ("banner-leanback", 640, 360, "app/src/main/res/drawable-xhdpi/banner.png"),
    # store listing
    ("feature-graphic-amber", 1024, 500, "metadata/en-US/images/featureGraphic.png"),
    ("banner-tv", 1280, 720, "metadata/en-US/images/tvBanner.png"),
    # home page logo: light theme and dark theme (CSS prefers-color-scheme)
    ("home-logo", 512, 384, "app/src/main/assets/pages/home/logo.png"),
    ("home-logo-dark", 512, 384, "app/src/main/assets/pages/home/logo-dark.png"),
    # README banner: light and dark (markdown <picture>)
    ("readme-banner", 1200, 300, "docs/branding/readme-banner.png"),
    ("readme-banner-dark", 1200, 300, "docs/branding/readme-banner-dark.png"),
]


def mean_rgba_diff(a, b):
    """Composite-aware difference (0..255): half colour, half alpha.

    RGB only counts where *both* images are visible - the colour stored under
    fully transparent pixels is arbitrary and would otherwise dominate the score.
    """
    alpha, rgb, n = 0.0, 0.0, 0
    for p, q in zip(a.getdata(), b.getdata()):
        alpha += abs(p[3] - q[3])
        if p[3] > 8 and q[3] > 8:
            rgb += (abs(p[0] - q[0]) + abs(p[1] - q[1]) + abs(p[2] - q[2])) / 3
            n += 1
    pixels = a.size[0] * a.size[1]
    return 0.5 * (rgb / n if n else 0.0) + 0.5 * (alpha / pixels)


print("\n== installed assets ==")
for variant, px, py, rel in INSTALL:
    dst = os.path.join(APP, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    render(f"{VARIANTS}/{variant}.svg", dst, px)
    im = Image.open(dst).convert("RGBA")
    ref = Image.open(f"{PREVIEW}/{variant}.png").convert("RGBA").resize((px, py), Image.LANCZOS)
    d = mean_rgba_diff(im, ref)
    opaque = min(p[3] for p in im.getdata()) == 255
    print("  %-50s %4dx%-4d %-6s %-22s |d|=%.2f"
          % (rel, im.size[0], im.size[1], "opaque" if opaque else "alpha",
             variant, d))
