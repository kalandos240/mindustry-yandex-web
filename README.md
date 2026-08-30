# mindustry-yandex-web

Browser port workspace for Mindustry, targeting a standalone Web build first and Yandex Games integration second.

## Status

**Phase 0: Web port bootstrap.** The upstream game does not currently ship a browser target, and the current Arc framework no longer contains its historical GWT backend. This repository therefore keeps the Web-specific port layer separate from upstream and reconstructs the browser platform support in controlled stages.

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
