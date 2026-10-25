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
        """    // Keep original spatial ambient sources on the simulation thread.\n    // TeaVM does not support Mindustry's desktop AudioThread worker.\n    protected final Seq<AmbientSource> webAmbientSources = new Seq<>(false, 16, AmbientSource.class);\n\n""",
        "ambient thread fields",
    ),
    (
        """            launchingAmbientThread = false;\n            if(ambientThread != null){\n                ambientThread.running = false;\n                ambientThread.interrupt();\n                ambientThread = null;\n            }\n""",
        """            // Clear old sector sources before the next sector is loaded.\n            webAmbientSources.clear();\n""",
        "reset ambient thread shutdown",
    ),
    (
        """    public void addAmbientSource(AmbientSource source){\n        if(headless) return;\n\n        if(launchingAmbientThread && ambientThread != null){\n            ambientThread.sources.add(source); //directly add to buffer while thread is launching; this prevents a million add calls to the queue during map load\n        }else if(ambientThread != null){\n            ambientThread.inputSources.add(source);\n        }else{\n            launchingAmbientThread = true;\n            ambientThread = new AudioThread();\n            ambientThread.setDaemon(true);\n\n            //start thread with all the sources that were added during launch\n            Core.app.post(() -> {\n                if(ambientThread != null){\n                    if(!ambientThread.isAlive()) ambientThread.start();\n                    launchingAmbientThread = false;\n                }\n            });\n        }\n    }\n""",
        """    public void addAmbientSource(AmbientSource source){\n        // Sources are registered by vanilla blocks/units during world loading.\n        // Process them on the game thread; never spawn Java workers under TeaVM.\n        if(!headless && source != null && !webAmbientSources.contains(source, true)){\n            webAmbientSources.add(source);\n        }\n    }\n""",
        "addAmbientSource",
    ),
    (
        """        //grab data from ambient thread\n        if(ambientThread != null){\n            synchronized(ambientThread.outputData){\n                localData.set(ambientThread.outputData);\n            }\n\n            for(var data : localData){\n                var target = sounds.get(data.sound, SoundData::new);\n\n                target.pitch = data.pitch;\n                target.volume = data.volume;\n                target.total = data.total;\n                target.totalVolume = data.totalVolume;\n                target.sumX = data.sumX;\n                target.sumY = data.sumY;\n            }\n        }\n""",
        """        // Web: spatial AmbientSource contributions are already incorporated\n        // into sounds before the normal mixer pass above. No JVM audio worker.\n""",
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

# With native Settings open on a paused browser game, stock SoundControl
# repeatedly invokes silence()->play(null), fading the live HTMLAudio track
# to zero. Unlike a menu transition, the user expects to preview their Music
# Volume slider without stopping the campaign soundtrack. Keep the normal
# paused-world simulation frozen and manage only the current music gain.
# Restore vanilla spatial ambience without JVM worker threads. A source\n# contributes to the SAME SoundData accumulator as SoundControl.loop(), so\n# ambientvol, falloff, pan and pitch go through the normal protected voice mix.\nambient_loop_anchor = """        float avol = Core.settings.getInt("ambientvol", 100) / 100f;\n\n        sounds.each((sound, data) -> {\n"""\nambient_loop_replacement = """        float avol = Core.settings.getInt("ambientvol", 100) / 100f;\n\n        // Frame-local spatial mixing replaces the desktop AudioThread. Invalid\n        // sources are pruned, and all contributions share the same avol gain.\n        for(int i = 0; i < webAmbientSources.size; ){\n            AmbientSource source = webAmbientSources.get(i);\n            if(!source.isValid()){\n                webAmbientSources.remove(i);\n                continue;\n            }\n            if(source.shouldAmbientSound()){\n                float volume = source.getAmbientVolume();\n                if(volume > 0.00001f){\n                    Sound ambient = source.getAmbientSound();\n                    if(ambient != null){\n                        SoundData data = sounds.get(ambient, SoundData::new);\n                        loop(data, ambient, source, volume, 1f);\n                    }\n                }\n            }\n            i++;\n        }\n\n        sounds.each((sound, data) -> {\n"""\nif text.count(ambient_loop_anchor) != 1:\n    raise SystemExit("Pinned SoundControl ambient mixer anchor changed; review per-frame spatial mixing")\ntext = text.replace(ambient_loop_anchor, ambient_loop_replacement, 1)\n\npaused_music_anchor = """    public void update(){
        boolean paused = state.isGame() && Core.scene.hasDialog();
"""
paused_music_replacement = """    public void update(){
        // Web: the current soundtrack is retained while the native Settings
        // dialog overlays a paused sector. Do not run the ordinary in-game
        // silence()/fade path during slider preview.
        if(state.isPaused() && ui != null && ui.settings != null && ui.settings.isShown()){
            // Preserve the ordinary paused SFX bus policy even though we
            // bypass the soundtrack fade and the rest of the update loop.
            Core.audio.setPaused(Core.audio.soundBus.id, true);
            if(current != null && !current.isPlaying()){
                current = null;
                fade = 0f;
            }
            if(current == null && !state.rules.disableMusic
                && Core.settings.getBool("alwaysmusic", false)
                && Core.settings.getInt("musicvol", 100) > 0){
                playRandom();
            }
            if(current != null && current.isPlaying()){
                current.setVolume(volumeMultiplier());
            }
            return;
        }
        boolean paused = state.isGame() && Core.scene.hasDialog();
"""
if text.count(paused_music_anchor) != 1:
    raise SystemExit("Pinned SoundControl.update() anchor changed; review paused music preview")
text = text.replace(paused_music_anchor, paused_music_replacement, 1)

reload_anchor = """        Events.fire(new MusicRegisterEvent());
    }

    public void stop(){
"""
reload_replacement = """        Events.fire(new MusicRegisterEvent());
    }

    /** Web launcher substitute for the desktop ClientLoadEvent audio registration. */
    public void reloadWeb(){
        reload();
    }

    public void stop(){
"""
if text.count(reload_anchor) != 1:
    raise SystemExit("SoundControl Web reload hook no longer matches pinned upstream")
text = text.replace(reload_anchor, reload_replacement, 1)

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
