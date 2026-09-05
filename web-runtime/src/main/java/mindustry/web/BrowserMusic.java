package mindustry.web;

import arc.audio.*;
import arc.files.*;

/** Streamed, same-origin browser music implementation backed by HTMLAudioElement. */
public final class BrowserMusic extends Music{
    private static int nextMusicId = 1;

    private final int browserId = nextMusicId++;
    private String url = "";
    private boolean browserLooping;
    private float browserVolume = 1f;
    private float browserPitch = 1f;
    private float browserPan;

    public BrowserMusic(Fi file){
        super();
        assign(file);
    }

    private void assign(Fi file){
        this.file = file;
        this.url = BrowserAudio.assetUrl(file);
        BrowserAudio.musicPrepare(browserId, url);
    }

    @Override
    public void load(Fi file){
        assign(file);
    }

    @Override
    public void load(byte[] bytes){
        throw new UnsupportedOperationException("BrowserMusic requires a packaged same-origin asset URL");
    }

    @Override
    public void play(){
        if(url.isEmpty()) return;
        BrowserAudio.musicPlay(browserId, url, browserVolume, browserPitch, browserPan, browserLooping);
    }

    @Override
    public void pause(boolean pause){
        BrowserAudio.musicPause(browserId, pause);
    }

    @Override
    public void stop(){
        BrowserAudio.musicStop(browserId);
    }

    @Override
    public boolean isPlaying(){
        return BrowserAudio.musicPlaying(browserId);
    }

    @Override
    public boolean isLooping(){
        return browserLooping;
    }

    @Override
    public void setLooping(boolean looping){
        browserLooping = looping;
        BrowserAudio.musicLoop(browserId, looping);
    }

    @Override
    public float getVolume(){
        return browserVolume;
    }

    @Override
    public void setVolume(float volume){
        browserVolume = Math.max(0f, Math.min(1f, volume));
        BrowserAudio.musicVolume(browserId, browserVolume);
    }

    @Override
    public void set(float pan, float volume){
        browserPan = Math.max(-1f, Math.min(1f, pan));
        setVolume(volume);
    }

    @Override
    public float getPosition(){
        return BrowserAudio.musicPosition(browserId);
    }

    @Override
    public void setPosition(float position){
        BrowserAudio.musicPosition(browserId, position);
    }

    @Override
    public float getLength(){
        return BrowserAudio.musicLength(browserId);
    }

    @Override
    public boolean valid(){
        return !url.isEmpty();
    }

    @Override
    public void dispose(){
        BrowserAudio.musicDispose(browserId);
        file = null;
        url = "";
    }

    @Override
    public String toString(){
        return "BrowserMusic: " + file;
    }
}
