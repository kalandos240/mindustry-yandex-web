package mindustry.web;

import arc.*;
import arc.audio.*;
import arc.files.*;
import arc.util.Nullable;
import org.teavm.jso.JSBody;

/**
 * Browser-native Arc Audio implementation.
 *
 * The actual Web Audio/HTMLAudio runtime lives in the local browser-audio.js file.
 * Keeping @JSBody methods as tiny calls prevents TeaVM's JavaScript parser from
 * having to parse the modern browser runtime while keeping every asset same-origin.
 */
public final class BrowserAudio extends Audio{
    private static final String smokeSound = "assets/sounds/ui/uiButton.ogg";

    public BrowserAudio(){
        super(false);
        initialized = installBackend();
        if(!initialized) return;

        sfxVolume = settingVolume("sfxvol", 100);
        Core.app.addListener(new ApplicationListener(){
            @Override
            public void update(){
                sfxVolume = settingVolume("sfxvol", 100);
            }

            @Override
            public void pause(){
                setPortalPaused(true);
            }

            @Override
            public void resume(){
                setPortalPaused(false);
            }
        });

        verifyPackagedSound(smokeSound);
    }

    private static float settingVolume(String key, int fallback){
        if(Core.settings == null) return fallback / 100f;
        return Math.max(0f, Math.min(1f, Core.settings.getInt(key, fallback) / 100f));
    }

    @Override
    public boolean initialized(){ return initialized; }

    @Override
    public Sound newSound(Fi file){ return initialized ? new BrowserSound(file) : new Sound(); }

    @Override
    public Music newMusic(Fi file){ return initialized ? new BrowserMusic(file) : new Music(); }

    @Override
    public boolean isPlaying(int soundId){ return initialized && voicePlaying(soundId); }

    @Override
    public void protect(int voice, boolean protect){}

    @Override
    public int play(AudioSource source, float volume, float pitch, float pan, boolean loop){
        if(!initialized) return -1;
        if(source instanceof BrowserSound){
            return ((BrowserSound)source).playBrowser(volume, pitch, pan, loop, false);
        }
        return -1;
    }

    @Override
    public void stop(AudioSource source){
        if(source instanceof BrowserSound){
            ((BrowserSound)source).stop();
        }else if(source instanceof BrowserMusic){
            ((BrowserMusic)source).stop();
        }
    }

    @Override
    public void stop(int soundId){ if(initialized) stopVoice(soundId); }

    @Override
    public void setPaused(int soundId, boolean paused){ if(initialized) pauseVoice(soundId, paused); }

    @Override
    public void setLooping(int soundId, boolean looping){ if(initialized) loopVoice(soundId, looping); }

    @Override
    public void setPitch(int soundId, float pitch){
        if(initialized && !Float.isInfinite(pitch) && !Float.isNaN(pitch)){
            pitchVoice(soundId, Math.max(pitch, 0.001f));
        }
    }

    @Override
    public void setVolume(int soundId, float volume){
        if(initialized && !Float.isInfinite(volume) && !Float.isNaN(volume)){
            volumeVoice(soundId, volume);
        }
    }

    @Override
    public void set(int soundId, float pan, float volume){
        if(!initialized) return;
        if(!Float.isInfinite(volume) && !Float.isNaN(volume)) volumeVoice(soundId, volume);
        if(!Float.isInfinite(pan) && !Float.isNaN(pan)) panVoice(soundId, pan);
    }

    @Override
    public void fadeFilterParam(int voice, int filter, int attribute, float value, float timeSec){}

    @Override
    public void setFilterParam(int voice, int filter, int attribute, float value){}

    @Override
    public void setFilter(int index, @Nullable AudioFilter filter){}

    @Override
    public int countPlaying(AudioSource source){
        if(!initialized) return 0;
        return source instanceof BrowserSound ? ((BrowserSound)source).countPlaying() : 0;
    }

    @Override
    public int countTotalPlaying(){ return initialized ? activeVoiceCountBrowser() : 0; }

    @Override
    public void dispose(){
        if(!initialized) return;
        disposeBackend();
        initialized = false;
    }

    public void setPortalPaused(boolean paused){ if(initialized) platformPause(paused); }

    static String assetUrl(Fi file){
        String path = file == null ? "" : file.path().replace('\\', '/');
        while(path.startsWith("/")) path = path.substring(1);
        return path.startsWith("assets/") ? path : "assets/" + path;
    }

    static int playSound(String url, float volume, float pitch, float pan, boolean loop){
        return playSoundJs(url, clamp01(volume), clampPitch(pitch), clampPan(pan), loop);
    }

    static void stopSound(String url){ stopSoundJs(url); }
    static int countSound(String url){ return countSoundJs(url); }
    static float soundLength(String url){ return soundLengthJs(url); }

    static void musicPrepare(int id, String url){ musicPrepareJs(id, url); }
    static void musicPlay(int id, String url, float volume, float pitch, float pan, boolean loop){
        musicPlayJs(id, url, clamp01(volume), clampPitch(pitch), clampPan(pan), loop);
    }
    static void musicPause(int id, boolean paused){ musicPauseJs(id, paused); }
    static void musicStop(int id){ musicStopJs(id); }
    static boolean musicPlaying(int id){ return musicPlayingJs(id); }
    static void musicLoop(int id, boolean loop){ musicLoopJs(id, loop); }
    static void musicVolume(int id, float volume){ musicVolumeJs(id, clamp01(volume)); }
    static void musicPosition(int id, float position){ musicPositionJs(id, Math.max(0f, position)); }
    static float musicPosition(int id){ return musicPositionJs(id); }
    static float musicLength(int id){ return musicLengthJs(id); }
    static void musicDispose(int id){ musicDisposeJs(id); }

    private static float clamp01(float value){
        if(Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static float clampPitch(float value){
        if(Float.isNaN(value) || Float.isInfinite(value)) return 1f;
        return Math.max(0.01f, Math.min(4f, value));
    }

    private static float clampPan(float value){
        if(Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(-1f, Math.min(1f, value));
    }

    @JSBody(script = "return window.__mindustryAudioApi.install();")
    private static native boolean installBackend();

    @JSBody(params = {"url"}, script = "window.__mindustryAudioApi.verify(url);")
    private static native void verifyPackagedSound(String url);

    @JSBody(params = {"url", "volume", "pitch", "pan", "loop"}, script = "return window.__mindustryAudioApi.playSound(url, volume, pitch, pan, loop);")
    private static native int playSoundJs(String url, float volume, float pitch, float pan, boolean loop);

    @JSBody(params = {"url"}, script = "window.__mindustryAudioApi.stopSound(url);")
    private static native void stopSoundJs(String url);

    @JSBody(params = {"url"}, script = "return window.__mindustryAudioApi.countSound(url);")
    private static native int countSoundJs(String url);

    @JSBody(params = {"url"}, script = "return window.__mindustryAudioApi.soundLength(url);")
    private static native float soundLengthJs(String url);

    @JSBody(params = {"id"}, script = "return window.__mindustryAudioApi.voicePlaying(id);")
    private static native boolean voicePlaying(int id);

    @JSBody(params = {"id"}, script = "window.__mindustryAudioApi.stopVoice(id);")
    private static native void stopVoice(int id);

    @JSBody(params = {"id", "paused"}, script = "window.__mindustryAudioApi.pauseVoice(id, paused);")
    private static native void pauseVoice(int id, boolean paused);

    @JSBody(params = {"id", "looping"}, script = "window.__mindustryAudioApi.loopVoice(id, looping);")
    private static native void loopVoice(int id, boolean looping);

    @JSBody(params = {"id", "pitch"}, script = "window.__mindustryAudioApi.pitchVoice(id, pitch);")
    private static native void pitchVoice(int id, float pitch);

    @JSBody(params = {"id", "volume"}, script = "window.__mindustryAudioApi.volumeVoice(id, volume);")
    private static native void volumeVoice(int id, float volume);

    @JSBody(params = {"id", "pan"}, script = "window.__mindustryAudioApi.panVoice(id, pan);")
    private static native void panVoice(int id, float pan);

    @JSBody(script = "return window.__mindustryAudioApi.activeVoiceCount();")
    private static native int activeVoiceCountBrowser();

    @JSBody(params = {"paused"}, script = "window.__mindustryAudioApi.platformPause(paused);")
    private static native void platformPause(boolean paused);

    @JSBody(script = "window.__mindustryAudioApi.dispose();")
    private static native void disposeBackend();

    @JSBody(params = {"id", "url"}, script = "window.__mindustryAudioApi.musicPrepare(id, url);")
    private static native void musicPrepareJs(int id, String url);

    @JSBody(params = {"id", "url", "volume", "pitch", "pan", "loop"}, script = "window.__mindustryAudioApi.musicPlay(id, url, volume, pitch, pan, loop);")
    private static native void musicPlayJs(int id, String url, float volume, float pitch, float pan, boolean loop);

    @JSBody(params = {"id", "paused"}, script = "window.__mindustryAudioApi.musicPause(id, paused);")
    private static native void musicPauseJs(int id, boolean paused);

    @JSBody(params = {"id"}, script = "window.__mindustryAudioApi.musicStop(id);")
    private static native void musicStopJs(int id);

    @JSBody(params = {"id"}, script = "return window.__mindustryAudioApi.musicPlaying(id);")
    private static native boolean musicPlayingJs(int id);

    @JSBody(params = {"id", "loop"}, script = "window.__mindustryAudioApi.musicLoop(id, loop);")
    private static native void musicLoopJs(int id, boolean loop);

    @JSBody(params = {"id", "volume"}, script = "window.__mindustryAudioApi.musicVolume(id, volume);")
    private static native void musicVolumeJs(int id, float volume);

    @JSBody(params = {"id", "position"}, script = "window.__mindustryAudioApi.musicPositionSet(id, position);")
    private static native void musicPositionJs(int id, float position);

    @JSBody(params = {"id"}, script = "return window.__mindustryAudioApi.musicPositionGet(id);")
    private static native float musicPositionJs(int id);

    @JSBody(params = {"id"}, script = "return window.__mindustryAudioApi.musicLength(id);")
    private static native float musicLengthJs(int id);

    @JSBody(params = {"id"}, script = "window.__mindustryAudioApi.musicDispose(id);")
    private static native void musicDisposeJs(int id);
}
