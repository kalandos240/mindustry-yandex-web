#!/usr/bin/env python3
"""Fail closed if unsupported controls return to the Web/Yandex game menu.

Yandex Games requirements, checked against 2026-10-09 official specification:
1.14 freezes/crashes, 1.8/1.10 screen hitboxes, 1.6.2.6/7 OS shortcuts and
context menu; 6.7 (recommended) useless "Exit" controls. Not a blanket ban
on all buttons or a substitute for browser interaction/moderation testing.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
menu = (ROOT / "web-runtime/src/main/java/mindustry/web/BrowserStockMainMenu.java").read_text(encoding="utf-8")
dialogs = (ROOT / "web-runtime/src/main/java/mindustry/web/BrowserVanillaDialogs.java").read_text(encoding="utf-8")
index = (ROOT / "web-runtime/src/web/index.html").read_text(encoding="utf-8")
application = (ROOT / "web-runtime/src/main/java/mindustry/web/BrowserApplication.java").read_text(encoding="utf-8")
settings_patch = (ROOT / "scripts/patch-vanilla-settings-web.py").read_text(encoding="utf-8")
browser_smoke = (ROOT / "scripts/browser-ui-button-stress.js").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/web-port-baseline.yml").read_text(encoding="utf-8")

def check(ok, message):
    if not ok:
        raise SystemExit("Yandex UI controls compliance failed: " + message)

legal = {
    "play": "BrowserStockMainMenu::openPlay",
    "customgame": "BrowserStockMainMenu::openPlay",
    "planets": "BrowserVanillaDialogs::openPlanets",
    "database": "BrowserStockDatabaseUi::show",
    "research": "BrowserResearchUi::show",
    "settings": "BrowserVanillaDialogs::openSettings",
    "schematics": "BrowserVanillaDialogs::openSchematics",
    "settings.controls": "BrowserVanillaDialogs::openControls",
    "loadgame": "BrowserVanillaDialogs::openSaves",
}
seen = []
for match in re.finditer(r'\bmenu\.button\(Core\.bundle\.get\("([^"]+)"', menu):
    key = match.group(1)
    seen.append(key)
    check(key in legal, f"unsupported or unreviewed visible menu button: {key}")
    tail = menu[match.end(): match.end() + 350]
    check(legal[key] in tail,
          f"menu button {key} must have a supported native callback {legal[key]}")
check(menu.count("menu.button(") == len(seen),
      "a menu button was added without the approved localized-action format")
check(set(seen) == set(legal), "missing legal menu action or unapproved action found")
check(len(seen) == 17, "unexpected menu button count (8 mobile + 9 desktop)")
check("markPointerCenter(home.find(\"web-main-custom\"), \"custom\")" in menu,
      "desktop Custom Game real pointer hitbox missing")

# These native dialog actions are required to resolve to real Arc Scene
# windows, not disabled placeholders or an application exit button.
for method in ("openSaves", "openSettings", "openControls", "openSchematics", "openPlanets"):
    check(f"public static void {method}()" in dialogs, f"missing native dialog action {method}")
check("public boolean openURI(String uri)" in application and
      'markExternalNavigationBlocked();' in application and
      'return false;' in application[application.index("public boolean openURI(String uri)"):][:240],
      "external desktop/browser link handler must remain inactive")
check("document.addEventListener('contextmenu', event => event.preventDefault()" in index,
      "long-click/right-click browser context menu must remain suppressed")
check("document.addEventListener('selectstart', event => event.preventDefault()" in index,
      "browser text selection must remain suppressed")
check("hide native fullscreen/vsync/landscape controls" in settings_patch,
      "desktop fullscreen/orientation controls must remain pruned")
check("browser file APIs support complete round-trips" in settings_patch,
      "unsupported desktop ZIP/export/log buttons must remain pruned")

# Require that the behavioral check genuinely uses native trusted clicks and
# observes completed Java frames; merely searching for a DOM button is insufficient.
for term in ("data-mindustry-cdp-click-request", "cpu-sample",
             "main-menu-page", "vanilla-modal-visible", "getBoundingClientRect",
             "elementFromPoint", "ui-button-stress"):
    check(term in browser_smoke, f"behavioral test lost {term}")
check("id: ui_button_stress" in workflow, "real button/hang CI gate missing")
check("steps.ui_button_stress.outcome" in workflow, "UI failure must block release")

# Pause is not "Exit game": the browser-safe action goes back to the native
# main menu and must not leave the player stranded with a JVM-style Quit.
hud_source = (ROOT / "web-runtime/src/main/java/mindustry/web/BrowserUiRuntime.java").read_text(encoding="utf-8")
pause_source = hud_source.split("private static void buildLocalPauseOverlay()", 1)[1].split(
    "private static void markPauseHitPoint(", 1
)[0]
pause_keys = [m.group(1) for m in re.finditer(r'dialog\.button\(Core\.bundle\.get\("([^"]+)"', pause_source)]
check(pause_keys == ["resume", "settings", "savegame", "research",
                     "schematics", "settings.controls", "loadgame", "back"],
      "pause dialog has a forbidden/unknown/misleading action")
check('"web-pause-menu"' in pause_source and
      'BrowserUiRuntime::returnToMenuWithAd' in pause_source and
      '"quit"' not in pause_source,
      "pause menu must expose a safe Back, not a browser Exit/Quit")
check("BrowserVanillaDialogs.sampleVisibility();" in pause_source,
      "paused dialogs must update their real visible state")
for action in ("resume", "settings", "save", "research", "schematics", "controls", "saves", "menu"):
    check(f'web-pause-{action}' in pause_source, f"pause {action} button is missing")
    check(f'markPauseHitPoint(dialog.find("web-pause-{action}")' in pause_source,
          f"pause {action} cannot be tested by real Chrome input")
check("id: gameplay_button_stress" in workflow and
      "steps.gameplay_button_stress.outcome" in workflow,
      "HUD/pause native-button freeze test must be a hard Yandex release gate")

# Real Settings navigation must not become a fake HTML menu. Watch the
# stock SettingsMenuDialog's actual category actors and persisted options.
native_dialogs = (ROOT / "web-runtime/src/main/java/mindustry/web/BrowserVanillaDialogs.java").read_text(encoding="utf-8")
settings_smoke = (ROOT / "scripts/browser-settings-tab-stress.js").read_text(encoding="utf-8")
for marker in ("sampleSettingsNestedControls()", "settings-native-page",
               "settings-native-hints", "settings-hit-", "Core.scene.hit("):
    check(marker in native_dialogs, f"stock Settings instrumentation lost {marker}")
for marker in ("settings-hit-toggle-hints", "settings-native-hints",
               "settings-hit-language", "settings-hit-controls",
               "settings-native-page", "cpu-sample", "cdp-click-request"):
    check(marker in settings_smoke, f"real nested Settings regression lost {marker}")
check("id: settings_nested_stress" in workflow and
      "steps.settings_nested_stress.outcome" in workflow,
      "nested Settings buttons cannot bypass the hard release gate")

# Verify a real configurable Serpulo building also gets native mouse/touch
# coverage. A successful inventory/building mechanics test alone does not prove
# the Sorter ItemSelection buttons respond in the game window.
config_smoke = (ROOT / "web-runtime/src/main/java/mindustry/web/BrowserBuildingConfigSmoke.java").read_text(encoding="utf-8")
config_chrome = (ROOT / "scripts/browser-building-config-stress.js").read_text(encoding="utf-8")
app = (ROOT / "web-runtime/src/main/java/mindustry/web/BrowserApplication.java").read_text(encoding="utf-8")
for term in ("Blocks.sorter", "Sorter.SorterBuild", "Items.copper", "control.input.config.isShown()",
             "data-mindustry-building-config-smoke", "mindustryBuildingConfigSmoke"):
    check(term in config_smoke, f"native building configuration observer lost {term}")
for term in ("cdp-click-request", "cpu-sample", "building-config-cleared",
             "building-config-selected", "building-config-fixture", "elementFromPoint"):
    check(term in config_chrome, f"building configuration browser gate lost {term}")
check("BrowserBuildingConfigSmoke.update();" in app,
      "CI building config probe not connected to Java scheduler")
check("id: native_building_config" in workflow and
      "steps.native_building_config.outcome" in workflow,
      "real building config mouse/touch acceptance must block Yandex release")

print(f"Yandex UI compliance: {len(seen)} approved main menu entries, "
      f"{len(pause_keys)} pause controls, nested Settings, real building configuration and freeze gates")
