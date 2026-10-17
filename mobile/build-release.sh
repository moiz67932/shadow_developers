#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
signing_dir="${NASCON_SIGNING_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/nascon-signing}"
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"
if [ ! -f "$signing_dir/password" ]; then
  python3 -c 'import secrets,sys,pathlib; p=pathlib.Path(sys.argv[1]); p.write_text(secrets.token_urlsafe(32)); p.chmod(0o600)' "$signing_dir/password"
fi
if [ ! -f "$signing_dir/release.jks" ]; then
  keytool -genkeypair -keystore "$signing_dir/release.jks" -storepass:file "$signing_dir/password" -keypass:file "$signing_dir/password" -alias nascon -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=NaSCon Sports, O=NaSCon, C=PK'
  chmod 600 "$signing_dir/release.jks"
fi
export NASCON_SIGNING_STORE="$signing_dir/release.jks"
NASCON_SIGNING_PASSWORD="$(cat "$signing_dir/password")"
export NASCON_SIGNING_PASSWORD
# The cached Material dependency lacks a desktop-only lint artifact; no compilation task is skipped.
./gradlew :app:assembleRelease --offline -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease "$@"
mkdir -p release
cp app/build/outputs/apk/release/app-release.apk release/app-release.apk
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
"$sdk_dir/build-tools/36.0.0/apksigner" verify --verbose release/app-release.apk
