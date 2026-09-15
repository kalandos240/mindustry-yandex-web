#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "async" / "AsyncCore.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry AsyncCore source: {PATH}")

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
print("Applied Web single-thread AsyncCore with stock process lifecycle and unit avoidance")
