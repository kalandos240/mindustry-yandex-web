#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARC = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "audio"
AUDIO = ARC / "Audio.java"
BUS = ARC / "AudioBus.java"
SOUND = ARC / "Sound.java"
MUSIC = ARC / "Music.java"

for path in (AUDIO, BUS, SOUND, MUSIC):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Arc audio source: {path}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(f"Arc Web audio patch expected exactly one pinned match ({label})")
    return text.replace(old, new, 1)


# BrowserAudio subclasses Audio(false); expose only the initialized state to subclasses.
audio = AUDIO.read_text(encoding="utf-8")
audio = replace_once(audio, "    boolean initialized;", "    protected boolean initialized;", "Audio initialized visibility")
AUDIO.write_text(audio, encoding="utf-8")

# Once BrowserAudio reports initialized=true, constructing any later AudioBus must not
# fall into busNew()/sourcePlay() JNI. Web playback treats buses as logical routing only.
bus = BUS.read_text(encoding="utf-8")
bus = replace_once(
    bus,
    '''    public AudioBus(){\n        if(Core.audio != null && Core.audio.initialized){\n            init();\n        }\n    }''',
    '''    public AudioBus(){\n        // Web: BrowserAudio owns the mixer graph; no SoLoud/JNI bus allocation.\n    }''',
    "AudioBus constructor"
)
BUS.write_text(bus, encoding="utf-8")

sound = SOUND.read_text(encoding="utf-8")

# Static stream construction must remain inside Core.audio so Web receives BrowserSound.
sound = replace_once(
    sound,
    '''    public static Sound createStream(Fi file){\n        Sound sound = new Sound();\n        try{\n            sound.file = file;\n            sound.stream = true;\n            sound.handle = streamLoadFile(file.path());\n        }catch(Throwable e){\n            Log.err("Failed loading sound from " + file, e);\n        }\n        return sound;\n    }''',
    '''    public static Sound createStream(Fi file){\n        return Core.audio == null ? new Sound() : Core.audio.newSound(file);\n    }''',
    "Sound.createStream"
)

# Remove the JNI/SoLoud execution point from base Sound. BrowserSound overrides this
# method; any plain/dummy Sound safely delegates through the installed Audio backend.
start = '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){'
end = '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame){'
if sound.count(start) != 1 or sound.count(end) != 1:
    raise SystemExit("Arc Web audio patch could not find pinned Sound primary play boundaries")
si = sound.index(start)
ei = sound.index(end, si)
sound = sound[:si] + '''    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){\n        if(Core.audio == null || !Core.audio.initialized()) return -1;\n        return Core.audio.play(this, volume, pitch, pan, loop);\n    }\n\n''' + sound[ei:]

# Preserve stock positional volume behavior while remaining safe before Core.audio exists.
sound = replace_once(
    sound,
    '''    public float calcVolume(float x, float y){\n        return calcFalloff(x, y) * Core.audio.sfxVolume;\n    }''',
    '''    public float calcVolume(float x, float y){\n        return calcFalloff(x, y) * (Core.audio == null ? 0f : Core.audio.sfxVolume);\n    }''',
    "Sound calcVolume null-safe"
)

# Keep getLength JNI-free for plain/dummy Sound; BrowserSound overrides it.
sound = replace_once(
    sound,
    '''    public float getLength(){\n        if(handle == 0 || !Core.audio.initialized) return 0f;\n        return stream ? (float)Soloud.streamLength(handle) : (float)Soloud.wavLength(handle);\n    }''',
    '''    public float getLength(){\n        return 0f;\n    }''',
    "Sound getLength"
)
SOUND.write_text(sound, encoding="utf-8")

music = MUSIC.read_text(encoding="utf-8")
# Static music construction must likewise remain inside the installed Audio backend.
music = replace_once(
    music,
    '''    public static Music create(Fi file){\n        Music music = new Music();\n        try{\n            music.file = file;\n            music.handle = streamLoadFile(file.path());\n        }catch(Throwable e){\n            Log.err("Failed loading music from " + file, e);\n        }\n        return music;\n    }''',
    '''    public static Music create(Fi file){\n        return Core.audio == null ? new Music() : Core.audio.newMusic(file);\n    }''',
    "Music.create"
)
MUSIC.write_text(music, encoding="utf-8")

print("Applied browser Audio delegation while keeping SoLoud/JNI unreachable from Web playback")
