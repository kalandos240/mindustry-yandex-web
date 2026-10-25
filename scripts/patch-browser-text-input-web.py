#!/usr/bin/env python3
"""Route stock text prompts through the installed browser-native Input adapter."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
path=ROOT/'work/Mindustry/core/src/mindustry/core/UI.java'
source=path.read_text(encoding='utf-8')
old='''    public void showTextInput(String titleText, String text, int textLength, String def, boolean numbers, boolean allowEmpty, Cons<String> confirmed, Runnable closed){
        if(mobile){'''
new='''    public void showTextInput(String titleText, String text, int textLength, String def, boolean numbers, boolean allowEmpty, Cons<String> confirmed, Runnable closed){
        // Web: native DOM input provides browser paste/IME on both input modes.
        // BrowserTextInput posts accepted/cancelled callbacks onto the Arc loop.
        if(true){'''
if source.count(old)!=1:raise SystemExit('Pinned UI.showTextInput browser adapter anchor changed')
path.write_text(source.replace(old,new,1),encoding='utf-8')
print('Stock text prompts use browser-native Unicode/paste input on desktop and mobile')
