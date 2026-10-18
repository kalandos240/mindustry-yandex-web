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

## Original desktop Play grouping

The desktop sidebar now exposes only the supported original Play, Database and
Settings parents. Play opens adjacent Campaign, Custom Game and Load Game rows;
Campaign enters the original 3D PlanetDialog. Custom Game retains the validated
browser selector and direct campaign recovery paths. Mobile navigation remains
unchanged. A single submenu switches between Play and Database, using original
styles/fades and clearing inactive hitboxes. Five adaptive row slots keep both
submenus inside the minimum portal frame.

Update actual input tests for nested campaign/saves/custom entries, Play toggle
and Escape, both locale Database visits, and direct-campaign recovery via Custom
Game. Fresh Java/TeaVM and native browser validation are still required. The
previous c553908 package passed focused run 39076024060 and screenshot proof
39076254461 at 92,684,526 unpacked bytes; those results do not certify this change.

The adjacent columns also need to fit when DevTools or portal furniture narrows
the desktop canvas. Two fixed 230-unit columns exceed a 390-pixel surface;
a visible center does not prove a complete button is visible. Cap both column
widths to the actual available scene width and publish/check their full projected
bounds. A permanent Russian native-input gate shrinks/translates the portal
surface to half width, exercises every menu action, and captures the open
Database submenu. Its failure blocks release alongside the normal UI gates.

The first narrow test exposed stale desktop coordinates after a container-only
resize: Arc Scene follows its own resize listener, while the menu had listened
only for Mindustry ResizeEvent. Rebuild on actual Scene dimension changes at the
existing 16-frame menu sampling cadence, clear old hitboxes, and require the
menu-size marker before the narrow input test begins. Explicit JSBody rectangle
assignments also avoid the TeaVM parser warnings from object shorthand.

Applied-source inspection found the missing global lifecycle hook: Bootstrap's
ApplicationListener forwarded setup/update but never resize to the stock UI.
Forward real logical resizes to UI.resize (margins, Scene viewport, ResizeEvent).
BrowserApplication still filters backing-DPR-only adjustments, avoiding actor
rebuilds during adaptive pixel-ratio changes. Validate menu, world HUD, portal
bounds and orientation with the actual resized Scene before delivering a ZIP.


## Original custom-game dialogs

The final Bootstrap resize hook passed focused acceptance 39079805483: normal
and narrow Russian menu input, native 3D navigation, desktop/mobile orientation
and portal-constrained HUD input. The candidate used 92,703,199 unpacked bytes.

CustomGameDialog, MapPlayDialog and CustomRulesDialog now use the pinned stock
widgets. The existing combined selector retains campaign recovery and save
entry points, with a visible button opening the original map catalog; removing
that temporary wrapper is still outstanding. Local map launching goes through
BrowserLocalMapRuntime, preserving the edited Rules instead of reapplying a
preset and discarding user changes. Online PvP remains unavailable.

WebFontBaker also runs the original MapIO.generatePreview decoder on the build
JVM for exactly Maps.defaultMapNames. It packages original PNG previews and the
stock cache fields (enemy spawn count and core teams), so stock survival/attack
validation uses map data. The browser caches textures on first catalog open;
world loading does not decode 18 previews on an input frame. The original color
picker is initialized for the ambient-light control.

verify-browser-custom-game.sh is a mandatory release gate. It uses trusted Chrome
clicks on actual Scene actors on desktop/mobile, verifies all 18 textures, opens
a map, selects Survival, edits the wave timer in the stock rules dialog, then
starts the map and checks both customized rules and a continuing game heartbeat.
The narrow Russian case uses real wheel input to reach Archipelago after Ancient
Caldera in the original alphabetical catalog. It requires the entire selected
card and every action button to fit, rather than accepting a visible center.

The baked catalog contains 18 maps, including 15 survival-capable maps and three
attack maps (Veins, Glacier and Passage). Native launching validates core teams
before resetting the world, showing the original localized missing-core message
for invalid customized rules instead of leaving a broken session.

MapPlayDialog fits the actual Scene bounds and scrolls its body while keeping
Back/Play reachable. It also refits when resized underneath CustomRulesDialog,
preserving the edited Rules. Rule checkboxes wrap within the available width on
phones. The build now rejects TeaVM JSBody parse errors even when the compiler
would otherwise return success.

Exact-ZIP acceptance: https://github.com/kalandos240/mindustry-yandex-web/actions/runs/39085530684
The unchanged compiled runtime occupies 93,672,298 unpacked bytes (6,327,702 bytes
below the portal limit). This focused acceptance covers the original custom-game
flow on desktop, mobile and a translated 390-pixel-wide portal, customized rules
surviving a covered-dialog resize and live launch, native dialogs/menu input,
3D planets, orientation/resize continuity and translated portal HUD input.
Nine native catalog/map/rules screenshots accompany the run. The broader full
game matrix previously passed on PR 166; it has not been recertified on this head.

This is still a draft UI migration. The temporary combined Play/recovery wrapper,
remaining HUD/Research dialogs and final whole-interface 1:1 comparison remain
outstanding; this change does not claim complete original UI parity.

## Direct original menu navigation

The next change removes the combined Play/recovery panel from player navigation.
Desktop Play > Custom Game opens the stock catalog directly. Mobile uses the
original Campaign caption/action for its play icon and an independent Custom
Game icon; Campaign opens PlanetDialog and Custom Game opens CustomGameDialog.
Load Game still opens the original save dialog. The hidden old panel remains
only as an overlay-script compatibility anchor, with no menu route to open it.

Real campaign acceptance now launches both planets through the original sector
Launch button. The two historical `direct-*` scenario names retain the unrelated
deferred-map failure-status marker case, but no longer depend on removed recovery buttons.
Navigation checks require native custom-dialog open/close/reopen and keep full
actor bounds; mobile Campaign needs one click and desktop Campaign needs two.
Fresh compiled runtime: 93,670,150 unpacked bytes, 6,329,850 bytes below the
100,000,000-byte limit. Exact-runtime gameplay/visual evidence is recorded in run 39086614356:
https://github.com/kalandos240/mindustry-yandex-web/actions/runs/39086614356
It covers the direct native catalog/rules/live launch on three surface sizes,
menu bounds and repeated modal visits, original 3D planets and four actual sector
launches, orientation/resize and translated portal HUD input. New menu/catalog
screenshots confirm the intermediate list is no longer visible behind dialogs.
That run's mobile interaction succeeded but its shell postcondition required at
least ten clicks after the shorter navigation needed nine. Add a second native
Save dialog visit, retaining the existing click-count/bounds/liveness checks.
Final acceptance run 39087430671 verifies every prior gameplay/visual step and
the five fresh narrow launches before rerunning desktop/mobile and narrow menu
input with the Save reopen, then delivering the unchanged ZIP:
https://github.com/kalandos240/mindustry-yandex-web/actions/runs/39087430671

The first compiled run had one narrow live-loop timeout. It did not reproduce
in the exact-ZIP acceptance, an isolated narrow run, or five fresh narrow profiles
in run 39086742263. Its root cause is not established. Keep this as an open
intermittent diagnostic item rather than claiming a confirmed runtime fix. The
Chrome harness now records capped state markers and a failure screenshot when
native interaction throws. The broader full game matrix on this head is pending.

Visual follow-up: in the 390-by-371 desktop game surface, the minimap, wave HUD
and build palette compete for space. The navigation fix does not resolve this
remaining HUD layout work; a passing menu/button gate is not full visual parity.

## Compact original HUD and placement controls

Compact layout follows the actual Arc Scene width/height, including an embedded
portal surface resized independently of the browser window. The original Minimap,
CoreItemsDisplay, health Bars and PlacementFragment keep their native rendering,
values and input handlers. Compact scenes reserve separate regions for resources,
waves, minimap, player health, guardian health and the build palette.

Resource rows reflow to the available width and scroll vertically. The viewport
fits a complete original item row; additional resources remain accessible. The
wave/mission text also scrolls without replacing the original wave control.
The original placement actor graph scales to the remaining Scene bounds. Its
information/cost panel has a fixed compact height and scrolls independently;
its scrollbar cannot increase the palette width and move categories under the
pointer. Child tables explicitly invalidate their cached layouts after resize.

Native desktop Research/Database actions now bind to the existing browser dialogs
at construction, including category rebuilds. Previously, clicking before the
periodic annotation/rebind pass could call an absent desktop dialog and stop the
frame loop. The live planet test also refreshes native button coordinates until
the dialog opens, after the initial responsive layout has settled.

The new browser gate launches a real Archipelago world, fills the real core with
every item and spawns a real unit with the boss status, only when its diagnostic
query is requested. It checks native actor bounds and pairwise overlap, real
category/conveyor input, stable category position after selection, portal resize,
full minimap open/Escape and continued simulation on desktop, mobile and a
320-pixel-wide surface. The normal game does not enable these world fixtures.

The latest compiled candidate occupies 93,737,977 unpacked bytes, leaving
6,262,023 bytes below the 100,000,000-byte portal limit. Focused acceptance:
https://github.com/kalandos240/mindustry-yandex-web/actions/runs/39092141103
This focused run passed the three-surface HUD bounds/input/resize checks, native
wave controls and actual enemy spawning, live Erekir HUD map -> Serpulo selector
-> original sector Launch -> playable Ground Zero, desktop/mobile Sorter
configuration, original custom catalog and edited-rule launches on three
surfaces, repeated native menu/dialog input, orientation continuity and translated
portal bounds. The unchanged ZIP also passed five fresh native cross-planet
transitions after the previous candidate failed five early-click attempts:
https://github.com/kalandos240/mindustry-yandex-web/actions/runs/39092523619
Playable experimental artifact:
https://github.com/kalandos240/mindustry-yandex-web/actions/runs/39092141103/artifacts/12123635285
The three final HUD screenshots were visually reviewed, including the
320-pixel-wide portal surface. The full 46-map/gameplay matrix has not been
recertified on this head. Full original HUD/Research dialog
migration and final whole-interface 1:1 comparison remain unfinished. The earlier
intermittent narrow live-loop timeout still has no established root cause.
