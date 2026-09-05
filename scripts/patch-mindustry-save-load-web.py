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


# Stock SaveIO.load() calls Logic.reset(). Constructing Logic just to execute that
# small reset routine makes its full AI/server/update-loop graph reachable in TeaVM.
# Keep the pinned reset semantics directly at the Web SaveIO boundary.
patch("io/SaveIO.java", [
    ('import mindustry.game.EventType.*;\nimport mindustry.io.versions.*;',
     'import mindustry.game.EventType.*;\nimport mindustry.core.*;\nimport mindustry.gen.*;\nimport mindustry.io.versions.*;',
     'SaveIO Web reset imports'),
    ('            logic.reset();',
     '            webReset();',
     'SaveIO logic.reset'),
    ('    /** Loads from a deflated (!) input stream. */\n    public static void load(InputStream is, WorldContext context) throws SaveException{',
     '''    /** Web equivalent of the pinned Logic.reset() save-load preamble. */\n    private static void webReset(){\n        Groups.clear();\n        Time.clear();\n        Events.fire(new ResetEvent());\n        world.tiles = new Tiles(0, 0);\n\n        state.data.unload();\n        var previous = state.getState();\n        state = new GameState();\n        Events.fire(new StateChangeEvent(previous, GameState.State.menu));\n\n        Core.settings.manualSave();\n    }\n\n    /** Loads from a deflated (!) input stream. */\n    public static void load(InputStream is, WorldContext context) throws SaveException{''',
     'SaveIO webReset helper'),
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

read_rules = '''    public void readRules(SaveReadState saveState){\n        if(saveState.ruleString == null) return; //in NetworkIO, rules are null, not read here\n        state.rules = JsonIO.read(Rules.class, saveState.ruleString);\n\n        if(state.rules.spawns.isEmpty()){\n            if(waves == null) waves = new Waves();\n            state.rules.spawns = waves.get();\n        }\n\n        if(saveState.context.getSector() != null){\n            state.rules.sector = saveState.context.getSector();\n            if(state.rules.sector != null){\n                state.rules.sector.planet.applyRules(state.rules);\n            }\n        }\n\n        //replace the default serpulo env with erekir\n        if(state.rules.planet == Planets.serpulo && state.rules.hasEnv(Env.scorching)){\n            state.rules.planet = Planets.erekir;\n        }\n    }'''

replace_method(
    "io/SaveVersion.java",
    '    public void readRules(SaveReadState saveState){',
    '    public void writeMap(DataOutput stream) throws IOException{',
    read_rules,
    'SaveVersion browser readRules'
)

print("Applied browser-safe stock SaveIO.load/reset/read overlays")
