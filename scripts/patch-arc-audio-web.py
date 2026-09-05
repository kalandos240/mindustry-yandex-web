#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOUND = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "audio" / "Sound.java"

if not SOUND.is_file():
    raise SystemExit(f"Missing pinned Arc Sound source: {SOUND}")

text = SOUND.read_text(encoding="utf-8")


def replace_once(old, new, label):
    global text
    if text.count(old) != 1:
        raise SystemExit(f"Arc Web audio patch expected exactly one pinned match ({label})")
    text = text.replace(old, new, 1)


def replace_method(start_marker, end_marker, replacement, label):
    global text
    if text.count(start_marker) != 1 or text.count(end_marker) != 1:
        raise SystemExit(f"Arc Web audio method boundary no longer matches pinned upstream ({label})")
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    if end <= start:
        raise SystemExit(f"Arc Web audio method boundaries are invalid ({label})")
    text = text[:start] + replacement + "\n\n" + text[end:]


# Arc's desktop Sound path lazy-loads through Core.executor and ultimately calls the
# JNI SoLoud backend. Neither JVM ExecutorService nor JNI exists in TeaVM JavaScript.
# Keep Sound call sites safe and allocation-free until the dedicated browser audio
# backend is wired; gameplay code may call sounds, but Web currently treats them as
# no-op instead of pulling desktop audio infrastructure into the JS graph.
replace_method(
    '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){',
    '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame){',
    '''    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){\n        return -1;\n    }''',
    'Sound primary play'
)

replace_once(
    '''    public float calcVolume(float x, float y){\n        return calcFalloff(x, y) * Core.audio.sfxVolume;\n    }''',
    '''    public float calcVolume(float x, float y){\n        return 0f;\n    }''',
    'Sound calcVolume'
)

replace_once(
    '''    public int play(){\n        return play(Core.audio.sfxVolume);\n    }''',
    '''    public int play(){\n        return -1;\n    }''',
    'Sound no-arg play'
)

replace_once(
    '''    public int play(AudioBus bus){\n        return play(Core.audio.sfxVolume, 1f, 0f, false, true, bus);\n    }''',
    '''    public int play(AudioBus bus){\n        return -1;\n    }''',
    'Sound bus play'
)

replace_once(
    '''    public float getLength(){\n        if(handle == 0 || !Core.audio.initialized) return 0f;\n        return stream ? (float)Soloud.streamLength(handle) : (float)Soloud.wavLength(handle);\n    }''',
    '''    public float getLength(){\n        return 0f;\n    }''',
    'Sound getLength'
)

SOUND.write_text(text, encoding="utf-8")
print("Applied TeaVM-safe no-op Arc Sound execution path")
