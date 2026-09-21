#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "async" / "AsyncCore.java"
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserGameplayRuntime.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry AsyncCore source: {PATH}")
if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser gameplay runtime source: {RUNTIME}")

text = PATH.read_text(encoding="utf-8")
required = [
    "import java.util.concurrent.*;",
    "private final Seq<Future<?>> futures = new Seq<>();",
    "private ExecutorService executor;",
    "Executors.newFixedThreadPool",
    "futures.add(executor.submit(p::process));",
    "future.get();",
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"AsyncCore Web patch no longer matches pinned upstream: {marker}")

replacement = '''package mindustry.async;

import arc.*;
import arc.struct.*;
import mindustry.game.EventType.*;

import static mindustry.Vars.*;

/**
 * Browser event-loop version of the stock async process coordinator.
 *
 * Process lifecycle and ordering are unchanged: begin() snapshots main-thread state,
 * process() computes against that snapshot, and end() flushes results after the gameplay
 * frame. The only removed behavior is JVM worker creation/Future waiting, which TeaVM's
 * JavaScript target cannot provide.
 */
public class AsyncCore{
    public final Seq<AsyncProcess> processes = Seq.with(
        new PhysicsProcess(),
        avoidance = new AvoidanceProcess()
    );

    public AsyncCore(){
        Events.on(WorldLoadEvent.class, e -> {
            complete();
            for(AsyncProcess p : processes){
                p.init();
            }
        });

        Events.on(ResetEvent.class, e -> {
            complete();
            for(AsyncProcess p : processes){
                p.reset();
            }
        });
    }

    public void begin(){
        if(state.isPlaying()){
            for(AsyncProcess p : processes){
                p.begin();
            }

            // Stock runs these workers concurrently until end(). Web has one event loop,
            // so compute them immediately from the same begin() snapshot. Buffered async
            // processes (including unit avoidance) still expose their previous completed
            // buffer to gameplay and publish the newly computed one on a later frame.
            for(AsyncProcess p : processes){
                if(p.shouldProcess()){
                    p.process();
                }
            }
        }
    }

    public void end(){
        if(state.isPlaying()){
            complete();
            for(AsyncProcess p : processes){
                p.end();
            }
        }
    }

    private void complete(){
        // Synchronous Web processing has no pending Future to await.
    }
}
'''

PATH.write_text(replacement, encoding="utf-8")

runtime = RUNTIME.read_text(encoding="utf-8")
if "data-mindustry-async-core" not in runtime:
    old_import = "import mindustry.ai.*;\nimport mindustry.content.*;"
    new_import = "import mindustry.ai.*;\nimport mindustry.async.*;\nimport mindustry.content.*;"
    if old_import not in runtime:
        raise SystemExit("BrowserGameplayRuntime async import anchor no longer matches")
    runtime = runtime.replace(old_import, new_import, 1)

    old_init = '''        if(logic == null) logic = new Logic();
        if(pathfinder == null) pathfinder = new Pathfinder();
        if(controlPath == null) controlPath = new ControlPathfinder();
        if(fogControl == null) fogControl = new FogControl();

        if(world == null || waves == null || collisions == null || universe == null
        || spawner == null || indexer == null || logicVars == null || logic == null
        || fogControl == null || pathfinder == null || controlPath == null
        || emptyMap == null || emptyTile == null || state.map == null){
'''
    new_init = '''        if(logic == null) logic = new Logic();
        if(pathfinder == null) pathfinder = new Pathfinder();
        if(controlPath == null) controlPath = new ControlPathfinder();
        if(fogControl == null) fogControl = new FogControl();
        if(asyncCore == null) asyncCore = new AsyncCore();

        if(world == null || waves == null || collisions == null || universe == null
        || spawner == null || indexer == null || logicVars == null || logic == null
        || fogControl == null || pathfinder == null || controlPath == null
        || asyncCore == null || avoidance == null
        || emptyMap == null || emptyTile == null || state.map == null){
'''
    if old_init not in runtime:
        raise SystemExit("BrowserGameplayRuntime AsyncCore initialization anchor no longer matches")
    runtime = runtime.replace(old_init, new_init, 1)

    old_play = '''        if(state.isPlaying()){
            if(smokeMode){
                if(!BrowserPlayingRuntime.active()){
                    throw new IllegalStateException("CI Web entered playing state outside the explicit deterministic gameplay smoke");
                }
                BrowserPlayingRuntime.updateFrame();
            }else{
                if(BrowserCampaignRuntime.active()){
                    BrowserCampaignRuntime.updateFrame();
                }else{
                    if(!BrowserLocalMapRuntime.active()){
                        throw new IllegalStateException("Production Web entered playing state outside a local-map or campaign session");
                    }
                    BrowserLocalMapRuntime.updateFrame();
                }
            }
            return;
        }
'''
    new_play = '''        if(state.isPlaying()){
            // Match ClientLauncher.update(): snapshot async inputs before the module frame,
            // compute on the same browser event loop, then flush process outputs after it.
            // This initializes and advances stock PhysicsProcess/AvoidanceProcess without
            // retaining ExecutorService, Future or worker threads in TeaVM.
            asyncCore.begin();
            try{
                if(smokeMode){
                    if(!BrowserPlayingRuntime.active()){
                        throw new IllegalStateException("CI Web entered playing state outside the explicit deterministic gameplay smoke");
                    }
                    BrowserPlayingRuntime.updateFrame();
                }else if(BrowserCampaignRuntime.active()){
                    BrowserCampaignRuntime.updateFrame();
                }else{
                    if(!BrowserLocalMapRuntime.active()){
                        throw new IllegalStateException("Production Web entered playing state outside a local-map or campaign session");
                    }
                    BrowserLocalMapRuntime.updateFrame();
                }
            }finally{
                asyncCore.end();
            }
            return;
        }
'''
    if old_play not in runtime:
        raise SystemExit("BrowserGameplayRuntime playing-frame AsyncCore anchor no longer matches")
    runtime = runtime.replace(old_play, new_play, 1)

    old_ready = '''document.documentElement.setAttribute('data-mindustry-control-pathfinder', 'constructed-web-single-thread'); document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'waiting-menu-frame');'''
    new_ready = '''document.documentElement.setAttribute('data-mindustry-control-pathfinder', 'constructed-web-single-thread'); document.documentElement.setAttribute('data-mindustry-async-core', 'constructed-web-single-thread'); document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'waiting-menu-frame');'''
    if old_ready not in runtime:
        raise SystemExit("BrowserGameplayRuntime ready-marker AsyncCore anchor no longer matches")
    runtime = runtime.replace(old_ready, new_ready, 1)

    RUNTIME.write_text(runtime, encoding="utf-8")

print("Applied Web single-thread AsyncCore with stock PhysicsProcess/AvoidanceProcess lifecycle")
