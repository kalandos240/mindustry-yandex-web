package mindustry.web;

import arc.struct.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Opt-in browser CI check that visits EVERY loaded vanilla Block on both
 * campaign planets. It validates registry/build-factory integrity; it does
 * not pretend that instantiating a building proves its gameplay recipe works.
 * Work is budgeted across frames, with no modification of a live game tile.
 */
public final class BrowserBlockRegistrySmoke{
    private static boolean checked, enabled, done;
    private static int cursor, buildings, iterations, total, vanillaClasses;
    private static String planet = "none";

    private BrowserBlockRegistrySmoke(){}

    public static void update(){
        if(!requested() || done || content == null || state == null
            || !state.isPlaying() || state.rules == null || state.rules.sector == null) return;

        if("none".equals(planet)) planet = state.rules.sector.planet.name;
        Seq<Block> all = content.blocks();
        if(total == 0){
            total = all.size;
            if(total < 100) throw new IllegalStateException(
                "Incomplete vanilla block registry: only " + total + " entries");
        }
        for(int frameBatch = 0; frameBatch < 12 && cursor < all.size; frameBatch++, cursor++){
            Block block = all.get(cursor);
            if(block == null || block.name == null || block.name.isEmpty()){
                throw new IllegalStateException("Invalid vanilla block entry at " + cursor);
            }
            if(block.size < 1) throw new IllegalStateException(
                "Invalid footprint for " + block.name + ": " + block.size);

            if(block.hasBuilding()){
                if(block.buildType == null){
                    throw new IllegalStateException("Missing building constructor for " + block.name);
                }
                try{
                    Building instance = block.newBuilding();
                    if(instance == null) throw new IllegalStateException("Building factory returned null");
                    buildings++;
                    if(instance.getClass() != Building.class) vanillaClasses++;
                }catch(Throwable failure){
                    throw new IllegalStateException(
                        "Vanilla building factory could not construct " + block.name, failure);
                }
            }
        }
        iterations++;
        if(cursor >= all.size){
            if(buildings < 80 || vanillaClasses < 40) throw new IllegalStateException(
                "Vanilla block subclass coverage too small: " + buildings + "/" + vanillaClasses);
            done = true;
            markPassed(planet, total, buildings, vanillaClasses, iterations);
        }
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryBlockRegistrySmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params={"planet","total","buildings","subclasses","frames"}, script="""
        const root = document.documentElement;
        root.setAttribute('data-mindustry-block-registry', 'passed');
        root.setAttribute('data-mindustry-block-registry-planet', planet);
        root.setAttribute('data-mindustry-block-registry-total', String(total));
        root.setAttribute('data-mindustry-block-registry-buildings', String(buildings));
        root.setAttribute('data-mindustry-block-registry-subclasses', String(subclasses));
        root.setAttribute('data-mindustry-block-registry-frames', String(frames));
        """)
    private static native void markPassed(String planet,int total,int buildings,int subclasses,int frames);
}
