#!/usr/bin/env python3
"""Browser-only bridge from stock processor configure buttons to native LCanvas.

The Web runtime calls UI.loadSync(), not UI.init(), so ui.logic is deliberately
null. Do not silently dereference that desktop dialog from LogicBuild.
"""
from pathlib import Path

path = Path("work/Mindustry/core/src/mindustry/world/blocks/logic/LogicBlock.java")
if not path.is_file():
    raise SystemExit("Pinned LogicBlock source is missing")
source = path.read_text(encoding="utf-8")
old_class = """    public class LogicBuild extends Building implements Ranged, LReadable, LWritable{"""
new_class = """    /** Native Arc Scene browser editor request; config remains stock LogicBuild. */
    public static class WebLogicEditorRequest{
        public final LogicBuild build;
        public final boolean forceEditor;

        public WebLogicEditorRequest(LogicBuild build, boolean forceEditor){
            this.build = build;
            this.forceEditor = forceEditor;
        }
    }

    public class LogicBuild extends Building implements Ranged, LReadable, LWritable{"""
if source.count(old_class) != 1:
    raise SystemExit("Expected exactly one LogicBuild declaration")
source = source.replace(old_class, new_class, 1)

old_edit = """        public void showEditDialog(boolean forceEditor){
            ui.logic.show(code, executor, privileged, code -> {"""
new_edit = """        public void showEditDialog(boolean forceEditor){
            if(ui.logic == null){
                Events.fire(new WebLogicEditorRequest(this, forceEditor));
                return;
            }
            ui.logic.show(code, executor, privileged, code -> {"""
if source.count(old_edit) != 1:
    raise SystemExit("LogicBuild original showEditDialog anchor changed")
source = source.replace(old_edit, new_edit, 1)
path.write_text(source, encoding="utf-8")
