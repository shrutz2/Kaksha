#!/usr/bin/env python3
"""
Softens the background picture of the poster.

The source picture is a smooth gradient, but at 2000 x 1000 pixels it shows blocky pixels, faint
vertical lines and visible steps in colour when it is stretched over a large screen. This script
blurs it, then adds a very light grain. The grain breaks up the steps, so the gradient looks
smooth instead of banded.

Needs Pillow and numpy.   Usage:
    python scripts/soften_background.py path/to/original.webp
The result is written to frontend/src/assets/hero-bg.webp.
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

OUT = Path("frontend/src/assets/hero-bg.webp")
BLUR_RADIUS = 16      # in pixels of the 2000 pixel wide picture
GRAIN = 1.6           # strength of the grain, in steps of 255


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", help="the original background picture")
    args = parser.parse_args()

    image = Image.open(args.source).convert("RGB")
    # Blur a copy with the edge pixels repeated outwards, so that the edges do not fade
    # towards black
    pad = BLUR_RADIUS * 3
    around = np.pad(np.asarray(image), ((pad, pad), (pad, pad), (0, 0)), mode="edge")
    padded = Image.fromarray(around, "RGB").filter(ImageFilter.GaussianBlur(BLUR_RADIUS))
    blurred = padded.crop((pad, pad, pad + image.width, pad + image.height))

    values = np.asarray(blurred, dtype=np.float32)
    rng = np.random.default_rng(7)
    values += rng.normal(0.0, GRAIN, values.shape[:2])[:, :, None]
    result = Image.fromarray(np.clip(values, 0, 255).astype(np.uint8), "RGB")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    result.save(OUT, "WEBP", quality=92, method=6)
    print(f"{OUT} written, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
