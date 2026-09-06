#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-mindustry-sound-web.py <SoundControl.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

replacements = [
    (
        "import java.util.concurrent.*;\n\n",
        "",
        "concurrent import",
    ),
    (
        """    protected @Nullable AudioThread ambientThread;\n    protected boolean launchingAmbientThread;\n    protected Seq<SoundData> localData = new Seq<>();\n\n""",
        """    // Web: native audio is disabled, so the desktop ambient-audio worker is omitted.\n\n""",
        "ambient thread fields",
    ),
    (
        """            launchingAmbientThread = false;\n            if(ambientThread != null){\n                ambientThread.running = false;\n                ambientThread.interrupt();\n                ambientThread = null;\n            }\n""",
        """            // Web: no ambient audio worker exists to stop.\n""",
        "reset ambient thread shutdown",
    ),
    (
        """    public void addAmbientSource(AmbientSource source){\n        if(headless) return;\n\n        if(launchingAmbientThread && ambientThread != null){\n            ambientThread.sources.add(source); //directly add to buffer while thread is launching; this prevents a million add calls to the queue during map load\n        }else if(ambientThread != null){\n            ambientThread.inputSources.add(source);\n        }else{\n            launchingAmbientThread = true;\n            ambientThread = new AudioThread();\n            ambientThread.setDaemon(true);\n\n            //start thread with all the sources that were added during launch\n            Core.app.post(() -> {\n                if(ambientThread != null){\n                    if(!ambientThread.isAlive()) ambientThread.start();\n                    launchingAmbientThread = false;\n                }\n            });\n        }\n    }\n""",
        """    public void addAmbientSource(AmbientSource source){\n        // Web: Core.audio is intentionally Audio(false). Ambient sources have no audible\n        // output, and the desktop worker primitives are unavailable in TeaVM JavaScript.\n        // Keep the stock API callable while doing no work in the disabled-audio backend.\n    }\n""",
        "addAmbientSource",
    ),
    (
        """        //grab data from ambient thread\n        if(ambientThread != null){\n            synchronized(ambientThread.outputData){\n                localData.set(ambientThread.outputData);\n            }\n\n            for(var data : localData){\n                var target = sounds.get(data.sound, SoundData::new);\n\n                target.pitch = data.pitch;\n                target.volume = data.volume;\n                target.total = data.total;\n                target.totalVolume = data.totalVolume;\n                target.sumX = data.sumX;\n                target.sumY = data.sumY;\n            }\n        }\n""",
        """        // Web: no ambient worker buffer to merge; ordinary loop aggregation above remains intact.\n""",
        "ambient output merge",
    ),
]

for old, new, label in replacements:
    if old not in text:
        raise SystemExit(f"SoundControl Web patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

start = text.find("    static class AudioThread extends Thread{")
if start < 0:
    raise SystemExit("SoundControl Web patch no longer matches pinned AudioThread class")
# AudioThread is the final nested class immediately before SoundControl's closing brace.
end_marker = "\n    }\n}"
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("SoundControl Web patch could not locate AudioThread end")
text = text[:start] + "    // Web: desktop ambient worker removed; browser audio is intentionally disabled.\n" + text[end + len("\n    }"):]

for forbidden in ("LinkedBlockingQueue", "Thread.sleep", "new AudioThread", ".interrupt()"):
    if forbidden in text:
        raise SystemExit(f"SoundControl Web patch left forbidden threading marker: {forbidden}")

path.write_text(text, encoding="utf-8")
print("Applied TeaVM-safe no-thread Mindustry ambient audio path")
