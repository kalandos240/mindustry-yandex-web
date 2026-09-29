#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "game" / "Saves.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry Saves source: {PATH}")

text = PATH.read_text(encoding="utf-8")

# BrowserSaves has already indexed metadata from the hydrated IndexedDB cache. When
# continuing a sector, SaveIO.load() must read the world, but re-inflating metadata
# immediately afterwards is redundant. Keep the indexed SaveSlot.meta unless absent.
old_load = '''        public void load(WorldContext context) throws SaveException{
            try{
                SaveIO.load(file, context);
                meta = SaveIO.getMeta(file);
                current = this;
                totalPlaytime = meta.timePlayed;
                savePreview();
            }catch(Throwable e){
                throw new SaveException(e);
            }
        }
'''
new_load = '''        public void load(WorldContext context) throws SaveException{
            try{
                SaveIO.load(file, context);
                if(meta == null) meta = SaveIO.getMeta(file);
                current = this;
                totalPlaytime = meta.timePlayed;
                savePreview();
            }catch(Throwable e){
                throw new SaveException(e);
            }
        }
'''
if old_load not in text:
    raise SystemExit("Saves.SaveSlot.load Web patch no longer matches pinned upstream")
text = text.replace(old_load, new_load, 1)

old = '''        private void savePreview(){
            if(Core.assets.isLoaded(loadPreviewFile().path())){
                Core.assets.unload(loadPreviewFile().path());
            }
            mainExecutor.submit(() -> {
                try{
                    previewFile().writePng(renderer.minimap.getPixmap());
                    requestedPreview = false;
                }catch(Throwable t){
                    Log.err(t);
                }
            });
        }
'''
new = '''        private void savePreview(){
            // Web: the lean save/campaign UI never renders stock SaveSlot preview
            // textures. Skip minimap readback + PNG compression + IndexedDB writes on
            // every save/load; previewTexture() safely returns null when absent.
            requestedPreview = false;
        }
'''

if old not in text:
    raise SystemExit("Saves.savePreview Web patch no longer matches pinned upstream")

text = text.replace(old, new, 1)
PATH.write_text(text, encoding="utf-8")
print("Applied browser save metadata reuse + disabled unused save preview generation")
