package mindustry.web;

import arc.Settings;
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
public final class BrowserSettings extends Settings{
    private static final String header = "MWS1|";
    private static final String hex = "0123456789abcdef";

    private final String storageKey;

    public BrowserSettings(String storageKey){
        if(storageKey == null || storageKey.isEmpty()){
            throw new IllegalArgumentException("storageKey must not be empty");
        }
        this.storageKey = storageKey;
    }

    @Override
    public synchronized void load(){
        loadValues();
        loaded = true;
        modified = false;
    }

    @Override
    public synchronized void forceSave(){
        if(!loaded) return;
        saveValues();
        modified = false;
    }

    @Override
    public synchronized void manualSave(){
        forceSave();
    }

    @Override
    public synchronized void autosave(){
        if(modified && shouldAutosave){
            forceSave();
        }
    }

    @Override
    public synchronized void loadValues(){
        values.clear();
        String payload = storageGet(storageKey);
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
    public synchronized void saveValues(){
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

        if(!storageSet(storageKey, payload.toString())){
            throw new IllegalStateException("Browser localStorage is unavailable");
        }
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

    @JSBody(params = {"key"}, script = "try { return window.localStorage.getItem(key); } catch (e) { return null; }")
    private static native String storageGet(String key);

    @JSBody(params = {"key", "value"}, script = "try { window.localStorage.setItem(key, value); return true; } catch (e) { return false; }")
    private static native boolean storageSet(String key, String value);
}
