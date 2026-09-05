package mindustry.web;

import arc.*;
import arc.audio.*;
import arc.files.*;
import arc.util.Nullable;
import org.teavm.jso.JSBody;

/**
 * Browser-native Arc Audio implementation.
 *
 * SFX are decoded lazily into Web Audio buffers. Music remains streamed by
 * HTMLAudioElement. Every URL is a relative path inside the staged Yandex package.
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

        // Decode a tiny real Mindustry OGG without playing it. CI waits for this marker,
        // proving that the packaged asset and the browser codec path are both functional.
        verifyPackagedSound(smokeSound);
    }

    private static float settingVolume(String key, int fallback){
        if(Core.settings == null) return fallback / 100f;
        return Math.max(0f, Math.min(1f, Core.settings.getInt(key, fallback) / 100f));
    }

    @Override
    public boolean initialized(){
        return initialized;
    }

    @Override
    public Sound newSound(Fi file){
        return initialized ? new BrowserSound(file) : new Sound();
    }

    @Override
    public Music newMusic(Fi file){
        return initialized ? new BrowserMusic(file) : new Music();
    }

    @Override
    public boolean isPlaying(int soundId){
        return initialized && voicePlaying(soundId);
    }

    @Override
    public void protect(int voice, boolean protect){
        // Browser voices are explicitly owned and are never mixer-stolen.
    }

    @Override
    public int play(AudioSource source, float volume, float pitch, float pan, boolean loop){
        if(!initialized) return -1;
        if(source instanceof BrowserSound sound){
            return sound.playBrowser(volume, pitch, pan, loop, false);
        }
        return -1;
    }

    @Override
    public void stop(AudioSource source){
        if(source instanceof BrowserSound sound){
            sound.stop();
        }else if(source instanceof BrowserMusic music){
            music.stop();
        }
    }

    @Override
    public void stop(int soundId){
        if(initialized) stopVoice(soundId);
    }

    @Override
    public void setPaused(int soundId, boolean paused){
        if(initialized) pauseVoice(soundId, paused);
    }

    @Override
    public void setLooping(int soundId, boolean looping){
        if(initialized) loopVoice(soundId, looping);
    }

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
    public void fadeFilterParam(int voice, int filter, int attribute, float value, float timeSec){
        // SoLoud DSP filters do not have a direct Web backend equivalent yet.
    }

    @Override
    public void setFilterParam(int voice, int filter, int attribute, float value){
        // SoLoud DSP filters do not have a direct Web backend equivalent yet.
    }

    @Override
    public void setFilter(int index, @Nullable AudioFilter filter){
        // Global SoLoud filters are intentionally omitted from the browser graph.
    }

    @Override
    public int countPlaying(AudioSource source){
        if(!initialized) return 0;
        return source instanceof BrowserSound sound ? sound.countPlaying() : 0;
    }

    @Override
    public int countTotalPlaying(){
        return initialized ? activeVoiceCountBrowser() : 0;
    }

    @Override
    public void dispose(){
        if(!initialized) return;
        disposeBackend();
        initialized = false;
    }

    /** Called from the Yandex pause/resume lifecycle too, so ads cannot leave audio running. */
    public void setPortalPaused(boolean paused){
        if(initialized) platformPause(paused);
    }

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

    @JSBody(script = """
        const root = document.documentElement;
        const state = globalThis.__mindustryAudio || (globalThis.__mindustryAudio = {
            ctx: null,
            buffers: new Map(),
            durations: new Map(),
            voices: new Map(),
            music: new Map(),
            nextVoice: 1,
            platformPaused: false,
            unlocked: false,
            installed: false
        });
        if(state.installed) return !!state.ctx;
        state.installed = true;

        const AudioContextCtor = globalThis.AudioContext || globalThis.webkitAudioContext;
        if(!AudioContextCtor){
            root.setAttribute('data-mindustry-audio', 'unsupported');
            return false;
        }
        try{
            state.ctx = new AudioContextCtor({latencyHint: 'interactive'});
        }catch(error){
            root.setAttribute('data-mindustry-audio', 'error');
            root.setAttribute('data-mindustry-audio-error', String(error).slice(0, 300));
            return false;
        }

        state.decode = (url) => {
            let pending = state.buffers.get(url);
            if(pending) return pending;
            pending = fetch(url).then(response => {
                if(!response.ok) throw new Error('Audio asset fetch failed (' + response.status + '): ' + url);
                return response.arrayBuffer();
            }).then(bytes => state.ctx.decodeAudioData(bytes)).then(buffer => {
                state.durations.set(url, buffer.duration || 0);
                return buffer;
            });
            state.buffers.set(url, pending);
            pending.catch(() => state.buffers.delete(url));
            return pending;
        };

        state.startVoice = (voice, buffer) => {
            if(!state.voices.has(voice.id) || voice.started || state.platformPaused) return;
            const source = state.ctx.createBufferSource();
            const gain = state.ctx.createGain();
            const panner = state.ctx.createStereoPanner ? state.ctx.createStereoPanner() : null;
            source.buffer = buffer;
            source.loop = !!voice.loop;
            source.playbackRate.value = Math.max(0.01, voice.pitch);
            gain.gain.value = voice.paused ? 0 : voice.volume;
            if(panner) panner.pan.value = Math.max(-1, Math.min(1, voice.pan));
            if(panner){
                source.connect(gain); gain.connect(panner); panner.connect(state.ctx.destination);
            }else{
                source.connect(gain); gain.connect(state.ctx.destination);
            }
            voice.source = source;
            voice.gain = gain;
            voice.panner = panner;
            voice.started = true;
            source.onended = () => {
                const current = state.voices.get(voice.id);
                if(current === voice && !voice.loop) state.voices.delete(voice.id);
            };
            source.start(0);
        };

        const unlock = () => {
            state.ctx.resume().then(() => {
                state.unlocked = true;
                root.setAttribute('data-mindustry-audio-unlocked', 'true');
                for(const entry of state.music.values()){
                    if(entry.pendingPlay && !state.platformPaused){
                        entry.pendingPlay = false;
                        entry.element.play().catch(() => { entry.pendingPlay = true; });
                    }
                }
            }).catch(() => {});
        };
        for(const type of ['pointerdown', 'touchstart', 'keydown']){
            globalThis.addEventListener(type, unlock, {passive:true, capture:true});
        }
        root.setAttribute('data-mindustry-audio', 'installed');
        return true;
        """)
    private static native boolean installBackend();

    @JSBody(params = {"url"}, script = """
        const root = document.documentElement;
        const state = globalThis.__mindustryAudio;
        if(!state || !state.ctx){ root.setAttribute('data-mindustry-audio', 'unsupported'); return; }
        root.setAttribute('data-mindustry-audio', 'decoding');
        state.decode(url).then(buffer => {
            if(!buffer || !(buffer.duration > 0)) throw new Error('Decoded audio has no duration: ' + url);
            root.setAttribute('data-mindustry-audio', 'ready');
            root.setAttribute('data-mindustry-audio-smoke-ms', String(Math.round(buffer.duration * 1000)));
        }).catch(error => {
            root.setAttribute('data-mindustry-audio', 'error');
            root.setAttribute('data-mindustry-audio-error', String(error).replace(/\\s+/g, ' ').slice(0, 500));
        });
        """)
    private static native void verifyPackagedSound(String url);

    @JSBody(params = {"url", "volume", "pitch", "pan", "loop"}, script = """
        const state = globalThis.__mindustryAudio;
        if(!state || !state.ctx) return -1;
        const id = state.nextVoice++;
        const voice = {id, url, volume, pitch, pan, loop, paused:false, started:false, source:null, gain:null, panner:null};
        state.voices.set(id, voice);
        state.decode(url).then(buffer => {
            if(state.voices.get(id) === voice) state.startVoice(voice, buffer);
        }).catch(error => {
            state.voices.delete(id);
            console.warn('Mindustry sound decode failed:', url, error);
        });
        return id;
        """)
    private static native int playSoundJs(String url, float volume, float pitch, float pan, boolean loop);

    @JSBody(params = {"url"}, script = """
        const state=globalThis.__mindustryAudio; if(!state)return;
        for(const [id,voice] of Array.from(state.voices.entries())){
            if(voice.url!==url)continue;
            try{if(voice.source)voice.source.stop();}catch(_){}
            state.voices.delete(id);
        }
        """)
    private static native void stopSoundJs(String url);

    @JSBody(params = {"url"}, script = """
        const state=globalThis.__mindustryAudio;if(!state)return 0;
        let count=0;for(const voice of state.voices.values())if(voice.url===url)count++;return count;
        """)
    private static native int countSoundJs(String url);

    @JSBody(params = {"url"}, script = "const s=globalThis.__mindustryAudio;return s&&s.durations.has(url)?s.durations.get(url):0;")
    private static native float soundLengthJs(String url);

    @JSBody(params = {"id"}, script = "const s=globalThis.__mindustryAudio;return !!(s&&s.voices.has(id));")
    private static native boolean voicePlaying(int id);

    @JSBody(params = {"id"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const v=s.voices.get(id);if(!v)return;try{if(v.source)v.source.stop();}catch(_){}s.voices.delete(id);")
    private static native void stopVoice(int id);

    @JSBody(params = {"id", "paused"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const v=s.voices.get(id);if(!v)return;v.paused=paused;if(v.gain)v.gain.gain.value=paused?0:v.volume;")
    private static native void pauseVoice(int id, boolean paused);

    @JSBody(params = {"id", "looping"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const v=s.voices.get(id);if(!v)return;v.loop=looping;if(v.source)v.source.loop=looping;")
    private static native void loopVoice(int id, boolean looping);

    @JSBody(params = {"id", "pitch"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const v=s.voices.get(id);if(!v)return;v.pitch=pitch;if(v.source)v.source.playbackRate.value=pitch;")
    private static native void pitchVoice(int id, float pitch);

    @JSBody(params = {"id", "volume"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const v=s.voices.get(id);if(!v)return;v.volume=volume;if(v.gain)v.gain.gain.value=v.paused?0:volume;")
    private static native void volumeVoice(int id, float volume);

    @JSBody(params = {"id", "pan"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const v=s.voices.get(id);if(!v)return;v.pan=pan;if(v.panner)v.panner.pan.value=pan;")
    private static native void panVoice(int id, float pan);

    @JSBody(script = "const s=globalThis.__mindustryAudio;return s?s.voices.size:0;")
    private static native int activeVoiceCountBrowser();

    @JSBody(params = {"paused"}, script = """
        const s=globalThis.__mindustryAudio;if(!s||!s.ctx)return;
        s.platformPaused=paused;
        if(paused){
            s.ctx.suspend().catch(()=>{});
            for(const entry of s.music.values()){
                entry.resumeAfterPlatform=!entry.element.paused||entry.pendingPlay;
                entry.element.pause();
            }
        }else{
            if(s.unlocked)s.ctx.resume().catch(()=>{});
            for(const entry of s.music.values()){
                if(entry.resumeAfterPlatform){
                    entry.resumeAfterPlatform=false;
                    if(s.unlocked)entry.element.play().catch(()=>{entry.pendingPlay=true;});
                    else entry.pendingPlay=true;
                }
            }
            for(const voice of s.voices.values()){
                if(!voice.started){
                    s.decode(voice.url).then(buffer=>{if(s.voices.get(voice.id)===voice)s.startVoice(voice,buffer);}).catch(()=>{});
                }
            }
        }
        """)
    private static native void platformPause(boolean paused);

    @JSBody(script = """
        const s=globalThis.__mindustryAudio;if(!s)return;
        for(const v of s.voices.values()){try{if(v.source)v.source.stop();}catch(_){}}
        s.voices.clear();
        for(const entry of s.music.values()){entry.element.pause();entry.element.removeAttribute('src');entry.element.load();}
        s.music.clear();
        if(s.ctx)s.ctx.close().catch(()=>{});
        document.documentElement.setAttribute('data-mindustry-audio','disposed');
        """)
    private static native void disposeBackend();

    @JSBody(params = {"id", "url"}, script = """
        const s=globalThis.__mindustryAudio;if(!s||s.music.has(id))return;
        const element=new Audio();element.preload='none';element.src=url;element.playsInline=true;
        s.music.set(id,{element,pendingPlay:false,resumeAfterPlatform:false});
        """)
    private static native void musicPrepareJs(int id, String url);

    @JSBody(params = {"id", "url", "volume", "pitch", "pan", "loop"}, script = """
        const s=globalThis.__mindustryAudio;if(!s)return;
        if(!s.music.has(id)){const element=new Audio();element.preload='none';element.src=url;element.playsInline=true;s.music.set(id,{element,pendingPlay:false,resumeAfterPlatform:false});}
        const entry=s.music.get(id),e=entry.element;e.volume=volume;e.playbackRate=pitch;e.loop=loop;
        if(s.platformPaused||!s.unlocked){entry.pendingPlay=true;return;}
        e.play().then(()=>{entry.pendingPlay=false;}).catch(()=>{entry.pendingPlay=true;});
        """)
    private static native void musicPlayJs(int id, String url, float volume, float pitch, float pan, boolean loop);

    @JSBody(params = {"id", "paused"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const x=s.music.get(id);if(!x)return;if(paused){x.element.pause();x.pendingPlay=false;}else{x.pendingPlay=true;if(s.unlocked&&!s.platformPaused)x.element.play().then(()=>x.pendingPlay=false).catch(()=>{});}")
    private static native void musicPauseJs(int id, boolean paused);

    @JSBody(params = {"id"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const x=s.music.get(id);if(!x)return;x.element.pause();try{x.element.currentTime=0;}catch(_){}x.pendingPlay=false;x.resumeAfterPlatform=false;")
    private static native void musicStopJs(int id);

    @JSBody(params = {"id"}, script = "const s=globalThis.__mindustryAudio;if(!s)return false;const x=s.music.get(id);return !!(x&&!x.element.paused&&!x.element.ended);")
    private static native boolean musicPlayingJs(int id);

    @JSBody(params = {"id", "loop"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const x=s.music.get(id);if(x)x.element.loop=loop;")
    private static native void musicLoopJs(int id, boolean loop);

    @JSBody(params = {"id", "volume"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const x=s.music.get(id);if(x)x.element.volume=volume;")
    private static native void musicVolumeJs(int id, float volume);

    @JSBody(params = {"id", "position"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const x=s.music.get(id);if(x){try{x.element.currentTime=position;}catch(_){}}")
    private static native void musicPositionJs(int id, float position);

    @JSBody(params = {"id"}, script = "const s=globalThis.__mindustryAudio;if(!s)return 0;const x=s.music.get(id);return x&&Number.isFinite(x.element.currentTime)?x.element.currentTime:0;")
    private static native float musicPositionJs(int id);

    @JSBody(params = {"id"}, script = "const s=globalThis.__mindustryAudio;if(!s)return 0;const x=s.music.get(id);return x&&Number.isFinite(x.element.duration)?x.element.duration:0;")
    private static native float musicLengthJs(int id);

    @JSBody(params = {"id"}, script = "const s=globalThis.__mindustryAudio;if(!s)return;const x=s.music.get(id);if(!x)return;x.element.pause();x.element.removeAttribute('src');x.element.load();s.music.delete(id);")
    private static native void musicDisposeJs(int id);
}
