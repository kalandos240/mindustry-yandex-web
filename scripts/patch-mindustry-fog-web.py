#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "game" / "FogControl.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry FogControl source: {PATH}")

text = PATH.read_text(encoding="utf-8")

old_fields = '''    private static final Object notifyStatic = new Object(), notifyDynamic = new Object();

    /** indexed by team */
    private volatile @Nullable FogData[] fog;

    private final LongSeq staticEvents = new LongSeq();
    private final LongSeq dynamicEventQueue = new LongSeq(), unitEventQueue = new LongSeq();
    /** access must be synchronized; accessed from both threads */
    private final LongSeq dynamicEvents = new LongSeq(100);

    private @Nullable Thread staticFogThread;
    private @Nullable Thread dynamicFogThread;
'''
new_fields = '''    /** indexed by team */
    private volatile @Nullable FogData[] fog;

    private final LongSeq staticEvents = new LongSeq();
    private final LongSeq dynamicEventQueue = new LongSeq(), unitEventQueue = new LongSeq();
    private final LongSeq dynamicEvents = new LongSeq(100);
    // Web: dynamic visibility uses the same stock double-buffer algorithm on the
    // browser event loop; reuse this bitset instead of allocating it every frame.
    private final Bits webDynamicCleared = new Bits(256);
'''
if old_fields not in text:
    raise SystemExit("FogControl Web field patch no longer matches pinned upstream")
text = text.replace(old_fields, new_fields, 1)

old_stop = '''    void stop(){
        lastEntityUpdateIndex = 0;
        fog = null;
        //I don't care whether the fog thread crashes here, it's about to die anyway
        staticEvents.clear();
        if(staticFogThread != null){
            staticFogThread.interrupt();
            staticFogThread = null;
        }

        dynamicEvents.clear();
        if(dynamicFogThread != null){
            dynamicFogThread.interrupt();
            dynamicFogThread = null;
        }
    }
'''
new_stop = '''    void stop(){
        lastEntityUpdateIndex = 0;
        fog = null;
        // Web has no fog worker threads. Clear every pending queue synchronously.
        staticEvents.clear();
        dynamicEvents.clear();
        dynamicEventQueue.clear();
        unitEventQueue.clear();
        webDynamicCleared.clear();
    }
'''
if old_stop not in text:
    raise SystemExit("FogControl Web stop patch no longer matches pinned upstream")
text = text.replace(old_stop, new_stop, 1)

old_threads = '''        if(staticFogThread == null){
            staticFogThread = new StaticFogThread();
            staticFogThread.setPriority(Thread.NORM_PRIORITY - 1);
            staticFogThread.setDaemon(true);
            staticFogThread.start();
        }

        if(dynamicFogThread == null){
            dynamicFogThread = new DynamicFogThread();
            dynamicFogThread.setPriority(Thread.NORM_PRIORITY - 1);
            dynamicFogThread.setDaemon(true);
            dynamicFogThread.start();
        }

'''
if old_threads not in text:
    raise SystemExit("FogControl Web worker startup patch no longer matches pinned upstream")
text = text.replace(old_threads, '''        // Web: the stock static/dynamic fog algorithms are processed incrementally
        // on this browser frame instead of daemon worker threads.

''', 1)

old_flush = '''        if(dynamicEventQueue.size > 0){
            //flush unit events over when something happens
            synchronized(dynamicEvents){
                dynamicEvents.clear();
                dynamicEvents.addAll(dynamicEventQueue);
            }
            dynamicEventQueue.clear();

            //force update so visibility doesn't have a pop-in
            if(justLoaded){
                updateDynamic(new Bits(256));
                justLoaded = false;
            }

            //notify that it's time for rendering
            //TODO this WILL block until it is done rendering, which is inherently problematic.
            synchronized(notifyDynamic){
                notifyDynamic.notify();
            }
        }

        //wake up, it's time to draw some circles
        if(state.rules.staticFog && staticEvents.size > 0 && staticFogThread != null){
            synchronized(notifyStatic){
                notifyStatic.notify();
            }
        }
'''
new_flush = '''        if(dynamicEventQueue.size > 0){
            // Flush the same stock event set into the same double-buffered visibility
            // algorithm, but execute it now on the browser event loop.
            synchronized(dynamicEvents){
                dynamicEvents.clear();
                dynamicEvents.addAll(dynamicEventQueue);
            }
            dynamicEventQueue.clear();
            updateDynamic(webDynamicCleared);
            justLoaded = false;
        }

        // Static exploration fog uses the stock circle rasterizer synchronously.
        if(state.rules.staticFog && staticEvents.size > 0){
            updateStatic();
        }
'''
if old_flush not in text:
    raise SystemExit("FogControl Web queue flush patch no longer matches pinned upstream")
text = text.replace(old_flush, new_flush, 1)

# Worker classes contain only wait/notify loops around the existing synchronous
# algorithms. Remove the wrappers while leaving updateStatic/updateDynamic unchanged.
static_start = text.find("    class StaticFogThread extends Thread{")
static_end = text.find("    void updateStatic(){", static_start)
if static_start < 0 or static_end < 0:
    raise SystemExit("FogControl StaticFogThread block no longer matches pinned upstream")
text = text[:static_start] + text[static_end:]

dynamic_start = text.find("    class DynamicFogThread extends Thread{")
dynamic_end = text.find("    void updateDynamic(Bits cleared){", dynamic_start)
if dynamic_start < 0 or dynamic_end < 0:
    raise SystemExit("FogControl DynamicFogThread block no longer matches pinned upstream")
text = text[:dynamic_start] + text[dynamic_end:]

# Guard against accidentally retaining browser-incompatible thread machinery.
for forbidden in ("StaticFogThread", "DynamicFogThread", "notifyStatic", "notifyDynamic", ".start()", ".interrupt()"):
    if forbidden in text:
        raise SystemExit(f"FogControl Web patch retained worker-thread marker: {forbidden}")

PATH.write_text(text, encoding="utf-8")
print("Applied Web single-thread FogControl scheduler with stock fog algorithms/save format")
