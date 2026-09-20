#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "annotations" / "src" / "main" / "java" / "mindustry" / "annotations" / "impl" / "AssetsProcess.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry AssetsProcess source: {PATH}")

text = PATH.read_text(encoding="utf-8")

old_sound = '''                loadBegin.addStatement("$T.assets.load($S, $L.class).loaded = a -> { $L = ($L)a; soundToId.put(a, $L); idToSound.put($L, a); }",
                Core.class, filepath, rtype, name, rtype, id, id);
'''
new_sound = '''                // Web/Yandex: generate synchronous same-origin browser audio bindings instead
                // of AssetManager SoundLoader tasks (which pull the desktop loader graph).
                loadBegin.addStatement("$L = $T.audio.newSound($T.files.internal($S)); $T.assets.addAsset($S, $L.class, $L); soundToId.put($L, $L); idToSound.put($L, $L)",
                name, Core.class, Core.class, filepath, Core.class, filepath, rtype, name, name, id, id, name);
'''

old_music = '''                loadBegin.addStatement("$T.assets.load($S, $L.class).loaded = a -> { $L = ($L)a; }", Core.class, filepath, rtype, name, rtype);
'''
new_music = '''                // Web/Yandex: music remains URL-streamed by BrowserMusic, while retaining
                // the stock AssetManager filename lookup used by SoundControl/MusicContainer.
                loadBegin.addStatement("$L = $T.audio.newMusic($T.files.internal($S)); $T.assets.addAsset($S, $L.class, $L)",
                name, Core.class, Core.class, filepath, Core.class, filepath, rtype, name);
'''

for old, new, label in (
    (old_sound, new_sound, "sound generator"),
    (old_music, new_music, "music generator"),
):
    if text.count(old) != 1:
        raise SystemExit(f"AssetsProcess Web audio patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding="utf-8")
print("Generated stock Sounds/Musics through synchronous same-origin BrowserAudio assets")
