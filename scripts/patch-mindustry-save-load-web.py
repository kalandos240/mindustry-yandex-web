#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"


def patch(path_rel, replacements):
    path = CORE / path_rel
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry source: {path}")
    text = path.read_text(encoding="utf-8")
    for old, new, label in replacements:
        if old not in text:
            raise SystemExit(f"Save-load Web patch no longer matches pinned upstream ({label}): {path_rel}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


# Stock SaveIO.load() calls Logic.reset(). Constructing Logic just to execute that
# tiny reset routine makes its full AI/server/update-loop graph reachable in TeaVM.
# Keep the exact pinned reset semantics in SaveIO itself for the Web checkout.
patch("io/SaveIO.java", [
    ('import mindustry.game.*;\nimport mindustry.io.versions.*;',
     'import mindustry.game.*;\nimport mindustry.core.*;\nimport mindustry.gen.*;\nimport mindustry.io.versions.*;',
     'SaveIO Web reset imports'),
    ('            logic.reset();',
     '            webReset();',
     'SaveIO logic.reset'),
    ('    /** Loads from a deflated (!) input stream. */\n    public static void load(InputStream is, WorldContext context) throws SaveException{',
     '''    /** Web equivalent of the pinned Logic.reset() save-load preamble. */\n    private static void webReset(){\n        Groups.clear();\n        Time.clear();\n        Events.fire(new ResetEvent());\n        world.tiles = new Tiles(0, 0);\n\n        state.data.unload();\n        var previous = state.getState();\n        state = new GameState();\n        Events.fire(new StateChangeEvent(previous, GameState.State.menu));\n\n        Core.settings.manualSave();\n    }\n\n    /** Loads from a deflated (!) input stream. */\n    public static void load(InputStream is, WorldContext context) throws SaveException{''',
     'SaveIO webReset helper'),
])

# Restore save metadata without desktop Control/input/camera or the desktop Maps
# registry. The binary format is untouched; the map tag already contains enough
# identity/dimensions for a browser-loaded save. Preserve legacy-version readRules.
old_read_meta = '''    public void readMeta(DataInput stream, SaveReadState saveState) throws IOException{\n        StringMap map = readStringMap(stream);\n\n        state.wave = map.getInt("wave");\n        state.wavetime = map.getFloat("wavetime", state.rules.waveSpacing);\n        state.tick = map.getFloat("tick");\n        state.stats = JsonIO.read(GameStats.class, map.get("stats", "{}"));\n        state.mapLocales = JsonIO.read(MapLocales.class, map.get("locales", "{}"));\n\n        saveState.ruleString = map.get("rules", "{}");\n\n        //for versions >= 13, rules are parsed after data patches are loaded\n        if(version < 13){\n            readRules(saveState);\n        }\n\n        if(!headless){\n            Tmp.v1.tryFromString(map.get("viewpos"));\n            Core.camera.position.set(Tmp.v1);\n            player.set(Tmp.v1);\n\n            control.input.controlledType = content.getByName(ContentType.unit, map.get("controlledType", "<none>"));\n            Team team = Team.get(map.getInt("playerteam", state.rules.defaultTeam.id));\n            if(!net.client() && team != Team.derelict){\n                player.team(team);\n            }\n\n            var groups = JsonIO.read(IntSeq[].class, map.get("controlGroups", "null"));\n            if(groups != null && groups.length == control.input.controlGroups.length){\n                control.input.controlGroups = groups;\n            }\n        }\n\n        Map worldmap = maps.byName(map.get("mapname", "\\\\\\\\"));\n        state.map = worldmap == null ? new Map(StringMap.of(\n            "name", map.get("mapname", "Unknown"),\n            "width", 1,\n            "height", 1\n        )) : worldmap;\n    }'''
new_read_meta = '''    public void readMeta(DataInput stream, SaveReadState saveState) throws IOException{\n        StringMap map = readStringMap(stream);\n\n        state.wave = map.getInt("wave");\n        state.wavetime = map.getFloat("wavetime", state.rules.waveSpacing);\n        state.tick = map.getFloat("tick");\n        state.stats = JsonIO.read(GameStats.class, map.get("stats", "{}"));\n        state.mapLocales = JsonIO.read(MapLocales.class, map.get("locales", "{}"));\n        saveState.ruleString = map.get("rules", "{}");\n\n        if(version < 13){\n            readRules(saveState);\n        }\n\n        String name = map.get("mapname", "Unknown");\n        state.map = new Map(StringMap.of(\n            "name", name,\n            "width", map.get("width", "1"),\n            "height", map.get("height", "1")\n        ));\n    }'''

old_read_rules = '''    public void readRules(SaveReadState saveState){\n        if(saveState.ruleString == null) return; //in NetworkIO, rules are null, not read here\n        state.rules = JsonIO.read(Rules.class, saveState.ruleString);\n\n        if(state.rules.spawns.isEmpty()) state.rules.spawns = waves.get();\n\n        if(saveState.context.getSector() != null){\n            state.rules.sector = saveState.context.getSector();\n            if(state.rules.sector != null){\n                state.rules.sector.planet.applyRules(state.rules);\n            }\n        }\n\n        //replace the default serpulo env with erekir\n        if(state.rules.planet == Planets.serpulo && state.rules.hasEnv(Env.scorching)){\n            state.rules.planet = Planets.erekir;\n        }\n    }'''
new_read_rules = '''    public void readRules(SaveReadState saveState){\n        if(saveState.ruleString == null) return; //in NetworkIO, rules are null, not read here\n        state.rules = JsonIO.read(Rules.class, saveState.ruleString);\n\n        if(state.rules.spawns.isEmpty()){\n            if(Vars.waves == null) Vars.waves = new Waves();\n            state.rules.spawns = Vars.waves.get();\n        }\n\n        if(saveState.context.getSector() != null){\n            state.rules.sector = saveState.context.getSector();\n            if(state.rules.sector != null){\n                state.rules.sector.planet.applyRules(state.rules);\n            }\n        }\n\n        //replace the default serpulo env with erekir\n        if(state.rules.planet == Planets.serpulo && state.rules.hasEnv(Env.scorching)){\n            state.rules.planet = Planets.erekir;\n        }\n    }'''

patch("io/SaveVersion.java", [
    (old_read_meta, new_read_meta, 'SaveVersion browser readMeta'),
    (old_read_rules, new_read_rules, 'SaveVersion browser readRules'),
])

print("Applied browser-safe stock SaveIO.load/reset/read overlays")
