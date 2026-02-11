# Roadmap

Prioritized next steps for turning this into a stronger glasses dev starter.

## Next priorities

1. Hand-tracking orientation correction for display rotation edge cases.
2. Performance mode (lower analysis fps / reduced resolution).
3. Long-run stability mode (minimize thermal and memory pressure).
4. Unified monochrome green-on-black theme across all demos.
5. Add release checklist + known-good device matrix.

## Suggested implementation notes

- Add a central `AppTheme` color palette and apply globally.
- Expose camera analysis target fps and model confidence in UI.
- Add watchdog logging around lifecycle (`onPause`/`onStop`) and camera bind/unbind.
- Add lightweight perf counters (frame time, dropped frames).

## Definition of done for starter quality

- New dev can clone -> build -> install -> run in under 10 minutes.
- All demos launch from `DemoSelectorActivity` on glasses.
- No crashes during 5+ minute hand-tracking run.
- Clear troubleshooting path in docs.
