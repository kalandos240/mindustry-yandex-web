#!/usr/bin/env python3
"""Preserve vanilla research notifications but label Web HUD event failures.

The browser mounts the original PlacementFragment without the full desktop UI
dialog tree. Stock Control's UnlockEvent listener must still show the toast and
check subsequent technology; mark the exact callback when TeaVM strips stacks.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch-mindustry-control-unlock-web.py Control.java")
path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
old = """        Events.on(UnlockEvent.class, e -> {
            if(e.content.showUnlock()){
                ui.hudfrag.showUnlock(e.content);
            }

            checkAutoUnlocks();
"""
new = """        Events.on(UnlockEvent.class, e -> {
            try{
                if(e.content.showUnlock()){
                    ui.hudfrag.showUnlock(e.content);
                }
            }catch(Throwable error){
                throw new IllegalStateException("stock-unlock: notification/" + e.content.name, error);
            }

            try{
                checkAutoUnlocks();
            }catch(Throwable error){
                throw new IllegalStateException("stock-unlock: auto-unlock/" + e.content.name, error);
            }
"""
if source.count(old) != 1:
    raise SystemExit("Pinned Control.UnlockEvent notification anchor changed")
path.write_text(source.replace(old, new, 1), encoding="utf-8")
print("Labeled vanilla HUD research notification and automatic-unlock listeners")
