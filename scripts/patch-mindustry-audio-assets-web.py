#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "annotations" / "src" / "main" / "java" / "mindustry" / "annotations" / "impl" / "AssetsProcess.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry AssetsProcess source: {PATH}")

text = PATH.read_text(encoding="utf-8")

helper_anchor = '''            type.addMethod(MethodSpec.methodBuilder("unregisterSound")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(Sound.class, "sound")
            .returns(void.class)
            .addStatement("int id = soundToId.get(sound); if(id != 0){ soundToId.remove(sound); idToSound.remove(id); }").build());
        }

        HashSet<String> names = new HashSet<>();
'''
helper_replacement = '''            type.addMethod(MethodSpec.methodBuilder("unregisterSound")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(Sound.class, "sound")
            .returns(void.class)
            .addStatement("int id = soundToId.get(sound); if(id != 0){ soundToId.remove(sound); idToSound.remove(id); }").build());
        }

        // Web/Yandex generator overlay: keep one compact helper in each generated class.
        // Every stock field still has its own filename/id, but repeated BrowserAudio +
        // AssetManager registration bytecode is emitted only once.
        if(genid){
            type.addMethod(MethodSpec.methodBuilder("loadBrowserSound")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(String.class, "path")
            .addParameter(int.class, "id")
            .returns(Sound.class)
            .addStatement("$T sound = $T.audio.newSound($T.files.internal(path))", Sound.class, Core.class, Core.class)
            .addStatement("$T.assets.addAsset(path, $T.class, sound)", Core.class, Sound.class)
            .addStatement("soundToId.put(sound, id); idToSound.put(id, sound)")
            .addStatement("return sound").build());
        }else{
            type.addMethod(MethodSpec.methodBuilder("loadBrowserMusic")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(String.class, "path")
            .returns(Music.class)
            .addStatement("$T music = $T.audio.newMusic($T.files.internal(path))", Music.class, Core.class, Core.class)
            .addStatement("$T.assets.addAsset(path, $T.class, music)", Core.class, Music.class)
            .addStatement("return music").build());
        }

        HashSet<String> names = new HashSet<>();
'''

old_sound = '''                loadBegin.addStatement("$T.assets.load($S, $L.class).loaded = a -> { $L = ($L)a; soundToId.put(a, $L); idToSound.put($L, a); }",
                Core.class, filepath, rtype, name, rtype, id, id);
'''
new_sound = '''                loadBegin.addStatement("$L = loadBrowserSound($S, $L)", name, filepath, id);
'''

old_music = '''                loadBegin.addStatement("$T.assets.load($S, $L.class).loaded = a -> { $L = ($L)a; }", Core.class, filepath, rtype, name, rtype);
'''
new_music = '''                loadBegin.addStatement("$L = loadBrowserMusic($S)", name, filepath);
'''

for old, new, label in (
    (helper_anchor, helper_replacement, "browser helper generator"),
    (old_sound, new_sound, "sound generator"),
    (old_music, new_music, "music generator"),
):
    if text.count(old) != 1:
        raise SystemExit(f"AssetsProcess Web audio patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding="utf-8")
print("Generated compact stock Sounds/Musics through same-origin BrowserAudio assets")
