package mindustry.web;

import arc.*;
import arc.audio.*;
import arc.files.*;

/** Arc Sound facade backed by BrowserAudio/Web Audio instead of SoLoud/JNI. */
public final class BrowserSound extends Sound{
    private String url = "";
    private long browserMinInterval = 16L;
    private long browserLastPlayed;
    private int browserLastVoice = -1;
    private float browserLastVolume;

    public BrowserSound(Fi file){
        super();
        load(file);
    }

    @Override
    public void load(Fi file){
        this.file = file;
        this.url = BrowserAudio.assetUrl(file);
    }

    @Override
    public void load(byte[] data, boolean stream){
        // Browser sounds are intentionally URL-backed so the packaged OGG is decoded by
        // the browser's native codec. Byte-array SoLoud loading is not available on Web.
        this.url = "";
    }

    @Override
    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){
        return playBrowser(volume, pitch, pan, loop, checkFrame);
    }

    int playBrowser(float volume, float pitch, float pan, boolean loop, boolean checkFrame){
        if(url.isEmpty() || Core.audio == null || !Core.audio.initialized()) return -1;

        long now = System.currentTimeMillis();
        if(checkFrame && browserLastVoice > 0 && now - browserLastPlayed <= browserMinInterval){
            if(volume > browserLastVolume){
                browserLastVolume = Math.max(browserLastVolume, Math.min(browserLastVolume + volume, volume * 1.25f));
                Core.audio.set(browserLastVoice, pan, browserLastVolume);
            }
            return -1;
        }

        browserLastPlayed = now;
        browserLastVolume = volume;
        float effectivePitch = pitch * Core.audio.globalPitch;
        browserLastVoice = BrowserAudio.playSound(url, volume, effectivePitch, pan, loop);
        return browserLastVoice;
    }

    @Override
    public float getLength(){
        return url.isEmpty() ? 0f : BrowserAudio.soundLength(url);
    }

    @Override
    public void setMinInterval(long interval){
        browserMinInterval = Math.max(0L, interval);
    }

    @Override
    public boolean valid(){
        return !url.isEmpty();
    }

    @Override
    public int countPlaying(){
        return url.isEmpty() ? 0 : BrowserAudio.countSound(url);
    }

    @Override
    public void stop(){
        if(!url.isEmpty()) BrowserAudio.stopSound(url);
    }

    @Override
    public void dispose(){
        stop();
        file = null;
        url = "";
    }

    String browserUrl(){
        return url;
    }
}
