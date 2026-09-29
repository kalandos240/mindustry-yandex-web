#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "util" / "TaskQueue.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Arc TaskQueue source: {PATH}")

text = PATH.read_text(encoding="utf-8")

anchor = '''    public void run(){
        synchronized(runnables){
            executedRunnables.clear();
            executedRunnables.addAll(runnables);
            runnables.clear();
        }

        for(Runnable runnable : executedRunnables){
            runnable.run();
        }
    }

'''
addition = anchor + '''    /**
     * Web-only bounded FIFO drain used by main-thread pathfinding schedulers.
     * Existing desktop callers retain run() semantics; this overload reuses the
     * same execution buffer and leaves excess tasks queued for a later frame.
     * @return number of tasks executed.
     */
    public int run(int maxTasks){
        if(maxTasks <= 0) return 0;

        int count;
        synchronized(runnables){
            count = Math.min(maxTasks, runnables.size);
            if(count == 0) return 0;

            executedRunnables.clear();
            for(int i = 0; i < count; i++){
                executedRunnables.add(runnables.get(i));
            }
            runnables.removeRange(0, count - 1);
        }

        for(Runnable runnable : executedRunnables){
            runnable.run();
        }
        return count;
    }

'''

if anchor not in text:
    raise SystemExit("TaskQueue bounded Web patch no longer matches pinned Arc")
if "public int run(int maxTasks)" in text:
    raise SystemExit("TaskQueue bounded Web overload already present before patch")

text = text.replace(anchor, addition, 1)

for marker in (
    "public int run(int maxTasks)",
    "count = Math.min(maxTasks, runnables.size);",
    "runnables.removeRange(0, count - 1);",
    "return count;",
):
    if marker not in text:
        raise SystemExit(f"TaskQueue bounded Web patch lost marker: {marker}")

PATH.write_text(text, encoding="utf-8")
print("Applied Web bounded FIFO TaskQueue drain")
