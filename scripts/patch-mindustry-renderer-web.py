#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Renderer.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry Renderer source: {PATH}")

text = PATH.read_text(encoding="utf-8")
old = '''            mainExecutor.submit(() -> {
                for(int i = 0; i < lines.length; i += 4){
                    lines[i + 3] = (byte)255;
                }
                Pixmap fullPixmap = new Pixmap(w, h);
                Buffers.copy(lines, 0, fullPixmap.pixels, lines.length);
                Fi file = screenshotDirectory.child("screenshot-" + Time.millis() + ".png");
                PixmapIO.writePng(file, fullPixmap);
                fullPixmap.dispose();
                app.post(() -> ui.showInfoFade(bundle.format("screenshot", file.toString())));
            });
'''
new = '''            // Web: TeaVM has no ExecutorService. The framebuffer readback already
            // happened synchronously above, so defer PNG post-processing to Arc's
            // browser event-loop queue instead of a JVM worker thread.
            app.post(() -> {
                for(int i = 0; i < lines.length; i += 4){
                    lines[i + 3] = (byte)255;
                }
                Pixmap fullPixmap = new Pixmap(w, h);
                Buffers.copy(lines, 0, fullPixmap.pixels, lines.length);
                Fi directory = screenshotDirectory != null ? screenshotDirectory : settings.getDataDirectory().child("screenshots/");
                directory.mkdirs();
                Fi file = directory.child("screenshot-" + Time.millis() + ".png");
                PixmapIO.writePng(file, fullPixmap);
                fullPixmap.dispose();
                ui.showInfoFade(bundle.format("screenshot", file.toString()));
            });
'''
if text.count(old) != 1:
    raise SystemExit("Renderer Web screenshot patch no longer matches pinned upstream")
text = text.replace(old, new, 1)

# The production screenshot path must stay reachable without introducing a JVM
# executor. PNG encoding remains functional through the browser-backed Fi layer.
if "mainExecutor.submit" in text[text.index("public void takeMapScreenshot()") :]:
    raise SystemExit("Renderer Web screenshot patch retained ExecutorService dispatch")
for marker in (
    "ScreenUtils.getFrameBufferPixels",
    "PixmapIO.writePng(file, fullPixmap);",
    'settings.getDataDirectory().child("screenshots/")',
):
    if marker not in text:
        raise SystemExit(f"Renderer Web screenshot patch lost required functionality marker: {marker}")

PATH.write_text(text, encoding="utf-8")
print("Applied Web event-loop whole-map screenshot post-processing without ExecutorService")
