#!/usr/bin/env bash
# Build an installable Improm APK from the recovered Java sources.
# The archive had no Gradle project, so this compiles source/ with javac,
# desugars/dexes with d8, patches the version into the binary manifest, and
# aligns + signs the result. Output: dist/Improm-<versionName>.apk
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RES="app/build/intermediates/linked_resources_binary_format/release/processReleaseResources/linked-resources-binary-format-release.ap_"
[[ -f "$RES" ]] || {
  echo "Missing compiled resources: $RES" >&2
  echo "Extract the source archive into this repo first." >&2
  exit 1
}

while IFS='=' read -r key value; do
  case "$key" in
    versionName) NAME="$value" ;;
    versionCode) CODE="$value" ;;
  esac
done < "$ROOT/VERSION"
: "${NAME:?VERSION missing versionName}"
: "${CODE:?VERSION missing versionCode}"

TOOLS="$ROOT/.android-tools"
mkdir -p "$TOOLS" dist signing

# --- Android SDK tooling (use local SDK, fall back to a cached download) ---
SDK="${ANDROID_SDK_HOME:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
find_file() { find "$1" -type f -name "$2" 2>/dev/null | head -1 || true; }

AJAR="$(find_file "$SDK/platforms" 'android.jar' | grep 'android-35' | head -1 || true)"
[[ -n "$AJAR" ]] || AJAR="$(find_file "$SDK/platforms" 'android.jar' | sort -V | tail -1 || true)"
if [[ -z "$AJAR" ]]; then
  AJAR="$TOOLS/android.jar"
  if [[ ! -f "$AJAR" ]]; then
    echo "Downloading Android 35 platform (android.jar)..."
    curl -fsSL https://dl.google.com/android/repository/platform-35_r02.zip -o "$TOOLS/platform.zip"
    unzip -q -o "$TOOLS/platform.zip" -d "$TOOLS/platform"
    cp "$(find "$TOOLS/platform" -type f -name android.jar | head -1)" "$AJAR"
  fi
fi

D8="$(find_file "$SDK/build-tools" 'd8' | sort -V | tail -1 || true)"
if [[ -z "$D8" ]]; then
  D8="$TOOLS/build-tools/d8"
  if [[ ! -f "$D8" ]]; then
    echo "Downloading Android build-tools 35 (d8)..."
    curl -fsSL https://dl.google.com/android/repository/build-tools_r35_macosx.zip -o "$TOOLS/build-tools.zip"
    unzip -q -o "$TOOLS/build-tools.zip" -d "$TOOLS/build-tools-tmp"
    mkdir -p "$TOOLS/build-tools"
    cp "$(find "$TOOLS/build-tools-tmp" -type f -name d8 | head -1)" "$D8"
    cp -R "$(dirname "$(find "$TOOLS/build-tools-tmp" -type f -name d8 | head -1)")/lib" "$TOOLS/build-tools/lib" 2>/dev/null || true
    chmod +x "$D8"
  fi
fi

ZXING="$TOOLS/zxing-core-3.5.3.jar"
if [[ ! -f "$ZXING" ]]; then
  echo "Downloading ZXing core 3.5.3..."
  curl -fsSL https://repo1.maven.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar -o "$ZXING"
fi

# --- uber-apk-signer for alignment + signing ---
SIGNER="${UBER_APK_SIGNER_JAR:-}"
if [[ -z "$SIGNER" || ! -f "$SIGNER" ]]; then
  SIGNER="$TOOLS/uber-apk-signer.jar"
  if [[ ! -f "$SIGNER" ]]; then
    echo "Downloading uber-apk-signer 1.3.0..."
    curl -fsSL https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar -o "$SIGNER"
    EXPECTED="e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835"
    ACTUAL="$(shasum -a 256 "$SIGNER" | awk '{print $1}')"
    [[ "$ACTUAL" == "$EXPECTED" ]] || { echo "Signer checksum mismatch: $ACTUAL" >&2; exit 1; }
  fi
fi

# --- signing key (local, gitignored) ---
PASS="${KEYSTORE_PASS:-FamilyRelay2026}"
KS="signing/family-relay.jks"
if [[ ! -f "$KS" ]]; then
  echo "Generating local signing key at $KS..."
  keytool -genkeypair -keystore "$KS" -alias familyrelay \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=Improm, OU=Local, O=Improm, L=Hyderabad, ST=Telangana, C=IN" >/dev/null
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes" "$TMP/dex" "$TMP/staging" "$TMP/signed"

echo "Compiling Java sources..."
javac -nowarn --release 17 -cp "$AJAR:$ZXING" -d "$TMP/classes" \
  $(find source -name '*.java')

echo "Building DEX with d8..."
"$D8" --min-api 26 --lib "$AJAR" --output "$TMP/dex" \
  $(find "$TMP/classes" -name '*.class') "$ZXING"

cd "$TMP/staging"
unzip -q "$ROOT/$RES"
python3 "$ROOT/scripts/patch-manifest.py" AndroidManifest.xml \
  AndroidManifest.patched "$NAME" "$CODE"
mv AndroidManifest.patched AndroidManifest.xml
cp "$TMP/dex/classes.dex" classes.dex
rm -f "$TMP/unsigned.apk"
zip -q -X -0 "$TMP/unsigned.apk" resources.arsc classes.dex
zip -q -X -r "$TMP/unsigned.apk" AndroidManifest.xml res

cd "$ROOT"
java -jar "$SIGNER" -a "$TMP/unsigned.apk" -o "$TMP/signed" \
  --ks "$KS" --ksAlias familyrelay --ksPass "$PASS" --ksKeyPass "$PASS" >/dev/null

APK="$TMP/signed/unsigned-aligned-signed.apk"
java -jar "$SIGNER" -a "$APK" -y 2>&1 | grep -E 'zipalign verified|signature verified' || true

OUT="dist/Improm-$NAME.apk"
cp "$APK" "$OUT"
echo "APK ready: $OUT (versionName $NAME, versionCode $CODE)"
shasum -a 256 "$OUT"
