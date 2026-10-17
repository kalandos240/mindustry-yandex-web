# Original UI parity — pinned Mindustry v159.7

The target is the original UI, not a separate web redesign. This branch is a
work in progress and is not a claim of full visual or behavioral parity.

| Surface | Current implementation | Work remaining |
| --- | --- | --- |
| Main menu scenery | Original `MenuRenderer`: generated tiles, cached walls/shadows, flying units | Desktop/mobile screenshots reviewed; 92,397,953-byte package verified |
| Main menu logo/version | Original `MenuFragment` drawing geometry | Verify portrait, landscape and reduced portal viewports |
| Mobile menu widgets | Original `MobileButton`, responsive grid | Restore original navigation grouping as supported screens are ported |
| Desktop menu | Stock styles; custom list of browser-supported actions | Replace list with original `MenuFragment` structure and animated submenus |
| Play / Custom Game | Browser selectors and original gameplay entry points | Port original selection dialogs and layout |
| Planets | Original `PlanetDialog` with browser launch boundary | Procedural sectors, launch/loadout parity, regression tests on the portal |
| Database | Original `DatabaseDialog` directly, with original search/tabs/categories/details | Native open/close verified in menu and live campaigns on desktop/mobile |
| Settings / language / keybinds | Original dialogs with browser compatibility patches | Review browser-specific omissions and pixel parity |
| Schematics | Original `SchematicsDialog` | Full import/export and all native controls regression |
| Saved games | Original `LoadDialog` subclass; browser persistence | Restore remaining original load/save flow and verify old saves |
| Research | Custom `BrowserResearchUi` | Replace with original `ResearchDialog`, preserving actual purchases and tutorial behavior |
| HUD | Original input/build/config widgets plus browser HUD additions | Replace custom overlays with original fragments |
| Pause / Game Over | Custom native Arc tables | Port original dialogs and transitions |
| Editor / mods / multiplayer | Not exposed by this single-player port | Require separate runtime support before restoring working entries |

## Verification

The menu acceptance test requires that the actual original background rendered
and continues advancing; it then clicks supported native controls and verifies
live game frames. CI saves final desktop/mobile screenshots for visual review.
A source-code audit alone is not a visual parity test.

Campaign regression tests must remain green while restoring UI. The two eager
start maps must remain playable after an unrelated deferred map fails. A new
sector's map must be ready before the active world is reset. Failed loads must
preserve existing saves. The final unpacked Yandex package must remain below
100 MB.

## Stabilization evidence (2026-10-17)

Full logs from 39058868543 invalidated the preliminary step-status summary:
continue-on-error normalized conclusions cannot establish a passing browser test.
The build is not certified. Remaining failures include building configuration,
wave HUD, native-modal pause integration and other full-suite gates.

The archived-build replay retained the exact 3D crash stack:
PlanetDialog.renderOverProjections -> findLauncher ->
PlanetGenerator.findLaunchCandidate -> preset.generator.map (null).
FileMapGenerator was constructed before the browser Maps catalog existed.
The production fix binds ready preset maps before original launcher selection,
waits asynchronously for missing selected maps and preserves saved-sector resume.
A controlled replay with that map binding passed sustained globe rendering,
Serpulo/Erekir switching and Research navigation on desktop and mobile.
Fresh package acceptance run 39064432270 passed those paths without the diagnostic adapter, plus actual native menu/globe launches into both worlds, HUD map cross-planet launch and Research/Database close in live campaigns. The checksum-verified package was built from d720693. A separate actual-outcome gate checks every continued test step.

Actual screenshot review of the archived build found legacy HTML Settings/mode
buttons over the original menu and a debug status paragraph across the bottom.
The native-only cleanup removes those DOM controls, adds an Arc mode selector to
Play and hides ready-state debug text. Fresh screenshots confirm those overlays are absent. Follow-up desktop spacing/highlight cleanup from cb8c8c9 compiled and passed desktop/mobile menu and native Settings interaction tests in run 39064596753; screenshot review confirms version text no longer overlaps Play.

The wave-HUD diagnostic stack from run 39065145258 reached
BrowserCampaignRuntime.maybeStartTestSector before any HUD test: its late-sector
URLs omitted mindustryCampaignMapMatrix=1. The corrected verifier must launch
the real survival maps and still require a trusted native HUD click, original
Logic.runWave progression and a continuing game loop. This fixture correction
alone does not certify wave gameplay. Sorter configuration remains under diagnosis.

## Current input and font stabilization

The native Sorter trace shows a real pointerdown targeting its copper Image,
followed by listener release coordinates around -1.4 million while the popup
shrinks. Scene's cached hover is updated in UI.act(), after Control polls world
input. The Web overlay queries the current stage hit for getHoverElement()
without changing act()'s enter/exit cache or advancing actors twice. The real
configure/clear browser test remains the acceptance gate.

Icon.load() creates every Small icon from Fonts.def, while the browser baker
previously collected only bundle characters. Include all 138 Iconc symbols in
baked UI fonts and validate every glyph in default, outline, icon and iconLarge
at browser startup. Both original source fonts contain all selected symbols.

The wave button now uses the original right-edge textures, disabled/hover states,
actor name and guarded hotkey. The remainder of the mission panel is still the
Web implementation; full HudFragment parity is not claimed.

Fresh unmodified package acceptance 39066015250 (d1b2435) confirmed native wave
clicks on Frozen Forest/desktop and Intersect/mobile, full original icon glyph
startup, desktop Sorter configure/clear, both menu device layouts and both 3D
planet layouts. Its only failed gate was mobile Sorter copper visibility.
Unpacked size: 92,521,887 bytes, leaving 7,478,113 bytes below the portal limit.

The actual mobile failure screenshot shows PlacementFragment covering the
configuration popup: InputHandler.add() mounted its private config group before
the later placement HUD. Name that native group at construction and restore its
priority after HUD installation. It remains below the separate modal/pause scene
roots; mobile command controls keep their existing priority. Verify the new Java
build with the unchanged real copper-selection/clear acceptance, not only an
instrumented replay.

## Full-regression follow-up

Run 39066456393 loaded/ticked all 46 campaign maps and completed all six basic
factory outputs and all six T1/T2 assembler outputs using original mechanics.
Its actual remaining failures were an incorrect 10+ click requirement for the
eight-click local pause scenario, a 2.2-second assumption about the 64-frame CPU
sample on the globe, and obsolete visible-debug-text assertions in startup.
Keep those gates: validate the scenario's exact click count, completed native
planet draws plus the scheduler, and explicit WebGL/bootstrap evidence instead.

Register all 16 original map-filter constructors using the existing browser JSON
factory bridge. The opt-in real-Chrome test requires preserved polymorphic types,
inherited seeds, nondefault Scatter/Ore/Mirror parameters and save/read round-trip;
it also boots both actual campaigns and rejects serialization/icon warnings.
Fresh compiled validation remains required before claiming this fix complete.

The constructor-only filter replay detected a lost inherited seed. The explicit
codec now covers all 60 original declared fields plus seed for every filter; its
generator is checked against the pinned source declarations in CI. Nondefault
resource-drop arrays and logic strings are included in the real-browser round-trip.

An additional UI discrepancy was found in Fonts.registerIcon: the desktop merges
font/UI atlas textures, whereas baked Web fonts keep separate PNGs. Inline
resource/content glyphs must use their atlas texture through matching font page
indices. Verify every registered glyph's UVs/texture and actual mixed text/icon
rendering in both normal and outline fonts before declaring the UI ready.

## Desktop submenu migration

Start replacing the crowded flat desktop list with MenuFragment's adjacent
Database submenu: original black6/flatToggleMenut styling, Icon.menu, 11-unit
label inset, selected parent, 0.15-second fade-in and 0.2-second fade-out.
Schematics, database, research and key bindings remain reachable with native
Scene input. Escape and selecting another action close the submenu, and hidden
children no longer advertise stale click coordinates. The six remaining browser
launch shortcuts and the combined Play selector still differ from original;
this is an incremental migration, not complete menu parity.

The unchanged full build at 12b9fe6 passed the 16-type filter JSON field/round-trip
gate on desktop/Ground Zero and mobile/Onset (run 39073865322). Decimal fixture
comparisons allow float rounding; block/item identity and integer fields are
exact. Menu migration validation must additionally exercise real parent clicks,
child hitboxes, toggle/Escape closing, native dialogs and research drag.

Visual QA of acceptance 39074767764 exposed a mid-word Schematics wrap on the
mobile portrait tiles. The attempted per-label font scaling regressed shared
font/icon rendering in compiled screenshot 39075464213 and was removed. Reduce
horizontal tile margins to give native labels their actual tile width, preserving
shared font/cache state. The corrected compiled screenshot remains the visual
gate; a computed width marker alone does not prove the displayed UI is correct.
