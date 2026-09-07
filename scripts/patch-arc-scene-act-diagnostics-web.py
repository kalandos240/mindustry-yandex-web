#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GROUP = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "scene" / "Group.java"

if not GROUP.is_file():
    raise SystemExit(f"Missing pinned Arc Group source: {GROUP}")

text = GROUP.read_text(encoding="utf-8")
old = '''    @Override
    public void act(float delta){
        super.act(delta);
        Element[] actors = children.begin();
        for(int i = 0, n = children.size; i < n; i++){
            actors[i].updateVisibility();
            if(actors[i].visible){
                actors[i].act(delta);
            }
        }
        children.end();
    }
'''
new = '''    @Override
    public void act(float delta){
        super.act(delta);
        Element[] actors = children.begin();
        for(int i = 0, n = children.size; i < n; i++){
            Element child = actors[i];
            String label = child.getClass().getName() + (child.name == null ? "" : "#" + child.name);
            try{
                child.updateVisibility();
            }catch(Throwable error){
                children.end();
                throw new RuntimeException("Web Scene visibility failed: " + label, error);
            }
            if(child.visible){
                try{
                    child.act(delta);
                }catch(Throwable error){
                    children.end();
                    throw new RuntimeException("Web Scene actor act failed: " + label, error);
                }
            }
        }
        children.end();
    }
'''

if text.count(old) != 1:
    raise SystemExit("Arc Group Web act diagnostic patch no longer matches pinned upstream")

GROUP.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Applied Web Scene.act actor diagnostics without changing child update order")
