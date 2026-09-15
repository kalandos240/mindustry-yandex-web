#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"
PLAYER_COMP = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "entities" / "comp" / "PlayerComp.java"

for path in (APPLICATION, VERIFY, LOGIC, PLAYER_COMP):
    if not path.is_file():
        raise SystemExit(f"Missing player-input smoke source: {path}")

application = APPLICATION.read_text(encoding="utf-8")
old_frame = '''            if(!platformPaused){
                frame();
                syncGameplayMarker();
            }
'''
new_frame = '''            if(!platformPaused){
                frame();
                syncGameplayMarker();
                // CI-only observer; inert unless mindustryPlayerInputSmoke=1.
                BrowserPlayerInputSmoke.update();
            }
'''
if application.count(old_frame) != 1:
    raise SystemExit("BrowserApplication player-input frame hook anchor no longer matches")
APPLICATION.write_text(application.replace(old_frame, new_frame, 1), encoding="utf-8")

# This overlay runs after waves/fog/weather/game-over, so it sees the final staged
# updateWebPlayingCore body. The movement smoke currently exposes an opaque TeaVM NPE
# while BrowserLocalMapRuntime is still in its "logic" phase. Wrap the final entity
# boundary so the outer BrowserApplication DOM error identifies that subsystem exactly.
logic = LOGIC.read_text(encoding="utf-8")
old_entities = '''        // Stock weather attributes: active WeatherState opacity contributes to the
        // world environment on the frame following its entity fade/update.
        state.envAttrs.clear();
        state.envAttrs.add(state.rules.attributes);
        Groups.weather.each(w -> state.envAttrs.add(w.weather.attrs, w.opacity));

        updateEntities();

        Events.fire(Trigger.afterGameUpdate);
'''
new_entities = '''        // Stock weather attributes: active WeatherState opacity contributes to the
        // world environment on the frame following its entity fade/update.
        state.envAttrs.clear();
        state.envAttrs.add(state.rules.attributes);
        Groups.weather.each(w -> state.envAttrs.add(w.weather.attrs, w.opacity));

        try{
            updateEntities();
        }catch(Throwable error){
            throw new IllegalStateException("Web playing logic failed at entity-update", error);
        }

        Events.fire(Trigger.afterGameUpdate);
'''
if logic.count(old_entities) != 1:
    raise SystemExit("Player-input logic entity diagnostic anchor no longer matches final staged playing core")
logic = logic.replace(old_entities, new_entities, 1)
LOGIC.write_text(logic, encoding="utf-8")

# The failure timing lines up with the first normal Player deathDelay expiry. The stock
# player is added but has no controlled unit yet, so isolate the two operations that run
# before CoreBlock.playerSpawn(): core selection and CoreBuild.requestSpawn(). Existing
# CoreBlock diagnostics then cover every operation after requestSpawn enters playerSpawn.
player = PLAYER_COMP.read_text(encoding="utf-8")
old_dead = '''        }else if((core = bestCore()) != null){
            //have a small delay before death to prevent the camera from jumping around too quickly
            //(this is not for balance, it just looks better this way)
            deathTimer += Time.delta;
            if(deathTimer >= deathDelay){
                //request spawn - this happens serverside only
                core.requestSpawn(self());
                deathTimer = 0;
            }
        }
'''
new_dead = '''        }else{
            try{
                core = bestCore();
            }catch(Throwable error){
                throw new IllegalStateException("Web local player pre-spawn failed at best-core", error);
            }
            if(core != null){
                //have a small delay before death to prevent the camera from jumping around too quickly
                //(this is not for balance, it just looks better this way)
                deathTimer += Time.delta;
                if(deathTimer >= deathDelay){
                    //request spawn - this happens serverside only
                    try{
                        core.requestSpawn(self());
                    }catch(Throwable error){
                        throw new IllegalStateException("Web local player pre-spawn failed at request-spawn", error);
                    }
                    deathTimer = 0;
                }
            }
        }
'''
if player.count(old_dead) != 1:
    raise SystemExit("Player-input pre-spawn diagnostic anchor no longer matches pinned PlayerComp")
PLAYER_COMP.write_text(player.replace(old_dead, new_dead, 1), encoding="utf-8")

text = VERIFY.read_text(encoding="utf-8")
function_anchor = '''run_locale(){
'''
player_function = '''run_player_input_map(){
  local profile="/tmp/mindustry-web-profile-player-input-map"
  local dom="/tmp/mindustry-web-player-input-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \\
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryPlayerInputSmoke=1" \\
    --profile "$profile" \\
    --port 9240 \\
    --timeout 45 \\
    --require 'data-mindustry-web="ready"' \\
    --require 'data-mindustry-smoke-mode="production"' \\
    --require 'data-mindustry-input="ready"' \\
    --require 'data-mindustry-input-mode="desktop"' \\
    --require 'data-mindustry-stock-input="desktop"' \\
    --require 'data-mindustry-local-map-state="playing"' \\
    --require 'data-mindustry-local-map-slug="maze"' \\
    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-player-input-smoke="moved"' \\
    --require 'data-mindustry-player-input-source="dom-keyboard-event"' \\
    --require 'data-mindustry-player-input-key="KeyD"' \\
    --require 'data-mindustry-player-input-key-state="up"' \\
    --require 'data-mindustry-network="local-only"' \\
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-player-input-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-player-input-unit="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-player-input-held-frames="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-player-input-start-x="-?[0-9]' "$dom"
  grep -Eq 'data-mindustry-player-input-end-x="-?[0-9]' "$dom"
  grep -Eq 'data-mindustry-player-input-dx="[0-9]' "$dom"
  echo 'Browser player input: DOM KeyD -> BrowserInputBridge -> WebInput -> stock Binding.moveX/DesktopInput -> real local player unit +X movement PASS'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Player-input verifier function anchor no longer matches final locale gate")
text = text.replace(function_anchor, player_function, 1)

call_anchor = '''run_production_menu
run_production_map
run_legacy_domain_map
run_fog_map
run_weather_map
run_locale en
'''
call_replacement = '''run_production_menu
run_production_map
run_legacy_domain_map
run_fog_map
run_weather_map
run_player_input_map
run_locale en
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Player-input verifier call anchor no longer matches post-weather production ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with real DOM keyboard movement plus final entity/pre-spawn diagnostics")
