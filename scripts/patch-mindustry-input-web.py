#!/usr/bin/env python3
from pathlib import Path
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
input_path.write_text(input_text.replace(old_lock, new_lock, 1), encoding="utf-8")

mobile_text = mobile_path.read_text(encoding="utf-8")
old_touch_up = """    @Override\n    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){\n        lastZoom = renderer.getScale();\n"""
new_touch_up = """    @Override\n    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){\n        lastZoom = currentWebSafeScale();\n"""
if old_touch_up not in mobile_text:
    raise SystemExit("MobileInput touchUp Web patch no longer matches pinned upstream")
mobile_text = mobile_text.replace(old_touch_up, new_touch_up, 1)

old_zoom = """    @Override\n    public boolean zoom(float initialDistance, float distance){\n        if(Core.settings.getBool(\"keyboard\")) return false;\n        if(lastZoom < 0){\n            lastZoom = renderer.getScale();\n        }\n\n        renderer.setScale(distance / initialDistance * lastZoom);\n        return true;\n    }\n"""
new_zoom = """    @Override\n    public boolean zoom(float initialDistance, float distance){\n        if(Core.settings.getBool(\"keyboard\") || initialDistance <= 0f) return false;\n        if(lastZoom < 0){\n            lastZoom = currentWebSafeScale();\n        }\n\n        setWebSafeScale(distance / initialDistance * lastZoom);\n        return true;\n    }\n\n    /**\n     * Browser bootstrap activates the stock MobileInput graph one milestone before the\n     * full Renderer module. Keep pinch/touch-up safe during that transition; once the\n     * Renderer exists these methods delegate to the normal renderer scale path.\n     */\n    private float currentWebSafeScale(){\n        if(renderer != null) return renderer.getScale();\n        if(Core.camera == null || Core.graphics == null || Core.camera.width <= 0f) return 1f;\n        return Core.graphics.getWidth() / Core.camera.width;\n    }\n\n    private void setWebSafeScale(float scale){\n        scale = Mathf.clamp(scale, 0.5f, 6f);\n        if(renderer != null){\n            renderer.setScale(scale);\n        }else if(Core.camera != null && Core.graphics != null){\n            Core.camera.width = Core.graphics.getWidth() / scale;\n            Core.camera.height = Core.graphics.getHeight() / scale;\n            Core.camera.update();\n        }\n    }\n"""
if old_zoom not in mobile_text:
    raise SystemExit("MobileInput zoom Web patch no longer matches pinned upstream")
mobile_text = mobile_text.replace(old_zoom, new_zoom, 1)
mobile_path.write_text(mobile_text, encoding="utf-8")
