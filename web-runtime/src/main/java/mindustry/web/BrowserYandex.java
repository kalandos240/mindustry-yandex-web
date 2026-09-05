package mindustry.web;

import org.teavm.jso.JSBody;

/** Thin TeaVM bridge to the Yandex Games SDK wrapper owned by index.html. */
public final class BrowserYandex{
    private BrowserYandex(){}

    @JSBody(script = "return !!(globalThis.__mindustryYandex && globalThis.__mindustryYandex.available);")
    public static native boolean available();

    @JSBody(script = "return !!(globalThis.__mindustryYandex && globalThis.__mindustryYandex.paused);")
    public static native boolean paused();

    @JSBody(script = "return globalThis.__mindustryYandex && globalThis.__mindustryYandex.locale ? globalThis.__mindustryYandex.locale : '';")
    public static native String locale();

    @JSBody(script = "return !!(globalThis.__mindustryYandex && globalThis.__mindustryYandex.loadingReady && globalThis.__mindustryYandex.loadingReady());")
    public static native boolean loadingReady();

    @JSBody(script = "return !!(globalThis.__mindustryYandex && globalThis.__mindustryYandex.gameplayStart && globalThis.__mindustryYandex.gameplayStart());")
    public static native boolean gameplayStart();

    @JSBody(script = "return !!(globalThis.__mindustryYandex && globalThis.__mindustryYandex.gameplayStop && globalThis.__mindustryYandex.gameplayStop());")
    public static native boolean gameplayStop();

    @JSBody(params = {"state"}, script = "document.documentElement.setAttribute('data-mindustry-platform-pause', state);")
    public static native void markPauseState(String state);
}
