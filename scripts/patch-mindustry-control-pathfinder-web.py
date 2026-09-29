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
        "    /** Web: true while this pathfinder accepts browser-frame worker steps. */\n    boolean webRunning;\n    /** Preserves the stock periodic invalidation cadence without a sleeping JVM thread. */\n    long webLastInvalidCheck;\n    /** True while a bounded invalid-request sweep still has work for a later frame. */\n    boolean webInvalidSweepPending;\n    /** Round-robin field cursor for the main-thread Web flow-field budget. */\n    int webFieldCursor;\n    /** Main-thread stale cleanup cursors; bounded to avoid O(n) scans per frame. */\n    int webCleanupRequestCursor, webCleanupFieldCursor;\n\n    /** If true, this pathfinder is no longer relevant (stopped) and its errors can be ignored. */\n    volatile boolean invalidated;\n",
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
        webLastInvalidCheck = Time.millis() + invalidateCheckInterval;
    }

    /** Stops browser-frame control pathfinding. */
    private void stop(){
        webRunning = false;
        invalidated = true;
        webFieldCursor = 0;
        webCleanupRequestCursor = 0;
        webCleanupFieldCursor = 0;
        webInvalidSweepPending = false;
        queue.clear();
    }

    /** True only for the current world-bound browser pathfinder instance. */
    public boolean webActive(){
        return webRunning && !invalidated;
    }

    /**
     * Preserve stock stale thresholds while distributing request/field cleanup across
     * browser frames. This avoids a full O(n) scan when hundreds of controlled units exist.
     */
    private void updateWebCleanup(){
        if(invalidated || !state.isPlaying()) return;

        int requestCount = threadPathRequests.size;
        int requestChecks = Math.min(32, requestCount);
        for(int i = 0; i < requestChecks && threadPathRequests.size > 0; i++){
            if(webCleanupRequestCursor >= threadPathRequests.size) webCleanupRequestCursor = 0;
            PathRequest req = threadPathRequests.get(webCleanupRequestCursor++);
            if(req.invalidated) continue;

            if(req.lastUpdateId <= state.updateId - 10 || !req.unit.isAdded()){
                req.invalidated = true;
                queue.post(() -> threadPathRequests.remove(req));
                Time.run(0f, () -> unitRequests.remove(req.unit));
            }
        }

        int fieldCount = fieldList.size;
        int fieldChecks = Math.min(8, fieldCount);
        for(int i = 0; i < fieldChecks && fieldList.size > 0; i++){
            if(webCleanupFieldCursor >= fieldList.size) webCleanupFieldCursor = 0;
            FieldCache field = fieldList.get(webCleanupFieldCursor++);
            if(field.lastUpdateId <= state.updateId - 30){
                queue.post(() -> fields.remove(field.mapKey));
                Time.run(0f, () -> fieldList.remove(field));
            }
        }
    }
'''
if old_start_stop not in text:
    raise SystemExit("ControlPathfinder Web start/stop patch no longer matches pinned upstream")
text = text.replace(old_start_stop, new_start_stop, 1)

old_main_cleanup = '''        //invalidate paths
        Events.run(Trigger.update, () -> {
            for(var req : controlPath.unitRequests.values()){
                //skipped N update -> drop it
                if(req.lastUpdateId <= state.updateId - 10 || !req.unit.isAdded()){
                    req.invalidated = true;
                    //concurrent modification!
                    controlPath.queue.post(() -> controlPath.threadPathRequests.remove(req));
                    Time.run(0f, () -> controlPath.unitRequests.remove(req.unit));
                }
            }

            for(var field : controlPath.fieldList){
                //skipped N update -> drop it
                if(field.lastUpdateId <= state.updateId - 30){
                    //make sure it's only modified on the main thread...? but what about calling get() on this thread??
                    controlPath.queue.post(() -> controlPath.fields.remove(field.mapKey));
                    Time.run(0f, () -> controlPath.fieldList.remove(field));
                }
            }
        });
'''
new_main_cleanup = '''        // Web: same stale criteria, bounded round-robin cleanup.
        Events.run(Trigger.update, () -> controlPath.updateWebCleanup());
'''
if old_main_cleanup not in text:
    raise SystemExit("ControlPathfinder Web main-thread cleanup block no longer matches pinned upstream")
text = text.replace(old_main_cleanup, new_main_cleanup, 1)

old_run = '''    @Override
    public void run(){
        long lastInvalidCheck = Time.millis() + invalidateCheckInterval;

        while(true){
            if(net.client() || invalidated) return;
            try{
                if(state.isPlaying()){
                    queue.run();

                    clustersToUpdate.each(cluster -> {
                        updateClustersComplete(cluster);

                        //just in case: don't redundantly update inner clusters after you've recalculated it entirely
                        clustersToInnerUpdate.remove(cluster);
                    });

                    clustersToInnerUpdate.each(cluster -> {
                        //only recompute the inner links
                        updateClustersInner(cluster);
                    });

                    clustersToInnerUpdate.clear();
                    clustersToUpdate.clear();

                    //periodically check for invalidated paths
                    if(Time.timeSinceMillis(lastInvalidCheck) > invalidateCheckInterval){
                        lastInvalidCheck = Time.millis();

                        var it = invalidRequests.iterator();
                        while(it.hasNext()){
                            var request = it.next();

                            //invalid request, ignore it
                            if(request.invalidated){
                                it.remove();
                                continue;
                            }

                            long mapKey = FieldIndex.get(request.destination, request.costId, request.team);

                            var field = fields.get(mapKey);

                            if(field != null){
                                //it's only worth recalculating a path when the current frontier has finished; otherwise the unit will be following something incomplete.
                                if(field.frontier.isEmpty()){

                                    //remove the field, to be recalculated next update once recalculatePath is processed
                                    fields.remove(field.mapKey);
                                    Core.app.post(() -> fieldList.remove(field));

                                    //once the field is invalidated, make sure that all the requests that have it stored in their 'old' field, so units don't stutter during recalculations
                                    for(var otherRequest : threadPathRequests){
                                        if(otherRequest.destination == request.destination){
                                            otherRequest.oldCache = field;

                                            if(otherRequest != request){
                                                queue.post(() -> recalculatePath(otherRequest));
                                            }
                                        }
                                    }

                                    //the recalculation is done next update, so multiple path requests in the same batch don't end up removing and recalculating the field multiple times.
                                    queue.post(() -> recalculatePath(request));
                                    //it has been processed.
                                    it.remove();
                                }
                            }else{ //there's no field, presumably because a previous request already invalidated it.
                                queue.post(() -> recalculatePath(request));
                                it.remove();
                            }
                        }
                    }

                    //each update time (not total!) no longer than maxUpdate
                    fields.eachValue(cache -> {
                        if(cache != null){
                            updateFields(cache, maxUpdate);
                        }
                    });
                }

                try{
                    Thread.sleep(updateInterval);
                }catch(InterruptedException e){
                    //stop looping when interrupted externally
                    return;
                }
            }catch(Throwable e){
                if(!invalidated){
                    Log.err(e);
                }else{
                    //This pathfinder is done, don't bother doing any tasks
                    return;
                }
            }
        }
    }
'''
new_run = '''    /**
     * Executes one stock ControlPathfinder worker iteration on the browser event loop.
     * Expensive maintenance and flow-field work are bounded separately so construction
     * bursts cannot monopolize rendering/input on the single Web thread.
     */
    public void updateWeb(){
        if(!webRunning || net.client() || invalidated || !state.isPlaying()) return;

        try{
            queue.run();

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
    "private void updateWebCleanup()",
    "int requestChecks = Math.min(32, requestCount);",
    "int fieldChecks = Math.min(8, fieldCount);",
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
print("Applied Web bounded single-thread ControlPathfinder scheduler with stock path algorithms")
