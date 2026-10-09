#!/usr/bin/env python3
"""Keep Mindustry's stock unlock toast functional with the lean Web HUD.

The full desktop HudFragment initializes an aggregation widget. BrowserStockPlacement
mounts only its original placement controls, not the entire HudFragment.build() graph.
Calling showUnlock() repeatedly in the same captured sector can enter an uninitialized
aggregation path. The native showToast(icon,text) uses the standalone vanilla Scene
toast, retains the content icon/name and serializes messages through the same scheduler.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch-mindustry-control-unlock-web.py Control.java")
path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
old = """            if(ui.hudfrag != null && e.content.showUnlock()){
                ui.hudfrag.showUnlock(e.content);
            }

            checkAutoUnlocks();
"""
new = """            if(ui.hudfrag != null && e.content.showUnlock()){
                try{
                    // SectorPreset.loadIcon() leaves uiIcon null if the compact
                    // browser bootstrap has no Icon.terrain atlas region yet.
                    // The stock HUD toast requires a non-null Drawable.
                    ui.hudfrag.showToast(e.content.uiIcon == null ? Icon.ok : new TextureRegionDrawable(e.content.uiIcon), iconLarge,
                        bundle.get("unlocked") + ": " + e.content.localizedName);
                }catch(Throwable error){
                    throw new IllegalStateException("web-unlock-toast/" + e.content.name, error);
                }
            }

            try{
                checkAutoUnlocks();
            }catch(Throwable error){
                throw new IllegalStateException("web-unlock-auto/" + e.content.name, error);
            }
"""
if source.count(old) != 1:
    raise SystemExit("Pinned single-player Control UnlockEvent toast anchor changed")
source = source.replace(old, new, 1)
available = 'ui.hudfrag.showToast(new TextureRegionDrawable(node.content.uiIcon), iconLarge, bundle.get("available"));'
fallback = 'ui.hudfrag.showToast(node.content.uiIcon == null ? Icon.ok : new TextureRegionDrawable(node.content.uiIcon), iconLarge, bundle.get("available"));'
if source.count(available) != 1:
    raise SystemExit("Pinned sector-available toast anchor changed")
source = source.replace(available, fallback, 1)
path.write_text(source, encoding="utf-8")
print("Preserved vanilla Scene unlock toasts with null-safe sector/research icons")
