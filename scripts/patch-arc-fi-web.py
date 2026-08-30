#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-arc-fi-web.py <Fi.java>")

path = Path(sys.argv[1])
text = path.read_text()
old = '''    public boolean exists(){
        switch(type){
            case internal:
                if(file().exists()) return true;
                // Fall through.
            case classpath:
                return Fi.class.getResource("/" + file.getPath().replace('\\\\', '/')) != null;
        }
        return file().exists();
    }
'''
new = '''    public boolean exists(){
        switch(type){
            case internal:
            case classpath:
                // Web: platform-created packaged handles (BrowserFi) override exists().
                // Do not retain the JVM Class.getResource/File fallback in TeaVM's graph.
                return false;
        }
        return file().exists();
    }
'''
if old not in text:
    raise SystemExit("Arc Fi.exists Web patch no longer matches pinned upstream.")
path.write_text(text.replace(old, new, 1))
