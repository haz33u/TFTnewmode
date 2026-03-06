#!/usr/bin/env python3
"""
AstralClash Resource Pack — Placeholder Texture Generator
==========================================================
Generates 16x16 PNG champion icons with distinctive color schemes.
No external dependencies — uses only Python stdlib (zlib, struct).

Usage:
    python3 generate_textures.py

Output: assets/astralclash/textures/item/champion/<name>.png
        assets/astralclash/textures/item/pack_icon.png

Replace these PNGs with real artwork (e.g. fan-made HSR character portraits
scaled to 16x16 or 32x32 pixels) to get the final look.
"""

import os
import struct
import zlib

# ── Champion color palette ────────────────────────────────────────────────────
# (R, G, B) main color, accent color (for border/details)
CHAMPIONS = {
    #  name          main              accent         initial
    "asta":       ((220, 100,  40),  (255, 200,  80), "A"),
    "blade":      (( 40, 100,  70),  (130, 255, 160), "B"),
    "bronya":     ((  0, 160, 200),  (180, 230, 255), "Br"),
    "danheng":    ((140, 200, 220),  (200, 240, 255), "D"),
    "fuxuan":     ((100,  40, 140),  (220, 160, 255), "F"),
    "gepard":     ((180, 220, 240),  (240, 250, 255), "G"),
    "himeko":     ((210,  50,  50),  (255, 160,  60), "H"),
    "jingyuan":   ((210, 180,  20),  (255, 240,  60), "JY"),
    "jingliu":    ((140, 200, 230),  (240, 250, 255), "JL"),
    "kafka":      (( 80,  20, 140),  (200,  80, 255), "K"),
    "luocha":     ((200, 160,  20),  (255, 230, 100), "L"),
    "march7th":   ((240,  80, 140),  (255, 200, 220), "M"),
    "natasha":    (( 60, 160,  90),  (140, 230, 160), "N"),
    "pela":       (( 50, 170, 190),  (150, 240, 250), "P"),
    "seele":      ((110,  40, 180),  (200, 130, 255), "S"),
    "welt":       (( 20,  60, 130),  ( 80, 140, 220), "W"),
}

# ── PNG writer ────────────────────────────────────────────────────────────────

def _chunk(tag: bytes, data: bytes) -> bytes:
    payload = tag + data
    return (struct.pack(">I", len(data)) + payload
            + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF))

def make_png(pixels: list[list[tuple[int,int,int]]]) -> bytes:
    """pixels[row][col] = (R, G, B)"""
    h = len(pixels)
    w = len(pixels[0])
    raw = b""
    for row in pixels:
        raw += b"\x00"  # filter byte = None
        for r, g, b in row:
            raw += bytes([r, g, b])
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib.compress(raw, 9))
        + _chunk(b"IEND", b"")
    )

# ── Pixel art helpers ─────────────────────────────────────────────────────────

def blend(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))

def darken(c, factor=0.55):
    return tuple(int(x * factor) for x in c)

def lighten(c, factor=1.4):
    return tuple(min(255, int(x * factor)) for x in c)

def make_champion_icon(main, accent, size=16):
    """
    Creates a 16x16 champion icon:
      - Radial gradient background (main → darker at edges)
      - 1px bright border (accent color)
      - Simple "gem" shape in the center (5x5 diamond)
    """
    pixels = [[main] * size for _ in range(size)]

    # Gradient — distance from center
    cx, cy = (size - 1) / 2, (size - 1) / 2
    max_dist = ((cx) ** 2 + (cy) ** 2) ** 0.5
    dark = darken(main, 0.35)
    for r in range(size):
        for c in range(size):
            dist = ((r - cy) ** 2 + (c - cx) ** 2) ** 0.5
            t = min(1.0, dist / max_dist)
            pixels[r][c] = blend(main, dark, t * 0.6)

    # Border (1px, accent)
    for i in range(size):
        pixels[0][i]       = accent
        pixels[size-1][i]  = accent
        pixels[i][0]       = accent
        pixels[i][size-1]  = accent

    # Inner border (1px, slightly lighter main)
    light = lighten(main, 1.3)
    for i in range(1, size-1):
        pixels[1][i]      = light
        pixels[size-2][i] = light
        pixels[i][1]      = light
        pixels[i][size-2] = light

    # Diamond gem shape in center (5x5 at 16x16)
    gem = lighten(accent, 1.15)
    gem_dark = darken(accent, 0.7)
    cx2, cy2 = size // 2, size // 2
    diamond = [
                    (cy2-2, cx2),
              (cy2-1, cx2-1),(cy2-1, cx2),(cy2-1, cx2+1),
        (cy2,  cx2-2),(cy2,  cx2-1),(cy2,  cx2),(cy2,  cx2+1),(cy2,  cx2+2),
              (cy2+1, cx2-1),(cy2+1, cx2),(cy2+1, cx2+1),
                    (cy2+2, cx2),
    ]
    for idx, (dr, dc) in enumerate(diamond):
        if 0 <= dr < size and 0 <= dc < size:
            pixels[dr][dc] = gem if idx % 3 != 2 else gem_dark

    # Top-left highlight dot on gem
    if 2 <= cy2-1 < size-2:
        pixels[cy2-1][cx2-1] = lighten(accent, 1.6)

    return pixels

def make_pack_icon(size=64):
    """64x64 pack icon — gradient purple with star."""
    main   = (80, 20, 140)
    accent = (220, 150, 255)
    pixels = [[main] * size for _ in range(size)]
    cx, cy = (size-1)/2, (size-1)/2
    max_d  = (cx**2 + cy**2) ** 0.5
    dark   = (20, 5, 50)
    for r in range(size):
        for c in range(size):
            d = ((r-cy)**2 + (c-cx)**2)**0.5
            t = min(1.0, d / max_d)
            pixels[r][c] = blend(main, dark, t * 0.7)
    # Border
    for i in range(size):
        pixels[0][i] = pixels[size-1][i] = pixels[i][0] = pixels[i][size-1] = accent
    for i in range(1, size-1):
        pixels[1][i] = pixels[size-2][i] = pixels[i][1] = pixels[i][size-2] = lighten(accent, 0.7)
    # Star in center (simplified 7x7 cross + diagonals)
    s = (255, 240, 100)
    mid = size // 2
    for d in range(-3, 4):
        for r, c in [(mid+d, mid), (mid, mid+d), (mid+d, mid+d), (mid+d, mid-d)]:
            if 0 < r < size-1 and 0 < c < size-1:
                pixels[r][c] = s
    return pixels

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    base = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(base, "assets", "astralclash", "textures", "item", "champion")
    os.makedirs(out_dir, exist_ok=True)

    print("Generating champion textures...")
    for name, (main, accent, _initial) in CHAMPIONS.items():
        pixels = make_champion_icon(main, accent)
        path   = os.path.join(out_dir, f"{name}.png")
        with open(path, "wb") as f:
            f.write(make_png(pixels))
        print(f"  + {name}.png")

    # Pack icon
    icon_dir = os.path.join(base)
    icon_pixels = make_pack_icon(64)
    icon_path = os.path.join(icon_dir, "pack.png")
    with open(icon_path, "wb") as f:
        f.write(make_png(icon_pixels))
    print(f"  + pack.png (64x64 icon)")

    print(f"\nDone! {len(CHAMPIONS)} champion textures generated.")
    print("Replace PNGs with real artwork (16x16 or 32x32) for the final look.")
    print("Then run:  bash pack.sh  to build the .zip")

if __name__ == "__main__":
    main()
