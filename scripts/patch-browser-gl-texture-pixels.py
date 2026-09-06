#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BROWSER_GL = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserGL20.java"

if not BROWSER_GL.is_file():
    raise SystemExit(f"Missing BrowserGL20 source: {BROWSER_GL}")

text = BROWSER_GL.read_text(encoding="utf-8")
old = '''    /**
     * TeaVM exposes a Java ByteBuffer to JavaScript as an Int8Array. WebGL validates
     * the concrete ArrayBufferView class against the GL pixel type, so e.g.
     * GL_UNSIGNED_BYTE + Int8Array is INVALID_OPERATION. Re-wrap the exact same
     * backing bytes with the typed-array class required by WebGL. No pixel copy is
     * performed; byteOffset/byteLength are preserved.
     */
    @JSBody(params = {"buffer", "type"}, script = """
        if (buffer == null) return null;
        var raw = buffer.buffer;
        var offset = buffer.byteOffset || 0;
        var bytes = buffer.byteLength;
        if (raw == null || bytes == null) return buffer;
        switch(type){
            case 0x1400: return new Int8Array(raw, offset, bytes);
            case 0x1401: return new Uint8Array(raw, offset, bytes);
            case 0x1402: return new Int16Array(raw, offset, bytes >> 1);
            case 0x1403:
            case 0x8033:
            case 0x8034:
            case 0x8363: return new Uint16Array(raw, offset, bytes >> 1);
            case 0x1404: return new Int32Array(raw, offset, bytes >> 2);
            case 0x1405: return new Uint32Array(raw, offset, bytes >> 2);
            case 0x1406: return new Float32Array(raw, offset, bytes >> 2);
            default: return buffer;
        }
        """)
    private static native ArrayBufferView texturePixels(Buffer buffer, int type);'''
new = '''    /**
     * TeaVM converts a java.nio.Buffer parameter of a JSO/@JSBody method before the
     * JavaScript body executes. In particular, even a null GLOnlyTextureData pixel
     * buffer reaches TJSBufferHelper and fails because there is no native JS view.
     * Never let a Java Buffer cross that boundary: handle null in Java and copy the
     * remaining elements into an explicit typed array whose class matches the GL type.
     */
    private static ArrayBufferView texturePixels(Buffer buffer, int type){
        if(buffer == null) return null;

        if(buffer instanceof ByteBuffer){
            ByteBuffer source = ((ByteBuffer)buffer).duplicate();
            byte[] values = new byte[source.remaining()];
            source.get(values);
            Int8Array raw = Int8Array.copyFromJavaArray(values);
            return pixelBytes(raw, type);
        }
        if(buffer instanceof ShortBuffer){
            ShortBuffer source = ((ShortBuffer)buffer).duplicate();
            short[] values = new short[source.remaining()];
            source.get(values);
            Int16Array raw = Int16Array.copyFromJavaArray(values);
            if(type == GL_UNSIGNED_SHORT || type == GL_UNSIGNED_SHORT_4_4_4_4 || type == GL_UNSIGNED_SHORT_5_5_5_1 || type == GL_UNSIGNED_SHORT_5_6_5){
                return new Uint16Array(raw.getBuffer(), raw.getByteOffset(), raw.getByteLength() >>> 1);
            }
            return raw;
        }
        if(buffer instanceof IntBuffer){
            IntBuffer source = ((IntBuffer)buffer).duplicate();
            int[] values = new int[source.remaining()];
            source.get(values);
            Int32Array raw = Int32Array.copyFromJavaArray(values);
            if(type == GL_UNSIGNED_INT){
                return new Uint32Array(raw.getBuffer(), raw.getByteOffset(), raw.getByteLength() >>> 2);
            }
            return raw;
        }
        if(buffer instanceof FloatBuffer){
            FloatBuffer source = ((FloatBuffer)buffer).duplicate();
            float[] values = new float[source.remaining()];
            source.get(values);
            return Float32Array.copyFromJavaArray(values);
        }
        throw new ArcRuntimeException("Unsupported WebGL texture pixel buffer: " + buffer.getClass().getName());
    }

    private static ArrayBufferView pixelBytes(Int8Array raw, int type){
        int offset = raw.getByteOffset();
        int bytes = raw.getByteLength();
        switch(type){
            case GL_BYTE: return raw;
            case GL_UNSIGNED_BYTE: return new Uint8Array(raw.getBuffer(), offset, bytes);
            case GL_SHORT: return new Int16Array(raw.getBuffer(), offset, bytes >>> 1);
            case GL_UNSIGNED_SHORT:
            case GL_UNSIGNED_SHORT_4_4_4_4:
            case GL_UNSIGNED_SHORT_5_5_5_1:
            case GL_UNSIGNED_SHORT_5_6_5: return new Uint16Array(raw.getBuffer(), offset, bytes >>> 1);
            case GL_INT: return new Int32Array(raw.getBuffer(), offset, bytes >>> 2);
            case GL_UNSIGNED_INT: return new Uint32Array(raw.getBuffer(), offset, bytes >>> 2);
            case GL_FLOAT: return new Float32Array(raw.getBuffer(), offset, bytes >>> 2);
            default: return raw;
        }
    }'''

if text.count(old) != 1:
    raise SystemExit("BrowserGL20 texturePixels bridge no longer matches pinned Web backend")
text = text.replace(old, new, 1)
BROWSER_GL.write_text(text, encoding="utf-8")
print("Applied TeaVM-safe texture pixel typed-array bridge")
