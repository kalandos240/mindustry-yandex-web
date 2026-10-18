package mindustry.ui.dialogs;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.web.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** Original ResearchDialog with browser lifecycle and read-only native input telemetry. */
public class BrowserResearchDialog extends ResearchDialog{
    private int sampleFrames;
    private boolean queryChecked, diagnostics, fixture;

    public BrowserResearchDialog(){
        name = "web-native-research";
        view.name = "web-native-research-view";
        shown(() -> markOpen(true));
        hidden(() -> markOpen(false));
        onResize(this::fitFooter);
        shown(this::fitFooter);
    }

    private void fitFooter(){
        float scale = arc.scene.ui.layout.Scl.scl(1f);
        float width = Math.min(210f, (Core.scene.getWidth() / scale - 20f) / 2f);
        boolean changed = false;
        for(var cell : buttons.getCells()){
            if(!(cell.get() instanceof Button button)) continue;
            float height = Math.max(64f, button.getPrefHeight()/scale);
            if(Math.abs(cell.minWidth()-width*scale) > 0.5f || Math.abs(cell.minHeight()-height*scale) > 0.5f){
                cell.width(width).height(height);
                changed = true;
            }
        }
        if(changed) buttons.invalidateHierarchy();
    }

    private void fitHeaders(){
        Element title = titleTable.find("web-research-choose-tree");
        if(title == null || itemDisplay.getChildren().isEmpty()) return;
        Element inventory = itemDisplay.getChildren().first();
        // Native portrait margin can be reset by ItemsDisplay.rebuild(). A
        // narrow landscape portal needs the same separation as a phone.
        boolean collide = title.visible && inventory.getPrefWidth() + title.getWidth()/2f + 8f > Core.scene.getWidth()/2f;
        float margin = collide ? title.getHeight() + arc.scene.ui.layout.Scl.scl(8f) : 0f;
        if(Math.abs(itemDisplay.getMarginTop() - margin) > 0.5f){
            itemDisplay.marginTop(margin / arc.scene.ui.layout.Scl.scl(1f));
            itemDisplay.invalidateHierarchy();
        }
    }

    @Override
    public void act(float delta){
        super.act(delta);
        if(!isShown()) return;
        fitFooter();
        fitHeaders();
        if((++sampleFrames & 15) != 0) return;
        if(!queryChecked){ diagnostics = requested(); queryChecked = true; }
        int visible = 0;
        for(var node : nodes) if(node.visible) visible++;
        String selected = view.hoverNode != null && view.hoverNode.userObject instanceof TechNode node
            ? node.content.name : lastNode.content.name;
        markState(lastNode.content.name, selected, visible, view.panX, view.panY, view.scaleX);
        if(!diagnostics) return;
        // Test-only world inventory; purchases still go through native spend().
        if(!fixture && state.isCampaign() && player.team().core() != null){
            player.team().core().items.set(Items.copper, 500);
            fixture = true;
            rebuildItems();
        }
        markPurchase(Blocks.conveyor.unlocked(), player.team().core() == null ? -1 : player.team().core().items.get(Items.copper),
            Blocks.conveyor.techNode.finishedRequirements[0].amount);
        report("title", titleTable.find("web-research-choose-tree"));
        report("items", itemDisplay.getChildren().isEmpty() ? null : itemDisplay.getChildren().first());
        report("view", view);
        report("pause-menu", Core.scene.root.find("web-pause-dialog"));
        report("conveyor", view.find("web-research-node-conveyor"));
        report("drill", view.find("web-research-node-mechanical-drill"));
        report("invest", view.find("web-research-invest-conveyor"));
        report("root-serpulo", Core.scene.root.find("web-research-root-core-shard"));
        report("root-erekir", Core.scene.root.find("web-research-root-core-bastion"));
        int footerIndex = 0;
        for(Element e : buttons.getChildren()){
            if(e instanceof TextButton button){
                String key = footerIndex++ == 0 ? "back" : "database";
                report(key, button);
                report(key + "-label", button.getLabel());
            }
        }
    }

    private static void report(String key, Element actor){
        if(actor == null || actor.getWidth() <= 1f || actor.getHeight() <= 1f){ markRect(key, 0,0,0,0,false); markVisible(key,false); return; }
        Vec2 lo = actor.localToStageCoordinates(new Vec2());
        Vec2 hi = actor.localToStageCoordinates(new Vec2(actor.getWidth(), actor.getHeight()));
        Element hit = Core.scene.hit((lo.x+hi.x)/2f, (lo.y+hi.y)/2f, true);
        boolean visible = actor.visible;
        for(Group p = actor.parent; p != null; p = p.parent) visible &= p.visible;
        markVisible(key,visible);
        Core.scene.getViewport().project(lo);
        Core.scene.getViewport().project(hi);
        markRect(key,lo.x,lo.y,hi.x,hi.y,visible && (hit == actor || hit != null && hit.isDescendantOf(actor)));
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryNativeResearchSmoke') === '1';")
    private static native boolean requested();
    @JSBody(params={"open"},script="document.documentElement.setAttribute('data-mindustry-research-open',open?'yes':'no');")
    private static native void markOpen(boolean open);
    @JSBody(params={"key","visible"},script="document.documentElement.setAttribute('data-mindustry-native-research-'+key+'-visible',visible?'yes':'no');")
    private static native void markVisible(String key,boolean visible);
    @JSBody(params={"root","selected","visible","x","y","scale"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-research-tree-root',root);
        r.setAttribute('data-mindustry-research-tree-selected',selected);
        r.setAttribute('data-mindustry-research-tree-visible-nodes',String(visible));
        r.setAttribute('data-mindustry-research-native-pan',JSON.stringify([x,y,scale]));
        """)
    private static native void markState(String root,String selected,int visible,float x,float y,float scale);
    @JSBody(params={"key","x0","y0","x1","y1","exposed"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-native-research-'+key,JSON.stringify([x0,y0,x1,y1]));
        r.setAttribute('data-mindustry-native-research-'+key+'-exposed',exposed?'yes':'no');
        """)
    private static native void markRect(String key,float x0,float y0,float x1,float y1,boolean exposed);
    @JSBody(params={"unlocked","copper","progress"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-native-research-conveyor-unlocked',unlocked?'yes':'no');
        r.setAttribute('data-mindustry-native-research-copper',String(copper));
        r.setAttribute('data-mindustry-native-research-progress',String(progress));
        """)
    private static native void markPurchase(boolean unlocked,int copper,int progress);
}
