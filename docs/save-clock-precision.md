# Saved campaign clock precision

The native planet Go button resumed valid saves through a clock consistency check. Arc StringMap's decimal parser could alter the serialized tick enough to cross a rounded millisecond boundary, rejecting the save at resume-clock.

Read SaveVersion metadata with Double.parseDouble, matching the double written to metadata. Keep the wave and millisecond consistency checks and include expected/actual values in mismatch diagnostics. No save migration or progress reset is required.

Regression fixtures:
- Ground Zero: 216000.99949999998 ticks, expected 216000999 milliseconds.
- Onset: 9999999.999499999 ticks, expected 9999999999 milliseconds.

The browser harness seeds a real save, restores pristine production JavaScript, restarts Chrome with the same profile, clicks native planet Go, verifies exact restored milliseconds, and waits for world simulation to advance. Run both campaigns on desktop and mobile. Fixture modifications are excluded from the release archive.

Previous ZIP reproduction: GitHub Actions run 39145055181 reproduces the original groundZero / resume-clock / java.lang.IllegalStateException / null rejection.
New archive: SHA-256 70a51958dcff9f81a5c797c791e4923bc79f08b98567cec51bcc631cbd9452b1, 93800651 unpacked bytes.
