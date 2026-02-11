# Contributing

Thanks for helping improve this Rokid glasses starter.

## Before opening a PR

1. Build locally:

```bash
source env.sh
./gradlew :app:assembleDebug
```

2. If you changed behavior on device, verify on glasses with:

```bash
./scripts/install_and_launch.sh
```

3. Keep changes focused; avoid unrelated refactors.

## Coding guidelines

- Kotlin style should match existing files.
- Prefer simple, low-overhead rendering/processing paths.
- For glasses UX, default to black background + green foreground.

## Commit and PR notes

- Use clear commit messages (what changed + why).
- Include test/validation notes in PR description.
- Mention device model + Android version used for validation.

