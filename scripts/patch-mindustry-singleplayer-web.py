#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MINDUSTRY = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
CONTROL = MINDUSTRY / "core" / "Control.java"
UI = MINDUSTRY / "core" / "UI.java"
MENU = MINDUSTRY / "ui" / "fragments" / "MenuFragment.java"
PAUSED = MINDUSTRY / "ui" / "dialogs" / "PausedDialog.java"
PLAYER_LIST = MINDUSTRY / "ui" / "fragments" / "PlayerListFragment.java"

for path in (CONTROL, UI, MENU, PAUSED, PLAYER_LIST):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry source: {path}")

control = CONTROL.read_text(encoding="utf-8")
control_replacements = [
    (
        '''        Events.on(PlayEvent.class, event -> {\n            player.team(netServer.assignTeam(player));\n            player.add();\n\n            state.set(State.playing);\n        });\n''',
        '''        Events.on(PlayEvent.class, event -> {\n            // Web/Yandex is intentionally single-player. There is no NetServer team\n            // allocator; local play uses the map/rules default team directly.\n            player.team(state.rules.defaultTeam);\n            player.add();\n\n            state.set(State.playing);\n        });\n''',
        "PlayEvent team assignment",
    ),
    (
        '''        //autohost for pvp maps\n        Events.on(WorldLoadEvent.class, event -> app.post(() -> {\n            if(state.rules.pvp && !net.active() && !state.rules.pauseDisabled){\n                try{\n                    net.host(port);\n                    player.admin = true;\n                }catch(IOException e){\n                    ui.showException("@server.error", e);\n                    state.set(State.menu);\n                }\n            }\n        }));\n''',
        '''        // Web/Yandex is intentionally single-player: never auto-host PvP maps.\n''',
        "PvP auto-host",
    ),
    (
        '''            if(!mobile && Core.input.keyTap(Binding.screenshot) && !scene.hasField() && !scene.hasKeyboard()){\n                renderer.takeMapScreenshot();\n            }\n\n''',
        '''            // Web/Yandex: the desktop whole-map screenshot hotkey is omitted.\n            // Reaching Renderer.takeMapScreenshot() retains Arc's full PNG/Deflater\n            // encoder in TeaVM and adds several MiB for a non-gameplay desktop utility.\n\n''',
        "desktop whole-map screenshot hotkey",
    ),
]
for old, new, label in control_replacements:
    if old not in control:
        raise SystemExit(f"Single-player Control patch no longer matches pinned upstream ({label})")
    control = control.replace(old, new, 1)

if "renderer.takeMapScreenshot();" in control:
    raise SystemExit("Single-player Web Control still reaches whole-map screenshot encoder")
CONTROL.write_text(control, encoding="utf-8")

menu = MENU.read_text(encoding="utf-8")
menu_replacements = [
    (
        '''            maps = new MobileButton(Icon.download, "@loadgame", () -> checkPlay(ui.load::show)),\n            join = new MobileButton(Icon.add, "@joingame", () -> checkPlay(ui.join::show)),\n            editor = new MobileButton(Icon.terrain, "@editor", () -> checkPlay(ui.maps::show)),\n''',
        '''            maps = new MobileButton(Icon.download, "@loadgame", () -> checkPlay(ui.load::show)),\n            editor = new MobileButton(Icon.terrain, "@editor", () -> checkPlay(ui.maps::show)),\n''',
        "mobile join declaration",
    ),
    (
        '''            container.add(play);\n            container.add(join);\n            container.add(custom);\n''',
        '''            container.add(play);\n            container.add(custom);\n''',
        "landscape mobile join button",
    ),
    (
        '''            container.add(custom);\n            container.add(join);\n            container.row();\n''',
        '''            container.add(custom);\n            container.row();\n''',
        "portrait mobile join button",
    ),
    (
        '''                        new MenuButton("@campaign", Icon.play, () -> checkPlay(ui.planet::show)),\n                        new MenuButton("@joingame", Icon.add, () -> checkPlay(ui.join::show)),\n                        new MenuButton("@customgame", Icon.terrain, () -> checkPlay(ui.custom::show)),\n''',
        '''                        new MenuButton("@campaign", Icon.play, () -> checkPlay(ui.planet::show)),\n                        new MenuButton("@customgame", Icon.terrain, () -> checkPlay(ui.custom::show)),\n''',
        "desktop join button",
    ),
]
for old, new, label in menu_replacements:
    if old not in menu:
        raise SystemExit(f"Single-player MenuFragment patch no longer matches pinned upstream ({label})")
    menu = menu.replace(old, new, 1)
MENU.write_text(menu, encoding="utf-8")

# UI.init() eagerly constructs every desktop dialog. In a permanent browser
# single-player build, constructing online/server/mod dialogs alone makes Arc Http,
# ThreadPoolExecutor and socket-facing code reachable even though their menu buttons
# are hidden. Do not construct functionality that is intentionally unavailable.
ui = UI.read_text(encoding="utf-8")
for line, label in [
    ("        join = new JoinDialog();\n", "JoinDialog"),
    ("        discord = new DiscordDialog();\n", "DiscordDialog"),
    ("        host = new HostDialog();\n", "HostDialog"),
    ("        bans = new BansDialog();\n", "BansDialog"),
    ("        admins = new AdminsDialog();\n", "AdminsDialog"),
    ("        traces = new TraceDialog();\n", "TraceDialog"),
    ("        mods = new ModsDialog();\n", "ModsDialog"),
]:
    if line not in ui:
        raise SystemExit(f"Single-player UI patch no longer matches pinned upstream ({label})")
    ui = ui.replace(line, f"        // Web/Yandex single-player: {label} is intentionally unavailable.\n", 1)
UI.write_text(ui, encoding="utf-8")

# The pause menu is part of real local gameplay, but hosting/invites are not.
paused = PAUSED.read_text(encoding="utf-8")
desktop_host = '''            //the button runs out of space when the editor button is added, so use the mobile text\n            cont.button(state.isEditor() ? "@hostserver.mobile" : "@hostserver", Icon.host, () -> {\n                if(net.server() && steam){\n                    platform.inviteFriends();\n                }else{\n                    ui.host.show();\n                }\n            }).disabled(b -> !((steam && net.server()) || !net.active())).colspan(state.isEditor() ? 1 : 2).width(state.isEditor() ? dw : dw * 2 + 10f)\n                .update(e -> e.setText(net.server() && steam ? "@invitefriends" : state.isEditor() ? "@hostserver.mobile" : "@hostserver"));\n\n'''
if desktop_host not in paused:
    raise SystemExit("Single-player PausedDialog patch no longer matches desktop host block")
paused = paused.replace(desktop_host, "            // Web/Yandex single-player: hosting/invite controls are omitted.\n\n", 1)
mobile_host = '            cont.buttonRow("@hostserver.mobile", Icon.host, ui.host::show).disabled(b -> net.active());\n\n'
if mobile_host not in paused:
    raise SystemExit("Single-player PausedDialog patch no longer matches mobile host button")
paused = paused.replace(mobile_host, "            // Web/Yandex single-player: no mobile host button.\n\n", 1)
PAUSED.write_text(paused, encoding="utf-8")

# The local player list remains useful for the normal UI graph, but server ban/admin
# management is meaningless without NetServer and would dereference pruned dialogs.
player_list = PLAYER_LIST.read_text(encoding="utf-8")
admin_buttons = '''                    menu.button("@server.bans", ui.bans::show).disabled(b -> net.client());\n                    menu.button("@server.admins", ui.admins::show).disabled(b -> net.client());\n'''
if admin_buttons not in player_list:
    raise SystemExit("Single-player PlayerListFragment patch no longer matches server admin buttons")
player_list = player_list.replace(admin_buttons, "                    // Web/Yandex single-player: server administration controls omitted.\n", 1)
PLAYER_LIST.write_text(player_list, encoding="utf-8")

for forbidden in ("new JoinDialog()", "new HostDialog()", "new ModsDialog()", "ui.host::show", "ui.host.show()"):
    bodies = UI.read_text(encoding="utf-8") + MENU.read_text(encoding="utf-8") + PAUSED.read_text(encoding="utf-8")
    if forbidden in bodies:
        raise SystemExit(f"Single-player Web UI still exposes unsupported path: {forbidden}")

print("Applied permanent single-player Control/menu/UI paths; pruned multiplayer/mod/server-admin reachability")
