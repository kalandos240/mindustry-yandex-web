#!/usr/bin/env python3
"""Browser-only compatibility for the stock local schematic library.

The Yandex client does not initialize Mods or Steam Workshop; neither is
needed for built-in loadouts or persistent local .msch files.
"""
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "work/Mindustry/core/src/mindustry/game/Schematics.java"

if not SOURCE.is_file():
    raise SystemExit(f"Pinned schematic source missing: {SOURCE}")

text = SOURCE.read_text(encoding="utf-8")
old = '''        //mod-specific schematics, cannot be removed
        mods.listFiles("schematics", (mod, file) -> {
            Schematic s = loadFile(file);
            if(s != null){
                s.mod = mod;
            }
        });
'''
new = '''        // Web/Yandex: persistent local MSCH files and built-in loadouts remain
        // available. Remove mod-specific scanning altogether so TeaVM cannot
        // reach the desktop Mods loader/reflection/ZIP thread graph.
'''
if text.count(old) != 1:
    raise SystemExit("Stock Schematics.load mods block no longer matches pinned v159.7")
text = text.replace(old, new, 1)

# Stock FileChooser.export uses UI.loadAnd/LoadingFragment on desktop. The lean
# browser launcher uses the same writer on a BrowserFi synchronously, then
# BrowserFiles starts a Blob download only once the file has received its bytes.
chooser = ROOT / "work/Mindustry/core/src/mindustry/ui/FileChooser.java"
chooser_text = chooser.read_text(encoding="utf-8")
old_export = """                ui.loadAnd(() -> {
                    try{
                        writer.write(file);
                    }catch(Throwable e){
                        ui.showException(e);
                        Log.err(e);
                    }
                });"""
new_export = """                // Web: write to BrowserFi; its export directory downloads the bytes.
                try{
                    writer.write(file);
                }catch(Throwable e){
                    ui.showException(e);
                    Log.err(e);
                }"""
if chooser_text.count(old_export) != 1:
    raise SystemExit("Pinned FileChooser.export non-iOS writer changed")
chooser.write_text(chooser_text.replace(old_export, new_export, 1), encoding="utf-8")

# The lean browser client does not fire desktop ClientLoadEvent. Create the
# stock fallback texture lazily on the actual rendering event loop instead.
preview_old = """    public Texture getPreview(Schematic schematic){
        if(errored.contains(schematic)) return errorTexture;"""
preview_new = """    public Texture getPreview(Schematic schematic){
        if(errorTexture == null) errorTexture = new Texture("sprites/error.png");
        if(errored.contains(schematic)) return errorTexture;"""
if text.count(preview_old) != 1:
    raise SystemExit("Stock Schematics.getPreview changed since v159.7")
text = text.replace(preview_old, preview_new, 1)

SOURCE.write_text(text, encoding="utf-8")

# A browser cannot synchronously read the operating-system clipboard. Let users
# paste through a native DOM text field, on desktop as well as mobile, instead
# of permanently disabling import until an in-memory copy was made this session.
dialog_path = ROOT / "work/Mindustry/core/src/mindustry/ui/dialogs/SchematicsDialog.java"
dialog = dialog_path.read_text(encoding="utf-8")
clipboard_old = '''                t.button("@load.clipboard", Icon.copy, style, () -> {
                    dialog.hide();
                    try{
                        Schematic s = Schematics.readBase64(Core.app.getClipboardText());
                        s.removeSteamID();
                        schematics.add(s);
                        setup();
                        ui.showInfoFade("@schematic.saved");
                        checkTags(s);
                        showInfo(s);
                    }catch(Throwable e){
                        ui.showException(e);
                    }
                }).marginLeft(12f).disabled(b -> Core.app.getClipboardText() == null || !Core.app.getClipboardText().startsWith(schematicBaseStart));'''
clipboard_new = '''                t.button("@load.clipboard", Icon.copy, style, () -> {
                    dialog.hide();
                    Core.input.getTextInput(new Input.TextInput(){{
                        title = Core.bundle.get("schematic.import");
                        message = Core.bundle.get("load.clipboard");
                        text = Core.app.getClipboardText() == null ? "" : Core.app.getClipboardText();
                        multiline = true;
                        allowEmpty = false;
                        maxLength = 16 * 1024 * 1024;
                        accepted = encoded -> {
                            try{
                                Schematic s = Schematics.readBase64(encoded.trim());
                                s.removeSteamID();
                                schematics.add(s);
                                setup();
                                ui.showInfoFade("@schematic.saved");
                                checkTags(s);
                                showInfo(s);
                            }catch(Throwable e){
                                ui.showException(e);
                            }
                        };
                    }});
                }).marginLeft(12f);'''
if dialog.count(clipboard_old) != 1:
    raise SystemExit("Pinned schematic clipboard import changed")
dialog = dialog.replace(clipboard_old, clipboard_new, 1)

# Upstream SchematicsDialog silently presents errors in a desktop load fragment
# that does not always exist in the minimal Web UI. Keep the stock exception
# handler, but also report the real parser failure to browser console so users
# (and automated Chrome tests) can diagnose rejected MSCH files.
source_try = '''                    for(Fi file : files){
                        try{
                            Schematic s = Schematics.read(file);
                            s.removeSteamID();
                            schematics.add(s);
                            checkTags(s);
                            last = s;
                        }catch(Exception e){
                            ui.showException(e);
                        }
                    }'''
reported_try = source_try.replace(
    '                            ui.showException(e);',
    chr(10).join(('                            Log.err(e);',
                  '                            ui.showException(e);'))
)
# Stage callbacks on the *actual* pinned stock dialog, rather than assuming
# BrowserFileChooser.handleChooseResult returning means parser/write succeeded.
# No file names, bytes, or user content are recorded.
reported_try = reported_try.replace(
    '                            Schematic s = Schematics.read(file);',
    chr(10).join((
        '                            arc.Core.settings.put("qa-schematic-import-stage", "before-read");',
        '                            Schematic s = Schematics.read(file);',
        '                            arc.Core.settings.put("qa-schematic-import-stage", "after-read");'
    ))
)
reported_try = reported_try.replace(
    '                            schematics.add(s);',
    chr(10).join((
        '                            arc.Core.settings.put("qa-schematic-import-stage", "before-add");',
        '                            schematics.add(s);',
        '                            arc.Core.settings.put("qa-schematic-import-stage", "after-add");'
    ))
)
reported_try = reported_try.replace(
    '                            last = s;',
    chr(10).join((
        '                            last = s;',
        '                            arc.Core.settings.put("qa-schematic-import-stage", "success");'
    ))
)
reported_try = reported_try.replace(
    '                            Log.err(e);',
    chr(10).join((
        '                            arc.Core.settings.put("qa-schematic-import-stage", "catch");',
        '                            Log.err(e);'
    ))
)
if dialog.count(source_try) != 1:
    raise SystemExit("Pinned native MSCH FileChooser parser callback changed")
dialog = dialog.replace(source_try, reported_try, 1)
dialog_path.write_text(dialog, encoding="utf-8")
runpy.run_path(str(ROOT / "scripts/patch-browser-text-input-web.py"), run_name="__main__")
print("Stock Schematics.load: local Web schematics preserved without mandatory Mods startup")
