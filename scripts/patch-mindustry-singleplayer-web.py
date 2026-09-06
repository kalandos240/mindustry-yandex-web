#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MINDUSTRY = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
CONTROL = MINDUSTRY / "core" / "Control.java"
MENU = MINDUSTRY / "ui" / "fragments" / "MenuFragment.java"

for path in (CONTROL, MENU):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry source: {path}")

control = CONTROL.read_text(encoding="utf-8")
control_replacements = [
    (
        '''        Events.on(PlayEvent.class, event -> {
            player.team(netServer.assignTeam(player));
            player.add();

            state.set(State.playing);
        });
''',
        '''        Events.on(PlayEvent.class, event -> {
            // Web/Yandex is intentionally single-player. There is no NetServer team
            // allocator; local play uses the map/rules default team directly.
            player.team(state.rules.defaultTeam);
            player.add();

            state.set(State.playing);
        });
''',
        "PlayEvent team assignment",
    ),
    (
        '''        //autohost for pvp maps
        Events.on(WorldLoadEvent.class, event -> app.post(() -> {
            if(state.rules.pvp && !net.active() && !state.rules.pauseDisabled){
                try{
                    net.host(port);
                    player.admin = true;
                }catch(IOException e){
                    ui.showException("@server.error", e);
                    state.set(State.menu);
                }
            }
        }));
''',
        '''        // Web/Yandex is intentionally single-player: never auto-host PvP maps.
''',
        "PvP auto-host",
    ),
    (
        '''            if(!mobile && Core.input.keyTap(Binding.screenshot) && !scene.hasField() && !scene.hasKeyboard()){
                renderer.takeMapScreenshot();
            }

''',
        '''            // Web/Yandex: the desktop whole-map screenshot hotkey is omitted.
            // Reaching Renderer.takeMapScreenshot() retains Arc's full PNG/Deflater
            // encoder in TeaVM and adds several MiB for a non-gameplay desktop utility.

''',
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
        '''            maps = new MobileButton(Icon.download, "@loadgame", () -> checkPlay(ui.load::show)),
            join = new MobileButton(Icon.add, "@joingame", () -> checkPlay(ui.join::show)),
            editor = new MobileButton(Icon.terrain, "@editor", () -> checkPlay(ui.maps::show)),
''',
        '''            maps = new MobileButton(Icon.download, "@loadgame", () -> checkPlay(ui.load::show)),
            editor = new MobileButton(Icon.terrain, "@editor", () -> checkPlay(ui.maps::show)),
''',
        "mobile join declaration",
    ),
    (
        '''            container.add(play);
            container.add(join);
            container.add(custom);
''',
        '''            container.add(play);
            container.add(custom);
''',
        "landscape mobile join button",
    ),
    (
        '''            container.add(custom);
            container.add(join);
            container.row();
''',
        '''            container.add(custom);
            container.row();
''',
        "portrait mobile join button",
    ),
    (
        '''                        new MenuButton("@campaign", Icon.play, () -> checkPlay(ui.planet::show)),
                        new MenuButton("@joingame", Icon.add, () -> checkPlay(ui.join::show)),
                        new MenuButton("@customgame", Icon.terrain, () -> checkPlay(ui.custom::show)),
''',
        '''                        new MenuButton("@campaign", Icon.play, () -> checkPlay(ui.planet::show)),
                        new MenuButton("@customgame", Icon.terrain, () -> checkPlay(ui.custom::show)),
''',
        "desktop join button",
    ),
]
for old, new, label in menu_replacements:
    if old not in menu:
        raise SystemExit(f"Single-player MenuFragment patch no longer matches pinned upstream ({label})")
    menu = menu.replace(old, new, 1)
MENU.write_text(menu, encoding="utf-8")

print("Applied permanent single-player Control/menu paths and pruned desktop screenshot encoder on Web/Yandex")
