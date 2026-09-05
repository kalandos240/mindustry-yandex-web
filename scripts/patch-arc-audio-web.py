#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOUND = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "audio" / "Sound.java"
BUFFERS = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "util" / "Buffers.java"
BROWSER_GL = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserGL20.java"

if not SOUND.is_file():
    raise SystemExit(f"Missing pinned Arc Sound source: {SOUND}")
if not BUFFERS.is_file():
    raise SystemExit(f"Missing pinned Arc Buffers source: {BUFFERS}")
if not BROWSER_GL.is_file():
    raise SystemExit(f"Missing BrowserGL20 source: {BROWSER_GL}")

text = SOUND.read_text(encoding="utf-8")


def replace_once(old, new, label):
    global text
    if text.count(old) != 1:
        raise SystemExit(f"Arc Web audio patch expected exactly one pinned match ({label})")
    text = text.replace(old, new, 1)


def replace_method(start_marker, end_marker, replacement, label):
    global text
    if text.count(start_marker) != 1 or text.count(end_marker) != 1:
        raise SystemExit(f"Arc Web audio method boundary no longer matches pinned upstream ({label})")
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    if end <= start:
        raise SystemExit(f"Arc Web audio method boundaries are invalid ({label})")
    text = text[:start] + replacement + "\n\n" + text[end:]


# Arc's desktop Sound path lazy-loads through Core.executor and ultimately calls the
# JNI SoLoud backend. Neither JVM ExecutorService nor JNI exists in TeaVM JavaScript.
# Keep Sound call sites safe and allocation-free until the dedicated browser audio
# backend is wired; gameplay code may call sounds, but Web currently treats them as
# no-op instead of pulling desktop audio infrastructure into the JS graph.
replace_method(
    '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){',
    '    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame){',
    '''    public int play(float volume, float pitch, float pan, boolean loop, boolean checkFrame, AudioBus bus){\n        return -1;\n    }''',
    'Sound primary play'
)

replace_once(
    '''    public float calcVolume(float x, float y){\n        return calcFalloff(x, y) * Core.audio.sfxVolume;\n    }''',
    '''    public float calcVolume(float x, float y){\n        return 0f;\n    }''',
    'Sound calcVolume'
)

replace_once(
    '''    public int play(){\n        return play(Core.audio.sfxVolume);\n    }''',
    '''    public int play(){\n        return -1;\n    }''',
    'Sound no-arg play'
)

replace_once(
    '''    public int play(AudioBus bus){\n        return play(Core.audio.sfxVolume, 1f, 0f, false, true, bus);\n    }''',
    '''    public int play(AudioBus bus){\n        return -1;\n    }''',
    'Sound bus play'
)

replace_once(
    '''    public float getLength(){\n        if(handle == 0 || !Core.audio.initialized) return 0f;\n        return stream ? (float)Soloud.streamLength(handle) : (float)Soloud.wavLength(handle);\n    }''',
    '''    public float getLength(){\n        return 0f;\n    }''',
    'Sound getLength'
)

SOUND.write_text(text, encoding="utf-8")

# Arc's VBO and VertexArray implementations both copy float vertex arrays through
# desktop JNI memcpy helpers. TeaVM supports NIO buffers directly, so keep the same
# byte-level contract with duplicate/slice views and never make native memcpy reachable.
# Arc core targets Java 8, so use classic instanceof + cast syntax here.
buffers = BUFFERS.read_text(encoding="utf-8")
old_simple = '''    private native static void copyJni(float[] src, Buffer dst, int numFloats, int offset); /*\n\t\tmemcpy(dst, src + offset, numFloats << 2 );\n\t*/'''
new_simple = '''    private static void copyJni(float[] src, Buffer dst, int numFloats, int offset){\n        copyFloatArray(src, offset, dst, 0, numFloats);\n    } /* Web: TeaVM-safe NIO copy; no JNI. */'''
old_offset = '''    private native static void copyJni(float[] src, int srcOffset, Buffer dst, int dstOffset, int numBytes); /*\n\t\tmemcpy(dst + dstOffset, src + srcOffset, numBytes);\n\t*/'''
new_offset = '''    private static void copyJni(float[] src, int srcOffset, Buffer dst, int dstOffset, int numBytes){\n        copyFloatArray(src, srcOffset, dst, dstOffset, numBytes >>> 2);\n    } /* Web: TeaVM-safe NIO copy; no JNI. */\n\n    private static void copyFloatArray(float[] src, int srcOffset, Buffer dst, int dstOffsetBytes, int count){\n        if(dst instanceof ByteBuffer){\n            ByteBuffer bytes = (ByteBuffer)dst;\n            ByteBuffer target = bytes.duplicate().order(bytes.order());\n            target.position(dstOffsetBytes);\n            target.slice().order(bytes.order()).asFloatBuffer().put(src, srcOffset, count);\n        }else if(dst instanceof FloatBuffer){\n            FloatBuffer floats = (FloatBuffer)dst;\n            FloatBuffer target = floats.duplicate();\n            target.position(dstOffsetBytes >>> 2);\n            target.put(src, srcOffset, count);\n        }else{\n            throw new ArcRuntimeException(\"Unsupported Web float buffer copy target: \" + dst.getClass().getName());\n        }\n    }'''
for old, new, label in [
    (old_simple, new_simple, "Buffers simple float copyJni"),
    (old_offset, new_offset, "Buffers offset float copyJni"),
]:
    if buffers.count(old) != 1:
        raise SystemExit(f"Arc Web buffer patch expected exactly one pinned match ({label})")
    buffers = buffers.replace(old, new, 1)
BUFFERS.write_text(buffers, encoding="utf-8")

# TeaVM 0.15 only exposes a Java NIO Buffer directly to JSO when that buffer is backed
# by TeaVM linear/native JS memory. Arc's browser-safe VBO/IBO storage intentionally uses
# ordinary Java ByteBuffers, so crossing directly into WebGL throws at runtime. Preserve
# the exact upload bytes and cross the JSO boundary as an explicit Int8Array copy instead.
gl = BROWSER_GL.read_text(encoding="utf-8")
old_buffer_data = '''    @Override\n    public void glBufferData(int target, int size, Buffer data, int usage){\n        if(data == null) gl.bufferData(target, size, usage);\n        else gl.bufferData(target, data, usage);\n    }\n\n    @Override\n    public void glBufferSubData(int target, int offset, int size, Buffer data){ gl.bufferSubData(target, offset, data); }'''
new_buffer_data = '''    @Override\n    public void glBufferData(int target, int size, Buffer data, int usage){\n        if(data == null) gl.bufferData(target, size, usage);\n        else gl.bufferData(target, copyBufferUpload(data, size), usage);\n    }\n\n    @Override\n    public void glBufferSubData(int target, int offset, int size, Buffer data){\n        gl.bufferSubData(target, offset, copyBufferUpload(data, size));\n    }'''
if gl.count(old_buffer_data) != 1:
    raise SystemExit("BrowserGL20 buffer upload patch no longer matches current Web backend")
gl = gl.replace(old_buffer_data, new_buffer_data, 1)

old_anchor = '''    private boolean validUniform(int location){ return location >= 0 && uniforms.get(location) != null; }'''
new_anchor = '''    /**\n     * WebGL bufferData/bufferSubData consume raw bytes. Arc VBO/IBO upload ByteBuffers,\n     * but TeaVM cannot pass these ordinary Java buffers directly through JSO. Copy only\n     * the requested byte range into a real JavaScript Int8Array; signedness is irrelevant\n     * for raw GL buffer storage and every packed float/color/index bit is preserved.\n     */\n    private static ArrayBufferView copyBufferUpload(Buffer data, int size){\n        if(!(data instanceof ByteBuffer)){\n            throw new ArcRuntimeException(\"Unsupported WebGL upload buffer: \" + data.getClass().getName());\n        }\n        ByteBuffer source = ((ByteBuffer)data).duplicate();\n        if(size < 0 || source.remaining() < size){\n            throw new ArcRuntimeException(\"Invalid WebGL upload byte range: requested=\" + size + \", remaining=\" + source.remaining());\n        }\n        byte[] bytes = new byte[size];\n        source.get(bytes);\n        return Int8Array.copyFromJavaArray(bytes);\n    }\n\n    private boolean validUniform(int location){ return location >= 0 && uniforms.get(location) != null; }'''
if gl.count(old_anchor) != 1:
    raise SystemExit("BrowserGL20 upload helper anchor no longer matches current Web backend")
gl = gl.replace(old_anchor, new_anchor, 1)
BROWSER_GL.write_text(gl, encoding="utf-8")

print("Applied TeaVM-safe no-op Arc Sound execution path")
print("Applied TeaVM-safe Arc float vertex-buffer copies without JNI")
print("Applied TeaVM-safe Arc VBO/IBO typed-array WebGL upload bridge")
