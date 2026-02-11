#!/usr/bin/env bash
set -euo pipefail

missing=0

check_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[missing] $cmd"
    missing=1
  else
    echo "[ok] $cmd -> $(command -v "$cmd")"
  fi
}

echo "Checking host toolchain..."
check_cmd java
check_cmd adb
check_cmd ./gradlew

if command -v rg >/dev/null 2>&1; then
  echo "[ok] rg -> $(command -v rg)"
else
  echo "[warn] rg not found (optional; log filtering falls back to grep)"
fi

if command -v java >/dev/null 2>&1; then
  echo "Java version:"
  java -version 2>&1 | head -n 1
fi

if command -v adb >/dev/null 2>&1; then
  echo "ADB devices:"
  adb devices | sed -n '1,6p'
fi

if [[ "$missing" -ne 0 ]]; then
  echo "One or more required tools are missing. See docs/SETUP.md." >&2
  exit 1
fi

echo "Environment looks ready."
