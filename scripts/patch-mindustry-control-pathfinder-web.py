#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "ai" / "ControlPathfinder.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry ControlPathfinder source: {PATH}")

text = PATH.read_text(encoding="utf-8")

replacements = [
    (
        "public class ControlPathfinder implements Runnable{",
        "public class ControlPathfinder{",
        "class declaration",
    ),
    (
        "    private static final int updateFPS = 30;\n    private static final int updateInterval = 1000 / updateFPS, invalidateCheckInterval = 1000;\n",
        "    private static final int invalidateCheckInterval = 1000;\n",
        "worker timing constants",
    ),
    (
        "    /** Current pathfinding thread */\n    @Nullable Thread thread;\n\n    /** If true, this pathfinder is no longer relevant (stopped) and its errors can be ignored. */\n    volatile boolean invalidated;\n",
        "    /** Web: true while this pathfinder accepts browser-frame worker steps. */\n    boolean webRunning;\n    /** Preserves the stock periodic invalidation cadence without a sleeping JVM thread. */\n    long webLastInvalidCheck;\n    /** True while a bounded invalid-request sweep still has work for a later frame. */\n    boolean webInvalidSweepPending;\n    /** Round-robin field cursor for the main-thread Web budget. */\n    int webFieldCursor;\n\n    /** If true, this pathfinder is no longer relevant (stopped) and its errors can be ignored. */\n    volatile boolean invalidated;\n",
        "worker fields",
    ),
]

for old, new, label in replacements:
    if old not in text:
        raise SystemExit(f"ControlPathfinder Web patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

old_start_stop = '''    /** Starts or restarts the pathfinding thread. */
    private void start(){
        if(net.client() || thread != null) return;

        thread = new Thread(this, "Control Pathfinder");
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
        invalidated = true;
        queue.clear();
    }
'''
new_start_stop = '''    /** Starts or restarts browser-frame control pathfinding. */
    private void start(){
        if(net.client() || webRunning) return;

        invalidated = false;
        webRunning = true;
        // Preserve the stock initial delay before the first invalid-path sweep.
        webLastInvalidCheck = Time.millis() + invalidateCheckInterval;
    }

    /** Stops browser-frame control pathfinding. */
    private void stop(){
        webRunning = false;
        invalidated = true;
        webFieldCursor = 0;
        webInvalidSweepPending = false;
        queue.clear();
    }

    /** True only for the current world-bound browser pathfinder instance. */
    public boolean webActive(){
        return webRunning && !invalidated;
    }
'''
if old_start_stop not in text:
    raise SystemExit("ControlPathfinder Web start/stop patch no longer matches pinned upstream")
text = text.replace(old_start_stop, new_start_stop, 1)

old_run = '''    @Override
    public void run(){
        long lastInvalidCheck = Time.millis() + invalidateCheckInterval;

        while(true){
            if(net.client() || invalidated) return;
            try{
                if(state.isPlaying()){
                    queue.run();

                    // Web/Yandex shares one event loop with rendering/input. Bound cluster
            // rebuilds and invalidation bursts so large construction/destruction spikes
            // cannot monopolize one frame; unprocessed entries remain queued for later.
            long maintenanceBudget = Time.millisToNanos(Core.app != null && Core.app.isMobile() ? 1 : 2);
            long maintenanceStart = Time.nanos();

            var fullClusters = clustersToUpdate.iterator();
            while(fullClusters.hasNext && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget){
                int cluster = fullClusters.next();
                updateClustersComplete(cluster);
                clustersToInnerUpdate.remove(cluster);
                fullClusters.remove();
            }

            if(Time.timeSinceNanos(maintenanceStart) < maintenanceBudget){
                var innerClusters = clustersToInnerUpdate.iterator();
                while(innerClusters.hasNext && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget){
                    int cluster = innerClusters.next();
                    updateClustersInner(cluster);
                    innerClusters.remove();
                }
            }

            if(!webInvalidSweepPending && Time.timeSinceMillis(webLastInvalidCheck) > invalidateCheckInterval){
                webLastInvalidCheck = Time.millis();
                webInvalidSweepPending = true;
            }

            if(webInvalidSweepPending && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget){
                var it = invalidRequests.iterator();
                while(it.hasNext() && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget){
                    var request = it.next();

                    if(request.invalidated){
                        it.remove();
                        continue;
                    }

                    long mapKey = FieldIndex.get(request.destination, request.costId, request.team);
                    var field = fields.get(mapKey);

                    if(field != null){
                        if(field.frontier.isEmpty()){
                            fields.remove(field.mapKey);
                            Core.app.post(() -> fieldList.remove(field));

                            for(var otherRequest : threadPathRequests){
                                if(otherRequest.destination == request.destination){
                                    otherRequest.oldCache = field;
                                    if(otherRequest != request){
                                        queue.post(() -> recalculatePath(otherRequest));
                                    }
                                }
                            }

                            queue.post(() -> recalculatePath(request));
                            it.remove();
                        }
                    }else{
                        queue.post(() -> recalculatePath(request));
                        it.remove();
                    }
                }
                if(!it.hasNext()) webInvalidSweepPending = false;
            }

            // Desktop gives every cache up to 12 ms on a worker thread. Web must
            // share one event loop with rendering/input, so spend a bounded TOTAL slice and
            // resume round-robin next frame. updateFields() itself remains stock.
            int fieldCount = fieldList.size;
            if(fieldCount > 0){
                long frameBudget = Time.millisToNanos(Core.app != null && Core.app.isMobile() ? 2 : 3);
                long frameStart = Time.nanos();
                int visited = 0;

                while(visited < fieldCount && Time.timeSinceNanos(frameStart) < frameBudget){
                    if(webFieldCursor >= fieldList.size) webFieldCursor = 0;
                    FieldCache cache = fieldList.get(webFieldCursor++);
                    visited++;
                    if(cache == null || fields.get(cache.mapKey) != cache) continue;

                    long remaining = frameBudget - Time.timeSinceNanos(frameStart);
                    if(remaining <= 0L) break;
                    updateFields(cache, Math.min(maxUpdate, remaining));
                }
            }
        }catch(Throwable e){
            if(!invalidated){
                Log.err(e);
            }
        }
    }
'''
if old_run not in text:
    raise SystemExit("ControlPathfinder Web run-loop patch no longer matches pinned upstream")
text = text.replace(old_run, new_run, 1)

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
        raise SystemExit(f"ControlPathfinder Web patch retained worker-thread marker: {forbidden}")

for required in (
    "public void updateWeb()",
    "public boolean webActive()",
    "updateClustersComplete(cluster);",
    "updateClustersInner(cluster);",
    "long maintenanceBudget = Time.millisToNanos(Core.app != null && Core.app.isMobile() ? 1 : 2);",
    "while(fullClusters.hasNext && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget)",
    "while(innerClusters.hasNext && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget)",
    "webInvalidSweepPending",
    "long frameBudget = Time.millisToNanos(Core.app != null && Core.app.isMobile() ? 2 : 3);",
    "while(visited < fieldCount && Time.timeSinceNanos(frameStart) < frameBudget)",
    "updateFields(cache, Math.min(maxUpdate, remaining));",
    "recalculatePath(request)",
):
    if required not in text:
        raise SystemExit(f"ControlPathfinder Web patch lost stock algorithm marker: {required}")

PATH.write_text(text, encoding="utf-8")
print("Applied Web single-thread ControlPathfinder scheduler with stock cluster/flow-field algorithms")
