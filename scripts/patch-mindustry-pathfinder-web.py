#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "ai" / "Pathfinder.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry Pathfinder source: {PATH}")

text = PATH.read_text(encoding="utf-8")

replacements = [
    (
        "public class Pathfinder implements Runnable{",
        "public class Pathfinder{",
        "class declaration",
    ),
    (
        "    private static final int updateFPS = 60;\n    private static final int updateInterval = 1000 / updateFPS;\n",
        "",
        "worker timing constants",
    ),
    (
        "    /** Current pathfinding thread */\n    @Nullable Thread thread;\n",
        "    /** Web: true while the browser-frame pathfinding scheduler is active. */\n    boolean webActive;\n",
        "worker field",
    ),
]

for old, new, name in replacements:
    if old not in text:
        raise SystemExit(f"Pathfinder Web patch no longer matches pinned upstream ({name})")
    text = text.replace(old, new, 1)

old_start = '''    /** Starts or restarts the pathfinding thread. */
    private void start(){
        stop();
        if(net.client()) return;

        thread = new Thread(this, "Pathfinder");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the pathfinding thread. */
    private void stop(){
        if(thread != null){
            thread.interrupt();
            thread = null;
        }
        queue.clear();
        needsRefresh = false;
    }
'''
new_start = '''    /** Starts or restarts browser-frame pathfinding. */
    private void start(){
        stop();
        if(net.client()) return;
        webActive = true;
    }

    /** Stops browser-frame pathfinding and clears deferred work. */
    private void stop(){
        webActive = false;
        queue.clear();
        needsRefresh = false;
    }
'''
if old_start not in text:
    raise SystemExit("Pathfinder Web start/stop patch no longer matches pinned upstream")
text = text.replace(old_start, new_start, 1)

old_run = '''    /** Thread implementation. */
    @Override
    public void run(){
        while(true){
            if(net.client()) return;
            try{

                if(state.isPlaying()){
                    queue.run();

                    //each update time (not total!) no longer than maxUpdate
                    for(Flowfield data : threadList){

                        //if it's dirty and there is nothing to update, begin updating once more
                        if(data.dirty && data.frontier.size == 0){
                            updateTargets(data);
                            data.dirty = false;
                        }

                        updateFrontier(data, maxUpdate);
                    }
                }

                try{
                    Thread.sleep(updateInterval);
                }catch(InterruptedException e){
                    //stop looping when interrupted externally
                    return;
                }
            }catch(Throwable e){
                e.printStackTrace();
            }
        }
    }
'''
new_run = '''    /**
     * Executes one stock Pathfinder worker iteration on the browser event loop.
     * The flow-field algorithm and its per-field 8 ms frontier budget are unchanged;
     * only the JVM daemon-thread/sleep scheduler is replaced.
     */
    public void updateWeb(){
        if(!webActive || net.client() || !state.isPlaying()) return;

        try{
            queue.run();

            //each update time (not total!) no longer than maxUpdate
            for(Flowfield data : threadList){

                //if it's dirty and there is nothing to update, begin updating once more
                if(data.dirty && data.frontier.size == 0){
                    updateTargets(data);
                    data.dirty = false;
                }

                updateFrontier(data, maxUpdate);
            }
        }catch(Throwable e){
            e.printStackTrace();
        }
    }
'''
if old_run not in text:
    raise SystemExit("Pathfinder Web run-loop patch no longer matches pinned upstream")
text = text.replace(old_run, new_run, 1)

# Pathfinder must remain algorithmically intact but contain no JVM worker scheduler.
for forbidden in (
    "implements Runnable",
    "@Nullable Thread thread",
    "new Thread(",
    "Thread.sleep(",
    ".interrupt()",
    "Thread.MIN_PRIORITY",
    ".setDaemon(",
):
    if forbidden in text:
        raise SystemExit(f"Pathfinder Web patch retained worker-thread marker: {forbidden}")

required = (
    "public void updateWeb()",
    "updateFrontier(data, maxUpdate);",
    "updateTargets(data);",
    "queue.run();",
    "preloadPath(getField(state.rules.waveTeam, costGround, fieldCore));",
)
for marker in required:
    if marker not in text:
        raise SystemExit(f"Pathfinder Web patch lost stock algorithm marker: {marker}")

PATH.write_text(text, encoding="utf-8")
print("Applied Web single-thread Pathfinder scheduler with stock flow-field algorithms")
