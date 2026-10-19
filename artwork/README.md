# Odysseus artwork

Derived from `source/odysseus-art.svg` — a **read-only** copy of the author's original (mode 444).
Nothing here writes to it.

## What is here

| path | contents |
|---|---|
| `source/` | read-only copy of the original artwork |
| `parts/` | the source split into `square`, `galley`, `lettering` (+ light recolours) |
| `variants/` | composed deliverables: icons, lockups, banners, home logo, README banner |
| `preview/` | rasterised checks and before/after snapshots (`previous-*.png`) |
| `preview.html` | visual review page — open it in a browser |

## Rebuild / verify

```
python3 artwork/build.py    # regenerate parts + variants, then install app/store assets
python3 artwork/verify.py   # re-derive variants analytically, diff renders and installed files
```

`build.py` also writes the deliverables into the app:

- `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
- `res/drawable-xhdpi/banner.png` (leanback banner)
- `metadata/en-US/images/{icon,featureGraphic,tvBanner}.png`
- `assets/pages/home/{logo,logo-dark}.png`
- `docs/branding/readme-banner{,-dark}.png`

## Layout rules

- The galley's **geometry** box in the source extends ~159 units (27% of its width) past any
  visible pixel (max alpha 9). Derived layouts therefore centre on the **ink bounds**
  (`803.77, 240.93, 430.00, 437.00` in source units), measured from a background-free render.
  `icon-full` deliberately keeps the geometry box so it stays pixel-faithful to the source.
- Palette: amber `#DE8E0C`, ink `#241511`, rust `#B95D2E`, near-black `#1C1A1A`. The light
  recolour used on dark surfaces maps them to cream `#F7ECD9`, gold `#EFA33C` and an amber star.
- Chosen icon: `icon-galley` — the galley alone on the amber square (no wordmark), so it stays
  legible at 48 dp.
- Dark-theme consumers get the light recolour: the home page swaps to `logo-dark.png` via
  `prefers-color-scheme`, the README swaps to `readme-banner-dark.png` via `<picture>`. The store
  TV banner uses the ink background.

## Inkscape gotchas

- The default PNG export area is the *page*, so part files (non-zero viewBox origin) need
  `--export-area-drawing`; variant files start at 0,0 and are exported normally.
- `verify.py` compares composite-aware (visible RGB + alpha): RGB under fully transparent pixels
  is arbitrary and would otherwise dominate the difference.
