# Mindustry Yandex Games release checklist

Source of truth: current Yandex Games requirements. This checklist is intentionally stricter where the project requires zero external links and autonomous game-owned runtime.

## 1. SDK and platform lifecycle

- [x] Load the official SDK only from the relative `/sdk.js` path; never bundle `sdk.js` in the archive.
- [x] Run `YaGames.init()` before Mindustry platform-dependent startup.
- [x] Read automatic language from `ysdk.environment.i18n.lang`.
- [x] Support guest play without Yandex authorization.
- [x] Keep authorization optional and available only after an explicit user action if it is added later.
- [x] Call `LoadingAPI.ready()` once, only when the runtime reports the game ready for interaction.
- [x] Map real Mindustry playing/not-playing transitions to `GameplayAPI.start()` / `GameplayAPI.stop()`.
- [x] Handle `game_api_pause` / `game_api_resume` without advancing the game while paused.
- [ ] Pause/resume the final audio backend on platform pause/resume and full-screen ads.
- [x] Provide Yandex Player bridge for future compact cloud profile/statistics.
- [x] Provide Yandex fullscreen-ad bridge.
- [ ] Add final logical interstitial placement after the complete menu/game flow exists.
- [ ] If rewarded video is ever added, make it strictly optional and clearly describe the reward before showing the ad.
- [ ] Do not add external purchases; if in-app purchases are added later, use only the Yandex SDK and console catalog.

## 2. Network and links

- [x] Zero direct `Core.app.openURI(...)` calls remain in patched user-facing UI/editor code.
- [x] `BrowserApplication.openURI()` always refuses navigation.
- [x] Remove upstream Discord/GitHub/wiki/store/workshop/mod-browser/sector-submission link entry points.
- [x] Remove upstream external URL constants reachable by the Web build.
- [x] Final `mindustry.js`, HTML, localization, BMFont and manifest contain no absolute HTTP(S)/WS(S) URL literals.
- [x] Game assets are packaged and loaded from the archive/same origin only.
- [x] Web game networking provider exposes no third-party remote transport.
- [x] On Yandex, do not break official SDK transport needed by Yandex Player/ads; game-owned code remains URL-free/offline.
- [x] Off-platform development uses a same-origin browser-network guard.
- [x] No external registration or authorization service.

## 3. Localization

- [x] Ship exactly English and Russian in the current release.
- [x] EN and RU have the same 2896 keys.
- [x] No missing or blank EN/RU values.
- [x] Fix known untranslated Russian upstream strings in the local Yandex overlay.
- [x] Load locale before Mindustry creates base content so names/descriptions are localized.
- [x] Remove URLs/e-mail contacts from visible localized text.
- [x] Keep only technical identical values (`???`, `IP`, `ID`) after EN/RU comparison.
- [ ] Perform final on-screen proofreading after all dialogs/menu screens are restored.
- [ ] Make the language-selection route understandable without knowing the current language (English / Русский labels or universal icon).

## 4. Persistent progress

- [x] Existing small settings are persisted in browser storage.
- [x] IndexedDB-backed binary `FileType.local` foundation exists for saves/maps/schematics.
- [ ] Cross-reload browser + TeaVM byte recovery CI must be green.
- [ ] Initialize real Mindustry save directories and `Saves` subsystem against persistent browser files.
- [ ] Verify campaign progress and manual saves survive reload, orientation change and browser restart.
- [ ] Verify save immediately after meaningful progress or explicit save action.
- [ ] Keep large world saves local; do not put them into the 200 KB Yandex Player data limit.
- [ ] Add compact Yandex Player cloud profile/statistics only where it fits SDK limits and is useful cross-device.
- [ ] If cloud saving is enabled, enable the matching option in the Yandex draft.

## 5. Mobile and responsive UX

- [x] Full-viewport canvas; no page scrollbar.
- [x] Disable swipe-to-refresh/overscroll.
- [x] Disable selection/context menu/long-tap browser UI inside the game field.
- [x] Canvas backing resolution follows display size and device pixel ratio.
- [x] Browser resize is handled continuously.
- [x] No system HTML audio/video player.
- [x] Web build does not show desktop cursor assets.
- [ ] Complete touch-first Mindustry gameplay controls and verify one-hand usability where practical for a strategy game.
- [ ] Verify every dialog and HUD element after `UI.loadSync()` on portrait/landscape phone and tablet sizes.
- [ ] Verify input fields summon the software keyboard correctly on mobile.
- [ ] Verify no browser/WebGL warning UI is shown to the player.
- [ ] Do not claim Android TV support until arrows/OK/Back alone can complete the game.

## 6. Desktop UX

- [x] Canvas fills the available browser area.
- [x] Keyboard/mouse browser backend exists.
- [x] Page selection/context menu is blocked inside the game field.
- [ ] Verify controls do not depend on keyboard layout.
- [ ] Audit OS/browser-reserved keyboard shortcuts after full controls are restored.
- [ ] Remove any useless desktop-only controls from the final Yandex menu (for example an Exit button if it adds no value in-browser).

## 7. Runtime stability

- [x] TeaVM compile is CI-gated.
- [x] Headless Chrome smoke covers core/content/atlas/fonts/input/settings/UI shell.
- [x] URL-free package audit is CI-gated.
- [x] EN/RU browser smoke is CI-gated.
- [x] Yandex release static audit is CI-gated in the Yandex integration branch.
- [ ] Yandex SDK lifecycle browser smoke must be fully green including resume.
- [ ] Restore and CI-gate `UI.loadSync()` (`Scene`, `Tex`, `Icon`, `Styles`).
- [ ] Restore complete menu/dialog flow.
- [ ] Add final browser audio backend and focus/ad pause behavior.
- [ ] Initialize actual campaign/save/load loop and stress test it.
- [ ] Test window resize, orientation changes, long tap, minimize/background, history navigation and ad open/close.
- [ ] Final DevTools console audit: no runtime errors.
- [ ] Manual browser matrix on selected platforms: Yandex Browser, Chrome, Firefox, Opera, Safari, Yandex mobile app.

## 8. Archive

- [x] `index.html` at archive root.
- [x] Unpacked-size CI limit is 100 MiB; current smoke package is well below it.
- [x] No spaces or non-ASCII/Cyrillic characters in archive paths.
- [x] Do not package `sdk.js`.
- [x] Do not stage TeaVM `.map` source maps in the moderation package.
- [x] `package-yandex.sh` creates a root-correct validated ZIP.
- [ ] Produce the final ZIP only after all gameplay/UI/audio/save checks are green.

## 9. Monetization

- [x] Only Yandex SDK advertising API is exposed by the platform bridge.
- [x] No third-party advertising code/assets.
- [ ] Enable monetization in the Yandex draft.
- [ ] Select a logical interstitial point that never interrupts active gameplay.
- [ ] Confirm progress survives ad click/return.
- [ ] Confirm full-screen advertising pauses game and audio.
- [ ] Confirm ad orientation matches the game orientation.

## 10. Content, rights and moderation metadata

- [ ] Re-check Mindustry/Arc licenses, notices and redistribution obligations in the final archive/repository release package.
- [ ] Audit final game content for Yandex content restrictions and set the correct age tag.
- [ ] Ensure the game is presented as a finished product, not a development/test build.
- [ ] Add complete control instructions in the game and Yandex draft.
- [ ] Select the correct genre/categories/tags.
- [ ] Make game title identical between the game and every selected-language draft/promotional asset.
- [ ] Fill required draft fields: title, about, how to play, version, categories, icon, cover, archive, supported platforms, orientation.
- [ ] Create a dedicated icon and cover; do not use raw gameplay screenshots as icon/cover.
- [ ] Add real-gameplay screenshots; gameplay should dominate the image.
- [ ] Proofread Russian and English draft text for spelling/punctuation and accuracy.

## Release gate

Do not submit to moderation until every applicable unchecked mandatory item above is resolved. Recommended items may remain only when there is a deliberate documented reason.
