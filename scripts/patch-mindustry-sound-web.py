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
        """    // Web: ambient-source spatial mixing must run without a JVM worker thread.\n\n""",
        "ambient thread fields",
    ),
    (
        """            launchingAmbientThread = false;\n            if(ambientThread != null){\n                ambientThread.running = false;\n                ambientThread.interrupt();\n                ambientThread = null;\n            }\n""",
        """            // Web: no ambient JVM worker exists to stop.\n""",
        "reset ambient thread shutdown",
    ),
    (
        """    public void addAmbientSource(AmbientSource source){\n        if(headless) return;\n\n        if(launchingAmbientThread && ambientThread != null){\n            ambientThread.sources.add(source); //directly add to buffer while thread is launching; this prevents a million add calls to the queue during map load\n        }else if(ambientThread != null){\n            ambientThread.inputSources.add(source);\n        }else{\n            launchingAmbientThread = true;\n            ambientThread = new AudioThread();\n            ambientThread.setDaemon(true);\n\n            //start thread with all the sources that were added during launch\n            Core.app.post(() -> {\n                if(ambientThread != null){\n                    if(!ambientThread.isAlive()) ambientThread.start();\n                    launchingAmbientThread = false;\n                }\n            });\n        }\n    }\n""",
        """    public void addAmbientSource(AmbientSource source){\n        // Web: ordinary Sound/Music playback is provided by BrowserAudio, but Arc's\n        // desktop ambient-source mixer is worker-thread based. Keep this registration\n        // API safe for world content until ambient spatial mixing is moved onto frames.\n    }\n""",
        "addAmbientSource",
    ),
    (
        """        //grab data from ambient thread\n        if(ambientThread != null){\n            synchronized(ambientThread.outputData){\n                localData.set(ambientThread.outputData);\n            }\n\n            for(var data : localData){\n                var target = sounds.get(data.sound, SoundData::new);\n\n                target.pitch = data.pitch;\n                target.volume = data.volume;\n                target.total = data.total;\n                target.totalVolume = data.totalVolume;\n                target.sumX = data.sumX;\n                target.sumY = data.sumY;\n            }\n        }\n""",
        """        // Web: no ambient worker buffer to merge; ordinary loop aggregation above remains intact.\n""",
        "ambient output merge",
    ),
    (
        """        }else if(state.isMenu()){\n            silenced = false;\n            if(ui.planet.isShown()){\n                play(ui.planet.state.planet.launchMusic);\n            }else if(ui.editor.isShown()){\n                play(Musics.editor);\n            }else{\n                play(Musics.menu);\n            }\n""",
        """        }else if(state.isMenu()){\n            silenced = false;\n            // Web bootstrap proves the production Control.update/SoundControl.update\n            // loop one milestone before full UI.init(). Planet/editor dialogs are null\n            // during that transition; once UI.init() creates them this is stock behavior.\n            if(ui.planet != null && ui.planet.isShown()){\n                play(ui.planet.state.planet.launchMusic);\n            }else if(ui.editor != null && ui.editor.isShown()){\n                play(Musics.editor);\n            }else{\n                play(Musics.menu);\n            }\n""",
        "menu music before full UI init",
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
text = text[:start] + "    // Web: desktop ambient worker removed; browser playback remains owned by BrowserAudio.\n" + text[end + len("\n    }"):]

for forbidden in ("LinkedBlockingQueue", "Thread.sleep", "new AudioThread", ".interrupt()"):
    if forbidden in text:
        raise SystemExit(f"SoundControl Web patch left forbidden threading marker: {forbidden}")

for unguarded in (
    "            if(ui.planet.isShown()){",
    "            }else if(ui.editor.isShown()){",
):
    if unguarded in text:
        raise SystemExit("SoundControl Web patch left menu dialog dereference before full UI init")

path.write_text(text, encoding="utf-8")
print("Applied TeaVM-safe no-thread Mindustry audio path with pre-UI-init menu safety")
