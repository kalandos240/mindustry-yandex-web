package mindustry.web;

import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ui.dialogs.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** Stable browser entry points backed by the original pinned ResearchDialog. */
public final class BrowserResearchUi{
    private BrowserResearchUi(){}

    public static void init(){
        int count = 0;
        for(TechNode node : TechTree.all) if(node.content != null) count++;
        markReady(count, TechTree.roots.size);
    }

    public static void show(){
        if(state == null || !(state.isMenu() || state.isCampaign())) return;
        if(ui.research == null) throw new IllegalStateException("Original ResearchDialog is not installed");
        ui.research.show();
    }

    public static void escape(){ close(); }
    public static void close(){ if(ui.research != null) ui.research.hide(); }
    public static boolean isOpen(){ return ui != null && ui.research != null && ui.research.isShown(); }
    public static void refresh(){ if(isOpen()) ui.research.rebuildItems(); }

    @JSBody(params={"nodes","roots"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-research-ui','ready');
        r.setAttribute('data-mindustry-research-implementation','original-ResearchDialog');
        r.setAttribute('data-mindustry-research-catalog','techtree-all');
        r.setAttribute('data-mindustry-research-layout','hierarchical-techtree');
        r.setAttribute('data-mindustry-research-tech-nodes',String(nodes));
        r.setAttribute('data-mindustry-research-tree-roots',String(roots));
        r.setAttribute('data-mindustry-research-open','no');
        """)
    private static native void markReady(int nodes,int roots);
}
