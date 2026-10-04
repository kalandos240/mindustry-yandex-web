#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"
SMOKE = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserPlayerMiningSmoke.java"
BUILD_SMOKE = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserBuildPlacementSmoke.java"

for path in (APPLICATION, VERIFY, SMOKE, BUILD_SMOKE):
    if not path.is_file():
        raise SystemExit(f"Missing player-mining/build smoke source: {path}")

# The build-placement overlay runs immediately before this patch. TeaVM DOM events are
# delivered between application frames, so confirming DesktopInput.mode==placing and
# releasing in the same smoke callback is still racy: the release can be consumed before
# stock input has retained a complete placing frame/linePlans. Hold one additional full
# frame, then require the real Binding.select release transition before watching BuildPlans.
build_smoke = BUILD_SMOKE.read_text(encoding="utf-8")
old_release = '''        if(stage == 5){
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
                        "DOM left-click never produced confirmed target linePlan: select=" +
                        Core.input.keyDown(Binding.select) + ", placing=" + placing +
                        ", targetPreview=" + targetPreview + ", linePlans=" + control.input.linePlans.size
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

        Tile tile = world.tile(targetX, targetY);
'''
new_release = '''        if(stage == 5){
            // First prove the real stock placement preview exists on the exact target.
            // This includes the camera-drift recovery added by the preceding overlay.
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
                        "DOM left-click never produced confirmed target linePlan: select=" +
                        Core.input.keyDown(Binding.select) + ", placing=" + placing +
                        ", targetPreview=" + targetPreview + ", linePlans=" + control.input.linePlans.size
                    );
                }
                markWaiting(targetPreview ? "world-down" : "world-line-plan", uiFrames);
                return;
            }

            // Retain one complete additional stock application frame with the exact
            // target preview and select binding held. This prevents a slow TeaVM event
            // turn from collapsing preview creation and the release edge together.
            uiFrames = 0;
            stage = -5;
            markStage("world-line-plan-confirmed", targetScreenX, targetScreenY);
            return;
        }

        if(stage == -5){
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
                releasePointer();
                throw new IllegalStateException(
                    "Stock DesktopInput lost confirmed target linePlan before DOM release: select=" +
                    Core.input.keyDown(Binding.select) + ", placing=" + placing +
                    ", targetPreview=" + targetPreview + ", linePlans=" + control.input.linePlans.size
                );
            }

            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            uiFrames = 0;
            stage = 6;
            markStage("world-up", targetScreenX, targetScreenY);
            return;
        }

        if(stage == 6){
            boolean stillPlacing = control.input instanceof DesktopInput &&
                ((DesktopInput)control.input).mode == PlaceMode.placing;
            if(Core.input.keyDown(Binding.select) || stillPlacing){
                if(++uiFrames >= maxUiFrames){
                    throw new IllegalStateException("DOM left-click release never left confirmed DesktopInput placing mode");
                }
                markWaiting("world-release", uiFrames);
                return;
            }
            uiFrames = 0;
        }

        Tile tile = world.tile(targetX, targetY);
'''
if build_smoke.count(old_release) != 1:
    raise SystemExit("Post-placement release handshake anchor no longer matches")
BUILD_SMOKE.write_text(build_smoke.replace(old_release, new_release, 1), encoding="utf-8")

application = APPLICATION.read_text(encoding="utf-8")
old_hook = '''                BrowserBuildPlacementSmoke.update();
'''
new_hook = '''                BrowserBuildPlacementSmoke.update();
                BrowserPlayerMiningSmoke.update();
'''
if application.count(old_hook) != 1:
    raise SystemExit("BrowserApplication mining observer anchor no longer matches post-build-placement overlay")
APPLICATION.write_text(application.replace(old_hook, new_hook, 1), encoding="utf-8")

verify = VERIFY.read_text(encoding="utf-8")
function_anchor = '''run_locale(){
'''
mining_function = '''run_player_mining_map(){
  local profile="/tmp/mindustry-web-profile-player-mining-map"
  local dom="/tmp/mindustry-web-player-mining-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryPlayerMiningSmoke=1" \
    --profile "$profile" \
    --port 9245 \
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
    --require 'data-mindustry-player-mining-smoke="deposited"' \
    --require 'data-mindustry-player-mining-source="dom-pointer-event"' \
    --require 'data-mindustry-player-mining-transfer="miner-auto"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-player-mining-item="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-player-mining-core-delta="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-player-mining-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-player-mining-tile-y="[0-9]+"' "$dom"
  echo 'Browser mining: real DOM ore click -> stock DesktopInput tryBeginMine -> player MinerComp -> local InputHandler.transferItemTo -> core inventory increase PASS'
}

run_locale(){
'''
if verify.count(function_anchor) != 1:
    raise SystemExit("Player-mining verifier function anchor no longer matches post-build-placement locale gate")
verify = verify.replace(function_anchor, mining_function, 1)

call_anchor = '''run_player_combat_map
run_build_placement_map
'''
call_replacement = '''run_player_combat_map
run_player_mining_map
run_build_placement_map
'''
if verify.count(call_anchor) != 1:
    raise SystemExit("Player-mining verifier call anchor no longer matches production ordering")
verify = verify.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(verify, encoding="utf-8")
print("Extended browser gate with real DOM mining and stock player MinerComp core transfer; stabilized confirmed build release")
