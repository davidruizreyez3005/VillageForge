#!/usr/bin/env python3
"""Verifies the game screenshot actually shows a COLORED 3D world.

Regression this guards against: a missing fragment shader body made every
material render pure white, which passed all "process alive" smoke tests.
"""
import sys
from PIL import Image


def main() -> None:
    path = sys.argv[1]
    img = Image.open(path).convert("RGB")
    w, h = img.size
    px = img.load()

    total = whiteish = green = 0
    for y in range(0, h, 4):
        for x in range(0, w, 4):
            r, g, b = px[x, y]
            total += 1
            if r > 235 and g > 235 and b > 235:
                whiteish += 1
            elif g > 55 and g > r + 8 and g > b + 8:
                green += 1

    white_ratio = whiteish / total
    green_ratio = green / total
    print(f"screenshot {w}x{h}: white_ratio={white_ratio:.3f} green_ratio={green_ratio:.3f}")

    # Broken state: the whole 3D world renders near-white (sky is pale blue,
    # HUD is dark, so a truly white screen means the terrain went white).
    if white_ratio > 0.40:
        print("FAIL: screen is mostly white — world materials lost their color")
        sys.exit(1)
    # Healthy state: grass terrain covers a large part of the view.
    if green_ratio < 0.10:
        print("FAIL: expected green terrain pixels not found (green_ratio < 0.10)")
        sys.exit(1)
    print("PASS: world renders with colored terrain (grass present, no white-out)")


if __name__ == "__main__":
    main()
