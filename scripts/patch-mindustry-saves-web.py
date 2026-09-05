#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "game" / "Saves.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry source: {PATH}")

text = PATH.read_text(encoding="utf-8")

# Browser saves run on the JS event loop. TeaVM does not provide the JVM Future /
# ExecutorService graph used by desktop Saves.load(). Remove only that import; the
# Web replacement below preserves the same save slot/meta source of truth.
old_import = "import java.util.concurrent.*;\n"
if old_import not in text:
    raise SystemExit("Mindustry Saves concurrent import no longer matches pinned upstream")
text = text.replace(old_import, "", 1)


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"Mindustry Saves method no longer matches pinned upstream: {signature.strip()}")
    brace = source.find("{", start)
    if brace < 0:
        raise SystemExit(f"Opening brace not found for {signature.strip()}")
    depth = 0
    end = None
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                break
    if end is None:
        raise SystemExit(f"Closing brace not found for {signature.strip()}")
    return source[:start] + replacement + source[end:]


web_load = '''    public void load(){
        saves.clear();

        // Web: IndexedDB is hydrated before TeaVM main(), so BrowserFi reads are
        // synchronous from the in-memory cache. Do not pull JVM Future/ExecutorService
        // into browser reachability; the .msav files remain the source of truth.
        saveDirectory.walk(file -> {
            if(!file.name().contains("backup") && SaveIO.isSaveValid(file)){
                try{
                    SaveSlot slot = new SaveSlot(file);
                    slot.meta = SaveIO.getMeta(file);
                    saves.add(slot);
                }catch(Throwable error){
                    Log.err(error);
                }
            }
        });

        clearOldMegabaseSectors();
        lastSectorSave = saves.find(s -> s.isSector() && s.getName().equals(Core.settings.getString("last-sector-save", "<none>")));

        // Browser local storage starts with current-format data. Historical desktop
        // beta sector remapping used Settings.putJson/reflection and OS-style migration
        // paths that TeaVM cannot support. Current sector saves are still bound exactly
        // from their parsed SaveMeta rules.
        for(SaveSlot slot : saves){
            Sector sector = slot.getSector();
            if(sector != null){
                if(sector.save != null && sector.save != slot){
                    Log.warn("Sector @ has two corresponding saves: @ and @", sector, sector.save.file, slot.file);
                }
                sector.save = slot;
            }
        }
    }'''
text = replace_method(text, "    public void load(){", web_load)

web_preview = '''        private void savePreview(){
            if(Core.assets.isLoaded(loadPreviewFile().path())){
                Core.assets.unload(loadPreviewFile().path());
            }

            // Web: no JVM worker pool. Preview generation is a browser-frame operation;
            // before Renderer/minimap exists, simply defer it rather than reaching an
            // ExecutorService. Once renderer is live, BrowserFi persists the PNG locally.
            if(renderer == null || renderer.minimap == null){
                requestedPreview = false;
                return;
            }

            try{
                previewFile().writePng(renderer.minimap.getPixmap());
                requestedPreview = false;
            }catch(Throwable error){
                Log.err(error);
            }
        }'''
text = replace_method(text, "        private void savePreview(){", web_preview)

# Hard guards: the browser-reachable Saves implementation must not retain JVM
# concurrency or the legacy JSON remap cache that triggered Class.isAnonymousClass.
if "Future<SaveSlot>" in text or "mainExecutor.submit" in text:
    raise SystemExit("JVM save executor reachability remained after Web patch")
if "Core.settings.putJson(remapTarget" in text:
    raise SystemExit("Legacy sector JSON remap remained after Web patch")

PATH.write_text(text, encoding="utf-8")
print("Patched Mindustry Saves for synchronous browser IndexedDB runtime")
