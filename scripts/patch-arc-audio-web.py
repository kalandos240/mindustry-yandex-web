#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARC = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "audio"
AUDIO = ARC / "Audio.java"
SOURCE = ARC / "AudioSource.java"
BUS = ARC / "AudioBus.java"
SOUND = ARC / "Sound.java"
MUSIC = ARC / "Music.java"

for path in (AUDIO, SOURCE, BUS, SOUND, MUSIC):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Arc audio source: {path}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(f"Arc Web audio patch expected exactly one pinned match ({label})")
    return text.replace(old, new, 1)


def replace_method(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    if text.count(start_marker) != 1 or text.count(end_marker) != 1:
        raise SystemExit(f"Arc Web audio method boundary no longer matches pinned upstream ({label})")
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    if end <= start:
        raise SystemExit(f"Arc Web audio method boundaries are invalid ({label})")
    return text[:start] + replacement.rstrip() + "\n\n" + text[end:]


# ---------------------------------------------------------------------------
# Audio: BrowserAudio subclasses Audio(false). The desktop Audio class must be
# a JNI-free fallback in TeaVM; BrowserAudio overrides all real playback paths.
# ---------------------------------------------------------------------------
audio = AUDIO.read_text(encoding="utf-8")
audio = replace_once(audio, "    boolean initialized;", "    protected boolean initialized;", "Audio initialized visibility")
audio = replace_method(
    audio,
    "    protected void initialize(){",
    "    /** Loads a sound, logging an error and returning a dummy track upon failure. */",
    '''    protected void initialize(){
        // Web: SoLoud/JNI is unavailable. BrowserAudio installs Web Audio explicitly.
        initialized = false;
    }''',
    "Audio.initialize"
)
audio = replace_once(
    audio,
    '''    @Override
    public void dispose(){
        if(!initialized) return;
        stopAll();
        deinit();
        initialized = false;
    }''',
    '''    @Override
    public void dispose(){
        initialized = false;
    }''',
    "Audio.dispose"
)
AUDIO.write_text(audio, encoding="utf-8")


# ---------------------------------------------------------------------------
# AudioSource: keep configuration state, but eliminate all direct native source
# calls. BrowserSound/BrowserMusic override the operations that need execution.
# ---------------------------------------------------------------------------
source = SOURCE.read_text(encoding="utf-8")
source = replace_once(
    source,
    '''    public void setFilter(int index, @Nullable AudioFilter filter){
        if(handle == 0) return;
        sourceFilter(handle, index, filter == null ? 0 : filter.handle);
    }''',
    '''    public void setFilter(int index, @Nullable AudioFilter filter){
        // Web fallback: BrowserAudio has no SoLoud filter graph.
    }''',
    "AudioSource.setFilter"
)
source = replace_once(
    source,
    '''    public void setPriority(float priority){
        this.priority = priority;
        if(handle == 0) return;
        sourcePriority(handle, priority);
    }''',
    '''    public void setPriority(float priority){
        this.priority = priority;
    }''',
    "AudioSource.setPriority"
)
source = replace_once(
    source,
    '''    public void setMaxConcurrent(int max){
        this.maxConcurrent = max;
        if(handle == 0) return;
        sourceMaxConcurrent(handle, max);
    }''',
    '''    public void setMaxConcurrent(int max){
        this.maxConcurrent = max;
    }''',
    "AudioSource.setMaxConcurrent"
)
source = replace_once(
    source,
    '''    public void setConcurrentGroup(int group){
        this.concurrentGroup = group;
        if(handle == 0) return;
        sourceConcurrentGroup(handle, group);
    }''',
    '''    public void setConcurrentGroup(int group){
        this.concurrentGroup = group;
    }''',
    "AudioSource.setConcurrentGroup"
)
source = replace_once(
    source,
    '''    public void setMinConcurrentInterrupt(float seconds){
        minInterruptAbsolute = seconds;
        minInterruptFraction = 0f;
        if(handle == 0) return;
        sourceMinConcurrentInterrupt(handle, seconds);
    }''',
    '''    public void setMinConcurrentInterrupt(float seconds){
        minInterruptAbsolute = seconds;
        minInterruptFraction = 0f;
    }''',
    "AudioSource.setMinConcurrentInterrupt"
)
source = replace_once(
    source,
    '''    public void setMinConcurrentInterruptFraction(float min, float fraction){
        minInterruptFraction = fraction;
        minInterruptAbsolute = min;
        if(handle == 0) return;
        sourceMinConcurrentInterrupt(handle, Math.min(min, getLength() * fraction));
    }''',
    '''    public void setMinConcurrentInterruptFraction(float min, float fraction){
        minInterruptFraction = fraction;
        minInterruptAbsolute = min;
    }''',
    "AudioSource.setMinConcurrentInterruptFraction"
)
source = replace_once(
    source,
    '''    public void setSingleInstance(boolean singleInstance){
        if(handle == 0 || !Core.audio.initialized) return;
        sourceSingleInstance(handle, singleInstance);
    }''',
    '''    public void setSingleInstance(boolean singleInstance){
        // Web fallback: concurrency is owned by BrowserAudio.
    }''',
    "AudioSource.setSingleInstance"
)
source = replace_once(
    source,
    '''    public void stop(){
        if(handle == 0) return;
        sourceStop(handle);
    }''',
    '''    public void stop(){
        // BrowserSound/BrowserMusic override this. Plain fallback sources are silent.
    }''',
    "AudioSource.stop"
)
source = replace_once(
    source,
    '''    @Override
    public void dispose(){
        if(handle != 0) sourceDestroy(handle);
        handle = 0;
    }''',
    '''    @Override
    public void dispose(){
        handle = 0;
    }''',
    "AudioSource.dispose"
)
SOURCE.write_text(source, encoding="utf-8")


# ---------------------------------------------------------------------------
# AudioBus: buses remain logical API objects on Web. No native bus allocation,
# sourcePlay or idValid calls may enter the TeaVM graph.
# ---------------------------------------------------------------------------
bus = BUS.read_text(encoding="utf-8")
bus = replace_once(
    bus,
    '''    public AudioBus(){
        if(Core.audio != null && Core.audio.initialized){
            init();
        }
    }''',
    '''    public AudioBus(){
        // Web: BrowserAudio owns the mixer graph; no SoLoud/JNI bus allocation.
    }''',
    "AudioBus constructor"
)
bus = replace_once(
    bus,
    '''    @Override
    public void setFilter(int index, @Nullable AudioFilter filter){
        if(handle == 0) return;
        sourceFilter(handle, index, filter == null ? 0 : filter.handle);
    }''',
    '''    @Override
    public void setFilter(int index, @Nullable AudioFilter filter){
        // Web: no native bus filters.
    }''',
    "AudioBus.setFilter"
)
bus = replace_once(
    bus,
    '''    AudioBus init(){
        if(handle != 0) return this;
        this.handle = busNew();
        this.id = sourcePlay(handle);
        return this;
    }''',
    '''    AudioBus init(){
        return this;
    }''',
    "AudioBus.init"
)
bus = replace_once(
    bus,
    '''    public void play(){
        if(handle == 0 || idValid(id)) return;
        id = sourcePlay(handle);
    }''',
    '''    public void play(){
        // Logical Web bus has no independently playing native source.
    }''',
    "AudioBus.play"
)
BUS.write_text(bus, encoding="utf-8")


# ---------------------------------------------------------------------------
# Sound: BrowserSound is URL-backed. Plain Sound is retained only as a silent
# compatibility object, so byte/file loading must not reach wav/stream JNI.
# ---------------------------------------------------------------------------
sound = SOUND.read_text(encoding="utf-8")
sound = replace_once(
    sound,
    '''    public static Sound createStream(Fi file){
        Sound sound = new Sound();
        try{
            sound.file = file;
            sound.stream = true;
            sound.handle = streamLoadFile(file.path());
        }catch(Throwable e){
            Log.err("Failed loading sound from " + file, e);
        }
        return sound;
    }''',
    '''    public static Sound createStream(Fi file){
        return Core.audio == null ? new Sound() : Core.audio.newSound(file);
    }''',
    "Sound.createStream"
)
sound = replace_once(
    sound,
    '''    public void load(byte[] data, boolean stream){
        this.stream = stream;
        handle = stream ? streamLoadBytes(data, data.length) : wavLoadBytes(data, data.length);

        if(Core.audio != null && Core.audio.defaultSoundMaxConcurrent > 0){
            setMaxConcurrent(Core.audio.defaultSoundMaxConcurrent);
        }
    }''',
    '''    public void load(byte[] data, boolean stream){
        this.stream = stream;
        handle = 0;
    }''',
    "Sound.load bytes"
)
sound = replace_once(
    sound,
    '''    public void load(Fi file){
        this.file = file;
        load(file.readBytes(), false);
    }''',
    '''    public void load(Fi file){
        this.file = file;
        handle = 0;
    }''',
    "Sound.load file"
)
start = '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){'
end = '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame){'
if sound.count(start) != 1 or sound.count(end) != 1:
    raise SystemExit("Arc Web audio patch could not find pinned Sound primary play boundaries")
si = sound.index(start)
ei = sound.index(end, si)
sound = sound[:si] + '''    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){
        if(Core.audio == null || !Core.audio.initialized()) return -1;
        return Core.audio.play(this, volume, pitch, pan, loop);
    }

''' + sound[ei:]
sound = replace_once(
    sound,
    '''    public float calcVolume(float x, float y){
        return calcFalloff(x, y) * Core.audio.sfxVolume;
    }''',
    '''    public float calcVolume(float x, float y){
        return calcFalloff(x, y) * (Core.audio == null ? 0f : Core.audio.sfxVolume);
    }''',
    "Sound.calcVolume"
)
sound = replace_once(
    sound,
    '''    public float getLength(){
        if(handle == 0 || !Core.audio.initialized) return 0f;
        return stream ? (float)Soloud.streamLength(handle) : (float)Soloud.wavLength(handle);
    }''',
    '''    public float getLength(){
        return 0f;
    }''',
    "Sound.getLength"
)
SOUND.write_text(sound, encoding="utf-8")


# ---------------------------------------------------------------------------
# Music: BrowserMusic owns real HTMLAudio playback. The base class becomes a
# state-preserving silent fallback so no stream/id native methods are reachable.
# ---------------------------------------------------------------------------
music = MUSIC.read_text(encoding="utf-8")
music = replace_once(
    music,
    '''    public static Music create(Fi file){
        Music music = new Music();
        try{
            music.file = file;
            music.handle = streamLoadFile(file.path());
        }catch(Throwable e){
            Log.err("Failed loading music from " + file, e);
        }
        return music;
    }''',
    '''    public static Music create(Fi file){
        return Core.audio == null ? new Music() : Core.audio.newMusic(file);
    }''',
    "Music.create"
)
music = replace_once(
    music,
    '''    public void load(byte[] bytes) throws Exception{
        handle = streamLoadBytes(bytes, bytes.length);
    }''',
    '''    public void load(byte[] bytes) throws Exception{
        handle = 0;
    }''',
    "Music.load bytes"
)
music = replace_method(
    music,
    "    public void load(Fi file) throws Exception{",
    "    public void play(){",
    '''    public void load(Fi file) throws Exception{
        this.file = file;
        handle = 0;
    }''',
    "Music.load file"
)
music = replace_method(
    music,
    "    public void play(){",
    "    public void pause(boolean pause){",
    '''    public void play(){
        // BrowserMusic overrides real playback.
    }''',
    "Music.play"
)
music = replace_method(
    music,
    "    public void pause(boolean pause){",
    "    @Override\n    public void stop(){",
    '''    public void pause(boolean pause){
        // BrowserMusic overrides real playback.
    }''',
    "Music.pause"
)
music = replace_method(
    music,
    "    public boolean isPlaying(){",
    "    public boolean isLooping(){",
    '''    public boolean isPlaying(){
        return false;
    }''',
    "Music.isPlaying"
)
music = replace_method(
    music,
    "    public void setLooping(boolean isLooping){",
    "    public float getVolume(){",
    '''    public void setLooping(boolean isLooping){
        this.looping = isLooping;
    }''',
    "Music.setLooping"
)
music = replace_method(
    music,
    "    public void setVolume(float volume){",
    "    public void set(float pan, float volume){",
    '''    public void setVolume(float volume){
        this.volume = volume;
    }''',
    "Music.setVolume"
)
music = replace_method(
    music,
    "    public void set(float pan, float volume){",
    "    public float getPosition(){",
    '''    public void set(float pan, float volume){
        this.volume = volume;
        this.pan = pan;
    }''',
    "Music.set"
)
music = replace_method(
    music,
    "    public float getPosition(){",
    "    public void setPosition(float position){",
    '''    public float getPosition(){
        return 0f;
    }''',
    "Music.getPosition"
)
music = replace_method(
    music,
    "    public void setPosition(float position){",
    "    /** @return length in seconds */",
    '''    public void setPosition(float position){
        // BrowserMusic overrides real playback position.
    }''',
    "Music.setPosition"
)
music = replace_once(
    music,
    '''    @Override
    public float getLength(){
        if(handle == 0 || !Core.audio.initialized) return 0f;
        return (float)Soloud.streamLength(handle);
    }''',
    '''    @Override
    public float getLength(){
        return 0f;
    }''',
    "Music.getLength"
)
MUSIC.write_text(music, encoding="utf-8")

print("Applied JNI-free Arc Web audio fallbacks; BrowserAudio owns all real browser playback")
