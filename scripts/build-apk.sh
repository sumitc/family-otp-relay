#!/usr/bin/env bash
# Rebuild an installable APK from the compiled artifacts in this repo.
# The archive has no sources/Gradle files, so this assembles aapt2 + DEX output.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RES="app/build/intermediates/linked_resources_binary_format/release/processReleaseResources/linked-resources-binary-format-release.ap_"
DEX="app/build/intermediates/dex/release/mergeDexRelease/classes.dex"

for f in "$RES" "$DEX"; do
  if [[ ! -f "$f" ]]; then
    echo "Missing compiled artifact: $f" >&2
    echo "The source archive must be extracted into this repo first." >&2
    exit 1
  fi
done

SIGNER="${UBER_APK_SIGNER_JAR:-}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [[ -z "$SIGNER" || ! -f "$SIGNER" ]]; then
  SIGNER="$TMP/uber-apk-signer.jar"
  URL="https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar"
  echo "Downloading uber-apk-signer 1.3.0..."
  curl -fsSL "$URL" -o "$SIGNER"
  EXPECTED="e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835"
  ACTUAL="$(shasum -a 256 "$SIGNER" | awk '{print $1}')"
  if [[ "$ACTUAL" != "$EXPECTED" ]]; then
    echo "Signer checksum mismatch: $ACTUAL" >&2
    exit 1
  fi
fi

PASS="${KEYSTORE_PASS:-FamilyRelay2026}"
KS="signing/family-relay.jks"
mkdir -p signing dist "$TMP/staging" "$TMP/signed"

if [[ ! -f "$KS" ]]; then
  echo "Generating local signing key at $KS..."
  keytool -genkeypair -keystore "$KS" -alias familyrelay \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=Family OTP Relay, OU=Local, O=Family Relay, L=Hyderabad, ST=Telangana, C=IN" >/dev/null
fi

while IFS='=' read -r key value; do
  case "$key" in
    versionName) NAME="$value" ;;
    versionCode) CODE="$value" ;;
  esac
done < "$ROOT/VERSION"
: "${NAME:?VERSION missing versionName}"
: "${CODE:?VERSION missing versionCode}"

cd "$TMP/staging"
unzip -q "$ROOT/$RES"
python3 "$ROOT/scripts/patch-manifest.py" AndroidManifest.xml \
  AndroidManifest.patched "$NAME" "$CODE"
mv AndroidManifest.patched AndroidManifest.xml
cp "$ROOT/$DEX" classes.dex
rm -f "$TMP/unsigned.apk"
zip -q -X -0 "$TMP/unsigned.apk" resources.arsc classes.dex
zip -q -X -r "$TMP/unsigned.apk" AndroidManifest.xml res

cd "$ROOT"
java -jar "$SIGNER" -a "$TMP/unsigned.apk" -o "$TMP/signed" \
  --ks "$KS" --ksAlias familyrelay --ksPass "$PASS" --ksKeyPass "$PASS"

APK="$TMP/signed/unsigned-aligned-signed.apk"
java -jar "$SIGNER" -a "$APK" -y >/dev/null

OUT="dist/family-otp-relay-$NAME.apk"
cp "$APK" "$OUT"
echo "APK ready: $OUT (versionName $NAME, versionCode $CODE)"
shasum -a 256 "$OUT"
