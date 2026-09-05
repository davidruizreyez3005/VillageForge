#!/usr/bin/env python3
"""Verifies the game screenshot shows a COLORED 3D world.

Regression this guards against: a missing fragment shader body made every
material render pure white, which passed all "process alive" smoke tests.

NOTE: the headless SwiftShader emulator has a washed-out compositing pipeline,
so absolute brightness is not trustworthy here. We test channel STRUCTURE
instead: a white screen has near-zero channel spread everywhere, while any
colored scene (even washed) keeps pixels with significant spread and a
green-dominant ground.
"""
import sys
from PIL import Image


def main() -> None:
    path = sys.argv[1]
    img = Image.open(path).convert("RGB")
    w, h = img.size
    px = img.load()

    total = colored = blown = 0
    gb_sum = gr_sum = gb_n = 0
    for y in range(0, h, 4):
        for x in range(0, w, 4):
            r, g, b = px[x, y]
            total += 1
            if max(r, g, b) - min(r, g, b) > 20:
                colored += 1
            if r > 245 and g > 245 and b > 245:
                blown += 1
            if 0.60 * h < y < 0.95 * h:
                gb_sum += g - b
                gr_sum += g - r
                gb_n += 1

    colored_ratio = colored / total
    blown_ratio = blown / total
    gb_mean = gb_sum / gb_n
    print(
        f"screenshot {w}x{h}: colored_ratio={colored_ratio:.3f} "
        f"blown_white_ratio={blown_ratio:.3f} ground(G-B)={gb_mean:.1f}"
    )

    # Failure mode of the old bug: materials rendered pure white -> no colored
    # pixels at all, everything blown out.
    if colored_ratio < 0.10:
        print("FAIL: almost no colored pixels (spread <= 20) — materials likely white")
        sys.exit(1)
    if blown_ratio > 0.50:
        print("FAIL: more than half the screen is blown-out white")
        sys.exit(1)
    # Ground band must lean green-family (G notably above B), not neutral white.
    if gb_mean < 10:
        print("FAIL: ground region is not green-family (G-B < 10)")
        sys.exit(1)
    print("PASS: world renders with colored (green-family) terrain")


if __name__ == "__main__":
    main()
