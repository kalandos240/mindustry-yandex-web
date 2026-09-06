#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-mindustry-block-reflection.py <Block.java>")

path = Path(sys.argv[1])
text = path.read_text()
old = '''            if(current.isAnonymousClass()){
                current = current.getSuperclass();
            }
'''
new = '''            // TeaVM's Class implementation does not expose isAnonymousClass().
            // Vanilla content uses javac-style anonymous subclasses (Outer$1, $2, ...),
            // so detect that shape without changing the subsequent build-class lookup.
            String className = current.getName();
            int marker = className.lastIndexOf('$');
            boolean anonymous = marker >= 0 && marker + 1 < className.length();
            for(int i = marker + 1; anonymous && i < className.length(); i++){
                char c = className.charAt(i);
                if(c < '0' || c > '9') anonymous = false;
            }
            if(anonymous){
                current = current.getSuperclass();
            }
'''
if old not in text:
    raise SystemExit("Mindustry Block.initBuilding anonymous-class patch no longer matches pinned upstream.")
path.write_text(text.replace(old, new, 1))

# Temporary module-loop diagnosis: the current browser NPE is inside Scene.root.act().
# Instrument Group.act after the normal Arc/Mindustry overlays so Chrome reports the
# exact root child/index that fails. Remove this hook together with the diagnostics
# once the underlying lifecycle dependency is fixed.
subprocess.run([
    sys.executable,
    str(Path(__file__).with_name("patch-arc-group-act-diagnostics.py")),
], check=True)
