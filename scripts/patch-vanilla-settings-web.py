#!/usr/bin/env python3
"""Patch the pinned v159.7 SettingsMenuDialog for the single-player browser.

Keep the original preferences, styles, sliders, language/keybind navigation,
confirmation dialogs and settings persistence. Prune only desktop-only actions
that require a JVM restart, filesystem, unsupported ZIP import/export or raw
server discovery. These changes are applied only to work/Mindustry.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work/Mindustry/core/src/mindustry/ui/dialogs/SettingsMenuDialog.java"
if not PATH.is_file():
    raise SystemExit(f"Missing pinned SettingsMenuDialog: {PATH}")
src = PATH.read_text(encoding="utf-8")


def replace(old: str, new: str, label: str) -> None:
    global src
    found = src.count(old)
    if found != 1:
        raise SystemExit(f"Vanilla settings web patch requires one {label}; found {found}")
    src = src.replace(old, new, 1)


# The Web runtime deliberately avoids NetServer and the community server list.
# The legacy UI-runtime patch may already have removed exactly this block.
# Accept only its explicit pinned replacement marker; never silently ignore
# a changed upstream definition.
old_server = """        game.checkPref("communityservers", true, val -> {
            defaultServers.clear();
            if(val){
                JoinDialog.fetchServers();
            }
        });

"""
prior_patch = "        // Web/Yandex single-player: community server discovery is unavailable."
if old_server in src:
    replace(old_server, "        // Browser single-player: there is no community server discovery.\n\n",
            "server discovery preference")
elif prior_patch not in src:
    raise SystemExit("Vanilla settings server discovery changed unexpectedly")

# Replacing the stock MobileInput while the browser is running would leave its
# Arc input processors, HUD group and touch handlers stranded. Keyboard users
# select the device-appropriate input at browser startup, not by re-registering
# an input graph from the settings dialog mid-session.
replace(
    '''        if(mobile){
            game.checkPref("autotarget", true);
            game.checkPref("keyboard", false, val -> {
                control.setInput(val ? new DesktopInput() : new MobileInput());
                input.setUseKeyboard(val);
            });
            if(Core.settings.getBool("keyboard")){
                control.setInput(new DesktopInput());
                input.setUseKeyboard(true);
            }

        }
''',
    '''        if(mobile){
            game.checkPref("autotarget", true);
            // Browser Web: keyboard/input mode is chosen once during launcher setup.
        }
''',
    "mobile input re-registration",
)

# Fullscreen/vsync and forced orientation are owned by the hosting browser
# iframe and Yandex platform; the browser canvas always fits its allotted area.
start = src.index('        if(!mobile){\n            graphics.checkPref("vsync"')
end = src.index('\n        graphics.checkPref("effects", true);', start)
src = src[:start] + '''        // Yandex Web: hide native fullscreen/vsync/landscape controls.
        // The host iframe and BrowserCanvas own viewport, orientation and frame rate.
''' + src[end:]

# BrowserFi serves persistent save/schematic files, but the original ZIP data
# importer uses OS filesystem paths, ZipFi and Core.app.exit. Keep destructive
# *confirmed* vanilla clear/reset actions; hide only unsupported desktop ZIP
# import/export/open-folder/log-export and avoid calling Core.app.exit.
replace(
    '''                Core.app.exit();
            })).marginLeft(4);''',
    '''                // Browser: the user can reload the page to restart after clearing data.
                ui.showInfo("@language.restart");
            })).marginLeft(4);''',
    "clear-data JVM exit",
)

start = src.index('            t.button("@data.export", Icon.upload, style, () -> {')
end = src.index('\n        });\n\n        row();', start)
src = src[:start] + '''            // Web: ZIP backup/import and OS log-folder operations are intentionally
            // not shown until the browser file APIs support complete round-trips.
            // Persistent local campaign/schematic/save data remains untouched.
''' + src[end:]

# The methods below are kept in upstream source, but are unreachable after their
# button actions have been pruned. TeaVM can omit their ZIP/OS dependencies.
replace('                Slider slider = new Slider(min, max, step, false);',
        '                Slider slider = new Slider(min, max, step, false);\n                slider.name = "setting-" + name;',
        "native slider identity")

# These controls belong to stock systems that are deliberately absent from this
# permanent single-player browser graph. Do not expose preferences that only
# change a stored value while having no consumer or effect in this runtime.
for statement, label in (
    ('        game.checkPref("savecreate", true);\n', 'desktop automatic save-slot creation'),
    ('            game.checkPref("backgroundpause", true);\n', 'mandatory platform background pause'),
    ('        graphics.sliderPref("chatopacity", 100, 0, 100, 5, s -> s + "%");\n', 'absent chat opacity'),
    ('        graphics.checkPref("playerchat", true);\n', 'absent multiplayer chat bubbles'),
    ('        dev.checkPref("console", false);\n', 'absent JavaScript console fragment'),
    ('        dev.checkPref("showperformance", false);\n', 'absent desktop performance fragment'),
    ('            dev.checkPref("modcrashdisable", true);\n', 'disabled mod loader crash preference'),
):
    replace(statement, '', label)

PATH.write_text(src, encoding="utf-8")

# The browser installs its saved Scl product before actors are built and provides
# an unscaled HTML Keep/Reset prompt. The desktop countdown calls app.exit(),
# which permanently stops the browser scheduler and cannot restore a usable UI.
control_path = ROOT / "work/Mindustry/core/src/mindustry/core/Control.java"
control = control_path.read_text(encoding="utf-8")
begin = control.index('        //display UI scale changed dialog\n')
end = control.index('\n    }\n\n    @Override\n    public void update(){', begin)
scale_block = control[begin:end]
if 'Core.app.exit();' not in scale_block or 'Core.settings.getBool("uiscalechanged", false)' not in scale_block:
    raise SystemExit('Pinned Control UI-scale watchdog changed; review browser recovery')
control_path.write_text(control[:begin] + '''        // Web: BrowserUiScale owns the persistent scale and browser-safe recovery UI.
''' + control[end:], encoding="utf-8")
print("Vanilla SettingsMenuDialog: original Sound/Game/Graphics/Data/Language/Controls/Dev UI; browser-only actions removed")

import subprocess
subprocess.run(["python3", str(ROOT / "scripts/patch-browser-keybinds-web.py")], check=True)
subprocess.run(["python3", str(ROOT / "scripts/patch-browser-data-reset-web.py")], check=True)
