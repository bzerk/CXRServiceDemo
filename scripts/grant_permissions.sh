#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.example.cxrservicedemo"

if [[ ! -f "env.sh" ]]; then
  echo "Run this script from project root (env.sh missing)." >&2
  exit 1
fi

source env.sh >/dev/null

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found on PATH. Check Android SDK platform-tools install." >&2
  exit 1
fi

permissions=(
  android.permission.CAMERA
  android.permission.RECORD_AUDIO
  android.permission.BLUETOOTH_CONNECT
  android.permission.BLUETOOTH_SCAN
)

for permission in "${permissions[@]}"; do
  echo "Granting ${permission}..."
  adb shell pm grant "$APP_ID" "$permission" || true
done

echo "Permission grant attempt complete."
