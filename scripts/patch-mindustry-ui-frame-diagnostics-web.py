#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "UI.java"

if not UI.is_file():
    raise SystemExit(f"Missing pinned Mindustry UI source: {UI}")

text = UI.read_text(encoding="utf-8")
old = '''    @Override
    public void update(){
        if(disableUI || Core.scene == null) return;

        PerfCounter.ui.begin();

        Events.fire(Trigger.uiDrawBegin);

        Core.scene.act();
        Core.scene.draw();

        if(Core.input.keyTap(KeyCode.mouseLeft) && Core.scene.hasField()){
            Element e = Core.scene.getHoverElement();
            if(!(e instanceof TextField)){
                Core.scene.setKeyboardFocus(null);
            }
        }

        Events.fire(Trigger.uiDrawEnd);

        PerfCounter.ui.end();
    }
'''
new = '''    @Override
    public void update(){
        if(disableUI || Core.scene == null) return;

        PerfCounter.ui.begin();

        try{
            Events.fire(Trigger.uiDrawBegin);
        }catch(Throwable error){
            throw new RuntimeException("Web UI uiDrawBegin failed", error);
        }

        try{
            Core.scene.act();
        }catch(Throwable error){
            throw new RuntimeException("Web UI Scene.act failed", error);
        }

        try{
            Core.scene.draw();
        }catch(Throwable error){
            throw new RuntimeException("Web UI Scene.draw failed", error);
        }

        try{
            if(Core.input.keyTap(KeyCode.mouseLeft) && Core.scene.hasField()){
                Element e = Core.scene.getHoverElement();
                if(!(e instanceof TextField)){
                    Core.scene.setKeyboardFocus(null);
                }
            }
        }catch(Throwable error){
            throw new RuntimeException("Web UI focus update failed", error);
        }

        try{
            Events.fire(Trigger.uiDrawEnd);
        }catch(Throwable error){
            throw new RuntimeException("Web UI uiDrawEnd failed", error);
        }

        PerfCounter.ui.end();
    }
'''

if text.count(old) != 1:
    raise SystemExit("UI Web frame diagnostic patch no longer matches pinned upstream")

UI.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Applied Web UI frame-stage diagnostics without changing Scene update/draw order")
