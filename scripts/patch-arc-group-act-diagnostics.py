#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "scene" / "Group.java"
if not path.is_file():
    raise SystemExit(f"Missing pinned Arc Group source: {path}")

text = path.read_text(encoding="utf-8")
old = '''    @Override\n    public void act(float delta){\n        super.act(delta);\n        Element[] actors = children.begin();\n        for(int i = 0, n = children.size; i < n; i++){\n            actors[i].updateVisibility();\n            if(actors[i].visible){\n                actors[i].act(delta);\n            }\n        }\n        children.end();\n    }\n'''
new = '''    @Override\n    public void act(float delta){\n        try{\n            super.act(delta);\n        }catch(Throwable error){\n            throw new RuntimeException("web-group-super-act", error);\n        }\n\n        Element[] actors;\n        try{\n            actors = children.begin();\n        }catch(Throwable error){\n            throw new RuntimeException("web-group-children-begin", error);\n        }\n\n        for(int i = 0, n = children.size; i < n; i++){\n            Element child = actors[i];\n            if(child == null){\n                throw new RuntimeException("web-group-null-child-" + i);\n            }\n\n            try{\n                child.updateVisibility();\n            }catch(Throwable error){\n                throw new RuntimeException("web-group-child-visibility-" + i + "-" + child.name, error);\n            }\n\n            if(child.visible){\n                try{\n                    child.act(delta);\n                }catch(Throwable error){\n                    throw new RuntimeException("web-group-child-act-" + i + "-" + child.name, error);\n                }\n            }\n        }\n\n        try{\n            children.end();\n        }catch(Throwable error){\n            throw new RuntimeException("web-group-children-end", error);\n        }\n    }\n'''
if old not in text:
    raise SystemExit("Arc Group.act diagnostic patch no longer matches pinned upstream")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Instrumented Arc Group.act root-child phases for Web runtime diagnosis")
