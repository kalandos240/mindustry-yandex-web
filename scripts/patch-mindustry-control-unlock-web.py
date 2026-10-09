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
                    ui.hudfrag.showToast(new TextureRegionDrawable(e.content.uiIcon), iconLarge,
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
path.write_text(source.replace(old, new, 1), encoding="utf-8")
print("Preserved vanilla Scene unlock toasts without full desktop aggregation widget")
