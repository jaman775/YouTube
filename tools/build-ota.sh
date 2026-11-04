#!/usr/bin/env bash
# Builds a signed engine update.
#
#   ANDROID_HOME=~/Android/Sdk OTA_KEY=ota-private.pem tools/build-ota.sh <sequence> [out-dir]
#
# <sequence> must be higher than the previous update (apps ignore anything not newer).
# Output (upload all of it to one folder): manifest.json, manifest.json.sig, engine-<n>.jar,
# potoken-<n>.html, inject-<n>.js
set -euo pipefail

SEQ="${1:?usage: build-ota.sh <sequence> [out-dir]}"
OUT="${2:-ota-out}"
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
: "${OTA_KEY:?set OTA_KEY to the private signing key (see tools/gen_keys.py)}"
GRADLE="${GRADLE:-./gradlew}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)"
D8="${BUILD_TOOLS}d8"
ANDROID_JAR="$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar | sort -V | tail -1)"
NEWPIPE="$(sed -n 's/^newpipe = "\(.*\)"/\1/p' gradle/libs.versions.toml)"

rm -rf "$OUT" && mkdir -p "$OUT/work"

echo "==> Building the engine jar (NewPipe Extractor $NEWPIPE)"
$GRADLE --no-daemon -q :engine:otaJar

echo "==> Converting to dex (Android 8+)"
"$D8" --release --min-api 26 --lib "$ANDROID_JAR" \
  --output "$OUT/work/engine.zip" engine/build/ota/engine-classes.jar
cp "$OUT/work/engine.zip" "$OUT/engine-$SEQ.jar"
unzip -l "$OUT/engine-$SEQ.jar" | grep -q classes.dex || { echo "d8 produced no classes.dex"; exit 1; }

echo "==> Building the PO-token page"
(cd tools/potoken && npm ci --silent && npm run --silent build)
cp tools/potoken/dist/potoken.html "$OUT/potoken-$SEQ.html"
cp app/src/main/assets/browser/inject.js "$OUT/inject-$SEQ.js"

echo "==> Signing"
python3 tools/sign_manifest.py --key "$OTA_KEY" --sequence "$SEQ" \
  --version "NewPipe Extractor $NEWPIPE" \
  --engine "$OUT/engine-$SEQ.jar" \
  --asset "potoken.html=$OUT/potoken-$SEQ.html" \
  --asset "inject.js=$OUT/inject-$SEQ.js" \
  --notes "Built $(date -u +%Y-%m-%dT%H:%MZ)" --out "$OUT"
rm -rf "$OUT/work"
ls -la "$OUT"
