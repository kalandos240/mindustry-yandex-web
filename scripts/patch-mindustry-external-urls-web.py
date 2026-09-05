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
            raise SystemExit(f"External-URL patch no longer matches pinned upstream ({label}): {path_rel}")
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

print("Stripped upstream external URL constants and sector-submission URL reachability")
