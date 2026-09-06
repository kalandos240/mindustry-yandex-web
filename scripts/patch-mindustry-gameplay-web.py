#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch-mindustry-gameplay-web.py <UnitGroup.java> <BuildingComp.java>")

unit_group_path = Path(sys.argv[1])
building_comp_path = Path(sys.argv[2])

# Browser JavaScript has one game/event-loop thread. Keep the exact formation algorithm,
# but execute it synchronously instead of making ExecutorService reachable to TeaVM.
text = unit_group_path.read_text(encoding="utf-8")
old_open = """        //run on new thread to prevent stutter\n        Vars.mainExecutor.submit(() -> {\n"""
new_open = """        // Web: run the stock formation algorithm on the browser event loop.\n        // A future optimization may slice very large groups across frames, but importing\n        // ExecutorService is impossible in TeaVM and would not create a real worker here.\n        {\n"""
if old_open not in text:
    raise SystemExit("UnitGroup executor opener no longer matches pinned upstream")
text = text.replace(old_open, new_open, 1)

old_close = """            }\n        });\n    }\n\n    public void updateRaycast(int index, Vec2 dest){\n"""
new_close = """            }\n        }\n    }\n\n    public void updateRaycast(int index, Vec2 dest){\n"""
if old_close not in text:
    raise SystemExit("UnitGroup executor closer no longer matches pinned upstream")
text = text.replace(old_close, new_close, 1)
unit_group_path.write_text(text, encoding="utf-8")

# TeaVM 0.15 does not implement Class.isAnonymousClass(). Vanilla configuration routing
# only needs to collapse anonymous subclasses to their declared superclass. The entity
# processor feeds component method bodies through JavaPoet, where a literal dollar sign
# is a format token; use its character code so the generated method remains processor-safe.
text = building_comp_path.read_text(encoding="utf-8")
old = """        //null is of type void.class; anonymous classes use their superclass.\n        Class<?> type = value == null ? void.class : value.getClass().isAnonymousClass() ? value.getClass().getSuperclass() : value.getClass();\n\n"""
new = """        //null is of type void.class; anonymous classes use their superclass.\n        // Web: Class.isAnonymousClass() is not implemented by TeaVM. Detect the javac\n        // anonymous-class numeric suffix without using unsupported reflection.\n        Class<?> type = value == null ? void.class : value.getClass();\n        if(value != null){\n            String className = type.getName();\n            int separator = className.lastIndexOf((char)36);\n            boolean anonymous = separator >= 0 && separator + 1 < className.length();\n            for(int i = separator + 1; anonymous && i < className.length(); i++){\n                char c = className.charAt(i);\n                anonymous = c >= '0' && c <= '9';\n            }\n            if(anonymous && type.getSuperclass() != null){\n                type = type.getSuperclass();\n            }\n        }\n\n"""
if old not in text:
    raise SystemExit("BuildingComp anonymous-config patch no longer matches pinned upstream")
text = text.replace(old, new, 1)
building_comp_path.write_text(text, encoding="utf-8")
