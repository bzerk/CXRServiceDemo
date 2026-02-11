#!/usr/bin/env bash
# Shell helper: set Java 21 and add Android platform-tools (adb) to PATH.

# Resolve Java 21 on macOS.
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set. Install/configure JDK 21 first." >&2
  return 1 2>/dev/null || exit 1
fi

android_sdk_root="${ANDROID_SDK_ROOT:-}"
android_home="${ANDROID_HOME:-}"

# Prefer standard Android SDK locations.
platform_tools_candidates=(
  "$HOME/Library/Android/sdk/platform-tools"
  "${android_sdk_root}/platform-tools"
  "${android_home}/platform-tools"
)

for candidate in "${platform_tools_candidates[@]}"; do
  if [[ -n "${candidate}" && -d "${candidate}" ]]; then
    case ":$PATH:" in
      *":$candidate:"*) ;;
      *) export PATH="$JAVA_HOME/bin:$candidate:$PATH" ;;
    esac
    break
  fi
done

# Always ensure Java bin is first.
case ":$PATH:" in
  *":$JAVA_HOME/bin:"*) ;;
  *) export PATH="$JAVA_HOME/bin:$PATH" ;;
esac

echo "JAVA_HOME set to $JAVA_HOME"
echo "PATH updated (java/adb should be available if SDK platform-tools exists)."
