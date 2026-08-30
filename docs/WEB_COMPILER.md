# Web compiler decision

## Primary experimental path: TeaVM 0.15

The first current-Arc browser compilation path uses TeaVM 0.15.0 JavaScript output.

Reasons:

- it consumes normal JVM bytecode, so current Arc/Mindustry can continue to compile with javac instead of being rewritten into a source-transpiler dialect;
- Java 17 is a supported baseline and matches the port CI;
- browser DOM/JS APIs are exposed through TeaVM JSO;
- TeaVM supports coroutine-based Java thread emulation;
- TeaVM 0.15 provides explicit reflection, resource and substitution policies, which are useful for replacing native/JVM-only Arc implementations on Web;
- dead-code elimination means desktop SDL/JNI code does not need to be made browser-compatible when it is not reachable from the Web launcher.

This does **not** mean the current game compiles unchanged. TeaVM intentionally restricts APIs such as arbitrary reflection, class loading and JNI, and its Java class-library emulation is incomplete in areas such as networking. Those incompatibilities are treated as explicit Web-port work rather than hidden runtime assumptions.

## Historical GWT backend

The Arc GWT backend from `ARC_LEGACY_WEB_REF` remains an implementation reference. It already documents Arc's historical browser behavior for WebGL, input, files, networking, clipboard, audio and JRE emulation. We migrate the behavior, not the 2019 package/API surface verbatim.

GWT 2.13.1 remains a fallback compiler if TeaVM encounters a blocker that is cheaper to solve by restoring the historical GWT route. No GWT dependency is added to the production path at this stage.

## Immediate proof gate

Before porting graphics or game code, CI must prove all of the following:

1. pinned Mindustry core still compiles normally;
2. the new `arc.backend.web` skeleton compiles against pinned current Arc;
3. TeaVM can consume that current Arc/Web bytecode and emit a browser JavaScript bundle;
4. the staged package contains a local `index.html` and generated `mindustry.js`, with no remotely hosted game runtime.

Only after this gate is green do we implement the WebGL/input/file subsystems.
