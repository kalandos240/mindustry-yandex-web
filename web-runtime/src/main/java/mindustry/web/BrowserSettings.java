package mindustry.web;

import arc.Core;
import arc.Settings;
import arc.files.Fi;
import org.teavm.jso.JSBody;

import java.util.Map;

/**
 * Browser-native Arc settings store.
 *
 * The desktop Settings implementation persists a binary file and starts a backup
 * executor. Neither behavior belongs in a single-threaded TeaVM browser target.
 * This implementation keeps Arc's public Settings API and supported value types,
 * but stores a compact length-prefixed payload in localStorage. Longs are encoded
 * as decimal text so their full 64-bit value never passes through a JavaScript
 * Number.
 */
/** Confined to the browser event loop; no JVM monitor may suspend a Scene callback. */
public final class BrowserSettings extends Settings{
    private static final String header = "MWS1|";
    private static final String hex = "0123456789abcdef";

    private final String storageKey;

    public BrowserSettings(String storageKey){
        if(storageKey == null || storageKey.isEmpty()){
            throw new IllegalArgumentException("storageKey must not be empty");
        }
        this.storageKey = storageKey;
        json.setSerializer(mindustry.game.SectorInfo.class, new BrowserSectorInfoCodec());
        markWritePolicy();
    }

    /**
     * Mindustry derives saves/maps/previews from Settings.getDataDirectory(). Never
     * fall back to Arc's OS absolute path in a browser; the Yandex data root is the
     * persistent FileType.local namespace backed by IndexedDB.
     */
    @Override
    public Fi getDataDirectory(){
        if(Core.files == null || !Core.files.isLocalStorageAvailable()){
            throw new IllegalStateException("Browser persistent local filesystem is not initialized");
        }
        return Core.files.local("");
    }

    @Override
    public void load(){
        loadValues();
        loaded = true;
        modified = false;
    }

    @Override
    public void forceSave(){
        if(!loaded) return;
        saveValues();
        modified = false;
    }

    @Override
    public void manualSave(){
        // Logic.reset() invokes manualSave() on every map/sector transition. Browser
        // localStorage is synchronous, so do not serialize/rewrite an unchanged map.
        if(modified) forceSave();
    }

    @Override
    public void autosave(){
        // Arc changes slider preferences on every pointer move. localStorage
        // rewrites the whole campaign/settings payload synchronously; doing that
        // every drag frame can starve input/rendering on slower devices. Commit
        // once after release. Explicit manual/checkpoint saves remain immediate.
        if(modified && shouldAutosave && (Core.input == null || !Core.input.isTouched())){
            forceSave();
        }
    }

    @Override
    public void loadValues(){
        values.clear();
        Fi durable = storageFile();
        String payload = durable.exists()
            ? new String(durable.readBytes(), java.nio.charset.StandardCharsets.UTF_8)
            : storageGet(storageKey);
        if(payload == null || payload.isEmpty()) return;
        if(!payload.startsWith(header)){
            throw new IllegalStateException("Unsupported Mindustry Web settings format");
        }

        int[] cursor = {header.length()};
        try{
            while(cursor[0] < payload.length()){
                char type = payload.charAt(cursor[0]++);
                String key = readPart(payload, cursor);
                String value = readPart(payload, cursor);

                switch(type){
                    case 'b' -> values.put(key, "1".equals(value));
                    case 'i' -> values.put(key, Integer.parseInt(value));
                    case 'l' -> values.put(key, Long.parseLong(value));
                    case 'f' -> values.put(key, Float.parseFloat(value));
                    case 's' -> values.put(key, value);
                    case 'x' -> values.put(key, decodeHex(value));
                    default -> throw new IllegalStateException("Unknown browser settings type: " + type);
                }
            }
        }catch(RuntimeException malformed){
            values.clear();
            throw new IllegalStateException("Corrupt Mindustry Web settings payload", malformed);
        }
    }

    @Override
    public void saveValues(){
        StringBuilder payload = new StringBuilder(header);
        for(Map.Entry<String, Object> entry : values.entrySet()){
            Object value = entry.getValue();
            char type;
            String encoded;

            if(value instanceof Boolean bool){
                type = 'b';
                encoded = bool ? "1" : "0";
            }else if(value instanceof Integer integer){
                type = 'i';
                encoded = Integer.toString(integer);
            }else if(value instanceof Long longValue){
                type = 'l';
                encoded = Long.toString(longValue);
            }else if(value instanceof Float floatValue){
                type = 'f';
                encoded = Float.toString(floatValue);
            }else if(value instanceof String string){
                type = 's';
                encoded = string;
            }else if(value instanceof byte[] bytes){
                type = 'x';
                encoded = encodeHex(bytes);
            }else{
                throw new IllegalArgumentException("Unsupported browser settings value: " + value);
            }

            payload.append(type);
            appendPart(payload, entry.getKey());
            appendPart(payload, encoded);
        }

        String encoded = payload.toString();
        // localStorage may still be awaiting its browser-process disk commit at
        // page termination. Mirror preferences/sector metadata in the same
        // IndexedDB transaction stream and flush barrier as the MSAV files.
        storageFile().writeBytes(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8), false);
        // Keep the existing portal/legacy settings key as a best-effort mirror.
        storageSet(storageKey, encoded);
    }

    private Fi storageFile(){
        return Core.files.local("settings/" + storageKey + ".mws1");
    }

    private static void appendPart(StringBuilder out, String value){
        out.append(value.length()).append(':').append(value);
    }

    private static String readPart(String payload, int[] cursor){
        int colon = payload.indexOf(':', cursor[0]);
        if(colon < 0) throw new IllegalStateException("Missing length separator");
        int length = Integer.parseInt(payload.substring(cursor[0], colon));
        if(length < 0) throw new IllegalStateException("Negative field length");
        int start = colon + 1;
        int end = start + length;
        if(end < start || end > payload.length()) throw new IllegalStateException("Field exceeds payload");
        cursor[0] = end;
        return payload.substring(start, end);
    }

    private static String encodeHex(byte[] bytes){
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for(byte value : bytes){
            int unsigned = value & 0xff;
            out.append(hex.charAt(unsigned >>> 4));
            out.append(hex.charAt(unsigned & 0x0f));
        }
        return out.toString();
    }

    private static byte[] decodeHex(String value){
        if((value.length() & 1) != 0) throw new IllegalStateException("Odd binary payload length");
        byte[] bytes = new byte[value.length() / 2];
        for(int i = 0; i < bytes.length; i++){
            int high = nibble(value.charAt(i * 2));
            int low = nibble(value.charAt(i * 2 + 1));
            bytes[i] = (byte)((high << 4) | low);
        }
        return bytes;
    }

    private static int nibble(char value){
        if(value >= '0' && value <= '9') return value - '0';
        if(value >= 'a' && value <= 'f') return value - 'a' + 10;
        if(value >= 'A' && value <= 'F') return value - 'A' + 10;
        throw new IllegalStateException("Invalid hexadecimal settings byte");
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-settings-write-policy','modified-only-manual'); document.documentElement.setAttribute('data-mindustry-settings-persistence','indexeddb-with-msav');")
    private static native void markWritePolicy();

    @JSBody(params = {"key"}, script = "try { return window.localStorage.getItem(key); } catch (e) { return null; }")
    private static native String storageGet(String key);

    @JSBody(params = {"key", "value"}, script = "var mirrored=true; try { window.localStorage.setItem(key, value); } catch (e) { mirrored=false; } var platform=globalThis.__mindustryYandex; if(platform && typeof platform.onSettingsStored==='function') platform.onSettingsStored(value); return mirrored;")
    private static native boolean storageSet(String key, String value);
}

