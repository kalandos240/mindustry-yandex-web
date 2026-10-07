# mindustry-yandex-web

Browser port workspace for Mindustry, targeting a standalone Web build first and Yandex Games integration second.

## Status

**Playable Web/Yandex port with complete pinned Serpulo + Erekir campaign coverage.** The browser target boots the pinned Mindustry v159.7 core through TeaVM, runs the stock renderer/game loop in permanent single-player mode, supports desktop and touch-first mobile input, browser-persistent saves, English/Russian localization, packaged audio/assets, Yandex lifecycle integration, and compact campaign progression for both planets.

The verified browser Serpulo campaign now covers every sector wired into the stock Serpulo TechTree, including the full path to **Planetary Launch Terminal** plus the optional Tainted Woods, Atolls, Testing Grounds, Sunken Pier and Weathered Channels branches. High-tier prerequisites remain real TechTree purchases and produced-resource objectives: Spore Pod discovery for Tainted Woods, Poly → Mega for Atolls, Water Extractor for Testing Grounds, and Surge Smelter plus Mend Projector → Force Projector → Overdrive Projector for Weathered Channels. Wave sectors use their stock capture waves; attack sectors are verified through the no-enemy-core victory predicate rather than forced capture flags. Desktop and auto-detected mobile progression smoke now finish on captured Weathered Channels after traversing the complete Serpulo TechTree sector set.

Erekir browser progression now reaches and captures **Origin** through the full stock Onset → Aegis → Lake → Intersect → Atlas → Split/Basin → Marsh → Peaks/Ravine → Caldera → Stronghold → Crevice → Siege → Crossroads → Karst → Origin route. The runtime validates map-specific mission objectives instead of copying Serpulo capture rules: Onset preserves its 20-step tutorial/openMap gate, Intersect performs wave 9 before switching to attack mode, Basin preserves its nuclear objective flags, Stronghold validates its timed chain and two Core Bastion targets, Crevice captures on wave 46, Siege/Crossroads wait for their scenario flags, and Karst captures on wave 10. The pinned Origin map is decoded and validated as five sequential Timer objectives with the exact u1 → u2 → u3 → u4 → u5 flag chain and durations 36000/72000/108000/108000/72000 ticks before stock attack capture. The final research gate preserves Core Acropolis, assembler unit branches, Malign, Basic Assembler Module and Neoplasia Reactor prerequisites. Desktop and auto-detected mobile progression smoke both finish on captured Origin.

The release pipeline is now in pre-publication stabilization: TeaVM source maps are excluded from the staged Yandex package, mobile viewport tests cover portrait → landscape → portrait → landscape re-entry and assert that the real canvas fills the viewport, and fullscreen-ad pause/resume tests require the same geometry after gameplay resumes. Local survival gameplay now has a dedicated desktop/mobile gate for pause clock freezing, manual SaveIO persistence, autosave on return to the map selector, cold Continue from the same browser profile and a second autosave after resumed play. CI builds a deterministic `mindustry-yandex-release.zip`, emits a SHA-256 sidecar/report, unpacks that exact ZIP into a clean directory and boots it again in desktop and mobile Chrome. The archive also carries the pinned Mindustry GPL-3.0 and Arc Apache-2.0 license texts, source notice and `upstream.lock`. These are release gates; publication readiness still depends on a successful full TeaVM/Chrome workflow run.

Custom Game now preserves the full proven local survival loop on the 18 stock built-in maps: stock wave timer/spawning, local core-loss Game Over, Pause/Resume with a frozen simulation clock, current-v13 Save/Continue across a cold browser restart, and automatic save before a normal Back-to-menu action. Valid built-in multi-team maps also expose stock local Attack mode without enabling multiplayer/PvP transport; Attack saves restore their saved attackMode through a completely new browser process on both desktop and auto-detected mobile. The Attack selector uses the stock localized `mode.attack.name` bundle key, and CI requires distinct English/Russian labels. Desktop and mobile persistence tests verify the restored map/mode after restart. A separate production load smoke runs 64 vanilla units for 120 complete Logic → pathfinding → Control → Renderer → UI frames and records elapsed-time/FPS telemetry; its threshold is only a catastrophic-regression guard, not a claim of constant 60 FPS.

## Yandex publication checklist

The Web package contains the Yandex SDK lifecycle, cloud saves and ad call sites, but Developer Console configuration is still required before publication:

- Enable YAN monetization for the game.
- Keep fullscreen interstitials enabled; the port requests them only on a logical Back-to-menu transition after save/storage/cloud durability barriers.
- Configure sticky banners for the intended desktop/mobile orientations and enable **Use the API to display a sticky-banner**. The runtime shows the banner only in menu/ready state and hides it while `GameplayAPI` is active.
- Do not add third-party advertising. All ad calls in the packaged build go through the Yandex Games SDK.
- Test the final uploaded build in Yandex's environment as well as the deterministic local release-ZIP smoke; `/sdk.js` is intentionally loaded from the platform and is not bundled into the archive.

If API-managed sticky banners are not enabled in the Developer Console, the runtime degrades safely: the banner API reports unavailable/not connected and gameplay continues without a sticky banner.

## Upstream baseline

- Mindustry: v8 Build 159.7 (`c9686eb5d0ae5dd47ee02c40f99f7d5018ccbc8c`)
- Arc: `c38f8f5ff27f47a5886d0903aadeba42e4302411`
- Historical Arc Web/GWT reference point: parent of the GWT-removal commit, `2303ab81bb76a973db8885f3ba14b6515782a1a4`

The exact revisions live in `upstream.lock` so CI and local builds use the same sources.

## Repository model

This repository is a **port overlay**, not a vendored copy of all upstream source files. `scripts/bootstrap.sh` checks out the pinned Mindustry and Arc revisions into `work/`, after which Web-specific overlays and patches can be applied. This keeps the port reviewable and makes upstream rebases explicit.

## Port stages

1. Reproducible upstream checkout and compatibility audit.
2. Restore/adapt Arc browser primitives: application loop, WebGL, input, files, clipboard and networking.
3. Browser audio and persistent storage.
4. Compile Mindustry core against the browser backend and reach the main menu.
5. Gameplay compatibility, save/import/export, performance and memory tuning.
6. Yandex Games SDK, lifecycle, saves/leaderboards/ads where appropriate, moderation packaging.

## Licensing

Mindustry is GPL-3.0 licensed. Changes derived from Mindustry must remain compatible with GPL-3.0 obligations. Arc is Apache-2.0 licensed. Keep notices and corresponding source available for distributed Web builds.
