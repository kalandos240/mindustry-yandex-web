#!/usr/bin/env python3
"""Browser-only compatibility for the stock local schematic library.

The Yandex client does not initialize Mods or Steam Workshop; neither is
needed for built-in loadouts or persistent local .msch files.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "work/Mindustry/core/src/mindustry/game/Schematics.java"

if not SOURCE.is_file():
    raise SystemExit(f"Pinned schematic source missing: {SOURCE}")

text = SOURCE.read_text(encoding="utf-8")
old = '''        //mod-specific schematics, cannot be removed
        mods.listFiles("schematics", (mod, file) -> {
            Schematic s = loadFile(file);
            if(s != null){
                s.mod = mod;
            }
        });
'''
new = '''        // Web/Yandex: persistent local MSCH files and built-in loadouts remain
        // available. Remove mod-specific scanning altogether so TeaVM cannot
        // reach the desktop Mods loader/reflection/ZIP thread graph.
'''
if text.count(old) != 1:
    raise SystemExit("Stock Schematics.load mods block no longer matches pinned v159.7")
text = text.replace(old, new, 1)

# Stock FileChooser.export uses UI.loadAnd/LoadingFragment on desktop. The lean
# browser launcher uses the same writer on a BrowserFi synchronously, then
# BrowserFiles starts a Blob download only once the file has received its bytes.
chooser = ROOT / "work/Mindustry/core/src/mindustry/ui/FileChooser.java"
chooser_text = chooser.read_text(encoding="utf-8")
old_export = """                ui.loadAnd(() -> {
                    try{
                        writer.write(file);
                    }catch(Throwable e){
                        ui.showException(e);
                        Log.err(e);
                    }
                });"""
new_export = """                // Web: write to BrowserFi; its export directory downloads the bytes.
                try{
                    writer.write(file);
                }catch(Throwable e){
                    ui.showException(e);
                    Log.err(e);
                }"""
if chooser_text.count(old_export) != 1:
    raise SystemExit("Pinned FileChooser.export non-iOS writer changed")
chooser.write_text(chooser_text.replace(old_export, new_export, 1), encoding="utf-8")

# The lean browser client does not fire desktop ClientLoadEvent. Create the
# stock fallback texture lazily on the actual rendering event loop instead.
preview_old = """    public Texture getPreview(Schematic schematic){
        if(errored.contains(schematic)) return errorTexture;"""
preview_new = """    public Texture getPreview(Schematic schematic){
        if(errorTexture == null) errorTexture = new Texture("sprites/error.png");
        if(errored.contains(schematic)) return errorTexture;"""
if text.count(preview_old) != 1:
    raise SystemExit("Stock Schematics.getPreview changed since v159.7")
text = text.replace(preview_old, preview_new, 1)

SOURCE.write_text(text, encoding="utf-8")
print("Stock Schematics.load: local Web schematics preserved without mandatory Mods startup")
