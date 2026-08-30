# Web port baseline

## What upstream provides today

Current Mindustry has desktop, Android, iOS and server targets, but no browser module. Current Arc has Android, headless, RoboVM and SDL-family backends. Its former GWT backend was removed in December 2019.

The last Arc revision immediately before that removal is preserved in `upstream.lock` as `ARC_LEGACY_WEB_REF`. `scripts/bootstrap.sh` extracts `backends/backend-gwt` from that revision into `work/legacy-arc-web/` for migration reference.

## Why the legacy backend matters

The old backend already solved many Arc-specific browser problems once:

- application lifecycle and animation loop;
- WebGL binding;
- keyboard, pointer and touch input;
- browser file handles and asset preloading;
- clipboard and browser URL integration;
- browser HTTP requests;
- browser audio abstractions;
- Java/JRE emulation required by Arc code.

It cannot be dropped into 2026 Arc unchanged. Packages, APIs, graphics/audio internals and Java assumptions have moved substantially. We use it as an implementation map and port behavior into the current Arc API one subsystem at a time.

## Non-goals

- No iframe of somebody else's hosted build.
- No remote game runtime dependency.
- No desktop executable streamed through a server.
- No Yandex SDK work before a local static browser build can boot reliably.

The target is a self-contained browser game package whose game assets are served from the package itself. Platform SDK calls are added only at the integration layer.

## Port order

### M0 — Reproducible baseline

- [x] Pin current Mindustry and matching Arc revisions.
- [x] Recover the historical Arc GWT backend automatically.
- [x] Add baseline CI.
- [x] Add compatibility inventory generation.
- [ ] Capture the first CI report and classify blockers.

### M1 — Minimal Arc browser runtime

Create a current-package browser backend capable of launching a tiny Arc `ApplicationListener` and clearing/drawing a frame in a browser canvas.

Required pieces:

1. `Application`/lifecycle implementation and frame scheduling.
2. `Graphics` implementation backed by WebGL 2 where Arc's API allows it.
3. `Input` implementation for keyboard, mouse, wheel, pointer/touch and text input.
4. Browser-safe `Files`, `Fi`, clipboard and URL handling.
5. HTTP implementation using browser fetch/XHR semantics.

Audio and persistence may initially be stubbed only for this minimal runtime; they become mandatory before Mindustry boot.

### M2 — Arc parity needed by Mindustry

- Web Audio implementation for sound and music.
- Persistent local storage using IndexedDB/OPFS or an equivalent browser persistence layer exposed through Arc's file API.
- Pixmap/image decoding and upload path.
- Font/freetype replacement or browser-compatible build path.
- Thread/concurrency substitutions for browser execution.
- Reflection/JRE emulation required by game code.

### M3 — Mindustry boot

Add a Web launcher/module, compile generated Mindustry sources for the browser target, load bundled assets, and reach the main menu without desktop/native code paths.

### M4 — Playable game

Validate campaign/custom games, controls, save/load, import/export, logic, maps/mod restrictions, networking policy, performance and memory pressure.

### M5 — Yandex Games

Add `/sdk.js` integration, lifecycle events, focus/pause/audio behavior, cloud/local saves where required, ads/leaderboards where appropriate, and final moderation packaging.

## Compatibility policy

Every Web-specific workaround must be isolated behind the browser target or a platform abstraction. Do not weaken desktop/mobile behavior to make Web compile. When an upstream API must change, keep the patch small and document why the browser needs it.
