#!/usr/bin/env bash
set -euo pipefail

APP_TAG="CXRServiceDemo"
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

echo "Streaming logcat for ${APP_TAG}/${APP_ID} (Ctrl+C to stop)..."
if command -v rg >/dev/null 2>&1; then
  adb logcat | rg --line-buffered "${APP_TAG}|${APP_ID}"
else
  adb logcat | grep --line-buffered -E "${APP_TAG}|${APP_ID}"
fi
