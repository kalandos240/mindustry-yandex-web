#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
AUDIO_DIR = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "audio"
SOLOUD = AUDIO_DIR / "Soloud.java"
AUDIO = AUDIO_DIR / "Audio.java"
SOUND = AUDIO_DIR / "Sound.java"
MUSIC = AUDIO_DIR / "Music.java"

for path in (SOLOUD, AUDIO, SOUND, MUSIC):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Arc audio source: {path}")

text = SOLOUD.read_text(encoding="utf-8")
pattern = re.compile(
    r"(?m)^(?P<indent>\s*)static native (?P<return>[^\s]+) (?P<name>[A-Za-z0-9_]+)\((?P<args>[^;]*)\);"
)


def body_for(return_type: str) -> str:
    if return_type == "void":
        return "{}"
    if return_type == "boolean":
        return "{ return false; }"
    if return_type in {"byte", "short", "int", "char"}:
        return "{ return 0; }"
    if return_type == "long":
        return "{ return 0L; }"
    if return_type == "float":
        return "{ return 0f; }"
    if return_type == "double":
        return "{ return 0d; }"
    if return_type == "String":
        return '{ return ""; }'
    if return_type.endswith("[]"):
        return "{ return null; }"
    raise SystemExit(f"Unsupported Soloud native return type in pinned Arc source: {return_type}")


methods = []


def replace_native(match: re.Match[str]) -> str:
    return_type = match.group("return")
    name = match.group("name")
    args = match.group("args")
    methods.append(f"{name}:{return_type}")
    return f'{match.group("indent")}static {return_type} {name}({args}){body_for(return_type)}'


patched = pattern.sub(replace_native, text)

# Pinned Arc currently exposes a broad SoLoud JNI surface. Requiring a substantial
# replacement count makes upstream drift fail loudly instead of silently leaving a
# native method reachable from Control/Sound/Music/filters in TeaVM.
if len(methods) < 30:
    raise SystemExit(f"Arc Soloud Web patch replaced only {len(methods)} native methods; pinned source likely changed")
if re.search(r"(?m)^\s*static native ", patched):
    raise SystemExit("Arc Soloud Web patch left native methods in the TeaVM source graph")
SOLOUD.write_text(patched, encoding="utf-8")


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    value = path.read_text(encoding="utf-8")
    if value.count(old) != 1:
        raise SystemExit(f"BrowserAudio integration patch expected one pinned match ({label})")
    path.write_text(value.replace(old, new, 1), encoding="utf-8")


# BrowserAudio lives outside arc.audio and must be able to set the inherited state.
replace_once(
    AUDIO,
    "    boolean initialized;",
    "    protected boolean initialized;",
    "Audio.initialized visibility",
)

# The older gameplay milestone deliberately silenced every Sound convenience method.
# Keep its primary plain-Sound path inert, but restore the normal overload chain and
# spatial volume calculation. BrowserSound overrides the primary play method, so these
# stock wrappers now dispatch to Web Audio instead of returning -1 before dispatch.
replace_once(
    SOUND,
    """    public float calcVolume(float x, float y){
        return 0f;
    }""",
    """    public float calcVolume(float x, float y){
        return calcFalloff(x, y) * Core.audio.sfxVolume;
    }""",
    "Sound.calcVolume BrowserAudio dispatch",
)
replace_once(
    SOUND,
    """    public int play(){
        return -1;
    }""",
    """    public int play(){
        return play(Core.audio.sfxVolume);
    }""",
    "Sound.play BrowserAudio dispatch",
)
replace_once(
    SOUND,
    """    public int play(AudioBus bus){
        return -1;
    }""",
    """    public int play(AudioBus bus){
        return play(Core.audio.sfxVolume, 1f, 0f, false, true, bus);
    }""",
    "Sound.play(bus) BrowserAudio dispatch",
)

# Static external-file factories bypass Audio.newSound/newMusic in desktop Arc. Route
# them through the installed platform backend so any reachable call still produces the
# URL-backed browser implementations and never a zero-handle inert desktop object.
replace_once(
    SOUND,
    """    public static Sound createStream(Fi file){
        Sound sound = new Sound();
        try{
            sound.file = file;
            sound.stream = true;
            sound.handle = streamLoadFile(file.path());
        }catch(Throwable e){
            Log.err("Failed loading sound from " + file, e);
        }
        return sound;
    }""",
    """    public static Sound createStream(Fi file){
        return Core.audio == null ? new Sound() : Core.audio.newSound(file);
    }""",
    "Sound.createStream BrowserAudio routing",
)
replace_once(
    MUSIC,
    """    public static Music create(Fi file){
        Music music = new Music();
        try{
            music.file = file;
            music.handle = streamLoadFile(file.path());
        }catch(Throwable e){
            Log.err("Failed loading music from " + file, e);
        }
        return music;
    }""",
    """    public static Music create(Fi file){
        return Core.audio == null ? new Music() : Core.audio.newMusic(file);
    }""",
    "Music.create BrowserAudio routing",
)

print(f"Applied TeaVM-safe inert Arc Soloud backend stubs: {len(methods)} methods")
print("Restored Arc Sound/Music dispatch into the BrowserAudio backend")
