#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"


def source(path_rel):
    path = CORE / path_rel
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry source: {path}")
    return path, path.read_text(encoding="utf-8")


def patch(path_rel, replacements):
    path, text = source(path_rel)
    for old, new, label in replacements:
        if text.count(old) != 1:
            raise SystemExit(f"Save-load Web patch expected exactly one pinned match ({label}): {path_rel}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def replace_method(path_rel, start_marker, end_marker, replacement, label):
    path, text = source(path_rel)
    if text.count(start_marker) != 1 or text.count(end_marker) != 1:
        raise SystemExit(f"Save-load Web method boundary no longer matches pinned upstream ({label}): {path_rel}")
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    if end <= start:
        raise SystemExit(f"Save-load Web method boundaries are invalid ({label}): {path_rel}")
    path.write_text(text[:start] + replacement + "\n\n" + text[end:], encoding="utf-8")


# EntityGroup.clear() invokes Entityc.remove() on every old entity. That is useful for
# a live desktop teardown, but a save load immediately discards every group/world and
# causes TeaVM to pull unit-destroy/audio/content-parser callbacks into reachability.
# Add a dedicated raw reset used only by the Web save-load boundary.
patch("entities/EntityGroup.java", [
    ('''    public void clear(){\n        clearing = true;\n\n        array.each(Entityc::remove);\n        array.clear();\n        if(map != null) map.clear();\n        Pools.freeAll(timeRuns, true);\n        timeRuns.clear();\n\n        clearing = false;\n    }''',
     '''    public void clearRaw(){\n        clearing = true;\n        array.clear();\n        if(map != null) map.clear();\n        Pools.freeAll(timeRuns, true);\n        timeRuns.clear();\n        clearing = false;\n    }\n\n    public void clear(){\n        clearing = true;\n\n        array.each(Entityc::remove);\n        array.clear();\n        if(map != null) map.clear();\n        Pools.freeAll(timeRuns, true);\n        timeRuns.clear();\n\n        clearing = false;\n    }''',
     'EntityGroup.clearRaw'),
])

# Stock SaveIO.load() calls Logic.reset(). Constructing Logic just to execute that
# small reset routine makes its full AI/server/update-loop graph reachable in TeaVM.
# Keep the pinned reset semantics directly at the Web SaveIO boundary, while clearing
# old entity groups without lifecycle callbacks because the old world is discarded.
# The browser release reads/writes the current v13 format only; pruning Save1..Save12
# removes migration-only classes and legacy patch infrastructure from the JS graph.
patch("io/SaveIO.java", [
    ('public static final Seq<SaveVersion> versionArray = Seq.with(new Save1(), new Save2(), new Save3(), new Save4(), new Save5(), new Save6(), new Save7(), new Save8(), new Save9(), new Save10(), new Save11(), new Save12(), new Save13());',
     'public static final Seq<SaveVersion> versionArray = Seq.with(new Save13()); // Web: current v13 saves only.',
     'SaveIO current-only version graph'),
    ('import mindustry.game.EventType.*;\nimport mindustry.io.versions.*;',
     'import mindustry.game.EventType.*;\nimport mindustry.core.*;\nimport mindustry.gen.*;\nimport mindustry.io.versions.*;',
     'SaveIO Web reset imports'),
    ('            logic.reset();',
     '            webReset();',
     'SaveIO logic.reset'),
    ('    /** Loads from a deflated (!) input stream. */\n    public static void load(InputStream is, WorldContext context) throws SaveException{',
     '''    /** Web equivalent of the pinned Logic.reset() save-load preamble. */\n    private static void webReset(){\n        if(Groups.all != null) Groups.all.clearRaw();\n        if(Groups.player != null) Groups.player.clearRaw();\n        if(Groups.bullet != null) Groups.bullet.clearRaw();\n        if(Groups.unit != null) Groups.unit.clearRaw();\n        if(Groups.build != null) Groups.build.clearRaw();\n        if(Groups.sync != null) Groups.sync.clearRaw();\n        if(Groups.draw != null) Groups.draw.clearRaw();\n        if(Groups.fire != null) Groups.fire.clearRaw();\n        if(Groups.puddle != null) Groups.puddle.clearRaw();\n        if(Groups.weather != null) Groups.weather.clearRaw();\n        if(Groups.label != null) Groups.label.clearRaw();\n        if(Groups.powerGraph != null) Groups.powerGraph.clearRaw();\n        Time.clear();\n        Events.fire(new ResetEvent());\n        world.tiles = new Tiles(0, 0);\n\n        // Yandex Web rejects non-empty data-patch assets at readDataPatches().\n        // Calling DataManager.unload() here would only traverse empty desktop audio/image\n        // loaders and pull JNI SoLoud/DataImagePacker code into the TeaVM graph.\n        var previous = state.getState();\n        state = new GameState();\n        Events.fire(new StateChangeEvent(previous, GameState.State.menu));\n\n        Core.settings.manualSave();\n    }\n\n    /** Loads from a deflated (!) input stream. */\n    public static void load(InputStream is, WorldContext context) throws SaveException{''',
     'SaveIO webReset helper'),
])

# This reflection helper only used isAnonymousClass() to improve an exception label.
# TeaVM does not implement that Class API. Keep all null-field validation behavior,
# but use the concrete class simple name for the diagnostic on Web.
patch("mod/ContentParser.java", [
    ('((object.getClass().isAnonymousClass() ? object.getClass().getSuperclass() : object.getClass()).getSimpleName())',
     'object.getClass().getSimpleName()',
     'ContentParser anonymous diagnostic'),
])

# Restore gameplay metadata without desktop Control/input/camera or the desktop Maps
# registry. The binary v13 format and region reader remain stock. Method-boundary
# replacement is intentionally strict: exactly one pinned readMeta/readRules pair must exist.
read_meta = '''    public void readMeta(DataInput stream, SaveReadState saveState) throws IOException{\n        StringMap map = readStringMap(stream);\n\n        state.wave = map.getInt("wave");\n        state.wavetime = map.getFloat("wavetime", state.rules.waveSpacing);\n        state.tick = map.getFloat("tick");\n        state.stats = JsonIO.read(GameStats.class, map.get("stats", "{}"));\n        state.mapLocales = JsonIO.read(MapLocales.class, map.get("locales", "{}"));\n        saveState.ruleString = map.get("rules", "{}");\n\n        if(version < 13){\n            readRules(saveState);\n        }\n\n        String name = map.get("mapname", "Unknown");\n        state.map = new Map(StringMap.of(\n            "name", name,\n            "width", map.get("width", "1"),\n            "height", map.get("height", "1")\n        ));\n    }'''

replace_method(
    "io/SaveVersion.java",
    '    public void readMeta(DataInput stream, SaveReadState saveState) throws IOException{',
    '    public void readRules(SaveReadState saveState){',
    read_meta,
    'SaveVersion browser readMeta'
)

read_rules = '''    public void readRules(SaveReadState saveState){\n        if(saveState.ruleString == null) return; //in NetworkIO, rules are null, not read here\n        state.rules = JsonIO.read(Rules.class, saveState.ruleString);\n\n        if(state.rules.spawns.isEmpty()){\n            if(waves == null) waves = new Waves();\n            state.rules.spawns = waves.get();\n        }\n\n        if(saveState.context.getSector() != null){\n            state.rules.sector = saveState.context.getSector();\n            if(state.rules.sector != null){\n                state.rules.sector.planet.applyRules(state.rules);\n            }\n        }\n\n        if(state.rules.planet == Planets.serpulo && state.rules.hasEnv(Env.scorching)){\n            state.rules.planet = Planets.erekir;\n        }\n    }'''

replace_method(
    "io/SaveVersion.java",
    '    public void readRules(SaveReadState saveState){',
    '    public void writeMap(DataOutput stream) throws IOException{',
    read_rules,
    'SaveVersion browser readRules'
)

# Yandex ships no mods, external asset cache or data patches. Keep the exact current
# v13 patches region header, but fail closed if a save contains assets instead of
# making the browser pull desktop DataImagePacker/DataPatcher infrastructure.
read_patches = '''    public void readDataPatches(DataInput stream, SaveReadState saveState) throws IOException{\n        stream.readInt(); //patch format version; current Web package has no patch assets\n        int total = stream.readInt();\n        if(total != 0){\n            throw new IOException("Mindustry Web cannot load saves containing mod/data patch assets: " + total);\n        }\n\n        readRules(saveState);\n    }'''

replace_method(
    "io/SaveVersion.java",
    '    public void readDataPatches(DataInput stream, SaveReadState saveState) throws IOException{',
    '    public void writeDataPatches(DataOutput stream, boolean forceEmbed) throws IOException{',
    read_patches,
    'SaveVersion browser readDataPatches'
)

# Versions below 11 used this method to synthesize an empty data-patch load event,
# which reaches DataManager/DataImagePacker even though current v13 saves never take
# that branch. The Web package only exposes Save13, so keep only content remapping.
read_content_header = '''    public void readContentHeader(DataInput stream) throws IOException{\n        int mapped = stream.readUnsignedByte();\n\n        MappableContent[][] map = new MappableContent[ContentType.all.length][0];\n\n        for(int i = 0; i < mapped; i++){\n            ContentType type = ContentType.all[stream.readByte()];\n            short total = stream.readShort();\n            map[type.ordinal()] = new MappableContent[total];\n\n            for(int j = 0; j < total; j++){\n                String name = stream.readUTF();\n                map[type.ordinal()][j] = content.getByName(type, type == ContentType.block ? fallback.get(name, name) : name);\n            }\n        }\n\n        content.setTemporaryMapper(map);\n    }'''

replace_method(
    "io/SaveVersion.java",
    '    public void readContentHeader(DataInput stream) throws IOException{',
    '    public void writeContentHeader(DataOutput stream) throws IOException{',
    read_content_header,
    'SaveVersion current-only content header'
)

print("Applied browser-safe current-v13 SaveIO.load/reset/read overlays")
