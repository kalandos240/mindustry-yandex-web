#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"
SMOKE = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserBuildPlacementSmoke.java"

for path in (APPLICATION, VERIFY, SMOKE):
    if not path.is_file():
        raise SystemExit(f"Missing browser build-placement source: {path}")

# DOM PointerEvents are delivered after the application frame that dispatches them.
# Do not release the world left button until stock DesktopInput has actually observed
# Binding.select and entered PlaceMode.placing. A one-frame down/up pair can otherwise
# collapse on slow/headless TeaVM runs and produce no BuildPlan at all; the separate
# demolition/rotation gates made that race visible even though the ordinary build gate
# often happened to pass.
smoke = SMOKE.read_text(encoding="utf-8")
old_world_gesture = '''        if(stage == 4){
            // The world click must not be intercepted by the Arc HUD. This also proves
            // the pointermove reached WebInput before placement starts.
            if(Core.scene.hasMouse()){
                throw new IllegalStateException("build:ui:" + targetX + "," + targetY);
            }
            if(Math.abs(Core.input.mouseX() - targetScreenX) > 3f || Math.abs(Core.input.mouseY() - targetScreenY) > 3f){
                throw new IllegalStateException(
                    "DOM pointermove did not reach build target: input=" + Core.input.mouseX() + "," + Core.input.mouseY() +
                    " expected=" + targetScreenX + "," + targetScreenY
                );
            }

            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
            pointerDown = true;
            stage = 5;
            markStage("world-down", targetScreenX, targetScreenY);
            return;
        }

        if(stage == 5){
            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            stage = 6;
            markStage("world-up", targetScreenX, targetScreenY);
            return;
        }
'''
new_world_gesture = '''        if(stage == 4){
            // The world click must not be intercepted by the Arc HUD. This also proves
            // the pointermove reached WebInput before placement starts.
            if(Core.scene.hasMouse()){
                throw new IllegalStateException("Chosen build tile is covered by an Arc Scene actor: " + targetX + "," + targetY);
            }
            if(Math.abs(Core.input.mouseX() - targetScreenX) > 3f || Math.abs(Core.input.mouseY() - targetScreenY) > 3f){
                throw new IllegalStateException(
                    "DOM pointermove did not reach build target: input=" + Core.input.mouseX() + "," + Core.input.mouseY() +
                    " expected=" + targetScreenX + "," + targetScreenY
                );
            }

            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
            pointerDown = true;
            pointerButton = 0;
            uiFrames = 0;
            stage = 5;
            markStage("world-down", targetScreenX, targetScreenY);
            return;
        }

        if(stage == 5){
            // Browser DOM input is queued after this smoke observer. Keep the physical
            // left button held until DesktopInput has both entered placing mode *and*
            // produced the real preview linePlan for our target tile. Seeing only the
            // mode is insufficient on slow/headless TeaVM: camera follow can shift the
            // world point between pointerdown and updateLine(), leaving linePlans empty.
            boolean placing = control.input instanceof DesktopInput &&
                ((DesktopInput)control.input).mode == PlaceMode.placing;
            boolean targetPreview = false;
            for(BuildPlan plan : control.input.linePlans){
                if(!plan.breaking && plan.block == Blocks.conveyor && plan.x == targetX && plan.y == targetY){
                    targetPreview = true;
                    break;
                }
            }

            if(!Core.input.keyDown(Binding.select) || !placing || !targetPreview){
                // Re-project the exact chosen tile while the button stays held. A DOM
                // pointermove becomes a stock drag event, allowing updateLine() to
                // converge even if the camera moved after the original projection.
                Vec2 projected = Core.camera.project(new Vec2(
                    targetX * tilesize + tilesize / 2f,
                    targetY * tilesize + tilesize / 2f
                ));
                if(Math.abs(projected.x - targetScreenX) > 0.5f ||
                Math.abs(projected.y - targetScreenY) > 0.5f || !targetPreview){
                    targetScreenX = projected.x;
                    targetScreenY = projected.y;
                    dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, true);
                }

                if(++uiFrames >= maxUiFrames){
                    releasePointer();
                    throw new IllegalStateException(
                        "build:no-line-plan s=" +
                        Core.input.keyDown(Binding.select) + ", p=" + placing +
                        ", t=" + targetPreview + ", n=" + control.input.linePlans.size
                    );
                }
                markWaiting(targetPreview ? "world-down" : "world-line-plan", uiFrames);
                return;
            }

            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            uiFrames = 0;
            stage = 6;
            markStage("world-up", targetScreenX, targetScreenY);
            return;
        }
'''
if smoke.count(old_world_gesture) != 1:
    raise SystemExit("Build-placement world gesture anchor no longer matches")
SMOKE.write_text(smoke.replace(old_world_gesture, new_world_gesture, 1), encoding="utf-8")

application = APPLICATION.read_text(encoding="utf-8")
old_hook = '''                BrowserPlayerInputSmoke.update();
                BrowserPlayerCombatSmoke.update();
'''
new_hook = '''                BrowserPlayerInputSmoke.update();
                BrowserPlayerCombatSmoke.update();
                BrowserBuildPlacementSmoke.update();
'''
if application.count(old_hook) != 1:
    raise SystemExit("BrowserApplication build-placement observer anchor no longer matches post-combat overlay")
APPLICATION.write_text(application.replace(old_hook, new_hook, 1), encoding="utf-8")

verify = VERIFY.read_text(encoding="utf-8")
function_anchor = '''run_locale(){'''
placement_function = '''run_build_placement_map(){
  local profile="/tmp/mindustry-web-profile-build-placement-map"
  local dom="/tmp/mindustry-web-build-placement-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \\
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryBuildPlacementSmoke=1" \\
    --profile "$profile" \\
    --port 9242 \\
    --timeout 75 \\
    --require 'data-mindustry-web="ready"' \\
    --require 'data-mindustry-smoke-mode="production"' \\
    --require 'data-mindustry-input="ready"' \\
    --require 'data-mindustry-input-mode="desktop"' \\
    --require 'data-mindustry-stock-input="desktop"' \\
    --require 'data-mindustry-local-map-state="playing"' \\
    --require 'data-mindustry-local-map-slug="maze"' \\
    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-build-palette="ready"' \\
    --require 'data-mindustry-build-selected="conveyor"' \\
    --require 'data-mindustry-build-placement-smoke="built"' \\
    --require 'data-mindustry-build-placement-source="dom-pointer-event"' \\
    --require 'data-mindustry-build-placement-block="conveyor"' \\
    --require 'data-mindustry-build-placement-plan-observed="true"' \\
    --require 'data-mindustry-build-placement-audio="loopBuild-browser-voice"' \\
    --require 'data-mindustry-network="local-only"' \\
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-build-placement-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-tile-y="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-unit="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-build-frames="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-audio-voices="[1-9][0-9]*"' "$dom"
  echo 'Browser construction: real palette DOM click -> conveyor selection -> world DOM click -> stock DesktopInput BuildPlan -> local builder + stock loopBuild BrowserAudio voice -> completed team conveyor PASS'
}

run_build_removal_map(){
  local profile="/tmp/mindustry-web-profile-build-removal-map"
  local dom="/tmp/mindustry-web-build-removal-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryBuildPlacementSmoke=1&mindustryBuildRemovalSmoke=1" \
    --profile "$profile" \
    --port 9243 \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-input="ready"' \
    --require 'data-mindustry-input-mode="desktop"' \
    --require 'data-mindustry-stock-input="desktop"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-slug="maze"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-build-palette="ready"' \
    --require 'data-mindustry-build-placement-audio="loopBuild-browser-voice"' \
    --require 'data-mindustry-build-removal-smoke="removed"' \
    --require 'data-mindustry-build-removal-source="dom-pointer-event"' \
    --require 'data-mindustry-build-removal-plan-observed="true"' \
    --require 'data-mindustry-build-removal-final-tile="air"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-build-removal-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-tile-y="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-unit="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-frames="[0-9]+"' "$dom"
  echo 'Browser demolition: real conveyor construction -> stock right-click deselect -> second right-click breaking mode -> breaking BuildPlan -> local builder -> air tile PASS'
}


run_build_rotate_map(){
  local profile="/tmp/mindustry-web-profile-build-rotate-map"
  local dom="/tmp/mindustry-web-build-rotate-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryBuildPlacementSmoke=1&mindustryBuildRotateSmoke=1" \
    --profile "$profile" \
    --port 9244 \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-input="ready"' \
    --require 'data-mindustry-input-mode="desktop"' \
    --require 'data-mindustry-stock-input="desktop"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-slug="maze"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-build-palette="ready"' \
    --require 'data-mindustry-build-placement-audio="loopBuild-browser-voice"' \
    --require 'data-mindustry-build-rotate-smoke="rotated"' \
    --require 'data-mindustry-build-rotate-source="dom-key-wheel"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-build-rotate-before="[0-3]"' "$dom"
  grep -Eq 'data-mindustry-build-rotate-after="[0-3]"' "$dom"
  grep -Eq 'data-mindustry-build-rotate-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-rotate-tile-y="[0-9]+"' "$dom"
  if grep -Eq 'data-mindustry-build-rotate-before="([0-3])"[^>]*data-mindustry-build-rotate-after="\1"' "$dom"; then
    echo 'Rotate smoke reported unchanged rotation.' >&2
    exit 1
  fi
  echo 'Browser rotation: real conveyor construction -> DOM R hold + wheel -> stock DesktopInput rotatePlaced -> local InputHandler.rotateBlock -> changed building.rotation PASS'
}

run_locale(){
'''
if verify.count(function_anchor) != 1:
    raise SystemExit("Build-placement verifier function anchor no longer matches post-combat locale gate")
verify = verify.replace(function_anchor, placement_function, 1)

call_anchor = '''run_player_input_map
run_player_combat_map
run_locale en
'''
call_replacement = '''run_player_input_map
run_player_combat_map
run_build_placement_map
run_build_removal_map
run_build_rotate_map
run_locale en
'''
if verify.count(call_anchor) != 1:
    raise SystemExit("Build-placement verifier call anchor no longer matches post-combat production ordering")
verify = verify.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(verify, encoding="utf-8")
print("Extended browser gate with real palette/world DOM clicks through confirmed stock DesktopInput placement, demolition and rotation")
