#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JSON = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "util" / "serialization" / "Json.java"

if not JSON.is_file():
    raise SystemExit(f"Missing pinned Arc Json source: {JSON}")

text = JSON.read_text(encoding="utf-8")

# This patch is reached both from patch-arc-fi-web.py and again through the final
# Web compatibility chain. Treat a fully patched source as success instead of
# reporting a false pinned-source mismatch on the second invocation.
already_patched = (
    "type = webDeclaredClass(type);" in text
    and "knownType = webDeclaredClass(knownType);" in text
    and "Class actualType = webDeclaredClass(value.getClass());" in text
    and "private static Class webDeclaredClass(Class type)" in text
    and "ObjectSet.class.getName().equals(type.getName())" in text
    and "isAnonymousClass()" not in text
)
if already_patched:
    print("Arc Json TeaVM anonymous-class compatibility already applied")
    raise SystemExit(0)

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
     * TeaVM 0.15 Class lacks anonymous-class reflection. javac anonymous
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

# TeaVM reflective Field.getType() can return a Class mirror that names the exact
# Arc type but does not compare identical to the class literal. Arc Json already has
# a direct-construction fast path for ObjectSet; preserve that intended path by also
# matching the exact binary name, avoiding unsupported reflective construction.
old_object_set = '''                ObjectSet result = type == ObjectSet.class ? new ObjectSet() : (ObjectSet)newInstance(type);
'''
new_object_set = '''                ObjectSet result = type == ObjectSet.class || ObjectSet.class.getName().equals(type.getName()) ? new ObjectSet() : (ObjectSet)newInstance(type);
'''
if old_object_set in text:
    text = text.replace(old_object_set, new_object_set, 1)
elif "ObjectSet.class.getName().equals(type.getName())" not in text:
    raise SystemExit("Arc Json ObjectSet direct-construction anchor no longer matches pinned source")

JSON.write_text(text, encoding="utf-8")
print("Patched Arc Json anonymous-class detection for TeaVM 0.15")
