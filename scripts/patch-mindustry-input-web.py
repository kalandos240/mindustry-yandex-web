#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch-mindustry-input-web.py <InputHandler.java> <MobileInput.java>")

input_path = Path(sys.argv[1])
mobile_path = Path(sys.argv[2])

def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} no longer matches pinned upstream")
    return text.replace(old, new, 1)

input_text = input_path.read_text(encoding="utf-8")
old_lock = "    public Seq<Boolp> inputLocks = Seq.with(() -> renderer.isCutscene(), () -> logicCutscene);"
new_lock = "    public Seq<Boolp> inputLocks = Seq.with(() -> renderer != null && renderer.isCutscene(), () -> logicCutscene);"
if old_lock not in input_text:
    raise SystemExit("InputHandler Web lock patch no longer matches pinned upstream")
input_text = input_text.replace(old_lock, new_lock, 1)

# Stock InputHandler.update() mirrors local chat visibility into Player.typing every game
# frame. Browser bootstrap intentionally runs a real playing frame before ChatFragment is
# constructed by full UI.init(); absent chat means simply "not typing" during this stage.
old_typing = "        player.typing = ui.chatfrag.shown();"
new_typing = "        player.typing = ui.chatfrag != null && ui.chatfrag.shown();"
if input_text.count(old_typing) != 1:
    raise SystemExit(f"Expected one pinned InputHandler chat typing read, found {input_text.count(old_typing)}")
input_text = input_text.replace(old_typing, new_typing, 1)

# Stock desktop startup calls UI.init() before ClientLoadEvent invokes input.add().
# Browser startup registers the processors immediately after UI.loadSync(), then a later
# local-UI stage creates hudGroup and calls add() again. Bind the stock input-owned UI as
# soon as hudGroup exists; do not require HudFragment or any dialog/fragment constructor.
old_add = """    public void add(){
        Core.input.getInputProcessors().remove(i -> i instanceof InputHandler || (i instanceof GestureDetector && ((GestureDetector)i).getListener() instanceof InputHandler));
        Core.input.addProcessor(detector = new GestureDetector(20, 0.5f, 0.3f, 0.15f, this));
        Core.input.addProcessor(this);
        if(Core.scene != null){
"""
new_add = """    public void add(){
        Core.input.getInputProcessors().remove(i -> i instanceof InputHandler || (i instanceof GestureDetector && ((GestureDetector)i).getListener() instanceof InputHandler));
        Core.input.addProcessor(detector = new GestureDetector(20, 0.5f, 0.3f, 0.15f, this));
        Core.input.addProcessor(this);
        if(Core.scene != null && ui.hudGroup != null){
"""
if old_add not in input_text:
    raise SystemExit("InputHandler Web deferred HUD binding patch no longer matches pinned upstream")
input_text = input_text.replace(old_add, new_add, 1)

# Full UI.init() normally inserts an element named overlaymarker before input is added.
# Preserve stock ordering when present; local Web input binding has no full HUD marker yet.
old_overlay = """            group.setFillParent(true);
            Vars.ui.hudGroup.addChildBefore(Core.scene.find(\"overlaymarker\"), group);

            inv.build(group);
"""
new_overlay = """            group.setFillParent(true);
            Element overlayMarker = Core.scene.find(\"overlaymarker\");
            if(overlayMarker == null){
                Vars.ui.hudGroup.addChild(group);
            }else{
                Vars.ui.hudGroup.addChildBefore(overlayMarker, group);
            }

            inv.build(group);
"""
if old_overlay not in input_text:
    raise SystemExit("InputHandler Web overlay-marker fallback patch no longer matches pinned upstream")
input_text = input_text.replace(old_overlay, new_overlay, 1)
# Browser UI.loadSync() intentionally does not create the desktop
# SchematicsDialog. Preserve stock schematic selection/Save button semantics
# by firing a typed request to the Arc Scene browser schematic panel.
old_save_request = """    protected void showSchematicSave(){
        if(lastSchematic == null) return;

        var last = lastSchematic;

        ui.showTextInput"""
new_save_request = """    public static class WebSchematicSaveRequest{
        public final Schematic schematic;

        public WebSchematicSaveRequest(Schematic schematic){
            this.schematic = schematic;
        }
    }

    protected void showSchematicSave(){
        if(lastSchematic == null) return;

        var last = lastSchematic;
        if(ui.schematics == null){
            Events.fire(new WebSchematicSaveRequest(last));
            return;
        }

        ui.showTextInput"""
if input_text.count(old_save_request) != 1:
    raise SystemExit("Schematic Save request source anchor no longer matches pinned InputHandler")
input_text = input_text.replace(old_save_request, new_save_request, 1)

input_path.write_text(input_text, encoding="utf-8")

mobile_text = mobile_path.read_text(encoding="utf-8")
old_touch_up = """    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){
        lastZoom = renderer.getScale();
"""
new_touch_up = """    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){
        lastZoom = currentWebSafeScale();
"""
if old_touch_up not in mobile_text:
    raise SystemExit("MobileInput touchUp Web patch no longer matches pinned upstream")
mobile_text = mobile_text.replace(old_touch_up, new_touch_up, 1)

old_zoom = """    @Override
    public boolean zoom(float initialDistance, float distance){
        if(Core.settings.getBool(\"keyboard\")) return false;
        if(lastZoom < 0){
            lastZoom = renderer.getScale();
        }

        renderer.setScale(distance / initialDistance * lastZoom);
        return true;
    }
"""
new_zoom = """    @Override
    public boolean zoom(float initialDistance, float distance){
        if(Core.settings.getBool(\"keyboard\") || initialDistance <= 0f) return false;
        if(lastZoom < 0){
            lastZoom = currentWebSafeScale();
        }

        setWebSafeScale(distance / initialDistance * lastZoom);
        return true;
    }

    /**
     * Browser bootstrap activates the stock MobileInput graph one milestone before the
     * full Renderer module. Keep pinch/touch-up safe during that transition; once the
     * Renderer exists these methods delegate to the normal renderer scale path.
     */
    private float currentWebSafeScale(){
        if(renderer != null) return renderer.getScale();
        if(Core.camera == null || Core.graphics == null || Core.camera.width <= 0f) return 1f;
        return Core.graphics.getWidth() / Core.camera.width;
    }

    private void setWebSafeScale(float scale){
        scale = Mathf.clamp(scale, 0.5f, 6f);
        if(renderer != null){
            renderer.setScale(scale);
        }else if(Core.camera != null && Core.graphics != null){
            Core.camera.width = Core.graphics.getWidth() / scale;
            Core.camera.height = Core.graphics.getHeight() / scale;
            Core.camera.update();
        }
    }
"""
if old_zoom not in mobile_text:
    raise SystemExit("MobileInput zoom Web patch no longer matches pinned upstream")
mobile_text = mobile_text.replace(old_zoom, new_zoom, 1)

# MobileInput's input-owned tables are built before the full ConsoleFragment exists.
# A missing console means simply "console not shown"; once UI.init/local fragments are
# introduced later, the exact stock shown() result is used again.
mobile_console_refs = mobile_text.count("ui.consolefrag.shown()")
if mobile_console_refs != 3:
    raise SystemExit(f"Expected 3 pinned MobileInput console visibility checks, found {mobile_console_refs}")
mobile_text = mobile_text.replace(
    "ui.consolefrag.shown()",
    "(ui.consolefrag != null && ui.consolefrag.shown())",
)
mobile_path.write_text(mobile_text, encoding="utf-8")

# DesktopInput also queries fragment visibility during ordinary game frames, before any
# key is pressed. Pinned v159.7 exposes HudFragment.shown as a public boolean field while
# Chat/Console/Minimap use shown() methods. Keep those exact reads transition-safe without
# constructing any of the heavy fragments merely to answer visibility questions.
desktop_path = input_path.with_name("DesktopInput.java")
desktop_text = desktop_path.read_text(encoding="utf-8")
visibility_replacements = [
    ("ui.hudfrag.shown", "(ui.hudfrag == null || ui.hudfrag.shown)", "hud visibility", 2),
    ("ui.chatfrag.shown()", "(ui.chatfrag != null && ui.chatfrag.shown())", "chat visibility", 3),
    ("ui.consolefrag.shown()", "(ui.consolefrag != null && ui.consolefrag.shown())", "console visibility", 1),
    ("ui.minimapfrag.shown()", "(ui.minimapfrag != null && ui.minimapfrag.shown())", "minimap visibility", 2),
]
for old, new, label, expected in visibility_replacements:
    count = desktop_text.count(old)
    if count != expected:
        raise SystemExit(f"Expected {expected} pinned DesktopInput {label} checks, found {count}")
    desktop_text = desktop_text.replace(old, new)
# Browser UI.loadSync() omits the heavyweight SchematicsDialog. Preserve
# the original shortcut in BrowserGameplayRuntime rather than dereferencing
# the desktop-only ui.schematics in DesktopInput's stock update.
old_schematic_toggle = "if(Core.input.keyTap(Binding.schematicMenu)) ui.schematics.toggle();"
new_schematic_toggle = "if(Core.input.keyTap(Binding.schematicMenu) && ui.schematics != null) ui.schematics.toggle();"
if desktop_text.count(old_schematic_toggle) != 1:
    raise SystemExit("Stock DesktopInput schematic hotkey anchor changed")
desktop_text = desktop_text.replace(old_schematic_toggle, new_schematic_toggle, 1)
desktop_path.write_text(desktop_text, encoding="utf-8")

# Stock InputHandler and Control make more gameplay code reachable than the earlier
# shell. Apply browser-only executor/reflection/audio fixes discovered by TeaVM while
# preserving stock gameplay/input semantics. The Web/Yandex single-player overlay
# prunes the desktop whole-map screenshot hotkey before TeaVM, so Renderer screenshot
# encoding intentionally remains unreachable instead of retaining PNG/Deflater code.
mindustry_root = input_path.parent.parent

# Schematics.loadSync() schedules a thumbnail FrameBuffer on the first frame.
# The full desktop GL/Renderer setup backs that job; the lightweight Yandex
# WebGL path has no compatible offscreen preview context at this stage.
# The native browser Schematics panel intentionally displays original block
# icon previews instead. Keep all stock .msch IO and build-plan logic intact,
# but do not enqueue an unused offscreen FrameBuffer startup job (which
# otherwise throws at frame-post #1 and prevents all menus from opening).
schematics_path = mindustry_root / "game" / "Schematics.java"
schematics_text = schematics_path.read_text(encoding="utf-8")
old_preview = """        if(shadowBuffer == null && !headless){
            Core.app.post(() -> shadowBuffer = new FrameBuffer(maxSchematicSize + padding + 8, maxSchematicSize + padding + 8));
        }
"""
if schematics_text.count(old_preview) != 1:
    raise SystemExit("Expected one pinned Schematics.loadSync thumbnail-framebuffer startup")
schematics_text = schematics_text.replace(old_preview, """        // Web: render compact original block icons instead of eager offscreen thumbnail FBOs.
""", 1)

# The lean Yandex launcher intentionally has no Mods manager: only the
# built-in game and browser-local .msch files are shipped. Stock Schematics.load
# scans mods unconditionally and dereferences null during the deferred Web UI
# startup. Preserve upstream scanning on platforms with a Mods manager.
old_mod_scan = """        mods.listFiles("schematics", (mod, file) -> {
            Schematic s = loadFile(file);
            if(s != null){
                s.mod = mod;
            }
        });"""
new_mod_scan = """        if(mods != null){
            mods.listFiles("schematics", (mod, file) -> {
                Schematic s = loadFile(file);
                if(s != null){
                    s.mod = mod;
                }
            });
        }"""
if schematics_text.count(old_mod_scan) != 1:
    raise SystemExit("Pinned Schematics mod-scanning block changed")
schematics_text = schematics_text.replace(old_mod_scan, new_mod_scan, 1)
schematics_path.write_text(schematics_text, encoding="utf-8")

unit_group = mindustry_root / "ai" / "UnitGroup.java"
building_comp = mindustry_root / "entities" / "comp" / "BuildingComp.java"
sound_control = mindustry_root / "audio" / "SoundControl.java"
subprocess.run([
    sys.executable,
    str(Path(__file__).with_name("patch-mindustry-gameplay-web.py")),
    str(unit_group),
    str(building_comp),
], check=True)
subprocess.run([
    sys.executable,
    str(Path(__file__).with_name("patch-mindustry-sound-web.py")),
    str(sound_control),
], check=True)

# The stock ControlPathfinder owns the remaining gameplay worker thread. Convert only
# its scheduler to browser-frame stepping; cluster/A*/flow-field algorithms stay stock.
subprocess.run([
    sys.executable,
    str(Path(__file__).with_name("patch-mindustry-control-pathfinder-web.py")),
], check=True)

# Stock client startup constructs Renderer (and therefore Shaders) before the queued
# Vars/content loaders execute. The Web launcher initializes those pieces explicitly;
# guard Renderer against replacing shader instances after CacheLayer captured them.
subprocess.run([
    sys.executable,
    str(Path(__file__).with_name("patch-mindustry-renderer-lifecycle-web.py")),
], check=True)
