# Rokid Hand Tracking Demo

Focused Android starter for local MediaPipe hand tracking on Rokid glasses.

## What this repo includes

- CameraX preview pipeline.
- MediaPipe Hand Landmarker (`hand_landmarker.task`) inference.
- Green-on-black landmark overlay for glasses displays.
- Simple scripts for env check, install/launch, permission grant, and logs.

## Quick start

```bash
source env.sh
./scripts/check_env.sh
./gradlew :app:assembleDebug
./scripts/grant_permissions.sh
./scripts/install_and_launch.sh
```

## Project layout

- `app/src/main/java/com/example/cxrservicedemo/HandTrackingActivity.kt` — camera + inference loop.
- `app/src/main/java/com/example/cxrservicedemo/HandOverlayView.kt` — overlay rendering.
- `app/src/main/assets/hand_landmarker.task` — MediaPipe model asset.
- `docs/SETUP.md` — environment setup.
- `docs/WORKFLOW.md` — iteration workflow.
- `scripts/` — helper scripts.

## Related repos

- `https://github.com/bzerk/CXRServiceDemo` (this repo): hand-tracking-only.
- `https://github.com/bzerk/RokidAudioVisualizerDemo`: standalone audio visualizer demo.
