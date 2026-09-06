#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch-mindustry-input-web.py <InputHandler.java> <MobileInput.java>")

input_path = Path(sys.argv[1])
mobile_path = Path(sys.argv[2])

input_text = input_path.read_text(encoding="utf-8")
old_lock = "    public Seq<Boolp> inputLocks = Seq.with(() -> renderer.isCutscene(), () -> logicCutscene);"
new_lock = "    public Seq<Boolp> inputLocks = Seq.with(() -> renderer != null && renderer.isCutscene(), () -> logicCutscene);"
if old_lock not in input_text:
    raise SystemExit("InputHandler Web lock patch no longer matches pinned upstream")
input_text = input_text.replace(old_lock, new_lock, 1)

# The stock desktop startup calls UI.init() before ClientLoadEvent invokes input.add().
# The browser milestone intentionally activates stock input earlier, after UI.loadSync(),
# so UI.hudGroup does not exist yet. Create only the normal HUD root required by
# InputHandler.add() instead of pulling the entire dialog/Control/mod graph into TeaVM.
old_add = """    public void add(){
        Core.input.getInputProcessors().remove(i -> i instanceof InputHandler || (i instanceof GestureDetector && ((GestureDetector)i).getListener() instanceof InputHandler));
"""
new_add = """    public void add(){
        if(ui.hudGroup == null){
            ui.hudGroup = new WidgetGroup();
            ui.hudGroup.setFillParent(true);
            Core.scene.add(ui.hudGroup);
        }

        Core.input.getInputProcessors().remove(i -> i instanceof InputHandler || (i instanceof GestureDetector && ((GestureDetector)i).getListener() instanceof InputHandler));
"""
if old_add not in input_text:
    raise SystemExit("InputHandler Web early HUD-root patch no longer matches pinned upstream")
input_text = input_text.replace(old_add, new_add, 1)

# Full UI.init() normally inserts an element named overlaymarker before input is added.
# During the earlier browser input milestone it is absent. Preserve the normal ordering
# when present and append to the same stock HUD root when it is not.
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
mobile_path.write_text(mobile_text, encoding="utf-8")

# Stock InputHandler and Control make more gameplay code reachable than the earlier
# shell. Apply browser-only executor/reflection and audio fixes discovered by TeaVM
# from that graph while preserving stock gameplay/input semantics.
mindustry_root = input_path.parent.parent
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
