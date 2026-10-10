package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only real canvas PointerEvents for research navigation.
 * No TechTree flags, unlock states or BrowserResearchUi selection fields are
 * changed by the observer: it can pass only by clicking genuine Scene buttons.
 */
public final class BrowserResearchTreeSmoke{
    private static boolean checked, enabled, complete;
    private static int stage, frames;
    private static float tapX, tapY;

    private BrowserResearchTreeSmoke(){}

    public static void update(){
        if(!requested() || complete || state == null || !state.isMenu() || !BrowserResearchUi.isOpen()) return;
        if(++frames > 600) throw new IllegalStateException("Research tree real-pointer navigation stalled: stage=" + stage +
            ", root=" + BrowserResearchUi.activeRootContent() + ", selected=" + BrowserResearchUi.selectedContent());

        switch(stage){
            case 0 -> down("web-research-root-core-bastion", 1);
            case 1 -> up(2);
            case 2 -> {
                if(BrowserResearchUi.activeRootContent().equals("core-bastion")) stage = 3;
            }
            case 3 -> down("web-research-root-core-shard", 4);
            case 4 -> up(5);
            case 5 -> {
                if(BrowserResearchUi.activeRootContent().equals("core-shard")) stage = 6;
            }
            case 6 -> down("web-research-node-conveyor", 7);
            case 7 -> up(8);
            case 8 -> {
                if(BrowserResearchUi.selectedContent().equals("conveyor")) stage = 9;
            }
            case 9 -> down("web-research-node-junction", 10);
            case 10 -> up(11);
            case 11 -> {
                if(BrowserResearchUi.selectedContent().equals("junction")
                    && Core.scene.root.find("web-research-node-router") != null){
                    markPassed();
                    stage = 12;
                }
            }
            // A real click on an expanded branch now collapses it. Validate
            // that Router disappears, then reveal it again through real
            // browser PointerEvents instead of setting expanded flags.
            case 12 -> down("web-research-node-junction", 13);
            case 13 -> up(14);
            case 14 -> {
                if(Core.scene.root.find("web-research-node-router") == null) stage = 15;
            }
            case 15 -> down("web-research-node-junction", 16);
            case 16 -> up(17);
            case 17 -> {
                if(Core.scene.root.find("web-research-node-router") != null){
                    Element junction = Core.scene.root.find("web-research-node-junction");
                    if(junction == null) return;
                    Vec2 center = junction.localToStageCoordinates(
                        new Vec2(junction.getWidth() * 0.5f, junction.getHeight() * 0.5f));
                    Element hit = Core.scene.hit(center.x, center.y, true);
                    if(hit != junction && (hit == null || !hit.isDescendantOf(junction))) return;
                    Vec2 screen = Core.scene.getViewport().project(center);
                    markTap(screen.x, screen.y);
                    complete = true;
                    markCollapsePassed();
                }
            }
        }
    }

    private static void down(String name, int nextStage){
        Element element = Core.scene.root.find(name);
        if(element == null) return;
        Vec2 stagePoint = element.localToStageCoordinates(new Vec2(element.getWidth() * 0.5f, element.getHeight() * 0.5f));
        Element hit = Core.scene.hit(stagePoint.x, stagePoint.y, true);
        if(hit != element && (hit == null || !hit.isDescendantOf(element))){
            // A spatial technology graph can put a real node outside the
            // scroll viewport. Move only the STOCK ScrollPane camera, then
            // try the same actual DOM pointer tap on the following frame.
            // Never mutate the TechTree, clicked selection or unlock flags.
            if(element.parent instanceof ScrollPane){ // No direct graph? kept for UI variants.
                ((ScrollPane)element.parent).validate();
            }else if(element.parent != null && element.parent.parent instanceof ScrollPane pane){
                pane.validate();
                element.parent.validate();
                pane.scrollTo(element.x, element.y, element.getWidth(), element.getHeight(), true, true);
                pane.updateVisualScroll();
            }
            markStage(stage, "scroll-to-" + name);
            return;
        }
        Vec2 projected = Core.scene.getViewport().project(stagePoint);
        tapX = projected.x;
        tapY = projected.y;
        markTap(tapX, tapY);
        dispatchPointer("pointerdown", tapX, tapY);
        stage = nextStage;
        markStage(stage, "pressed-" + name);
    }

    private static void up(int nextStage){
        dispatchPointer("pointerup", tapX, tapY);
        stage = nextStage;
        markStage(stage, "released");
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryResearchTreeSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params = {"type", "x", "y"}, script = """
        const canvas=document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('Missing Mindustry game canvas');
        const rect=canvas.getBoundingClientRect();
        canvas.dispatchEvent(new PointerEvent(type,{
            bubbles:true,cancelable:true,pointerId:397,pointerType:'mouse',
            clientX:rect.left+x,clientY:rect.top+rect.height-y,
            button:0,buttons:type==='pointerup'?0:1
        }));
        """)
    private static native void dispatchPointer(String type, float x, float y);

    @JSBody(params={"x","y"},script = """
        const root=document.documentElement;
        root.setAttribute('data-mindustry-research-pointer-x',String(x));
        root.setAttribute('data-mindustry-research-pointer-y',String(y));
        """)
    private static native void markTap(float x, float y);

    @JSBody(script = """
        document.documentElement.setAttribute('data-mindustry-research-tree-collapse','passed');
        """)
    private static native void markCollapsePassed();

    @JSBody(params={"stage","detail"},script = """
        const root=document.documentElement;
        root.setAttribute('data-mindustry-research-tree-stage',String(stage));
        root.setAttribute('data-mindustry-research-tree-detail',detail);
        """)
    private static native void markStage(int stage,String detail);

    @JSBody(script = """
        const root=document.documentElement;
        root.setAttribute('data-mindustry-research-tree-navigation','passed');
        root.setAttribute('data-mindustry-research-tree-navigation-source','real-dom-pointer-arc-scene');
        """)
    private static native void markPassed();
}
