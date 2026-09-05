package mindustry.web;

import arc.*;
import arc.files.*;
import arc.util.*;
import mindustry.*;
import org.teavm.jso.JSBody;

import java.util.*;

/** Browser-localized Mindustry bundles. Only English and Russian ship in the Yandex package. */
public final class BrowserI18n{
    private BrowserI18n(){}

    public static void loadAndVerify(){
        Fi base = Core.files.internal("bundles/bundle");
        I18NBundle english = I18NBundle.createBundle(base, Locale.ENGLISH);
        I18NBundle russian = I18NBundle.createBundle(base, new Locale("ru"));

        require(english, "play", "Play");
        require(english, "campaign", null);
        require(english, "settings", null);
        require(russian, "play", null);
        require(russian, "campaign", null);
        require(russian, "settings", null);

        // Yandex release exposes exactly two selectable languages. Do not inherit the
        // upstream locale catalog or its special "router" pseudo-locale.
        Vars.locales = new Locale[]{Locale.ENGLISH, new Locale("ru")};

        // An explicit ?lang=en|ru is useful for deterministic platform/test startup and
        // takes priority. Normal launches use the saved setting, then browser language.
        String forced = queryLanguage();
        String requested = Core.settings == null ? "default" : Core.settings.getString("locale", "default");
        boolean useRussian;
        if("ru".equals(forced)){
            useRussian = true;
        }else if("en".equals(forced)){
            useRussian = false;
        }else if(requested == null || requested.isEmpty() || requested.equals("default")){
            useRussian = browserLanguage().toLowerCase(Locale.ROOT).startsWith("ru");
        }else{
            useRussian = requested.toLowerCase(Locale.ROOT).startsWith("ru");
        }

        Locale chosen = useRussian ? new Locale("ru") : Locale.ENGLISH;
        Locale.setDefault(chosen);
        Core.bundle = useRussian ? russian : english;
        if(Core.settings != null) Core.settings.put("locale", useRussian ? "ru" : "en");
        markLocale(useRussian ? "ru" : "en");
    }

    private static void require(I18NBundle bundle, String key, String expected){
        String value = bundle.get(key, "");
        if(value == null || value.trim().isEmpty()){
            throw new IllegalStateException("Browser localization missing key: " + key + " for " + bundle.getLocale());
        }
        if(expected != null && !value.equals(expected)){
            throw new IllegalStateException("Unexpected English localization value for " + key + ": " + value);
        }
    }

    @JSBody(script = "var v = new URLSearchParams(location.search).get('lang'); return v === 'ru' || v === 'en' ? v : '';")
    private static native String queryLanguage();

    @JSBody(script = "return (navigator.language || navigator.userLanguage || 'en');")
    private static native String browserLanguage();

    @JSBody(params = {"locale"}, script = "document.documentElement.setAttribute('data-mindustry-locale', locale);")
    private static native void markLocale(String locale);
}
