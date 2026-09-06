#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MINDUSTRY = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
ARC = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc"


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"Missing pinned source: {path}")
    return path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Web UI runtime patch no longer matches pinned upstream ({label})")
    return text.replace(old, new, 1)


# The browser module-loop milestone executes production UI.update() against the Scene
# already created by UI.loadSync(), without eagerly constructing the enormous UI.init()
# dialog graph. TeaVM currently reports a bare NPE inside that call, so wrap each stock
# stage with a precise temporary label. BrowserApplication preserves the exception message
# in data-mindustry-error, allowing Chrome CI to identify the exact failing operation.
ui_path = MINDUSTRY / "core" / "UI.java"
ui_text = read(ui_path)
ui_text = replace_once(
    ui_text,
    '''    @Override\n    public void update(){\n        if(disableUI || Core.scene == null) return;\n\n        PerfCounter.ui.begin();\n\n        Events.fire(Trigger.uiDrawBegin);\n\n        Core.scene.act();\n        Core.scene.draw();\n\n        if(Core.input.keyTap(KeyCode.mouseLeft) && Core.scene.hasField()){\n            Element e = Core.scene.getHoverElement();\n            if(!(e instanceof TextField)){\n                Core.scene.setKeyboardFocus(null);\n            }\n        }\n\n        Events.fire(Trigger.uiDrawEnd);\n\n        PerfCounter.ui.end();\n    }\n''',
    '''    @Override\n    public void update(){\n        if(disableUI || Core.scene == null) return;\n\n        try{\n            PerfCounter.ui.begin();\n        }catch(Throwable error){\n            throw new RuntimeException("web-ui-perf-begin", error);\n        }\n\n        try{\n            Events.fire(Trigger.uiDrawBegin);\n        }catch(Throwable error){\n            throw new RuntimeException("web-ui-event-begin", error);\n        }\n\n        try{\n            Core.scene.act();\n        }catch(Throwable error){\n            throw new RuntimeException("web-ui-scene-act", error);\n        }\n\n        try{\n            Core.scene.draw();\n        }catch(Throwable error){\n            throw new RuntimeException("web-ui-scene-draw", error);\n        }\n\n        try{\n            if(Core.input.keyTap(KeyCode.mouseLeft) && Core.scene.hasField()){\n                Element e = Core.scene.getHoverElement();\n                if(!(e instanceof TextField)){\n                    Core.scene.setKeyboardFocus(null);\n                }\n            }\n        }catch(Throwable error){\n            throw new RuntimeException("web-ui-focus", error);\n        }\n\n        try{\n            Events.fire(Trigger.uiDrawEnd);\n        }catch(Throwable error){\n            throw new RuntimeException("web-ui-event-end", error);\n        }\n\n        try{\n            PerfCounter.ui.end();\n        }catch(Throwable error){\n            throw new RuntimeException("web-ui-perf-end", error);\n        }\n    }\n''',
    "UI.update diagnostic stages",
)
ui_path.write_text(ui_text, encoding="utf-8")

# Chrome #309 localized the remaining NPE to Scene.act(). Keep the stock Arc Scene
# behavior intact and label only its internal phases so the next browser run identifies
# the exact missing lifecycle dependency without bypassing scene input/action processing.
scene_path = ARC / "scene" / "Scene.java"
scene = read(scene_path)
scene = replace_once(
    scene,
    '''    /** Calls {@link #act(float)} with {@link Graphics#getDeltaTime()}. */\n    public void act(){\n        act(graphics.getDeltaTime());\n    }\n''',
    '''    /** Calls {@link #act(float)} with {@link Graphics#getDeltaTime()}. */\n    public void act(){\n        float delta;\n        try{\n            delta = graphics.getDeltaTime();\n        }catch(Throwable error){\n            throw new RuntimeException("web-scene-delta", error);\n        }\n        act(delta);\n    }\n''',
    "Arc Scene.act delta diagnostic",
)
scene = replace_once(
    scene,
    '''    public void act(float delta){\n        root.y = marginBottom;\n        root.x = marginLeft;\n        root.height = getHeight() - marginBottom - marginTop;\n        root.width = getWidth() - marginLeft - marginRight;\n\n        // Update over actors. Done in act() because actors may change position, which can fire enter/exit without an input event.\n        for(int pointer = 0, n = pointerOverActors.length; pointer < n; pointer++){\n            Element overLast = pointerOverActors[pointer];\n            // Check if pointer is gone.\n            if(!pointerTouched[pointer]){\n                if(overLast != null){\n                    pointerOverActors[pointer] = null;\n                    screenToStageCoordinates(tempCoords.set(pointerScreenX[pointer], pointerScreenY[pointer]));\n                    // Exit over last.\n                    InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);\n                    event.type = (InputEventType.exit);\n                    event.stageX = (tempCoords.x);\n                    event.stageY = (tempCoords.y);\n                    event.relatedActor = (overLast);\n                    event.pointer = (pointer);\n                    overLast.fire(event);\n                    Pools.free(event);\n                }\n                continue;\n            }\n            // Update over actor for the pointer.\n            pointerOverActors[pointer] = fireEnterAndExit(overLast, pointerScreenX[pointer], pointerScreenY[pointer], pointer);\n        }\n        // Update over element for the mouse on the desktop.\n        if(Core.app.isDesktop() || Core.app.isWeb()){\n            mouseOverElement = fireEnterAndExit(mouseOverElement, mouseScreenX, mouseScreenY, -1);\n        }else{\n            mouseOverElement = hit(mouseScreenX, mouseScreenY, true);\n        }\n\n        if(scrollFocus != null && (!scrollFocus.visible || scrollFocus.getScene() == null)) scrollFocus = null;\n        if(keyboardFocus != null && (!keyboardFocus.visible || keyboardFocus.getScene() == null)) keyboardFocus = null;\n\n        if(scrollFocus != null){\n            Element curr = scrollFocus;\n            while(curr.parent != null){\n                if(!curr.visible){\n                    scrollFocus = null;\n                    break;\n                }\n                curr = curr.parent;\n            }\n        }\n\n        root.act(delta);\n    }\n''',
    '''    public void act(float delta){\n        try{\n            root.y = marginBottom;\n            root.x = marginLeft;\n            root.height = getHeight() - marginBottom - marginTop;\n            root.width = getWidth() - marginLeft - marginRight;\n        }catch(Throwable error){\n            throw new RuntimeException("web-scene-root-layout", error);\n        }\n\n        try{\n            // Update over actors. Done in act() because actors may change position, which can fire enter/exit without an input event.\n            for(int pointer = 0, n = pointerOverActors.length; pointer < n; pointer++){\n                Element overLast = pointerOverActors[pointer];\n                // Check if pointer is gone.\n                if(!pointerTouched[pointer]){\n                    if(overLast != null){\n                        pointerOverActors[pointer] = null;\n                        screenToStageCoordinates(tempCoords.set(pointerScreenX[pointer], pointerScreenY[pointer]));\n                        // Exit over last.\n                        InputEvent event = Pools.obtain(InputEvent.class, InputEvent::new);\n                        event.type = (InputEventType.exit);\n                        event.stageX = (tempCoords.x);\n                        event.stageY = (tempCoords.y);\n                        event.relatedActor = (overLast);\n                        event.pointer = (pointer);\n                        overLast.fire(event);\n                        Pools.free(event);\n                    }\n                    continue;\n                }\n                // Update over actor for the pointer.\n                pointerOverActors[pointer] = fireEnterAndExit(overLast, pointerScreenX[pointer], pointerScreenY[pointer], pointer);\n            }\n        }catch(Throwable error){\n            throw new RuntimeException("web-scene-pointer-hover", error);\n        }\n\n        try{\n            // Update over element for the mouse on desktop/Web.\n            if(Core.app.isDesktop() || Core.app.isWeb()){\n                mouseOverElement = fireEnterAndExit(mouseOverElement, mouseScreenX, mouseScreenY, -1);\n            }else{\n                mouseOverElement = hit(mouseScreenX, mouseScreenY, true);\n            }\n        }catch(Throwable error){\n            throw new RuntimeException("web-scene-mouse-hover", error);\n        }\n\n        try{\n            if(scrollFocus != null && (!scrollFocus.visible || scrollFocus.getScene() == null)) scrollFocus = null;\n            if(keyboardFocus != null && (!keyboardFocus.visible || keyboardFocus.getScene() == null)) keyboardFocus = null;\n\n            if(scrollFocus != null){\n                Element curr = scrollFocus;\n                while(curr.parent != null){\n                    if(!curr.visible){\n                        scrollFocus = null;\n                        break;\n                    }\n                    curr = curr.parent;\n                }\n            }\n        }catch(Throwable error){\n            throw new RuntimeException("web-scene-focus-cleanup", error);\n        }\n\n        try{\n            root.act(delta);\n        }catch(Throwable error){\n            throw new RuntimeException("web-scene-root-act", error);\n        }\n    }\n''',
    "Arc Scene.act phase diagnostics",
)
scene_path.write_text(scene, encoding="utf-8")

# The advanced embedded map asset/mod-content editor pulls hashing, dynamic content
# patching and image-packing worker executors into every UI.init() through MapInfoDialog.
# It is not needed to play, create ordinary maps, edit rules/waves/objectives/locales,
# or use processors. Keep the ordinary editor and omit only this mod-authoring surface.
map_info_path = MINDUSTRY / "editor" / "MapInfoDialog.java"
map_info = read(map_info_path)
map_info = replace_once(
    map_info,
    "    private MapAssetsDialog patches = new MapAssetsDialog();\n",
    "    // Web/Yandex: embedded mod-asset authoring is not part of the browser editor.\n",
    "MapInfoDialog embedded asset editor field",
)
map_info = replace_once(
    map_info,
    '''                r.row();\n\n                r.button("@asset.title", Icon.fileCode, style, () -> {\n                    hide();\n                    patches.show();\n                }).marginLeft(10f).colspan(2).width(460f).row();\n''',
    '''                // Web/Yandex: embedded mod-asset authoring is intentionally omitted.\n''',
    "MapInfoDialog embedded asset editor button",
)
map_info_path.write_text(map_info, encoding="utf-8")

# Community server discovery is permanently unavailable in the single-player Yandex
# build. Leaving this preference in SettingsMenuDialog makes JoinDialog.fetchServers()
# and Arc Http's ThreadPoolExecutor reachable even though JoinDialog itself is pruned.
settings_path = MINDUSTRY / "ui" / "dialogs" / "SettingsMenuDialog.java"
settings = read(settings_path)
settings = replace_once(
    settings,
    '''        game.checkPref("communityservers", true, val -> {\n            defaultServers.clear();\n            if(val){\n                JoinDialog.fetchServers();\n            }\n        });\n\n''',
    '''        // Web/Yandex single-player: community server discovery is unavailable.\n\n''',
    "SettingsMenuDialog community server preference",
)
settings_path.write_text(settings, encoding="utf-8")

# Map preview generation is user-triggered editor work. The desktop implementation
# submits it to mainExecutor and keeps a Future solely to wait during Apply. In the
# browser there is one event loop, so execute the exact filter algorithm synchronously.
map_gen_path = MINDUSTRY / "editor" / "MapGenerateDialog.java"
map_gen = read(map_gen_path)
map_gen = replace_once(map_gen, "import java.util.concurrent.*;\n\n", "", "MapGenerateDialog concurrent import")
map_gen = replace_once(map_gen, "    Future<?> result;\n", "", "MapGenerateDialog Future field")
map_gen = replace_once(
    map_gen,
    '''        if(result != null){\n            //ignore errors yay\n            try{\n                result.get();\n            }catch(Exception e){}\n        }\n\n''',
    "",
    "MapGenerateDialog Future wait",
)
map_gen = replace_once(
    map_gen,
    '''        result = mainExecutor.submit(() -> {\n            try{\n''',
    '''        // Web: run the stock preview algorithm on this browser event-loop turn.\n        try{\n''',
    "MapGenerateDialog executor submit",
)
map_gen = replace_once(
    map_gen,
    '''            }catch(Exception e){\n                generating = false;\n                Log.err(e);\n            }\n            world.setGenerating(false);\n        });\n''',
    '''        }catch(Exception e){\n            generating = false;\n            Log.err(e);\n        }\n        world.setGenerating(false);\n''',
    "MapGenerateDialog executor closure",
)
for forbidden in ("Future<", "mainExecutor.submit", "ExecutorService"):
    if forbidden in map_gen:
        raise SystemExit(f"MapGenerateDialog Web patch left JVM async marker: {forbidden}")
map_gen_path.write_text(map_gen, encoding="utf-8")

# Campaign planet mesh refresh is rare and already has the stock synchronous reloadMesh
# implementation. Reuse it on Web instead of retaining mainExecutor in the scene graph.
planet_path = MINDUSTRY / "type" / "Planet.java"
planet = read(planet_path)
planet = replace_once(
    planet,
    '''    public void reloadMeshAsync(){\n        if(headless) return;\n\n        mainExecutor.submit(() -> {\n            var newMesh = meshLoader.get();\n\n            Core.app.post(() -> {\n                if(mesh != null){\n                    mesh.dispose();\n                }\n                mesh = newMesh;\n            });\n        });\n    }\n''',
    '''    public void reloadMeshAsync(){\n        // Web: one browser event loop; preserve mesh semantics without a JVM executor.\n        reloadMesh();\n    }\n''',
    "Planet.reloadMeshAsync",
)
planet_path.write_text(planet, encoding="utf-8")

# TeaVM's Class subset has getSimpleName()/getSuperclass(), but not isAnonymousClass().
# Java anonymous classes have an empty simple name, so this is the equivalent test and
# preserves the upstream superclass fallback used by serializers/localization helpers.
# Files with dedicated compatibility overlays are deliberately excluded so those
# patches retain ownership of their pinned source transformations.
anonymous_replacements = 0
anonymous_owned_elsewhere = {
    MINDUSTRY / "world" / "Block.java",
    MINDUSTRY / "io" / "JsonIO.java",
}
for path in MINDUSTRY.rglob("*.java"):
    if path in anonymous_owned_elsewhere:
        continue
    text = path.read_text(encoding="utf-8")
    count = text.count(".isAnonymousClass()")
    if count:
        text = text.replace(".isAnonymousClass()", ".getSimpleName().isEmpty()")
        path.write_text(text, encoding="utf-8")
        anonymous_replacements += count
if anonymous_replacements < 5:
    raise SystemExit(f"Expected multiple pinned Mindustry anonymous-class checks, replaced {anonymous_replacements}")

# Arc's byte-count helper uses java.text.StringCharacterIterator, absent from the
# TeaVM JavaScript class library. Keep identical SI-unit behavior with a tiny array.
strings_path = ARC / "util" / "Strings.java"
strings = read(strings_path)
strings = replace_once(strings, "import java.text.*;\n", "", "Arc Strings java.text import")
strings = replace_once(
    strings,
    '''    //https://stackoverflow.com/a/3758880\n    public static String formatByteCount(long bytes){\n        if(-1000 < bytes && bytes < 1000) return bytes + " B";\n\n        CharacterIterator ci = new StringCharacterIterator("kMGTPE");\n        while(bytes <= -999_950 || bytes >= 999_950){\n            bytes /= 1000;\n            ci.next();\n        }\n        return String.format("%.1f %cB", bytes / 1000.0, ci.current());\n    }\n''',
    '''    // Web-safe SI byte formatter; avoids java.text, which TeaVM does not provide.\n    public static String formatByteCount(long bytes){\n        if(-1000 < bytes && bytes < 1000) return bytes + " B";\n\n        final char[] units = {'k', 'M', 'G', 'T', 'P', 'E'};\n        double value = bytes;\n        int unit = 0;\n        while((value <= -999_950d || value >= 999_950d) && unit < units.length - 1){\n            value /= 1000d;\n            unit++;\n        }\n        value /= 1000d;\n        long tenths = Math.round(value * 10d);\n        return (tenths / 10) + "." + Math.abs(tenths % 10) + " " + units[unit] + "B";\n    }\n''',
    "Arc Strings.formatByteCount",
)
if "StringCharacterIterator" in strings:
    raise SystemExit("Arc Strings Web patch left StringCharacterIterator reachability")
strings_path.write_text(strings, encoding="utf-8")

print(
    "Applied Web-safe local UI runtime: exact UI/Scene diagnostics, editor preview sync, planet mesh sync, "
    f"anonymous reflection compatibility ({anonymous_replacements}), byte formatter, and single-player settings"
)
