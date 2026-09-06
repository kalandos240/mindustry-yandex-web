#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUFFERS = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "util" / "Buffers.java"
BROWSER_GL = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserGL20.java"

for path in (BUFFERS, BROWSER_GL):
    if not path.is_file():
        raise SystemExit(f"Missing WebGL/NIO source: {path}")

# Arc's VBO and VertexArray implementations copy float vertex arrays through desktop
# JNI memcpy helpers. TeaVM owns browser memory itself, so preserve the same byte-level
# contract with Java NIO duplicate/slice views and never make native memcpy reachable.
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
        raise SystemExit(f"Arc Web NIO patch expected exactly one pinned match ({label})")
    buffers = buffers.replace(old, new, 1)
BUFFERS.write_text(buffers, encoding="utf-8")

# Ordinary Java NIO buffers cannot be passed directly across TeaVM's JSO boundary.
# Convert the exact requested data to explicit typed arrays before WebGL sees it.
gl = BROWSER_GL.read_text(encoding="utf-8")
old_buffer_data = '''    @Override\n    public void glBufferData(int target, int size, Buffer data, int usage){\n        if(data == null) gl.bufferData(target, size, usage);\n        else gl.bufferData(target, data, usage);\n    }\n\n    @Override\n    public void glBufferSubData(int target, int offset, int size, Buffer data){ gl.bufferSubData(target, offset, data); }'''
new_buffer_data = '''    @Override\n    public void glBufferData(int target, int size, Buffer data, int usage){\n        if(data == null) gl.bufferData(target, size, usage);\n        else gl.bufferData(target, copyBufferUpload(data, size), usage);\n    }\n\n    @Override\n    public void glBufferSubData(int target, int offset, int size, Buffer data){\n        gl.bufferSubData(target, offset, copyBufferUpload(data, size));\n    }'''
if gl.count(old_buffer_data) != 1:
    raise SystemExit("BrowserGL20 buffer upload patch no longer matches current Web backend")
gl = gl.replace(old_buffer_data, new_buffer_data, 1)

for old, new in [
    (
        'public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, Buffer data){\n        gl.compressedTexImage2D(target, level, internalformat, width, height, border, data);\n    }',
        'public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, Buffer data){\n        gl.compressedTexImage2D(target, level, internalformat, width, height, border, copyBufferUpload(data, imageSize));\n    }'
    ),
    (
        'public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, Buffer data){\n        gl.compressedTexSubImage2D(target, level, format, xoffset, yoffset, width, height, data);\n    }',
        'public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, Buffer data){\n        gl.compressedTexSubImage2D(target, level, format, xoffset, yoffset, width, height, copyBufferUpload(data, imageSize));\n    }'
    ),
]:
    if old in gl:
        gl = gl.replace(old, new, 1)

uniform_replacements = [
    ('public void glUniform1fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform1fv(uniforms.get(location), v); }',
     'public void glUniform1fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform1fv(uniforms.get(location), copyFloatUpload(v, count)); }'),
    ('public void glUniform1iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform1iv(uniforms.get(location), v); }',
     'public void glUniform1iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform1iv(uniforms.get(location), copyIntUpload(v, count)); }'),
    ('public void glUniform2fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform2fv(uniforms.get(location), v); }',
     'public void glUniform2fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform2fv(uniforms.get(location), copyFloatUpload(v, count * 2)); }'),
    ('public void glUniform2iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform2iv(uniforms.get(location), v); }',
     'public void glUniform2iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform2iv(uniforms.get(location), copyIntUpload(v, count * 2)); }'),
    ('public void glUniform3fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform3fv(uniforms.get(location), v); }',
     'public void glUniform3fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform3fv(uniforms.get(location), copyFloatUpload(v, count * 3)); }'),
    ('public void glUniform3iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform3iv(uniforms.get(location), v); }',
     'public void glUniform3iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform3iv(uniforms.get(location), copyIntUpload(v, count * 3)); }'),
    ('public void glUniform4fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform4fv(uniforms.get(location), v); }',
     'public void glUniform4fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform4fv(uniforms.get(location), copyFloatUpload(v, count * 4)); }'),
    ('public void glUniform4iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform4iv(uniforms.get(location), v); }',
     'public void glUniform4iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform4iv(uniforms.get(location), copyIntUpload(v, count * 4)); }'),
    ('public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix2fv(uniforms.get(location), transpose, value); }',
     'public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix2fv(uniforms.get(location), transpose, copyFloatUpload(value, count * 4)); }'),
    ('public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix3fv(uniforms.get(location), transpose, value); }',
     'public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix3fv(uniforms.get(location), transpose, copyFloatUpload(value, count * 9)); }'),
    ('public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix4fv(uniforms.get(location), transpose, value); }',
     'public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix4fv(uniforms.get(location), transpose, copyFloatUpload(value, count * 16)); }'),
    ('public void glVertexAttrib1fv(int indx, FloatBuffer values){ gl.vertexAttrib1fv(indx, values); }',
     'public void glVertexAttrib1fv(int indx, FloatBuffer values){ gl.vertexAttrib1fv(indx, copyFloatUpload(values, 1)); }'),
    ('public void glVertexAttrib2fv(int indx, FloatBuffer values){ gl.vertexAttrib2fv(indx, values); }',
     'public void glVertexAttrib2fv(int indx, FloatBuffer values){ gl.vertexAttrib2fv(indx, copyFloatUpload(values, 2)); }'),
    ('public void glVertexAttrib3fv(int indx, FloatBuffer values){ gl.vertexAttrib3fv(indx, values); }',
     'public void glVertexAttrib3fv(int indx, FloatBuffer values){ gl.vertexAttrib3fv(indx, copyFloatUpload(values, 3)); }'),
    ('public void glVertexAttrib4fv(int indx, FloatBuffer values){ gl.vertexAttrib4fv(indx, values); }',
     'public void glVertexAttrib4fv(int indx, FloatBuffer values){ gl.vertexAttrib4fv(indx, copyFloatUpload(values, 4)); }'),
]
for old, new in uniform_replacements:
    if gl.count(old) != 1:
        raise SystemExit(f"BrowserGL20 NIO uniform/attribute patch expected one match: {old[:80]}")
    gl = gl.replace(old, new, 1)

old_anchor = '''    private boolean validUniform(int location){ return location >= 0 && uniforms.get(location) != null; }'''
new_anchor = '''    /**
     * WebGL bufferData/bufferSubData consume raw bytes. Arc VBO/IBO upload ByteBuffers,
     * but TeaVM cannot pass these ordinary Java buffers directly through JSO. Copy only
     * the requested byte range into a real JavaScript Int8Array; signedness is irrelevant
     * for raw GL buffer storage and every packed float/color/index bit is preserved.
     */
    private static ArrayBufferView copyBufferUpload(Buffer data, int size){
        if(data == null) return null;
        if(!(data instanceof ByteBuffer)){
            throw new ArcRuntimeException("Unsupported WebGL upload buffer: " + data.getClass().getName());
        }
        ByteBuffer source = ((ByteBuffer)data).duplicate();
        if(size < 0 || source.remaining() < size){
            throw new ArcRuntimeException("Invalid WebGL upload byte range: requested=" + size + ", remaining=" + source.remaining());
        }
        byte[] bytes = new byte[size];
        source.get(bytes);
        return Int8Array.copyFromJavaArray(bytes);
    }

    private static Float32Array copyFloatUpload(FloatBuffer data, int count){
        FloatBuffer source = data.duplicate();
        if(count < 0 || source.remaining() < count){
            throw new ArcRuntimeException("Invalid WebGL float upload range: requested=" + count + ", remaining=" + source.remaining());
        }
        float[] values = new float[count];
        source.get(values);
        return Float32Array.copyFromJavaArray(values);
    }

    private static Int32Array copyIntUpload(IntBuffer data, int count){
        IntBuffer source = data.duplicate();
        if(count < 0 || source.remaining() < count){
            throw new ArcRuntimeException("Invalid WebGL int upload range: requested=" + count + ", remaining=" + source.remaining());
        }
        int[] values = new int[count];
        source.get(values);
        return Int32Array.copyFromJavaArray(values);
    }

    private boolean validUniform(int location){ return location >= 0 && uniforms.get(location) != null; }'''
if gl.count(old_anchor) != 1:
    raise SystemExit("BrowserGL20 upload helper anchor no longer matches current Web backend")
gl = gl.replace(old_anchor, new_anchor, 1)
BROWSER_GL.write_text(gl, encoding="utf-8")

print("Applied TeaVM-safe Arc float vertex-buffer copies without JNI")
print("Applied TeaVM-safe Arc VBO/IBO typed-array WebGL upload bridge")
print("Applied TeaVM-safe WebGL uniform/attribute typed-array bridges")
