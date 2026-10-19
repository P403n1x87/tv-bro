#!/usr/bin/env python3
"""Verify artwork/variants/*.svg against the original source render.

Every variant is rebuilt analytically from the background-free source raster
using the transforms *written into the variant files themselves*, then diffed
against Inkscape's render of the variant.
  * normal variants  -> compared on colour + alpha
  * "-light" variants -> alpha only (they are deliberately recoloured)
"""
import glob
import os
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageStat

BASE = os.path.dirname(os.path.abspath(__file__))
NS = "{http://www.w3.org/2000/svg}"
TRUTH = os.path.join(BASE, "preview", "_source-nobg.png")  # 2048 wide, no square

# source-space bboxes (measured with inkscape --query-all)
SRC_BB = {
    "g1096": (644.77, 240.93, 589.45, 436.89),     # galley
    "g1023": (653.44, 774.60, 742.05, 108.51),     # lettering
    "path2": (599.35, 162.13, 858.18, 858.11),     # square
}

truth_src = Image.open(TRUTH).convert("RGBA")
SCALE = truth_src.size[0] / 2048.0   # == 1.0: source render matches user units

part_truth = {}
for pid, (x, y, w, h) in SRC_BB.items():
    if pid == "path2":
        continue
    ox, oy = round(x * SCALE), round(y * SCALE)
    box = (ox, oy, round((x + w) * SCALE), round((y + h) * SCALE))
    part_truth[pid] = (truth_src.crop(box), ox, oy)

# synthetic truth for the background square (style fill #de8e0c wins)
_sx, _sy, _sw, _sh = SRC_BB["path2"]
part_truth["path2"] = (
    Image.new("RGBA", (round(_sw * SCALE), round(_sh * SCALE)), (222, 142, 12, 255)),
    round(_sx * SCALE), round(_sy * SCALE),
)


def parse_tf(tf):
    """translate(tx ty) scale(s) -> (s, tx, ty) (only form we generate)."""
    s, tx, ty = 1.0, 0.0, 0.0
    if tf:
        m = re.search(r"translate\(([-\d.]+)[ ,]+([-\d.]+)\)", tf)
        if m:
            tx, ty = float(m.group(1)), float(m.group(2))
        m = re.search(r"scale\(([-\d.]+)\)", tf)
        if m:
            s = float(m.group(1))
    return s, tx, ty


def walk(el, cs, ct, out):
    """Accumulate placement transforms; stop at part groups.

    The truth raster is cropped from a *rendered* source, so a part group's own
    translate (e.g. g1096's -5.9,-2.7) is already baked into the pixels: the
    group's transform must NOT be re-applied.
    """
    s, tx, ty = parse_tf(el.get("transform"))
    cs2, ctx, cty = cs * s, ct[0] + cs * tx, ct[1] + cs * ty
    tag = el.tag
    if tag == NS + "g":
        if el.get("id") in SRC_BB:
            # parent placement only: this group's own translate is baked into
            # the truth pixels
            out.append((el.get("id"), cs, ct[0], ct[1]))
        else:
            for child in el:
                walk(child, cs2, (ctx, cty), out)
    elif tag == NS + "path":
        out.append((el.get("id") or "?", cs2, ctx, cty))
    elif tag == NS + "rect":
        out.append(("RECT", float(el.get("width")), float(el.get("height")),
                    el.get("fill")))
    else:                                  # <svg>: recurse over children
        for child in el:
            walk(child, cs2, (ctx, cty), out)


def rebuild(variant_path):
    """compose an ideal RGBA image from source truth + variant transforms"""
    root = ET.parse(variant_path).getroot()
    W = float(root.get("width"))
    H = float(root.get("height"))
    vb = [float(v) for v in root.get("viewBox").split()]
    canvas = Image.new("RGBA", (int(round(W)), int(round(H))), (0, 0, 0, 0))
    leaves = []
    walk(root, 1.0, (0.0, 0.0), leaves)

    for leaf in leaves:
        pid = leaf[0]
        if pid == "RECT":
            _, w, h, fill = leaf
            col = tuple(int(fill[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
            canvas.paste(Image.new("RGBA", canvas.size, col), (0, 0))
            continue
        if pid not in part_truth:
            continue
        s, tx, ty = leaf[1], leaf[2], leaf[3]
        px, ox, oy = part_truth[pid]
        # dest X -> source coord (X-tx)/s -> crop index (X-tx)/s - ox
        data = (1 / s, 0, -tx / s - ox, 0, 1 / s, -ty / s - oy)
        scaled = px.transform(canvas.size, Image.AFFINE, data=data,
                              resample=Image.BICUBIC)
        canvas = Image.alpha_composite(canvas, scaled)
    return canvas, (vb[2], vb[3])


def masked_mean_rgb(diff, mask):
    d = ImageChops.multiply(diff.convert("RGB"), mask.convert("RGB"))
    total = sum(ImageStat.Stat(d).sum)
    count = sum(1 for v in mask.getdata() if v) or 1
    return total / (3 * count)


def compare(name, ideal, recol):
    png = os.path.join(BASE, "preview", name + ".png")
    actual = Image.open(png).convert("RGBA")
    if actual.size != ideal.size:
        if abs(actual.size[0] - ideal.size[0]) <= 1 and abs(actual.size[1] - ideal.size[1]) <= 1:
            ideal = ideal.resize(actual.size, Image.LANCZOS)   # inkscape floors w/h
        else:
            return f"SIZE MISMATCH {actual.size} vs {ideal.size}"

    ia, aa = ideal.getchannel("A"), actual.getchannel("A")
    a_diff = ImageStat.Stat(ImageChops.difference(ia, aa)).mean[0]
    if recol:
        return ("geometry ok (mean|a|=%.1f)" % a_diff) if a_diff < 3 else \
               ("GEOMETRY DIFF %.1f" % a_diff)

    mask = ImageChops.multiply(ia.point(lambda v: 255 if v > 250 else 0),
                               aa.point(lambda v: 255 if v > 250 else 0))
    c_diff = masked_mean_rgb(ImageChops.difference(ideal, actual), mask)
    ok = a_diff < 3 and c_diff < 3
    return ("ok (|dA|=%.1f |dRGB|=%.2f)" % (a_diff, c_diff)) if ok else \
           ("DIFF |dA|=%.1f |dRGB|=%.2f" % (a_diff, c_diff))


# installed deliverables: (variant, width, height, path, must be fully opaque)
INSTALL = [
    ("icon-galley", 48, 48, "app/src/main/res/drawable-mdpi/ic_launcher.png", True),
    ("icon-galley", 72, 72, "app/src/main/res/drawable-hdpi/ic_launcher.png", True),
    ("icon-galley", 96, 96, "app/src/main/res/drawable-xhdpi/ic_launcher.png", True),
    ("icon-galley", 144, 144, "app/src/main/res/drawable-xxhdpi/ic_launcher.png", True),
    ("icon-galley", 192, 192, "app/src/main/res/drawable-xxxhdpi/ic_launcher.png", True),
    ("icon-galley", 192, 192, "metadata/en-US/images/icon.png", True),
    ("banner-leanback", 640, 360, "app/src/main/res/drawable-xhdpi/banner.png", True),
    ("feature-graphic-amber", 1024, 500, "metadata/en-US/images/featureGraphic.png", True),
    ("banner-tv", 1280, 720, "metadata/en-US/images/tvBanner.png", True),
    ("home-logo", 512, 384, "app/src/main/assets/pages/home/logo.png", False),
    ("home-logo-dark", 512, 384, "app/src/main/assets/pages/home/logo-dark.png", False),
    ("readme-banner", 1200, 300, "docs/branding/readme-banner.png", False),
    ("readme-banner-dark", 1200, 300, "docs/branding/readme-banner-dark.png", False),
]
APP = "/home/gabriele/Projects/tv-bro"


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


def check_installed():
    """Every installed PNG must still be the render of the intended variant."""
    msgs, ok = [], True
    for variant, px, py, rel, want_opaque in INSTALL:
        ref_path = os.path.join(BASE, "preview", variant + ".png")
        dst = os.path.join(APP, rel)
        if not os.path.exists(ref_path):
            msgs.append(f"MISSING reference for {variant} (run build.py first)")
            ok = False
            continue
        if not os.path.exists(dst):
            msgs.append(f"MISSING {rel}")
            ok = False
            continue
        ref = Image.open(ref_path).convert("RGBA")
        im = Image.open(dst).convert("RGBA")
        opaque = min(p[3] for p in im.getdata()) == 255
        d = mean_rgba_diff(im, ref.resize((px, py), Image.LANCZOS))
        good = im.size == (px, py) and (opaque or not want_opaque) and d < 12
        ok = ok and good
        msgs.append(f"{'ok' if good else 'BAD'} {rel} {im.size[0]}x{im.size[1]} "
                    f"opaque={opaque} |d|={d:.1f} ({variant})")
    header = ("ok: %d files" % len(INSTALL)) if ok else "INSTALLED ASSET MISMATCH"
    return header + "\n" + "\n".join("    " + m for m in msgs)


def main():
    print("%-30s %s" % ("variant", "verdict"))
    ok = True
    for path in sorted(glob.glob(os.path.join(BASE, "variants", "*.svg"))):
        name = os.path.splitext(os.path.basename(path))[0]
        ideal, _ = rebuild(path)
        # recoloured variants can only be checked geometrically (alpha)
        txt = open(path).read().lower()
        recol = any(c in txt for c in ("#f7ecd9", "#efa33c"))
        verdict = compare(name, ideal, recol)
        bad = verdict.startswith(("DIFF", "GEOMETRY", "SIZE"))
        ok = ok and not bad
        print("%-30s %s%s" % (name, "*" if bad else " ", verdict))

    print("\ninstalled deliverables:")
    v = check_installed()
    bad = v.startswith(("MISSING", "INSTALLED"))
    ok = ok and not bad
    print("%s %s" % ("*" if bad else " ", v.replace("\n", "\n  ")))

    print("\nALL OK" if ok else "\nFAILURES PRESENT")


if __name__ == "__main__":
    main()
