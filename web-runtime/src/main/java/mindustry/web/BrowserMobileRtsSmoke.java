package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.input.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only original MobileInput RTS browser acceptance test.
 * A physical-style pointerType='touch' tap on the visible stock command
 * TextButton enables the RTS mode; the next tap selects an allied stock
 * Dagger and another tap orders it to move. Neither MobileInput.commandMode,
 * InputHandler.selectedUnits nor CommandAI.targetPos is assigned by the probe.
 */
public final class BrowserMobileRtsSmoke{
    private static boolean checked, enabled, finished;
    private static int stage, frames;
    private static Unit probe;
    private static float commandX, commandY, unitX, unitY, targetX, targetY;
    private static float orderWorldX, orderWorldY;

    private BrowserMobileRtsSmoke(){}

    public static void update(){
        if(!requested() || finished) return;
        if(state == null || !state.isPlaying() || state.gameOver ||
            !BrowserLocalMapRuntime.active() || player == null ||
            player.unit() == null || !player.unit().isAdded() ||
            !(control.input instanceof MobileInput) || !Core.app.isMobile()) return;

        if(stage == 0){
            probe = UnitTypes.dagger.create(player.team());
            probe.set(player.unit().x + 24f, player.unit().y + 12f);
            probe.add();
            if(!(probe.controller() instanceof CommandAI) || !probe.isCommandable()){
                throw new IllegalStateException("Mobile RTS test requires a stock allied controllable Dagger");
            }
            stage = 1;
            frames = 0;
            mark("dagger-spawned");
            return;
        }

        if(!probe.isAdded() || !probe.isValid())
            throw new IllegalStateException("Mobile RTS test stock Dagger despawned");

        if(stage == 1){
            // Find the real mobile command TextButton in the live Arc scene.
            // Prefer the button whose center Scene.hit resolves to itself or
            // its children; this also proves it is not hidden by another UI.
            TextButton[] found = {null};
            Vec2[] pos = {null};
            int[] buttons = {0}, matches = {0}, visibleMatches = {0}, hitMatches = {0};
            StringBuilder samples = new StringBuilder(), hitDetails = new StringBuilder();
            String commandLabel = Core.bundle.get("command");
            Core.scene.root.forEach(actor -> {
                if(!(actor instanceof TextButton button)) return;
                buttons[0]++;
                String label = button.getText().toString();
                if(samples.length() < 250) samples.append('[').append(label).append(']');
                if(!commandLabel.equals(label) && !"@command".equals(label)) return;
                matches[0]++;
                if(!button.visible) return;
                visibleMatches[0]++;
                // A transparent Arc actor can overlap only the middle of a
                // mobile button. Scan several points inside the *real* button,
                // accepting only hits on this button or its descendants.
                float[] locations = {0.5f, 0.25f, 0.75f};
                for(float fy : locations){
                    for(float fx : locations){
                        Vec2 stagePos = button.localToStageCoordinates(new Vec2(
                            button.getWidth() * fx, button.getHeight() * fy));
                        Element top = Core.scene.hit(stagePos.x, stagePos.y, true);
                        if(hitDetails.length() < 650){
                            hitDetails.append('[').append(stagePos.x).append(',')
                                .append(stagePos.y).append(" button=")
                                .append(button.getWidth()).append('x').append(button.getHeight())
                                .append(" top=").append(top == null ? "none" : top.getClass().getSimpleName())
                                .append(" parent=").append(button.parent == null ? "none" :
                                    button.parent.getClass().getSimpleName()).append(']');
                        }
                        if(top == button || (top != null && top.isDescendantOf(button))){
                            hitMatches[0]++;
                            found[0] = button;
                            pos[0] = stagePos;
                            return;
                        }
                    }
                }
            });

            if(found[0] == null){
                if(++frames > 200)
                    throw new IllegalStateException(
                        "Mobile RTS command button is not hittable in the stock Arc scene" +
                        ": localized=" + commandLabel + ", allButtons=" + buttons[0] +
                        ", matches=" + matches[0] + ", visible=" + visibleMatches[0] +
                        ", hittable=" + hitMatches[0] +
                        ", sceneInputUiChildren=" + (control.input.uiGroup == null ? -1 :
                            control.input.uiGroup.getChildren().size) +
                        ", hudGroup=" + (ui.hudGroup == null ? "missing" : ui.hudGroup.getChildren().size) +
                        ", samples=" + samples + ", hitDetails=" + hitDetails +
                        ", scene=" + Core.scene.getWidth() + "x" + Core.scene.getHeight());
                return;
            }
            Vec2 screen = Core.scene.getViewport().project(pos[0]);
            commandX = screen.x;
            commandY = screen.y;
            dispatchTouch("pointerdown", commandX, commandY);
            stage = 2;
            mark("dom-mobile-command-down");
            return;
        }

        if(stage == 2){
            dispatchTouch("pointerup", commandX, commandY);
            stage = 3;
            frames = 0;
            mark("dom-mobile-command-up");
            return;
        }

        if(stage == 3){
            if(control.input.commandMode){
                Vec2 point = Core.camera.project(new Vec2(probe.x, probe.y));
                unitX = point.x;
                unitY = point.y;
                assertGameplayPosition(unitX, unitY, "allied Dagger selection");
                dispatchTouch("pointerdown", unitX, unitY);
                stage = 4;
                mark("dom-mobile-unit-down");
                return;
            }
            if(++frames > 120)
                throw new IllegalStateException("Touching the stock mobile RTS button did not enable command mode");
            return;
        }

        if(stage == 4){
            dispatchTouch("pointerup", unitX, unitY);
            stage = 5;
            frames = 0;
            mark("dom-mobile-unit-up");
            return;
        }

        if(stage == 5){
            if(control.input.selectedUnits.contains(probe)){
                orderWorldX = Math.max(16f, Math.min((world.width()-2)*tilesize,
                    Core.camera.position.x + Core.camera.width * 0.24f));
                orderWorldY = Math.max(16f, Math.min((world.height()-2)*tilesize,
                    Core.camera.position.y + Core.camera.height * 0.12f));
                Vec2 p = Core.camera.project(new Vec2(orderWorldX, orderWorldY));
                targetX = p.x;
                targetY = p.y;
                assertGameplayPosition(targetX,targetY,"RTS move target");
                dispatchTouch("pointerdown",targetX,targetY);
                stage = 6;
                markSelected(probe.id);
                return;
            }
            if(++frames > 120)
                throw new IllegalStateException("MobileInput tap did not select the real allied Dagger");
            return;
        }

        if(stage == 6){
            dispatchTouch("pointerup",targetX,targetY);
            stage = 7;
            frames = 0;
            mark("dom-mobile-order-up");
            return;
        }

        if(stage == 7){
            if(probe.controller() instanceof CommandAI ai &&
                ai.targetPos != null &&
                ai.targetPos.dst(orderWorldX,orderWorldY) < 48f){
                finished = true;
                markCompleted(probe.id,ai.targetPos.x,ai.targetPos.y);
                return;
            }
            if(++frames > 180){
                CommandAI ai = probe.controller() instanceof CommandAI c ? c : null;
                throw new IllegalStateException(
                    "Real mobile DOM touch did not reach stock CommandAI movement" +
                    ": commandMode=" + control.input.commandMode +
                    ", selected=" + control.input.selectedUnits.size +
                    ", actualTarget=" + (ai == null ? "missing AI" : ai.targetPos) +
                    ", intended=" + orderWorldX + "," + orderWorldY);
            }
        }
    }

    private static void assertGameplayPosition(float x,float y,String what){
        if(x < 32f || x > Core.graphics.getWidth()-32f ||
           y < 32f || y > Core.graphics.getHeight()-32f ||
           Core.scene.hasMouse(x,Core.graphics.getHeight()-y)){
            throw new IllegalStateException("Mobile RTS " + what + " outside free gameplay canvas: " +
                x + "," + y + " / " + Core.graphics.getWidth() + "x" + Core.graphics.getHeight());
        }
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
            if(enabled) mark("requested");
        }
        return enabled;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMobileRtsSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params = {"type","sx","sy"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('Mobile RTS game canvas absent');
        const rect = canvas.getBoundingClientRect();
        canvas.dispatchEvent(new PointerEvent(type,{
            pointerId:271,pointerType:'touch',isPrimary:true,
            clientX:rect.left+sx,clientY:rect.top+rect.height-sy,
            button:0,buttons:type==='pointerup'?0:1,
            bubbles:true,cancelable:true
        }));
        """)
    private static native void dispatchTouch(String type,float sx,float sy);

    @JSBody(params={"stage"},script="document.documentElement.setAttribute('data-mindustry-mobile-rts-smoke',stage);")
    private static native void mark(String stage);

    @JSBody(params={"id"},script="""
        document.documentElement.setAttribute('data-mindustry-mobile-rts-selection','native-MobileInput');
        document.documentElement.setAttribute('data-mindustry-mobile-rts-unit-id',String(id));
        """)
    private static native void markSelected(int id);

    @JSBody(params={"id","x","y"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-mobile-rts-smoke','commanded');
        root.setAttribute('data-mindustry-mobile-rts-source','real-dom-touch-mobileinput');
        root.setAttribute('data-mindustry-mobile-rts-command-unit-id',String(id));
        root.setAttribute('data-mindustry-mobile-rts-ai-target',String(x)+','+String(y));
        """)
    private static native void markCompleted(int id,float x,float y);
}
