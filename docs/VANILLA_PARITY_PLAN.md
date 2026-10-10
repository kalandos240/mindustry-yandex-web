# Mindustry — Vanilla Feature Parity Acceptance Plan

**Target:** The pinned Mindustry v159.7 vanilla game experience in a Yandex Games browser, not a small themed reimplementation. A passing TeaVM compile or synthetic campaign smoke is **not** sufficient evidence of feature parity. Never label a build “full Mindustry” until the applicable player-facing milestones below are proven.

## Milestones (ordered by practical player value)

| Area | Expected parity | Acceptance / gate | Status |
|---|---|---|---|
| Startup, assets and rendering | Original graphics, fonts, audio, block sprites, effects and input scaling | Real desktop + touch Chrome run on the actual Yandex game viewport, no broken atlas or blank HUD | Partial |
| In-game HUD essentials | Original corner minimap/full-map, core inventory, wave count and mission objectives | Real desktop/touch map with nonempty minimap texture, player health, boss bar, all concurrent objectives, and live wave updates | PR #123 minimap/items/waves; stacked follow-up restores native player/boss `Bar`, coordinates, all qualified objectives, and wave-timer visibility. Full browser validation pending; full stock HUD still incomplete |
| Construction HUD | Vanilla `PlacementFragment` block sprites, category icons, costs, placement, rotations, context configuration | Open real map; select conveyor, drag-build conveyors, rotate, place drill, configure block; repeat on touch | Native placement active; select/place/rotate/removal and mobile tap passed CI; drag drill/config still unverified |
| Research | Full TechTree per planet, resource costs, parent requirements, objectives and saved unlocks | In ordinary gameplay mine copper, purchase Conveyor and Mechanical Drill, unlock Junction and Router, build them, reload | PR #120 experimental; confirm live interaction |
| Campaign navigation | Stock Serpulo/Erekir sector selection, planet progression, capture, launch and objectives | Traverse Ground Zero → Frozen Forest and Onset → Aegis manually; no CI-only staging bypass | Partial |
| Survival and attack | Original wave timers, hostile AI, combat, cores, victory/loss, commands, logistics | Play through waves, lose/win normally, select groups and issue RTS move/stance orders on desktop/mobile | Stock `PlacementFragment` command panel and desktop toggle present in #125; new CI-only browser probe enters stock RTS via DOM ShiftLeft, selects a real allied Dagger with DOM KeyG and verifies a right-click reaches stock `CommandAI` through `Call.commandUnits`. separate two-Dagger DOM Shift+G / single-right-click test checks both CommandAI targets (#128). CI pending; rectangular group selection, stances, mobile touch orders and complex pathfinding still require direct verification |
| Save & resume | Vanilla MSAV state, sector/tech progress, cold restart, browser storage and cloud restore | Save, close browser, resume with buildings, inventory, unlocks, fog, unit orders and current wave intact | Partial |
| Player tools | Schematics, logic processor editor, settings, keybinds, content encyclopedia, pause map, camera, minimap | Open all vanilla local interfaces and complete their normal user actions on desktop/touch | Lean sound/effects/graphics settings available in menu and paused gameplay (new follow-up); stock settings, schematics, logic editor and content/database parity still missing |
| Advanced modes | Vanilla sandbox/custom maps, map editor, mods and content importing where browser policy permits | Test each mode end-to-end and document remaining Yandex-specific incompatibilities | Partial |
| Multiplayer | Host/join/connect and network gameplay as in original game, using browser-compatible transport and permitted server infrastructure | Real two-client multiplayer end-to-end, or clearly mark as unavailable; cannot call that full feature parity | Not implemented |
| Portal integration | Ads only via Yandex SDK, game area never overlaps portal ad area, pause lifecycle, localization, <100 MiB unpacked | Live Yandex environment, desktop+mobile, no blocked resources/external links, proper ad events | Partial |
| Performance | Practical 60 FPS target without game mechanic deletions | Repeated identical baseline + candidate loads on real hardware; report p50/p95 frame time, CPU/GPU and FPS | Unproven |

## Non-negotiable human playthrough checks

1. **New Serpulo campaign:** Launch Ground Zero with a clean profile; identify the actual copper resources and core inventory, and follow the stock tutorial.
2. **Research/construction:** Research Conveyor and Mechanical Drill through visible, clickable buttons with the correct resource requirements. Confirm their icons appear in the vanilla construction HUD. Drag to lay a conveyor chain, rotate, place and build a Mechanical Drill, feed the core, and check the stock costs.
3. **Progress:** Research Junction/Router when resources and prerequisites permit, survive ordinary enemy waves, capture the sector without dev/smoke shortcuts, and view the next sector on the map.
4. **Cold resume:** Save, close the browser tab, reopen and verify structures, items, tech progress, wave, unit state and pause/continue UI.
5. **Touch parity:** Repeat selecting, dragging, rotating, configuring, research, scrolling, and saving using touch emulation **and a real mobile device**.
6. **Erekir:** Play Onset, research duct/condenser/borer technology, produce the required items, command units and advance to Aegis.
7. **Broader gameplay:** Exercise RTS controls, schematics, logic, settings, fog, weather, audio and local attack/sandbox maps.
8. **Yandex live placement:** Check both menus and active gameplay with the SDK ad slot present or empty, without canvas overlap, and verify SDK ad show/hide lifecycle.

## Engineering safeguards

- Prefer pinned upstream UI/gameplay classes over independent HTML/text substitutes; patch only verified browser incompatibilities.
- Keep all work behind branches/PRs until both unmodified core and full TeaVM+production Chrome CI are successful.
- Browser markers are helpful diagnostics but **do not substitute for actual clicks, research purchases and block placement**.
- The true Yandex package limit is 100 MiB unpacked; JS size and gzip deltas are tracked and budgeted separately. Never remove real gameplay solely to satisfy an obsolete artificial JS ceiling.
- Do not report steady 60 FPS or a completed full Steam-equivalent port on the strength of a headless CI smoke test.
- Steam-specific accounts, Workshop and achievements require their own platform integrations; identify unsupported platform services explicitly rather than silently calling them restored.
