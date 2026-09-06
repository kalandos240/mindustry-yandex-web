#!/usr/bin/env python3
from pathlib import Path
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
