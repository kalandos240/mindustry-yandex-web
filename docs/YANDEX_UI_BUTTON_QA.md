# Yandex Games UI actions and freeze-prevention acceptance policy

Reference: https://yandex.ru/dev/games/doc/ru/concepts/requirements.html
(current official requirements, updated 9 October 2026)

## Scope of the requirement

- **Mandatory 1.14**: no technical errors, hard freezes or crashes on ordinary
  clicks, touch gestures, orientation changes, backgrounding, advertising and
  browser history.
- **Mandatory 1.8 and 1.10**: every interactive element must fit the declared
  device's **actual game iframe** (not the full browser/portal page), remain
  usable, and not be obscured by ads or overlays. No page scrollbars or
  swipe-to-refresh during game interaction.
- **Mandatory 1.6.2.6 and 1.6.2.7**: do not steal operating-system keyboard
  shortcuts or open browser context menus/select text while interacting with
  the game. Mobile long-press must not open a system context menu either.
- **Recommended 6.7**: avoid platform-useless controls (the explicit example
  is an **Exit game** button in the main browser menu).
- Only Yandex SDK may initiate advertisements or platform purchases, and
  fullscreen advertisements should occur at appropriate logical pauses.
- This is **not** a rule that every original Mindustry button is forbidden.
  Desktop-only options are hidden when they cannot function safely in Web.

## Approved main-menu actions (with real native targets)

| Arc Scene action | Expected functional result | Verification |
| --- | --- | --- |
| Play | Browser-supported local/campaign selector and Back | Chrome CDP real click + native frames |
| Custom Game (desktop) | Same supported map selector; no dead duplicate action | Chrome CDP real click + Back |
| Research | Original technology content through working browser research overlay | Chrome CDP click, Escape, loop heartbeat |
| Database | Working encyclopedia overlay (native database is a separate deep test) | Chrome CDP click, Escape, loop heartbeat |
| Settings | Native Mindustry Settings dialog | Chrome CDP click, Escape, loop heartbeat |
| Schematics | Native schematic browsing/import/export | Chrome CDP click, Escape, loop heartbeat |
| Controls | Native Mindustry key bindings | Chrome CDP click, Escape, loop heartbeat |
| Saved Games | Browser-safe Load dialog | Chrome CDP click, Escape, loop heartbeat |
| Planets | Original 3D planet renderer/sector selector | Separate real Chrome `verify-browser-planet-dialog.sh`, including first-run chooser |

CI script `scripts/verify-browser-ui-button-stress.sh` launches independent
desktop **and** mobile Chrome profiles. `scripts/browser-ui-button-stress.js`
requires every visible home button's real Arc Scene hitbox to fall entirely
inside the actual canvas; it sends real Chrome mouse/touch actions through
`--cdp-input-bridge` instead of calling Java callbacks, opens and closes
dialogs, revisits Settings and Play, and requires the real TeaVM
`data-mindustry-cpu-sample` (sampled every 64 successful simulation frames)
to increment after each action. A blocked UI, lost key event or stopped
frame scheduler fails the test and **blocks the release**.

The static audit `scripts/audit-yandex-ui-controls.py` is a fail-closed
allow-list on **rendered main-menu actions**. Unsupported menu buttons do not
ship: Exit/Quit, external URLs, community server browser, Mods and unported
Editor workflows. Adding any new menu button requires a reviewed Yandex-safe
functional destination **and** real browser interaction acceptance.
Existing source patches prevent unsupported desktop ZIP backup/import,
log-folder, fullscreen/vsync and OS restart buttons from appearing in stock
Settings. The game also suppresses browser context menu and text selection.

## Real in-game HUD and Pause acceptance (October 17)

A second **blocking** job step `gameplay_button_stress` runs
`scripts/verify-browser-gameplay-button-stress.sh` on four independent
actual-world browser profiles: Serpulo Ground Zero campaign desktop and mobile;
stock Maze local game desktop and mobile. It reuses the original
`BrowserUiRuntime` Arc Scene actor/callbacks and the existing campaign/local
state machines rather than calling any gameplay methods from the test.

All eight supported pause actions (seven outside campaign) are now explicitly
tracked in native pause UI, not in the dormant main-menu actor graph:
**Resume, Settings, Save Game, Research (campaign), Schematics,
Controls, Saved Games and Back to menu**. The formerly mislabeled
`Quit` button is now **Back** and returns to the menu after normal save/cloud
barriers; it does not attempt to exit the browser. The test also clicks the
real native in-campaign Research icon before pausing.

Chrome sends its actual desktop pointer/touch input to the game canvas by CDP
and asserts: usable hit rectangles remain within the current canvas; opening
Settings/Controls/Schematics/Saves exposes the correct stock modal; Escape
closes it without resuming gameplay; Research opens and closes; Save Game
creates an actual campaign/local checkpoint while the simulation remains
paused; Resume returns to live gameplay; and Back reaches the original menu.
After **every** transition the real Java scheduler must advance
`data-mindustry-cpu-sample`, rather than merely receiving browser
`requestAnimationFrame` callbacks. Both a missing callback and a stopped
simulation result in a failing release gate.

Note: this covers the reachable first-level Pause and Research actions.
Nested buttons inside each dialog, unit-specific commands, building config
widgets, alternative resolutions and live Yandex SDK ad interruption
remain **separately required acceptance work**, not already passed.

## Other required, independent browser gates

These already form part of the release CI and should **not** be removed to
make UI tests pass:

- `verify-browser-space-pause.sh`, `verify-browser-campaign-pause.sh`,
  `verify-browser-local-save.sh`: Space/Escape pause, save, resume and cold
  Continue; ensure game state is not stuck.
- `verify-browser-research-actions.sh`, `verify-browser-vanilla-dialogs.sh`,
  `verify-browser-planet-dialog.sh`: gameplay HUD tech tree/navigation and
  original dialogs on actual Chrome input.
- `verify-browser-rts-command.sh`, `verify-browser-mobile-rts.sh`,
  `verify-browser-palette-visibility.sh`: selection/command/construction controls.
- `verify-browser-resize-orientation.sh`,
  `verify-browser-portal-bounds.sh`, `verify-yandex-sdk.sh`:
  iframe/ad geometry, device rotations and platform pause/resume.
- `verify-browser-campaign-capture.sh`,
  `verify-browser-erekir-progression.sh`, and
  `verify-browser-all-campaign-maps.sh`: actual planet progression and
  individual 46-map boot matrix.

## Release truthfulness and outstanding manual checks

A successful menu smoke is **not** proof that every nested option of every
native settings/gameplay window works. For complete parity, continue building
a per-window click-and-close matrix covering every visible nested native
setting, keybinding, unit command, block configuration, schematic and
campaign button, including portrait/landscape and very small embedded
Yandex surfaces. Exercise real Yandex draft ads and post-ad focus/sound
resumption, mobile back/history behavior, and accessibility/touch targets
in an actual platform preview. Do **not** certify these until exercised.

Remain on draft PR #165, keep `main` untouched and enforce the
**100,000,000 unpacked bytes** limit as an independent blocking gate.
