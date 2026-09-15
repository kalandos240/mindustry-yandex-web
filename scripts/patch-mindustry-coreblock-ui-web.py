#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE_BLOCK = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "world" / "blocks" / "storage" / "CoreBlock.java"
CONTROL = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Control.java"

for path in (CORE_BLOCK, CONTROL):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry source: {path}")

text = CORE_BLOCK.read_text(encoding="utf-8")
old = "                        ui.loadfrag.toFront();"
new = "                        if(ui.loadfrag != null) ui.loadfrag.toFront(); // Web lean UI has no desktop loading fragment."

if text.count(old) != 2:
    raise SystemExit(f"CoreBlock Web lean-UI patch expected exactly two loading-fragment foreground calls, found {text.count(old)}")
text = text.replace(old, new)

# CoreBlock.requestSpawn() normally crosses the generated multiplayer Call transport.
# This Web build is permanently local single-player, so invoke the exact stock RPC
# implementation directly. CoreBlock.playerSpawn() still owns the vanilla spawn type,
# mount reload, core position, rotation, impulse, spawnedByCore flag, controller binding
# and entity add semantics; only the absent network transport wrapper is removed.
old_spawn = '''            Call.playerSpawn(tile, player);
'''
new_spawn = '''            CoreBlock.playerSpawn(tile, player);
'''
if text.count(old_spawn) != 1:
    raise SystemExit(f"CoreBlock Web local-spawn patch expected one Call.playerSpawn site, found {text.count(old_spawn)}")
text = text.replace(old_spawn, new_spawn, 1)

# The stock spawn RPC emits Fx.spawn before the Unit exists. In the lean Web renderer
# this cosmetic effect reaches desktop-only effect state and can fail before unit creation,
# leaving the local Player permanently dead. Skip only that optional pre-spawn visual on
# Web; the authoritative unit creation/controller/add path immediately below is unchanged.
old_fx = '''        if(core.wasVisible){
            Fx.spawn.at(core);
        }
'''
new_fx = '''        if(core.wasVisible && (Core.app == null || !Core.app.isWeb())){
            Fx.spawn.at(core);
        }
'''
if text.count(old_fx) != 1:
    raise SystemExit(f"CoreBlock Web spawn-VFX patch expected one stock Fx.spawn block, found {text.count(old_fx)}")
text = text.replace(old_fx, new_fx, 1)

# Keep the stock local spawn semantics but attach phase information to any Web-only
# failure. BrowserApplication already exposes the outer exception message in DOM, so this
# turns an otherwise opaque TeaVM NPE into a precise create/mount/controller/add failure.
old_body = '''        player.set(core);

        if(!net.client()){
            Unit unit = spawnType.create(tile.team());
            //reset reload so that the player can't shoot immediately
            for(var mount : unit.mounts){
                mount.reload = mount.weapon.reload;
            }
            unit.set(core);
            unit.rotation(90f);
            unit.impulse(0f, 3f);
            unit.spawnedByCore(true);
            unit.controller(player);
            unit.add();
        }
'''
new_body = '''        try{
            player.set(core);
        }catch(Throwable error){
            throw new IllegalStateException("Web local player spawn failed at player-set", error);
        }

        if(!net.client()){
            Unit unit;
            try{
                unit = spawnType.create(tile.team());
            }catch(Throwable error){
                throw new IllegalStateException("Web local player spawn failed at unit-create", error);
            }
            try{
                //reset reload so that the player can't shoot immediately
                for(var mount : unit.mounts){
                    mount.reload = mount.weapon.reload;
                }
            }catch(Throwable error){
                throw new IllegalStateException("Web local player spawn failed at mount-reload", error);
            }
            try{
                unit.set(core);
                unit.rotation(90f);
                unit.impulse(0f, 3f);
                unit.spawnedByCore(true);
            }catch(Throwable error){
                throw new IllegalStateException("Web local player spawn failed at unit-init", error);
            }
            try{
                unit.controller(player);
            }catch(Throwable error){
                throw new IllegalStateException("Web local player spawn failed at controller-bind", error);
            }
            try{
                unit.add();
            }catch(Throwable error){
                throw new IllegalStateException("Web local player spawn failed at entity-add", error);
            }
        }
'''
if text.count(old_body) != 1:
    raise SystemExit(f"CoreBlock Web spawn diagnostics expected one stock spawn body, found {text.count(old_body)}")
text = text.replace(old_body, new_body, 1)

CORE_BLOCK.write_text(text, encoding="utf-8")

# Stock Trigger.newGame delays the first player respawn by CoreBlock.landDuration (160
# ticks) while Renderer.showLanding() owns a desktop-style core landing sequence. The lean
# browser renderer deliberately does not provide that lifecycle; its delayed completion
# VFX was reached at exactly the same point as the opaque Web NPE, before playerSpawn().
# Skip only this cosmetic intro on Web. The player still starts at the core and respawns
# through the normal Player.deathDelay -> CoreBuild.requestSpawn -> playerSpawn path.
control = CONTROL.read_text(encoding="utf-8")
old_landing = '''            if(!settings.getBool("skipcoreanimation") && !state.rules.pvp){
'''
new_landing = '''            if((Core.app == null || !Core.app.isWeb()) && !settings.getBool("skipcoreanimation") && !state.rules.pvp){
'''
if control.count(old_landing) != 1:
    raise SystemExit(f"Control Web landing bypass expected one stock core-animation condition, found {control.count(old_landing)}")
control = control.replace(old_landing, new_landing, 1)
CONTROL.write_text(control, encoding="utf-8")

print("Applied Web-safe core UI/spawn diagnostics and bypassed unsupported browser core landing intro")
