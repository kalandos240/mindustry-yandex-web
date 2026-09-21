#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JSON = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "util" / "serialization" / "Json.java"

if not JSON.is_file():
    raise SystemExit(f"Missing pinned Arc Json source: {JSON}")

text = JSON.read_text(encoding="utf-8")

old_default = '''    private Object[] getDefaultValues(Class type){
        if(!usePrototypes) return null;
        if(type.isAnonymousClass()) type = type.getSuperclass();
'''
new_default = '''    private Object[] getDefaultValues(Class type){
        if(!usePrototypes) return null;
        type = webDeclaredClass(type);
'''
if text.count(old_default) != 1:
    raise SystemExit("Arc Json default-value anonymous-class anchor no longer matches pinned source")
text = text.replace(old_default, new_default, 1)

old_known = '''        if(knownType != null && knownType.isAnonymousClass()){
            knownType = knownType.getSuperclass();
        }
'''
new_known = '''        if(knownType != null){
            knownType = webDeclaredClass(knownType);
        }
'''
if text.count(old_known) != 1:
    raise SystemExit("Arc Json known-type anonymous-class anchor no longer matches pinned source")
text = text.replace(old_known, new_known, 1)

old_actual = '''            Class actualType = value.getClass().isAnonymousClass() ? value.getClass().getSuperclass() : value.getClass();
'''
new_actual = '''            Class actualType = webDeclaredClass(value.getClass());
'''
if text.count(old_actual) != 1:
    raise SystemExit("Arc Json actual-type anonymous-class anchor no longer matches pinned source")
text = text.replace(old_actual, new_actual, 1)

anchor = '''    private Object[] getDefaultValues(Class type){
'''
helper = '''    /**
     * TeaVM 0.15 Class does not implement Class.isAnonymousClass(). javac anonymous
     * classes use a numeric suffix after the final '$' (Outer$1, Outer$2, ...);
     * named nested classes do not. Preserve Arc's desktop serializer semantics without
     * retaining an unsupported reflection method in the Web graph.
     */
    private static Class webDeclaredClass(Class type){
        if(type == null) return null;
        String name = type.getName();
        int separator = name.lastIndexOf((char)36);
        boolean anonymous = separator >= 0 && separator + 1 < name.length();
        for(int i = separator + 1; anonymous && i < name.length(); i++){
            char c = name.charAt(i);
            anonymous = c >= '0' && c <= '9';
        }
        return anonymous && type.getSuperclass() != null ? type.getSuperclass() : type;
    }

'''
if text.count(anchor) != 1:
    raise SystemExit("Arc Json helper insertion anchor no longer matches pinned source")
text = text.replace(anchor, helper + anchor, 1)

if "isAnonymousClass()" in text:
    raise SystemExit("Arc Json Web patch left an unsupported isAnonymousClass() call")

JSON.write_text(text, encoding="utf-8")
print("Patched Arc Json anonymous-class detection for TeaVM 0.15")
