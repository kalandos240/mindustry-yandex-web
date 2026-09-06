#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "game" / "Saves.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry Saves source: {PATH}")

text = PATH.read_text(encoding="utf-8")
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
            if(Core.assets.isLoaded(loadPreviewFile().path())){
                Core.assets.unload(loadPreviewFile().path());
            }
            // Web: preserve deferred preview generation without a JVM ExecutorService.
            // BrowserApplication drains Core.app.post on the requestAnimationFrame loop,
            // so SaveIO remains synchronous while PNG preview work happens afterwards.
            Core.app.post(() -> {
                try{
                    previewFile().writePng(renderer.minimap.getPixmap());
                    requestedPreview = false;
                }catch(Throwable t){
                    Log.err(t);
                }
            });
        }
'''

if old not in text:
    raise SystemExit("Saves.savePreview Web patch no longer matches pinned upstream")

text = text.replace(old, new, 1)
PATH.write_text(text, encoding="utf-8")
print("Applied browser-event-loop save preview generation without ExecutorService")
