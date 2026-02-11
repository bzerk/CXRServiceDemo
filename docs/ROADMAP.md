# Roadmap

Prioritized next steps for improving the hand-tracking starter.

## Next priorities

1. Improve orientation handling for display rotation edge cases.
2. Add performance modes (lower analysis fps / reduced resolution).
3. Harden long-run stability (reduce thermal and memory pressure).
4. Add optional handedness labels and confidence overlays.
5. Add release checklist + known-good device matrix.

## Suggested implementation notes

- Expose camera analysis target fps and model confidence in UI.
- Add watchdog logging around lifecycle (`onPause`/`onStop`) and camera bind/unbind.
- Add lightweight perf counters (frame time, dropped frames).

## Definition of done for starter quality

- New dev can clone -> build -> install -> run in under 10 minutes.
- No crashes during 5+ minute hand-tracking run.
- Clear troubleshooting path in docs.
