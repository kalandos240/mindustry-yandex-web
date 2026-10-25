# Native control coverage and read-only inventory

`scripts/verify-ui-control-inventory.py` records the actual live Arc Scene actor
tree while navigating with trusted mouse, touch, wheel and Escape input. It does
not equate discovering a control with testing its function.

The runner inventories desktop 1280×800, narrow desktop 780×437 and mobile
390×844. It opens the three settings pages and native main-menu dialogs, scrolls
settings, checks that navigation remains in menu state, and records every
discovered control—including disabled and clipped controls—in
`work/ui-control-inventory/coverage.json` and per-profile JSON files. Setting
effect, save/import mutation, progression and destructive reset gates must attach
their own semantic assertions to these identities.

## Coverage levels

| Level | What is proved |
| --- | --- |
| Discovered | A live actor exists with its current label, setting/content identity and enabled/visible state. |
| Native navigation | Trusted input opens/closes the expected destination and the runtime remains healthy. |
| Semantic effect | Trusted input changes the actual corresponding renderer/game/audio/UI state, not just the stored preference. |
| Cold round trip | The relevant value/world survives a complete browser restart. |
| Traversal fixture | A campaign/map/objective path works with explicitly prepared test data; this is not normal-player completion. |
| Production gameplay | Actual playing state and advancing game tick/update ID are verified without gameplay smoke branches. |

A frame callback counter is not proof that the world is playing. The state probe
must distinguish playing, paused and menu, and observe actual tick/update ID for
gameplay transitions. Geometry eligibility is not proof that a pointer reaches
the intended actor: every functional test still needs trusted input and a
specific resulting-state assertion.

## Probe installation

The helpers are injected only into test copies of the generated bundle. Install
`prepare-qa-probe.py` first, then `prepare-ui-control-probe.py`. Restore in the
reverse order and compare the final `mindustry.js` hash with the pristine package.
The UI helper refuses restoration if a later fixture has not been removed.

No generated helper, backup file or altered bundle may be included in the game
archive. The normal `index.html` does not load any of these helpers.

## Read-only APIs

- `__mindustryUiControls(options)` returns live button, slider, text-field,
  scroll-pane and other pointer-listener actors. The default excludes disabled,
  off-screen and background-under-modal controls; `includeDisabled`,
  `includeOffscreen`, `includeBackground` and `includeHidden` opt in to inventory
  those states. Results include `name`, localized `label`, nearby labels,
  available icon names, exact content-icon descriptors, modal, setting key,
  enabled/visible state, CSS and stage rectangles, and fresh CSS input coordinates.
- `__mindustryUiActor(name)` reads a named actor's effective ancestor visibility,
  visible label text and rectangles. This supports actual FPS/cursor/minimap HUD
  effect assertions.
- `__mindustryUiText(options)` reads visible labels in the active modal (or scene),
  with optional off-screen labels. This supports content-detail and map-dialog
  assertions without invoking callbacks.
- `__mindustryUiDialogRead(path)` reads a scalar field path in the active modal,
  for example `rules.waveTimer`. Numeric components traverse an array or `Seq`.
- `__mindustryUiConfigRead(path)` reads the selected building only while its
  actual configuration table is visible, for example `currentPlan` or
  `block.plans.0.unit.name`.

Actor IDs stay stable for each live actor; rebuilding a dialog can create new
actors. Match by context plus setting key, content identity, actor name or label,
then fetch coordinates again after any resize, scroll or selection change.

The helper reads compiled fields and pure coordinate transforms. It never fires
listeners, changes a preference/focus/selection, configures a building, unlocks
content, or changes game state. Constructor-field suffixes are derived from the
actual generated runtime and checked before injection. `truncated` prevents a
bounded inventory walk from being reported as complete.

## Explicit remaining scope

Inventory/navigation output is not a claim that every dynamically generated
button has been semantically exercised. In particular, this runner does not
execute data deletion, research/campaign resets, language selection, key binding,
save/schematic import, individual content cards, factory plans or building
configuration changes. Those need dedicated isolated-profile tests and specific
observable outcomes. Planet sectors, map tiles and units are rendered interaction
surfaces rather than Arc buttons and require separate world/input gates.
Browser-native HTML text/file prompts are outside the Arc Scene. Their controls
must be exercised with DOM roles/selectors in the save, schematic and text-input
suites; they are not silently counted as covered by this actor inventory.
