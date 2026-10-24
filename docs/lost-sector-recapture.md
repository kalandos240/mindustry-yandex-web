# Lost-sector native Go and settings continuity

The reported portal error was `groundZero / resume-missing-core`. The previous browser resume path recovered a defeated Erekir save only; the non-clearing Serpulo branch threw after loading a valid MSAV without a player core.

Recapture now follows pinned v159.7 `Control.playSector`: preserve the original MSAV in `.lost-backup`, validate preset dimensions and spawn position, temporarily place the launch core for `SectorDamage.apply(1f)`, collect surviving buildings/derelicts/rebuild plans, and load the original sector with the previous core position. Restore old derelicts and surviving buildings (config and health included), rebuild their power graphs/proximity, and carry rebuild plans before `logic.play()` and the initial save. Erekir retains its clearing behavior. Legacy saves with incompatible map/spawn metadata use a new landing; the original bytes remain backed up.

The portal log contains ad close/resume events but no settings exception or stack explaining the menu transition. Do not claim the underlying portal-specific settings bug has been reproduced. A live paused session now requires a separate native cancelable exit confirmation; settings navigation cannot directly run the menu exit. Tests exercise native sound drags and SDK pause/resume while Sound is open, then verify the same world remains paused. Canceling exit must keep that world; accepting it saves and returns to the menu. Defeat retains a direct usable menu exit.

Inventory remains authoritative in MSAV plus the durable explicit SectorInfo codec. The abandoned write-only `.items` companion was removed; no reader or recovery path used it.

Acceptance fixtures add a derelict copper-wall sentinel to the pre-loss Serpulo save. The cold native globe Go test checks original backup bytes and the sentinel in the recaptured live world. Fixture-only JavaScript is removed before package identity checks and delivery.

Size control: keep the existing 44 progression branches unchanged. Name the internal browser campaign class `Campaign` to reduce repeated symbols in TeaVM's unobfuscated output, retaining source and stack readability. Remove obsolete JS settings callbacks: the shell consumes only preference telemetry, while all editable settings use SettingsMenuDialog.
