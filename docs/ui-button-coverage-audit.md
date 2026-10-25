# UI action coverage audit — 2026-10-25

Baseline audited: `fcfa613a81df9a3b8d1810b1ccc63a77800e0dbd`.
This is a coverage inventory, not a declaration that every visible control works.
Opening a window and observing a browser heartbeat does not verify its inner controls
or prove that the game simulation is advancing.

| Control group | Existing input and effect checks | Coverage boundary |
| --- | --- | --- |
| Desktop Home / Play / Database groups | Native CDP clicks, group toggles, whole button bounds, Escape, repeated open/close; `browser-ui-button-stress.js` | Group navigation is checked, not every child in each opened dialog. |
| Main Campaign / Custom / Saves / Settings / Controls / Schematics / Research / Database | Native menu clicks and expected modal/overlay visibility | Most inner dialog controls are not exercised by this suite. |
| Pause Resume / Save / Back | Native clicks; playing/paused transitions, saved bytes, frozen save update ID, exit Cancel and Confirm; `browser-gameplay-button-stress.js` | Actual state/tick probing must accompany DOM markers. |
| Pause Settings / Controls / Schematics / Saves / Research | Native open/close and return to same paused world | Only three Sound sliders receive inner effect checks here. |
| Mobile Menu / Hide HUD / Schematics / Pause / Research | Native toolbar clicks, hidden HUD restoration, inline pause, menu, real dialogs, compact touch scrolling; `browser-mobile-hud-stress.js` | Pause freeze checks originally used DOM markers; actual state probing is stronger. |
| Globe Serpulo / Erekir / selected-sector Go | Native clicks, cross-planet start and saved-sector return; `browser-hud-planet-stress.js`, cold-Go suites | Old HUD test used stale selected-sector coordinates. New test awaits matching sampled selection and actual advancing tick. |
| Globe reopening after a sector switch | Repeated native HUD clicks | Old HUD probe stopped sampling after the first globe open. Probe now refreshes on every active world; new browser acceptance is pending. |
| Globe selected-save Export | Native Firefox hitbox/download and exact selected save/settings/backup checks in existing selected-sector suite | Browser Save dialog export and arbitrary schematic exports are separate actions. |
| Globe search / rename / icon selection / launch-loadout subcontrols | No exhaustive native action inventory in existing suites | Dynamic controls require separate enumeration and semantic assertions. Procedural-sector launch is intentionally unsupported. |
| Mobile campaign Back localization case | Previously called Java `returnToMenuWithAd()` and waited for immediate exit | Stale test ignored the required confirmation. New native Menu → Back → Cancel → Back → Confirm test is added; acceptance pending. |
| Research | Real node input, roots, purchase resource spending, dialog drag/resize; `browser-native-research-stress.js` | Does not click every node; progression fixtures can programmatically unlock prerequisites. |
| Database | Native open/close and category overlay entry | No exhaustive content/category/search/detail button actions are asserted. |
| Custom map catalog | Eighteen previews, native first map selection, Survival/Sandbox toggles, custom wave-timer option, real launch, resize/scroll; `browser-custom-game-stress.js` | One catalog map is clicked. All rules controls and every map card are not clicked. Separate map boot tests are not UI-button tests. |
| Building configuration | Native Sorter tile, Copper choice, reopen and clear; `browser-building-config-stress.js` | Only this configuration family is exercised. Logic, processor, power links, factory plan selection, unloader and payload configuration controls remain outside this test. |
| Factories / Assemblers | Original production mechanics and constructors are tested by dedicated fixtures | Fixture-selected production plans do not verify each factory's user-facing plan button. |
| Saved Games slots | Native dialog open/close; local Continue round trips are separate tests | At audited head generic slot activation displays an unsupported-action message. Import, rename, delete, export and slot launch need native state/content assertions. Save-controls work owns these changes. |
| Schematic library | Native dialog open/close; original file adapter exists | Import file/base64, export file/base64, rename, tags, delete and placing a selected schematic are not covered by the open/close test. |
| Game Data destructive actions | Explicitly skipped by existing settings sampling | Reset research, clear campaign saves, per-planet clears and clear-all require isolated profiles and persistence/cloud checks. |
| Keybind editing and language selection | Controls/Language dialogs can open; locale rendering is tested for EN/RU | Opening is not proof that editing, persistence, restart and remapped input work. |
| Portal fullscreen | SDK lifecycle fixture and fullscreen-control state checks | Not proof of every real Yandex portal/session/browser policy. |
| Error report Copy / Reload | Real nested Java exception, trusted copy or fallback, native reload and unchanged MSAV bytes; renderer-report acceptance | A diagnostic report is not a fix for the unresolved user renderer exception. |

## Confirmed baseline failures and corrections

- HUD cross-planet test clicked an obsolete Go position: the failure DOM shows
  launch tap count zero, no launch request marker, and the original Onset still
  paused under the globe. This is a stale-input test failure, not evidence of a
  broken `BrowserPlanetBridge` launch.
- The old mobile Back readiness actor directly invoked Back and never accepted
  its new confirmation dialog. The replacement drives the actual controls and
  verifies Cancel preserves the paused tick before Confirm saves and exits.

## Rule for new button results

Each enumerated visible action should record screen/state, native hitpoint,
enabled/disabled precondition, trusted input receipt, its semantic result, actual
game state/tick, persistence when relevant, and exceptions. Controls unavailable
because of normal game prerequisites are distinguished from unsupported features.
Untested actions remain untested rather than inheriting a passing result from
their parent dialog.
