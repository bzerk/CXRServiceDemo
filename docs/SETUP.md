# Setup Guide

This guide gets a fresh machine ready to build and run the hand-tracking demo on Rokid glasses.

## 1) Install required tools

- Android Studio (latest stable).
- Android SDK Platform Tools (includes `adb`).
- JDK 21 (or use Android Studio's JetBrains Runtime 21 for IDE builds).

## 2) Configure Android Studio

1. Open this project in Android Studio.
2. Set **Gradle JDK** to Java 21:
   - `Android Studio` -> `Settings` -> `Build, Execution, Deployment` -> `Build Tools` -> `Gradle` -> `Gradle JDK`.
3. Let project sync complete.

## 3) Configure terminal environment

From project root:

```bash
source env.sh
java -version
adb version
```

If `adb` still is not found, add one of these to your shell profile (`~/.zshrc` or `~/.bashrc`):

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

Then open a new terminal and run `adb version`.

## 4) Connect the glasses

1. Connect the glasses by USB.
2. Ensure developer/debug mode is enabled on device.
3. Verify connection:

```bash
source env.sh
adb devices
```

Expected: a line with your device ID and status `device`.

## 5) Build + install + launch

```bash
source env.sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.rokidhandtrackingdemo/.HandTrackingActivity
```

## 6) Optional: grant runtime permissions via ADB

```bash
source env.sh
adb shell pm grant com.example.rokidhandtrackingdemo android.permission.CAMERA
```

Note: some Android versions/devices may reject one or more grants if permission policy differs.
