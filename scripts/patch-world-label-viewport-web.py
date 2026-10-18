#!/usr/bin/env python3
"""Keep on-screen world/tutorial labels readable inside the portal viewport."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / 'work/Mindustry/core/src/mindustry/entities/comp/WorldLabelComp.java'
s = p.read_text()
s = s.replace('import arc.graphics.*;', 'import arc.*;\nimport arc.math.*;\nimport arc.graphics.*;', 1)
s = s.replace('    public static final byte', '    public static arc.scene.Element webHudHealth, webHudPlacement;\n\n    public static final byte', 1)
signature = '    public static void drawAt(String text, float x, float y, float layer, int flags, float fontSize, int align, int lineAlign){'
assert s.count(signature) == 1
s = s.replace(signature, signature + '\n        drawAt(text, x, y, layer, flags, fontSize, align, lineAlign, false);\n    }\n\n    public static void drawAt(String text, float x, float y, float layer, int flags, float fontSize, int align, int lineAlign, boolean tutorial){', 1)
old = '        layout.setText(font, text);\n'
new = '''        layout.setText(font, text);

        // Fit text that intersects the view, even if its anchor is just outside.
        // Fully off-screen labels stay off-screen. All measurements use world units.
        float edge = Core.camera.width * 0.015f;
        float left = Core.camera.position.x - Core.camera.width / 2f + edge;
        float right = Core.camera.position.x + Core.camera.width / 2f - edge;
        float bottom = Core.camera.position.y - Core.camera.height / 2f + edge;
        float top = Core.camera.position.y + Core.camera.height / 2f - edge;
        float padding = (flags & flagBackground) != 0 ? 1f : 0f;
        float centerX = x + (Align.isLeft(align) ? layout.width / 2f + padding : Align.isRight(align) ? -layout.width / 2f - padding : 0f);
        float textTop = y + (Align.isBottom(align) ? layout.height + padding * 1.5f : Align.isTop(align) ? -padding * 1.5f : layout.height / 2f);
        float reach = tutorial && Vars.state.isCampaign() ? Core.camera.width * 0.5f : edge;
        boolean fit = Core.app.isWeb() && centerX + layout.width / 2f >= left - reach
            && centerX - layout.width / 2f <= right + reach && textTop >= bottom - edge
            && textTop - layout.height <= top + edge;
        // Compact HUDs leave a reading area below health and beside/above
        // construction. Use actual transformed actor bounds, not device pixels.
        // Cache actors: do not scan the Scene tree for every rendered label.
        if(fit && Core.scene != null && Vars.ui != null && Vars.ui.hudfrag != null
            && Vars.ui.hudfrag.shown && (Core.scene.getWidth() < 600f || Core.scene.getHeight() < 480f)){
            if(webHudHealth == null || webHudHealth.parent == null) webHudHealth = Core.scene.root.find("web-hud-player-health");
            if(webHudPlacement == null || webHudPlacement.parent == null) webHudPlacement = Core.scene.root.find("web-placement-frame");
            float cameraLeft = Core.camera.position.x - Core.camera.width / 2f;
            float cameraBottom = Core.camera.position.y - Core.camera.height / 2f;
            if(webHudHealth != null && webHudHealth.getHeight() > 1f){
                var lower = webHudHealth.localToStageCoordinates(Tmp.v1.set(0f, 0f));
                top = Math.min(top, cameraBottom + lower.y / Core.scene.getHeight() * Core.camera.height - edge);
            }
            if(webHudPlacement != null && webHudPlacement.getWidth() > 1f){
                var lower = webHudPlacement.localToStageCoordinates(Tmp.v1.set(0f, 0f));
                var upper = webHudPlacement.localToStageCoordinates(Tmp.v2.set(webHudPlacement.getWidth(), webHudPlacement.getHeight()));
                if(lower.x > Core.scene.getWidth() * 0.45f){
                    right = Math.min(right, cameraLeft + lower.x / Core.scene.getWidth() * Core.camera.width - edge);
                }else{
                    bottom = Math.max(bottom, cameraBottom + upper.y / Core.scene.getHeight() * Core.camera.height + edge);
                }
            }
        }
        float wrapWidth = Math.max(1f, Math.min(Core.camera.width * 0.65f, right - left - 2f));
        boolean wrap = fit && layout.width > wrapWidth;
        if(wrap){
            layout.setText(font, text, Color.white, wrapWidth, lineAlign, true);
            // Match the draw call's alignment box, including inline color/icon runs.
            layout.width = wrapWidth;
        }
        float availableHeight = Math.max(1f, top - bottom - 3f);
        if(fit && layout.height > availableHeight){
            float shrink = availableHeight / layout.height;
            font.getData().setScale(font.getData().scaleX * shrink);
            layout.setText(font, text, Color.white, wrap ? wrapWidth : 0f, lineAlign, wrap);
            if(wrap) layout.width = wrapWidth;
        }
'''
assert s.count(old) == 1, 'Pinned WorldLabel layout anchor changed'
s = s.replace(old, new)
old = '        if((flags & flagBackground) != 0){\n'
new = '''        if(fit){
            float half = layout.width / 2f + border;
            x = Mathf.clamp(x, left + half, right - half);
            y = Mathf.clamp(y, bottom + layout.height + border * 1.5f, top - border * 1.5f);
        }

        if((flags & flagBackground) != 0){
'''
assert s.count(old) == 1, 'Pinned WorldLabel background anchor changed'
s = s.replace(old, new)
old = '        font.draw(text, x + tx, y, 0, lineAlign, false);'
new = '        font.draw(text, wrap ? x - layout.width / 2f : x + tx, y, wrap ? layout.width : 0f, lineAlign, wrap);'
assert s.count(old) == 1, 'Pinned WorldLabel draw anchor changed'
p.write_text(s.replace(old, new))
markers = root / 'work/Mindustry/core/src/mindustry/game/MapObjectives.java'
source = markers.read_text()
old_call = 'WorldLabel.drawAt(fetchedText, pos.x, pos.y, drawLayer, flags, fontSize * scaleFactor, textAlign, lineAlign);'
assert source.count(old_call) == 1, 'Pinned tutorial text marker draw anchor changed'
markers.write_text(source.replace(old_call, old_call[:-2] + ', true);'))
print('World labels: wrap and fit visible anchors to the Web camera viewport')
