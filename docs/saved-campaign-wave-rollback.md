# Saved campaign wave rollback

A cold Onset save with wave=3 and matching tick metadata failed native Go with `resume-clock-mismatch:w=3/1`. The SaveVersion reader restored wave=3, but Logic's SaveLoadEvent handler then called `SectorInfo.write()`. Its separately persisted settings record could still contain the default wave=1.

The browser synchronizes SectorInfo's wave, waves, waveSpacing, winWave and attack fields from the loaded MSAV before that handler applies SectorInfo. The existing strict resume check stays enabled. No progress reset or file deletion is needed. Resource recovery retains its existing MSAV/companion-checkpoint handling.

The native Go fixture now saves wave=3 alongside both decimal-clock boundary cases. It creates real saved worlds, restores pristine production JS, restarts the browser with the same profile, clicks the original planet Go button, verifies exact saved wave and milliseconds, and waits for simulation to continue. The fixture runs for Ground Zero and Onset on desktop/mobile Chrome, with an additional actual Firefox cold-restart harness.

Previous archive reproduction: Actions run 39146845790 reproduces the exact Onset wave 3/1 rejection from the user screenshot. Fixed archive compilation/regressions: run 39146826837. Firefox acceptance: run 39146984318.

Firefox acceptance passed both campaigns with wave=3 and exact tick milliseconds. The four desktop/mobile Chrome native Go cases also passed. Archive SHA-256: `831bd5784d964919df8196102253878f041c5f875c46751121063368dfc10b5f`; unpacked size: 93,810,007 bytes.
