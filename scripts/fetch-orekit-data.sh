#!/usr/bin/env bash
# Downloads the Orekit data folder (leap seconds, Earth orientation, constants) that Orekit
# needs to convert between time scales and reference frames. Run once from the project root.
set -euo pipefail

TARGET="backend/data/orekit-data"
URL="https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip"

if [ -d "$TARGET" ]; then
  echo "$TARGET already exists, nothing to do."
  exit 0
fi

WORK="$(mktemp -d)"
curl -fsSL "$URL" -o "$WORK/orekit-data.zip"
unzip -q "$WORK/orekit-data.zip" -d "$WORK"

mkdir -p backend/data
mv "$WORK"/orekit-data-main "$TARGET"
rm -rf "$WORK"
echo "Orekit data installed in $TARGET"
