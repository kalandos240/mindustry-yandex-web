#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"


def read(rel):
    path = CORE / rel
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry source: {path}")
    return path, path.read_text(encoding="utf-8")


def write(path, text):
    path.write_text(text, encoding="utf-8")


def exact(rel, old, new, label):
    path, text = read(rel)
    if old not in text:
        raise SystemExit(f"No-links patch no longer matches pinned upstream ({label}): {rel}")
    write(path, text.replace(old, new, 1))


def regex(rel, pattern, replacement, label, count=1):
    path, text = read(rel)
    out, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f"No-links regex patch expected {count} match(es), got {n} ({label}): {rel}")
    write(path, out)


# AboutDialog obtains all external destinations from Links. Keep About itself, but
# expose an empty link list in the Yandex build so there are no website buttons.
regex(
    "ui/Links.java",
    r"    private static void createLinks\(\)\{.*?\n    \}\n\n    public static LinkEntry\[\] getLinks\(\)",
    "    private static void createLinks(){\n        links = new LinkEntry[0];\n    }\n\n    public static LinkEntry[] getLinks()",
    "Links.createLinks"
)
regex(
    "ui/dialogs/AboutDialog.java",
    r'''\n            table\.button\(Icon\.link, Styles\.clearNonei, \(\) -> \{\n                if\(link\.name\.equals\("wiki"\)\) Events\.fire\(Trigger\.openWiki\);\n\n                if\(!Core\.app\.openURI\(link\.link\)\)\{\n                    ui\.showErrorMessage\("@linkfail"\);\n                    Core\.app\.setClipboardText\(link\.link\);\n                \}\n            \}\)\.size\(h - 5, h\);''',
    "",
    "AboutDialog external link button"
)

# Main menu: remove Discord banner, Mods entry and any Steam Workshop entry. Local
# About/Database/Settings remain available.
exact(
    "ui/fragments/MenuFragment.java",
    '''        parent.fill(c -> c.bottom().right().button(Icon.discord, new ImageButtonStyle(){{\n            up = discordBanner;\n        }}, ui.discord::show).visible(() -> !ui.consolefrag.shown()).marginTop(9f).marginLeft(10f).tooltip("@discord").size(84, 45).name("discord"));\n\n''',
    "",
    "Discord main-menu banner"
)
exact(
    "ui/fragments/MenuFragment.java",
    '            mods = new MobileButton(Icon.book, "@mods", ui.mods::show),\n',
    "",
    "mobile Mods button declaration"
)
path, text = read("ui/fragments/MenuFragment.java")
if text.count("            container.add(mods);\n") != 2:
    raise SystemExit("No-links patch expected two mobile Mods placements")
text = text.replace("            container.add(mods);\n", "")
write(path, text)
exact(
    "ui/fragments/MenuFragment.java",
    '                    new MenuButton("@editor", Icon.terrain, () -> checkPlay(ui.maps::show)), steam ? new MenuButton("@workshop", Icon.steam, platform::openWorkshop) : null,\n                    new MenuButton("@mods", Icon.book, ui.mods::show),\n',
    '                    new MenuButton("@editor", Icon.terrain, () -> checkPlay(ui.maps::show)),\n',
    "desktop Workshop/Mods buttons"
)

# Developer-only content documentation link must not exist even when console mode
# is enabled.
regex(
    "ui/dialogs/ContentInfoDialog.java",
    r'''\n        if\(settings\.getBool\("console"\)\)\{\n            table\.button\("@viewfields", Icon\.link, Styles\.grayt, \(\) -> \{.*?\n            \}\)\.margin\(8f\)\.pad\(4f\)\.padTop\(16f\)\.size\(300f, 50f\)\.row\(\);\n        \}\n''',
    "\n",
    "ContentInfoDialog external documentation"
)

# Editor guide link.
exact(
    "editor/data/MapAssetsDialog.java",
    '        types.button("@asset.guide", Icon.link, Styles.grayt, () -> Core.app.openURI(patchesGuideURL)).marginLeft(10f).size(200f, 50f).pad(4f);\n\n',
    "",
    "MapAssetsDialog guide"
)

# Campaign sector submission/community thread link.
regex(
    "ui/dialogs/PlanetDialog.java",
    r'''\n            if\(Vars\.showSectorSubmissions\)\{\n                String link = SectorSubmissions\.getSectorThread\(sector\);\n                if\(link != null\)\{\n                    stable\.button\("@sectors\.viewsubmission", Icon\.link, \(\) -> \{\n                        Core\.app\.openURI\(link\);\n                    \}\)\.growX\(\)\.height\(54f\)\.minWidth\(170f\)\.padTop\(2f\)\.row\(\);\n                \}\n            \}\n''',
    "\n",
    "PlanetDialog sector submission"
)

# Remote/scripted URI requests are ignored by the Yandex client before they can
# become a confirmation dialog containing a URL.
exact(
    "ui/Menus.java",
    '''    @Remote(variants = Variant.both)\n    public static void openURI(String uri){\n        if(uri == null) return;\n\n        ui.showConfirm(Core.bundle.format("linkopen", uri), () -> Core.app.openURI(uri));\n    }\n''',
    '''    @Remote(variants = Variant.both)\n    public static void openURI(String uri){\n        // Yandex Web: external URI prompts/navigation are intentionally disabled.\n    }\n''',
    "Menus.openURI"
)

# Mods are not exposed in the Yandex menu. Also remove the obvious online entry
# points from ModsDialog itself so an indirect invocation cannot reveal website or
# browser controls.
exact(
    "ui/dialogs/ModsDialog.java",
    '        buttons.button("@mods.guide", Icon.link, () -> Core.app.openURI(modGuideURL)).size(210, 64f);\n\n',
    "",
    "ModsDialog guide"
)
exact(
    "ui/dialogs/ModsDialog.java",
    '            buttons.button("@mods.browser", Icon.menu, style, () -> browser.show()).margin(margin);\n',
    "",
    "ModsDialog browser button"
)
regex(
    "ui/dialogs/ModsDialog.java",
    r'''\n                    t\.row\(\);\n\n                    t\.button\("@mod\.import\.github", Icon\.github, bstyle, \(\) -> \{.*?\n                    \}\)\.margin\(12f\);''',
    "",
    "ModsDialog GitHub import"
)
regex(
    "ui/dialogs/ModsDialog.java",
    r'''\n        if\(mod\.getRepo\(\) != null\)\{\n            boolean showImport = !mod\.hasSteamID\(\);\n            dialog\.buttons\.button\("@mods\.github\.open", Icon\.link, \(\) -> Core\.app\.openURI\("https://github\.com/" \+ mod\.getRepo\(\)\)\);\n            if\(mobile && showImport\) dialog\.buttons\.row\(\);\n            if\(showImport\) dialog\.buttons\.button\("@mods\.browser\.reinstall", Icon\.download, \(\) -> githubImportMod\(mod\.getRepo\(\), mod\.isJava\(\), null, false\)\);\n        \}\n''',
    "\n",
    "ModsDialog GitHub/reinstall buttons"
)

# DiscordDialog is no longer reachable from the menu; remove the actual open-link
# action too, leaving only non-link informational content if invoked internally.
regex(
    "ui/dialogs/DiscordDialog.java",
    r'''\n        buttons\.button\("@openlink", Icon\.discord, \(\) -> \{.*?\n        \}\);''',
    "",
    "DiscordDialog open-link button"
)

# ModBrowserDialog is an online service and is not exposed in Yandex. Strip any
# remaining direct external-navigation buttons from its details UI.
regex(
    "ui/dialogs/ModBrowserDialog.java",
    r'''\n\s*sel\.buttons\.button\("@mods\.github\.open", Icon\.link, \(\) -> \{\n\s*Core\.app\.openURI\("https://github\.com/" \+ mod\.repo\);\n\s*\}\);''',
    "",
    "ModBrowserDialog repository button"
)
regex(
    "ui/dialogs/ModBrowserDialog.java",
    r'''\n\s*b\.button\("@mods\.github\.open-release", Icon\.link, \(\) -> Core\.app\.openURI\(release\.getString\("html_url"\)\)\);''',
    "",
    "ModBrowserDialog release button"
)

# Guardrail: after overlay application, no direct external-navigation call may
# remain anywhere in user-facing UI/editor sources. Platform-level openURI is still
# blocked as a second line of defense.
violations = []
for base in [CORE / "ui", CORE / "editor"]:
    for file in base.rglob("*.java"):
        body = file.read_text(encoding="utf-8")
        if "Core.app.openURI(" in body:
            violations.append(str(file.relative_to(CORE)))
if violations:
    raise SystemExit("Direct external URI calls remain in Yandex UI: " + ", ".join(sorted(violations)))

print("Applied Yandex no-links UI overlay; direct Core.app.openURI calls in UI/editor: 0")
