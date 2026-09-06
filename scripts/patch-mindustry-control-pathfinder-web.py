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
        "    /** Web: true while this pathfinder accepts browser-frame worker steps. */\n    boolean webRunning;\n    /** Preserves the stock periodic invalidation cadence without a sleeping JVM thread. */\n    long webLastInvalidCheck;\n\n    /** If true, this pathfinder is no longer relevant (stopped) and its errors can be ignored. */\n    volatile boolean invalidated;\n",
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
     * Cluster rebuilding, invalidation and flow-field algorithms are unchanged; only
     * the dedicated JVM daemon thread and sleep cadence are replaced.
     */
    public void updateWeb(){
        if(!webRunning || net.client() || invalidated || !state.isPlaying()) return;

        try{
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
            if(Time.timeSinceMillis(webLastInvalidCheck) > invalidateCheckInterval){
                webLastInvalidCheck = Time.millis();

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
    "updateFields(cache, maxUpdate);",
    "recalculatePath(request)",
):
    if required not in text:
        raise SystemExit(f"ControlPathfinder Web patch lost stock algorithm marker: {required}")

PATH.write_text(text, encoding="utf-8")
print("Applied Web single-thread ControlPathfinder scheduler with stock cluster/flow-field algorithms")
