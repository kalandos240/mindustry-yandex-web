# Saved-sector Go after a lost Erekir core

The user reported that selecting Onset and pressing Go did nothing. Their
console contained `onset / sector-load / java.lang.IllegalStateException / null`.

Run 39099637207 reproduced the identical failure on the previously delivered
archive (SHA-256
`f8ab367b195b27f6baeccb60cd982d26f119e23bc2c9f4372d832b46a5a06c42`).
A test-only seed removed the player core, wrote a real v13 checkpoint and
flushed IndexedDB. A new Chrome process loaded that profile, entered the
original planet dialog and pressed its original Go button. The TeaVM throw
stack identified the post-load core/world guard in continuePreset. Healthy
cold Onset saves and in-process round trips did not reproduce this exception.
This reproduces the reported symptom without claiming access to the user's
actual persisted save.

The pinned Control.playSector supports a new landing when a sector has lost
its core. Erekir uses clearSectorOnLose. The Web path instead rejected all
coreless saves forever. The fix keeps the loaded-file validation, then applies
that rule to a coreless world: copy the source MSAV to a separate
`.lost-backup`, reset and enter the packaged preset again. Healthy saves keep
the exact saved wave/tick validation and inventory recovery. Serpulo's default
non-clearing recapture path is outside this change.

The native bridge fetches the packaged map before changing the active world,
including when a saved sector may need a new landing. Resume diagnostics now
separate file loading, world/core checks, player placement and clock checks.
UI.showException silently returns when loadfrag is absent in the lean Web UI;
use the original dependency-free showErrorMessage instead.

Acceptance covers desktop/mobile cold-menu Go for both starting sectors,
in-process native round trips, exact cold-save wave/tick/resource retention,
lost-Onset redeployment and original research regressions. The destroyed-core
fixture alters only a test staging copy of JavaScript while making its seed;
the original production JavaScript is restored before Go loads the save.
Fixture instrumentation is never included in the downloadable ZIP.

The rebuilt ZIP is 52,698,904 bytes (93,799,615 unpacked; 6,200,385 below the
100,000,000-byte cap), SHA-256
`01159ffd8795796036efb35efbb8eb0e6951cb89bb3634c0da80bcf5dcf69039`.
Run 39099773308 passed healthy cold-menu Go on desktop/mobile for both starting
sectors and the saved-sector native round trips. Exact-ZIP proof run
39100155688 passed lost-Onset Go on desktop/mobile, byte-for-byte preservation
of the lost MSAV, storage flush and continuing world simulation. It restores
the production JavaScript before the real native Go action.

The seed deliberately destroys a core, so its initial live session can end
after writing the checkpoint. The harness accepts that seed only if both the
checkpoint-ready and durable-flush markers exist; this exception never applies
to the subsequent production load/redeployment assertion. The original build
job's earlier seed runner did not yet handle that expected seed termination.
The checkpoint-resource regression also passed in run 39099773308. Final
acceptance run 39100403832 verified these specific successful evidence steps,
then passed original research on desktop/mobile/narrow using the unchanged
ZIP and rechecked archive integrity and size. Its gesture harness waits for
the native root chooser close animation on mobile as well as desktop; an
earlier mobile gesture could hit the still-closing chooser instead of the tree.

This change does not claim whole-interface parity or recertify the full
46-sector gameplay matrix.

Additional unchanged-ZIP run 39100720051 passed the same lost-sector/backup
checks and waited for actual player spawn before sending input. Its reviewed
desktop screenshot shows the spawned Evoke at tile 300,108 after KeyD, versus
the core/landing tile 277,108. The mobile screenshot confirms a spawned Evoke
and a responding native core selection after a touch gesture; it does not
prove mobile unit displacement.
