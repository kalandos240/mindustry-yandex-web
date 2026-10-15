# Full vanilla Mindustry Web port — 100 MB release contract

**Development branch:** `full-vanilla-port-100mb`. **Baseline:** pinned Mindustry v159.7 and existing Arc/TeaVM browser port. The `main` branch stays deployable until this branch passes all required checks.

## Hard size rule

Yandex Games requirement **1.21** limits the **sum of unpacked game file sizes to 100 MB**. The official requirement says *MB*; conservatively enforce **100,000,000 bytes** rather than 100 MiB (104,857,600 bytes). The compressed ZIP is a second useful metric, but it does not substitute for the unpacked limit. `scripts/yandex_size_budget.py` generates a category/large-file report after `stageWeb` and fails above 100,000,000 bytes. Aim for <= 95,000,000 bytes to preserve 5 MB of headroom when possible; exceeding 95 MB warns, exceeding 100 MB fails. Keep the atlas, full campaigns and user-facing content unless a replacement has been validated against parity tests.

Official documentation: https://yandex.ru/dev/games/doc/ru/concepts/requirements.html

## Architecture decision

This is a **browser adaptation of original Mindustry**, not a new imitation. Keep original game logic, assets and UI types as canonical; implement missing compatibility at the Arc/browser boundary rather than writing parallel HTML versions of stock dialogs.

1. **Reuse already verified systems:** pinned gameplay, Serpulo and Erekir campaign maps, Arc Scene, native desktop/mobile input, PlacementFragment, persistence, audio, shaders, localization and Yandex SDK.
2. **Extract browser-compatible adapters:** file chooser/import/export, clipboard, sound, pause/resume and persistent saves. These adapters must serve stock dialogs as well as existing gameplay.
3. **Restore the original UI graph:** MenuFragment, HudFragment, PlanetDialog, SchematicsDialog, ContentInfoDialog, ResearchDialog, DatabaseDialog, SettingsMenuDialog, KeybindDialog, CustomGameDialog, editor and relevant save/load dialogs. Do **not** call `UI.init()` blindly: its eager constructors also reach desktop-only multiplayer/mod/network/editor facilities and assume initialized dependencies such as `netServer`, `ui.hints`, and `ui.paused`.
4. **Recover unsupported functionality at its correct boundary:** web-compatible multiplayer transport, browser mod policy and input/upload handling. Do not equate a visible button with a working capability.
5. **Remove redundant custom browser UI only after the replacement passes smoke and interactive tests** and saves can be restored across a real browser restart.

The original UI graph must be enabled on a **single integration branch**, with intermediate build/test commits permitted. It is not a requirement to publish incomplete UI screens or to overwrite the currently playable main branch.

## Functional completion matrix

| Area | Required interaction | Acceptance evidence |
| --- | --- | --- |
| Main menu | Vanilla desktop/mobile menu and navigation | Real desktop/mobile click-through |
| Campaign | Stock planet camera, sector browse, sector info, launch, attack and resume | Serpulo/Erekir progression + cold resume |
| Gameplay HUD | Native minimap, alerts, selection, unit bars, objectives, commands, pause | Desktop and touch gameplay, no duplicate controls |
| Schematics | Browse, place, search, tags, import/export and save | Real schematic round-trip |
| Technology | Stock TechTree and content information links | Unlock costs, prerequisites, save persistence |
| Encyclopedia | Stock item/block/unit/liquid stats and filters | Search, click-through, correct stats |
| Saves | Stock slots, restore, rename/delete/export and confirmations | Save/close/reopen/restore |
| Settings | Stock Sound, Game, Graphics, key bindings, data and language | Change/reload values; browser-safe I/O |
| Custom maps | Vanilla map browser, modes and applicable rules | Play and reopen chosen map |
| Map/logic editor | Local browser-safe editing and import/export | Save edited map/processor |
| Networking/mods | Functional implementations **or explicit documented browser limitations** | Dedicated multiplayer/mod compatibility gates |
| Portal | SDK lifecycle, ad pause, no external resource dependency | Chrome and actual Yandex validation |
| Size/performance | <=100,000,000 bytes unpacked and playable desktop/touch | Deterministic ZIP, detailed budget, runtime tests |

## Build sequence / stop-the-line gates

1. `bash scripts/bootstrap.sh` then `bash scripts/apply-port.sh` and the existing pinned overlay steps from `.github/workflows/web-port-baseline.yml`.
2. Compile original Mindustry and Arc, then `work/Arc/gradlew -p web-runtime generateJavaScript stageWeb`.
3. **Immediately** run `python3 scripts/yandex_size_budget.py`; read the size report and JSON file in `work/release/`.
4. Run `bash scripts/audit-yandex-release.sh` and all existing real Chrome desktop/mobile interaction checks.
5. Package using `python3 scripts/package-yandex-release.py`, verify the exact ZIP and repeat the cold-start tests.
6. Merge only after full parity claims are supported by visible interaction evidence. CI infrastructure alone cannot certify parity or current release size before a build completes.

## Optimization order if size budget is exceeded

1. Remove `*.map` and other debug-only artifacts (already done in baseline).
2. Audit duplicate/unused staged files using the generated category report.
3. Losslessly recompress PNGs, optimize audio encoding while preserving audible quality, and confirm maps still load.
4. Verify original UI assets use the **existing sprite atlas/fonts**, rather than bundling duplicate packs.
5. Strip genuinely unreachable code through TeaVM; never delete player-facing UI or core mechanics simply to hit an arbitrary JS-only target.
6. Re-test runtime memory, decode latency and p95 frame time after asset changes.

**Current state:** branch setup and conservative release-size guard are initial engineering work, *not* a completed full-vanilla UI re-port. All functional checkboxes above remain acceptance requirements until tested.
