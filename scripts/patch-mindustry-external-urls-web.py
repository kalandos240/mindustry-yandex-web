#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
ARC_CORE = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc"


def patch(path_rel, replacements):
    path = CORE / path_rel
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry source: {path}")
    text = path.read_text(encoding="utf-8")
    for old, new, label in replacements:
        if old not in text:
            raise SystemExit(f"External-URL/Web patch no longer matches pinned upstream ({label}): {path_rel}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def patch_arc(path_rel, replacements):
    path = ARC_CORE / path_rel
    if not path.is_file():
        raise SystemExit(f"Missing pinned Arc source: {path}")
    text = path.read_text(encoding="utf-8")
    for old, new, label in replacements:
        if old not in text:
            raise SystemExit(f"Arc Web patch no longer matches pinned upstream ({label}): {path_rel}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


# Strip all global website/server-list constants from the browser bytecode. Online
# discovery, mod feeds, Discord, issue reporting and Steam-ban feeds do not exist in
# the Yandex build. Empty arrays also prevent accidental same-origin fallback requests.
patch("Vars.java", [
    ('    public static final String ghApi = "https://api.github.com";',
     '    public static final String ghApi = ""; // Web: no external GitHub API.',
     'Vars.ghApi'),
    ('    public static final String discordURL = "https://discord.gg/mindustry";',
     '    public static final String discordURL = ""; // Web: no external Discord URL.',
     'Vars.discordURL'),
    ('    public static final String modGuideURL = "https://mindustrygame.github.io/wiki/modding/1-modding/";',
     '    public static final String modGuideURL = ""; // Web: no external mod guide.',
     'Vars.modGuideURL'),
    ('    public static final String patchesGuideURL = "https://mindustrygame.github.io/wiki/datapatches/";',
     '    public static final String patchesGuideURL = ""; // Web: no external patch guide.',
     'Vars.patchesGuideURL'),
    ('    public static final String[] serverJsonBeURLs = {"https://raw.githubusercontent.com/Anuken/MindustryServerList/master/servers_be.json", "https://cdn.jsdelivr.net/gh/anuken/mindustryserverlist/servers_be.json"};',
     '    public static final String[] serverJsonBeURLs = {}; // Web: external server discovery disabled.',
     'Vars.serverJsonBeURLs'),
    ('    public static final String[] serverJsonURLs = {"https://raw.githubusercontent.com/Anuken/MindustryServerList/master/servers_v8.json", "https://cdn.jsdelivr.net/gh/anuken/mindustryserverlist/servers_v8.json"};',
     '    public static final String[] serverJsonURLs = {}; // Web: external server discovery disabled.',
     'Vars.serverJsonURLs'),
    ('    public static final String[] modJsonURLs = {"https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json", "https://cdn.jsdelivr.net/gh/anuken/mindustrymods/mods.json"};',
     '    public static final String[] modJsonURLs = {}; // Web: external mod feed disabled.',
     'Vars.modJsonURLs'),
    ('    public static final String[] steamBansURLs = {"https://raw.githubusercontent.com/Anuken/MindustrySteamBans/master/data.json", "https://cdn.jsdelivr.net/gh/anuken/mindustrysteambans/data.json"};',
     '    public static final String[] steamBansURLs = {}; // Web: external Steam-ban feed disabled.',
     'Vars.steamBansURLs'),
    ('    public static final String reportIssueURL = "https://github.com/Anuken/Mindustry/issues/new?labels=bug&template=bug_report.md";',
     '    public static final String reportIssueURL = ""; // Web: external issue reporting disabled.',
     'Vars.reportIssueURL'),
])

# SectorPresets is loaded during vanilla content registration and normally pulls in
# SectorSubmissions, whose data table embeds Discord thread URLs. Yandex has no such
# UI/functionality, so remove the registration call at the source of reachability.
patch("content/SectorPresets.java", [
    ('        SectorSubmissions.registerSectors();',
     '        // Web: sector submission links are intentionally not registered.',
     'SectorSubmissions.registerSectors'),
])

# TeaVM 0.15 does not implement Class.isAnonymousClass(), but Arc Json uses it only
# to normalize anonymous subclasses to their superclass. Preserve that behavior with
# the JVM binary-name rule used by Java compilers (Outer$1, Outer$2, ...), avoiding
# a broad reflection downgrade. This keeps normal JSON/UBJSON save serialization,
# including MapMarkers, on the stock codec.
patch_arc("util/serialization/Json.java", [
    ('public class Json{\n    private static final boolean debug = false;',
     '''public class Json{\n    private static final boolean debug = false;\n\n    // Web: TeaVM 0.15 lacks Class.isAnonymousClass(). Java anonymous classes use\n    // a binary name whose final '$' component is numeric (Outer$1, Outer$2, ...).\n    private static boolean webIsAnonymousClass(Class type){\n        String name = type.getName();\n        int dollar = name.lastIndexOf('$');\n        if(dollar < 0 || dollar == name.length() - 1) return false;\n        for(int i = dollar + 1; i < name.length(); i++){\n            char c = name.charAt(i);\n            if(c < '0' || c > '9') return false;\n        }\n        return true;\n    }''',
     'Json.webIsAnonymousClass helper'),
    ('if(type.isAnonymousClass()) type = type.getSuperclass();',
     'if(webIsAnonymousClass(type)) type = type.getSuperclass();',
     'Json.getDefaultValues anonymous class'),
    ('if(knownType != null && knownType.isAnonymousClass()){',
     'if(knownType != null && webIsAnonymousClass(knownType)){',
     'Json.writeValue anonymous class'),
])

# The stock Serpulo pre-save hook updates only the visual planet mesh. Its async
# ExecutorService path is a desktop rendering side effect and is not part of save
# data. Keep SectorInfo.prepare()/saveInfo() fully intact and remove only this hook.
patch("maps/planet/SerpuloPlanetGenerator.java", [
    ('    public void beforeSaveWrite(Sector sector){\n        sector.planet.reloadMeshAsync();\n    }',
     '    public void beforeSaveWrite(Sector sector){\n        // Web: visual planet mesh reload is deferred; save data is already prepared.\n    }',
     'SerpuloPlanetGenerator.beforeSaveWrite'),
])

# SaveVersion is shared core code, but the Yandex/Web target deliberately has no
# desktop Control or Mods subsystem. Patch only the metadata fields that otherwise
# make those subsystems reachable. The v13 region layout and all game-state writers
# remain stock. A Web runtime may update webPlaytime before a save without depending
# from core on browser-specific classes.
patch("io/SaveVersion.java", [
    ('    public final int version;\n',
     '''    public final int version;\n    private static long webPlaytime;\n\n    public static void setWebPlaytime(long value){\n        webPlaytime = Math.max(value, 0L);\n    }\n''',
     'SaveVersion.webPlaytime bridge'),
    ('            "playtime", headless ? 0 : control.saves.getTotalPlaytime(),',
     '            "playtime", webPlaytime,',
     'SaveVersion playtime'),
    ('            "mods", JsonIO.write(mods.getModStrings().toArray(String.class)),',
     '            "mods", "[]",',
     'SaveVersion mods metadata'),
    ('            "controlGroups", headless || control == null ? "null" : JsonIO.write(control.input.controlGroups),',
     '            "controlGroups", "null",',
     'SaveVersion controlGroups metadata'),
    ('            "controlledType", headless || control.input.controlledType == null ? "null" : control.input.controlledType.name,',
     '            "controlledType", "null",',
     'SaveVersion controlledType metadata'),
])

print("Stripped upstream external URLs and applied browser-safe save serialization overlays")