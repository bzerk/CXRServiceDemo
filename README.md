# Rokid Glasses Android Starter

Starter Android project for building and testing apps on Rokid glasses via the CXR bridge.

This repo is optimized for onboarding: clone, verify environment, build, install, and run demos on-device.

## Included demos

- **Demo selector** launcher with glasses-friendly black/green UI.
- **CXR bridge demo** for pairing, subscribe/send, and status callbacks.
- **MediaPipe hand tracking** with CameraX preview + landmark overlay.
- **Ambient visualizer** driven by mic + motion sensors.
- **Object labels** demo with overlay rendering.

## Quick start

```bash
# from project root
source env.sh
./scripts/check_env.sh
./gradlew :app:assembleDebug
./scripts/install_and_launch.sh
```

If `adb` is missing, follow `docs/SETUP.md`.

## Project layout

- `app/src/main/java/com/example/cxrservicedemo` — Kotlin source.
- `app/src/main/res/layout` — activity layouts.
- `app/src/main/assets` — MediaPipe model assets.
- `docs/SETUP.md` — environment + device setup.
- `docs/WORKFLOW.md` — day-to-day dev commands.
- `docs/ROADMAP.md` — prioritized next improvements.
- `scripts/check_env.sh` — validates Java/adb/Gradle environment.
- `scripts/install_and_launch.sh` — install APK and launch app.
- `scripts/grant_permissions.sh` — attempts runtime permission grants.
- `scripts/logcat_app.sh` — filtered app log stream.

## Common commands

```bash
source env.sh
./gradlew :app:assembleDebug
./scripts/grant_permissions.sh
./scripts/install_and_launch.sh
./scripts/logcat_app.sh
```

## Troubleshooting

- **Device missing:** replug USB, ensure debugging mode is enabled, then `adb devices`.
- **JDK error:** use Java 21 (`source env.sh`), confirm with `java -version`.
- **App not visible:** `adb shell am start -n com.example.cxrservicedemo/.DemoSelectorActivity`.
- **Permission issues:** run `./scripts/grant_permissions.sh` and relaunch.

## Contributing

Read `CONTRIBUTING.md` for contribution and validation expectations.
