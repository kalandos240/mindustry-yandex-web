package mindustry.web;

import org.teavm.jso.JSBody;

/** Optional Playgama advertisement placements. No-op in Yandex builds. */
public final class BrowserPlaygamaAds{
    private BrowserPlaygamaAds(){}

    @JSBody(params = {"placement"}, script = """
        const ads = globalThis.__mindustryPlaygamaAds;
        if(ads && typeof ads.request === 'function') ads.request(placement);
        """)
    public static native void placement(String placement);
}
