# Original UI parity — pinned Mindustry v159.7

The target is the original UI, not a separate web redesign. This branch is a
work in progress and is not a claim of full visual or behavioral parity.

| Surface | Current implementation | Work remaining |
| --- | --- | --- |
| Main menu scenery | Original `MenuRenderer`: generated tiles, cached walls/shadows, flying units | Verify actual browser screenshots and size budget |
| Main menu logo/version | Original `MenuFragment` drawing geometry | Verify portrait, landscape and reduced portal viewports |
| Mobile menu widgets | Original `MobileButton`, responsive grid | Restore original navigation grouping as supported screens are ported |
| Desktop menu | Stock styles; custom list of browser-supported actions | Replace list with original `MenuFragment` structure and animated submenus |
| Play / Custom Game | Browser selectors and original gameplay entry points | Port original selection dialogs and layout |
| Planets | Original `PlanetDialog` with browser launch boundary | Procedural sectors, launch/loadout parity, regression tests on the portal |
| Database | Original `DatabaseDialog` directly, with original search/tabs/categories/details | Verify native Back and Escape in menu and live campaigns |
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
