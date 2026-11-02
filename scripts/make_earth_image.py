#!/usr/bin/env python3
"""
Draws the black and white Earth used on the poster.

A world map in the usual flat form (longitude across, latitude down) is wrapped around a
sphere, the way a camera far above India would see it. The picture is turned to grey, lit softly
from the upper left, sharpened a little and made dim, so that it stays in the background.
The dimness is part of the picture and the globe is fully solid. If it were made faint with
CSS opacity instead, the stars behind it would show through.

Needs Pillow and numpy.

Better picture: pass a large world map with --map. The NASA Blue Marble with topography and
bathymetry (5400 x 2700, from NASA Visible Earth) works well:
    https://eoimages.gsfc.nasa.gov/images/imagerecords/73000/73909/world.topo.bathy.200412.3x5400x2700.jpg
    python scripts/make_earth_image.py --map blue-marble-5400.jpg

Without --map, the small map that ships with CesiumJS is used (run "npm install" in frontend first).
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

CESIUM_TILES = Path("frontend/node_modules/cesium/Build/Cesium/Assets/Textures/NaturalEarthII/2")
OUT = Path("frontend/src/assets/earth-bw.webp")

SIZE = 1200                 # width and height of the picture in pixels
DISK_RADIUS = 0.40 * SIZE   # the globe itself, the rest is room for a faint glow
VIEW_LON, VIEW_LAT = 78.0, 22.0   # the point that faces the viewer (India)
DIMNESS = 0.66              # 1 would be the full brightness of the map


def load_cesium_map():
    """Joins the 8 x 4 tiles of the Cesium map into one grey world map. Tile row 0 is the southernmost."""
    world = Image.new("L", (8 * 256, 4 * 256))
    for x in range(8):
        for y in range(4):
            tile = Image.open(CESIUM_TILES / str(x) / f"{y}.jpg").convert("L")
            world.paste(tile, (x * 256, (3 - y) * 256))
    return world


def sample(world, lon, lat):
    """Reads the map at the given longitudes and latitudes (radians), with smoothing."""
    h, w = world.shape
    u = (lon + math.pi) / (2 * math.pi) * w
    v = (math.pi / 2 - lat) / math.pi * h - 0.5
    u0 = np.floor(u).astype(int)
    v0 = np.floor(v).astype(int)
    fu, fv = u - u0, v - v0
    x0, x1 = u0 % w, (u0 + 1) % w
    y0, y1 = np.clip(v0, 0, h - 1), np.clip(v0 + 1, 0, h - 1)
    top = world[y0, x0] * (1 - fu) + world[y0, x1] * fu
    bottom = world[y1, x0] * (1 - fu) + world[y1, x1] * fu
    return top * (1 - fv) + bottom * fv


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--map", help="a world map in the usual flat form, wider than it is high by a factor of two")
    args = parser.parse_args()

    image = Image.open(args.map).convert("L") if args.map else load_cesium_map()
    world = np.asarray(image, dtype=np.float32) / 255.0
    print(f"map: {world.shape[1]} x {world.shape[0]} pixels")

    rows, cols = np.mgrid[0:SIZE, 0:SIZE].astype(np.float64)
    centre = (SIZE - 1) / 2
    x = (cols - centre) / DISK_RADIUS          # to the right
    y = (centre - rows) / DISK_RADIUS          # up
    r2 = x * x + y * y
    on_disk = r2 <= 1.0
    z = np.sqrt(np.clip(1.0 - r2, 0.0, None))  # towards the viewer

    # Which longitude and latitude lies under each pixel of the disk
    lat0, lon0 = math.radians(VIEW_LAT), math.radians(VIEW_LON)
    lat = np.arcsin(np.clip(z * math.sin(lat0) + y * math.cos(lat0), -1, 1))
    lon = lon0 + np.arctan2(x, z * math.cos(lat0) - y * math.sin(lat0))

    # Land comes out lighter than the sea once the colour is gone. A power curve widens the gap.
    ground = np.clip(sample(world, lon, lat), 0, 1) ** 1.25

    # Soft light from the upper left, with a narrow range so that nothing is very bright
    light = np.array([-0.55, 0.45, 0.70])
    light /= np.linalg.norm(light)
    lambert = np.clip(x * light[0] + y * light[1] + z * light[2], 0, 1)
    shade = 0.20 + 0.65 * lambert ** 0.8

    # No pale edge and no glow. The shape of the globe comes from its body instead: even the sea
    # is a dark grey that is a little lighter on the lit side, so the whole disk stays visible
    # against the black sky and the globe stays round.
    body = 0.040 + 0.040 * lambert
    detail = np.clip(ground * shade, 0, 1) * DIMNESS
    grey = body + detail * (1.0 - body)     # the map is added on top of the body, so the detail stays

    r = np.sqrt(r2)
    # Fully solid inside the globe, with a clean edge, and completely clear outside it.
    edge = np.clip((1.0 - r) * DISK_RADIUS / 1.2, 0, 1)
    alpha = np.where(on_disk, edge, 0.0)
    value = np.where(on_disk, grey, 0.0)

    # A little sharpening brings out coasts and mountains. Only the grey values are sharpened, not
    # the transparent edge, which would leave a bright ring around the globe.
    sharp = Image.fromarray((np.clip(value, 0, 1) * 255).astype(np.uint8), "L")
    sharp = sharp.filter(ImageFilter.UnsharpMask(radius=1.4, percent=70, threshold=2))
    channel = np.asarray(sharp)
    opacity = (np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    picture = Image.fromarray(np.dstack([channel, channel, channel, opacity]), "RGBA")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    picture.save(OUT, "WEBP", quality=88, method=6)
    print(f"{OUT} written, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
