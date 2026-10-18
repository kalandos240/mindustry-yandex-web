#!/usr/bin/env python3
"""Keep on-screen world/tutorial labels readable inside the portal viewport."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / 'work/Mindustry/core/src/mindustry/entities/comp/WorldLabelComp.java'
s = p.read_text()
s = s.replace('import arc.graphics.*;', 'import arc.*;\nimport arc.math.*;\nimport arc.graphics.*;', 1)
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
        boolean fit = Core.app.isWeb() && centerX + layout.width / 2f >= left - edge
            && centerX - layout.width / 2f <= right + edge && textTop >= bottom - edge
            && textTop - layout.height <= top + edge;
        float wrapWidth = Core.camera.width * 0.65f;
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
print('World labels: wrap and fit visible anchors to the Web camera viewport')
