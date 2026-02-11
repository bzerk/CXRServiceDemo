# Development Workflow

Day-to-day loop for fast iteration on glasses.

## Build + deploy loop

```bash
source env.sh
./gradlew :app:assembleDebug
./scripts/install_and_launch.sh
```

## Useful commands

Start launcher activity:

```bash
source env.sh
adb shell am start -n com.example.cxrservicedemo/.DemoSelectorActivity
```

Start hand tracking directly:

```bash
source env.sh
adb shell am start -n com.example.cxrservicedemo/.HandTrackingActivity
```

Watch app logs:

```bash
source env.sh
./scripts/logcat_app.sh
```

Clean build:

```bash
source env.sh
./gradlew clean :app:assembleDebug
```

## Performance tips for glasses

- Keep overlays simple and avoid heavy per-frame allocations.
- Prefer lower camera resolution and fewer model inferences.
- Use `STRATEGY_KEEP_ONLY_LATEST` for image analysis streams.
- Pause camera/sensors when activity is backgrounded.

## If app exits unexpectedly

1. Capture logs with `./scripts/logcat_app.sh`.
2. Check for thermal/memory/system kills in `logcat`.
3. Reduce workload (fps, resolution, inference frequency).

