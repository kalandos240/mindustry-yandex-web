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
        // Arc defaults to java.text.MessageFormat. In TeaVM this reaches the
        // incomplete Currency/CLDR table (notably the obsolete CYP entry),
        // throwing from live Erekir tutorial objective text after unit production.
        // Both pinned EN/RU bundles exclusively use simple {0}, {1} placeholders.
        // The original text/content is unchanged; only its browser formatter is
        // switched to Arc's built-in lightweight placeholder implementation.
        I18NBundle.setSimpleFormatter(true);
        Fi base = Core.files.internal("bundles/bundle");
        I18NBundle english = I18NBundle.createBundle(base, Locale.ENGLISH);
        I18NBundle russian = I18NBundle.createBundle(base, new Locale("ru"));
        // UnitCountObjective.text() is the precise live Onset code path that
        // used to fail with TeaVM's Currency not found: CYP while displaying
        // "Produce a unit". Exercise the actual localized objective key.
        String unitGoal = english.format("objective.buildunit", 1, "", "Stell");
        String russianGoal = russian.format("objective.buildunit", 1, "", "Stell");
        if(unitGoal == null || !unitGoal.contains("Stell") || !unitGoal.contains("1")
        || russianGoal == null || !russianGoal.contains("1")){
            throw new IllegalStateException("TeaVM-safe UnitCountObjective formatting failed");
        }

        require(english, "play", "Play");
        require(english, "campaign", null);
        require(english, "settings", null);
        require(russian, "play", null);
        require(russian, "campaign", null);
        require(russian, "settings", null);

        // Yandex release exposes exactly two selectable languages. Do not inherit the
        // upstream locale catalog or its special "router" pseudo-locale.
        Vars.locales = new Locale[]{Locale.ENGLISH, new Locale("ru")};

        // ?lang=en|ru is reserved for deterministic CI/debug startup. A previously
        // selected in-game locale remains explicit user choice. Otherwise the required
        // Yandex SDK environment.i18n.lang is the authoritative startup language, with
        // navigator.language only as an off-platform development fallback.
        String forced = queryLanguage();
        String requested = Core.settings == null ? "default" : Core.settings.getString("locale", "default");
        String platform = yandexLanguage();
        String automatic = platform == null || platform.isEmpty() ? browserLanguage() : platform;

        boolean useRussian;
        if("ru".equals(forced)){
            useRussian = true;
        }else if("en".equals(forced)){
            useRussian = false;
        }else if(requested != null && !requested.isEmpty() && !requested.equals("default")){
            useRussian = requested.toLowerCase(Locale.ROOT).startsWith("ru");
        }else{
            useRussian = automatic.toLowerCase(Locale.ROOT).startsWith("ru");
        }

        Locale chosen = useRussian ? new Locale("ru") : Locale.ENGLISH;
        Locale.setDefault(chosen);
        Core.bundle = useRussian ? russian : english;
        // Keep the stock Mindustry setting defined even on the first launch.
        // Some original UI and player flows use Core.settings.getString("locale")
        // without a fallback. "default" is the original automatic preference:
        // unlike writing detected en/ru, it continues to honor the Yandex SDK
        // on each cold restart unless the player makes an explicit selection.
        if(Core.settings != null && !Core.settings.has("locale")){
            Core.settings.put("locale", "default");
        }
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

    @JSBody(script = "return globalThis.__mindustryYandex && globalThis.__mindustryYandex.locale ? globalThis.__mindustryYandex.locale : '';")
    private static native String yandexLanguage();

    @JSBody(script = "return (navigator.language || navigator.userLanguage || 'en');")
    private static native String browserLanguage();

    @JSBody(params = {"locale"}, script = "document.documentElement.setAttribute('data-mindustry-locale', locale);")
    private static native void markLocale(String locale);
}
