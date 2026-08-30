package mindustry.web;

import arc.graphics.*;
import arc.util.*;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.*;
import org.teavm.jso.webgl.*;

import java.nio.*;
import java.util.*;

/**
 * Arc GL20 implementation backed directly by TeaVM's WebGL JSO API.
 *
 * Object handles are represented as stable integer IDs because Arc's GL20 API
 * mirrors OpenGL ES while WebGL exposes JavaScript objects for buffers,
 * textures, shaders, programs and uniform locations.
 */
public final class BrowserGL20 implements GL20{
    private final WebGLRenderingContext gl;

    private final Registry<WebGLBuffer> buffers = new Registry<>();
    private final Registry<WebGLFramebuffer> framebuffers = new Registry<>();
    private final Registry<WebGLRenderbuffer> renderbuffers = new Registry<>();
    private final Registry<WebGLTexture> textures = new Registry<>();
    private final Registry<WebGLProgram> programs = new Registry<>();
    private final Registry<WebGLShader> shaders = new Registry<>();
    private final Registry<WebGLUniformLocation> uniforms = new Registry<>();

    public BrowserGL20(WebGLRenderingContext gl){
        if(gl == null) throw new IllegalArgumentException("WebGL context is null");
        this.gl = gl;
        gl.pixelStorei(WebGLRenderingContext.UNPACK_PREMULTIPLY_ALPHA_WEBGL, 0);
    }

    @Override
    public void glActiveTexture(int texture){ gl.activeTexture(texture); }

    @Override
    public void glBindTexture(int target, int texture){ gl.bindTexture(target, textures.get(texture)); }

    @Override
    public void glBlendFunc(int sfactor, int dfactor){ gl.blendFunc(sfactor, dfactor); }

    @Override
    public void glClear(int mask){ gl.clear(mask); }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha){ gl.clearColor(red, green, blue, alpha); }

    @Override
    public void glClearDepthf(float depth){ gl.clearDepth(depth); }

    @Override
    public void glClearStencil(int s){ gl.clearStencil(s); }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha){ gl.colorMask(red, green, blue, alpha); }

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, Buffer data){
        gl.compressedTexImage2D(target, level, internalformat, width, height, border, data);
    }

    @Override
    public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, Buffer data){
        gl.compressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, data);
    }

    @Override
    public void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border){
        gl.copyTexImage2D(target, level, internalformat, x, y, width, height, border);
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height){
        gl.copyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void glCullFace(int mode){ gl.cullFace(mode); }

    @Override
    public void glDeleteTexture(int texture){
        WebGLTexture value = textures.remove(texture);
        if(value != null) gl.deleteTexture(value);
    }

    @Override
    public void glDepthFunc(int func){ gl.depthFunc(func); }

    @Override
    public void glDepthMask(boolean flag){ gl.depthMask(flag); }

    @Override
    public void glDepthRangef(float zNear, float zFar){ gl.depthRange(zNear, zFar); }

    @Override
    public void glDisable(int cap){ gl.disable(cap); }

    @Override
    public void glDrawArrays(int mode, int first, int count){ gl.drawArrays(mode, first, count); }

    @Override
    public void glDrawElements(int mode, int count, int type, Buffer indices){
        throw new ArcRuntimeException("Client-side index buffers are not supported by WebGL; bind an element array buffer and use the offset overload.");
    }

    @Override
    public void glEnable(int cap){ gl.enable(cap); }

    @Override
    public void glFinish(){ gl.finish(); }

    @Override
    public void glFlush(){ gl.flush(); }

    @Override
    public void glFrontFace(int mode){ gl.frontFace(mode); }

    @Override
    public int glGenTexture(){ return textures.add(gl.createTexture()); }

    @Override
    public int glGetError(){ return gl.getError(); }

    @Override
    public void glGetIntegerv(int pname, IntBuffer params){ fillIntParameter(gl, pname, params); }

    @Override
    public String glGetString(int name){ return getStringParameter(gl, name); }

    @Override
    public void glHint(int target, int mode){ gl.hint(target, mode); }

    @Override
    public void glLineWidth(float width){ gl.lineWidth(width); }

    @Override
    public void glPixelStorei(int pname, int param){ gl.pixelStorei(pname, param); }

    @Override
    public void glPolygonOffset(float factor, float units){ gl.polygonOffset(factor, units); }

    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels){
        readPixels(gl, x, y, width, height, format, type, pixels);
    }

    @Override
    public void glScissor(int x, int y, int width, int height){ gl.scissor(x, y, width, height); }

    @Override
    public void glStencilFunc(int func, int ref, int mask){ gl.stencilFunc(func, ref, mask); }

    @Override
    public void glStencilMask(int mask){ gl.stencilMask(mask); }

    @Override
    public void glStencilOp(int fail, int zfail, int zpass){ gl.stencilOp(fail, zfail, zpass); }

    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels){
        gl.texImage2D(target, level, internalformat, width, height, border, format, type, texturePixels(pixels, type));
    }

    @Override
    public void glTexParameterf(int target, int pname, float param){ gl.texParameterf(target, pname, param); }

    @Override
    public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, Buffer pixels){
        gl.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, texturePixels(pixels, type));
    }

    @Override
    public void glViewport(int x, int y, int width, int height){ gl.viewport(x, y, width, height); }

    @Override
    public void glAttachShader(int program, int shader){ gl.attachShader(programs.getRequired(program), shaders.getRequired(shader)); }

    @Override
    public void glBindAttribLocation(int program, int index, String name){ gl.bindAttribLocation(programs.getRequired(program), index, name); }

    @Override
    public void glBindBuffer(int target, int buffer){ gl.bindBuffer(target, buffers.get(buffer)); }

    @Override
    public void glBindFramebuffer(int target, int framebuffer){ gl.bindFramebuffer(target, framebuffers.get(framebuffer)); }

    @Override
    public void glBindRenderbuffer(int target, int renderbuffer){ gl.bindRenderbuffer(target, renderbuffers.get(renderbuffer)); }

    @Override
    public void glBlendColor(float red, float green, float blue, float alpha){ gl.blendColor(red, green, blue, alpha); }

    @Override
    public void glBlendEquation(int mode){ gl.blendEquation(mode); }

    @Override
    public void glBlendEquationSeparate(int modeRGB, int modeAlpha){ gl.blendEquationSeparate(modeRGB, modeAlpha); }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha){ gl.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha); }

    @Override
    public void glBufferData(int target, int size, Buffer data, int usage){
        if(data == null) gl.bufferData(target, size, usage);
        else gl.bufferData(target, data, usage);
    }

    @Override
    public void glBufferSubData(int target, int offset, int size, Buffer data){ gl.bufferSubData(target, offset, data); }

    @Override
    public int glCheckFramebufferStatus(int target){ return gl.checkFramebufferStatus(target); }

    @Override
    public void glCompileShader(int shader){ gl.compileShader(shaders.getRequired(shader)); }

    @Override
    public int glCreateProgram(){ return programs.add(gl.createProgram()); }

    @Override
    public int glCreateShader(int type){ return shaders.add(gl.createShader(type)); }

    @Override
    public void glDeleteBuffer(int buffer){
        WebGLBuffer value = buffers.remove(buffer);
        if(value != null) gl.deleteBuffer(value);
    }

    @Override
    public void glDeleteFramebuffer(int framebuffer){
        WebGLFramebuffer value = framebuffers.remove(framebuffer);
        if(value != null) gl.deleteFramebuffer(value);
    }

    @Override
    public void glDeleteProgram(int program){
        WebGLProgram value = programs.remove(program);
        if(value != null) gl.deleteProgram(value);
    }

    @Override
    public void glDeleteRenderbuffer(int renderbuffer){
        WebGLRenderbuffer value = renderbuffers.remove(renderbuffer);
        if(value != null) gl.deleteRenderbuffer(value);
    }

    @Override
    public void glDeleteShader(int shader){
        WebGLShader value = shaders.remove(shader);
        if(value != null) gl.deleteShader(value);
    }

    @Override
    public void glDetachShader(int program, int shader){ gl.detachShader(programs.getRequired(program), shaders.getRequired(shader)); }

    @Override
    public void glDisableVertexAttribArray(int index){ gl.disableVertexAttribArray(index); }

    @Override
    public void glDrawElements(int mode, int count, int type, int indices){ gl.drawElements(mode, count, type, indices); }

    @Override
    public void glEnableVertexAttribArray(int index){ gl.enableVertexAttribArray(index); }

    @Override
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer){
        gl.framebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffers.get(renderbuffer));
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level){
        gl.framebufferTexture2D(target, attachment, textarget, textures.get(texture), level);
    }

    @Override
    public int glGenBuffer(){ return buffers.add(gl.createBuffer()); }

    @Override
    public void glGenerateMipmap(int target){ gl.generateMipmap(target); }

    @Override
    public int glGenFramebuffer(){ return framebuffers.add(gl.createFramebuffer()); }

    @Override
    public int glGenRenderbuffer(){ return renderbuffers.add(gl.createRenderbuffer()); }

    @Override
    public String glGetActiveAttrib(int program, int index, IntBuffer size, IntBuffer type){
        WebGLActiveInfo info = gl.getActiveAttrib(programs.getRequired(program), index);
        if(info == null) return null;
        size.put(size.position(), info.getSize());
        type.put(type.position(), info.getType());
        return info.getName();
    }

    @Override
    public String glGetActiveUniform(int program, int index, IntBuffer size, IntBuffer type){
        WebGLActiveInfo info = gl.getActiveUniform(programs.getRequired(program), index);
        if(info == null) return null;
        size.put(size.position(), info.getSize());
        type.put(type.position(), info.getType());
        return info.getName();
    }

    @Override
    public int glGetAttribLocation(int program, String name){ return gl.getAttribLocation(programs.getRequired(program), indexOfUnusedNameFix(name)); }

    private static String indexOfUnusedNameFix(String name){ return name; }

    @Override
    public void glGetBooleanv(int pname, Buffer params){
        boolean value = getBooleanParameter(gl, pname);
        if(params instanceof IntBuffer ints) ints.put(ints.position(), value ? GL_TRUE : GL_FALSE);
        else if(params instanceof ByteBuffer bytes) bytes.put(bytes.position(), (byte)(value ? 1 : 0));
        else throw new ArcRuntimeException("Unsupported boolean parameter buffer: " + params.getClass().getName());
    }

    @Override
    public void glGetBufferParameteriv(int target, int pname, IntBuffer params){
        params.put(params.position(), getBufferParameterInt(gl, target, pname));
    }

    @Override
    public void glGetFloatv(int pname, FloatBuffer params){ fillFloatParameter(gl, pname, params); }

    @Override
    public void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params){
        params.put(params.position(), getFramebufferAttachmentParameterInt(gl, target, attachment, pname));
    }

    @Override
    public void glGetProgramiv(int program, int pname, IntBuffer params){
        params.put(params.position(), getProgramParameterInt(gl, programs.getRequired(program), pname));
    }

    @Override
    public String glGetProgramInfoLog(int program){ return gl.getProgramInfoLog(programs.getRequired(program)); }

    @Override
    public void glGetRenderbufferParameteriv(int target, int pname, IntBuffer params){
        params.put(params.position(), getRenderbufferParameterInt(gl, target, pname));
    }

    @Override
    public void glGetShaderiv(int shader, int pname, IntBuffer params){
        params.put(params.position(), getShaderParameterInt(gl, shaders.getRequired(shader), pname));
    }

    @Override
    public String glGetShaderInfoLog(int shader){ return gl.getShaderInfoLog(shaders.getRequired(shader)); }

    @Override
    public void glGetShaderPrecisionFormat(int shadertype, int precisiontype, IntBuffer range, IntBuffer precision){
        WebGLShaderPrecisionFormat format = gl.getShaderPrecisionFormat(shadertype, precisiontype);
        if(format == null) return;
        range.put(range.position(), format.getRangeMin());
        if(range.remaining() > 1) range.put(range.position() + 1, format.getRangeMax());
        precision.put(precision.position(), format.getPrecision());
    }

    @Override
    public void glGetTexParameterfv(int target, int pname, FloatBuffer params){
        params.put(params.position(), getTexParameterFloat(gl, target, pname));
    }

    @Override
    public void glGetTexParameteriv(int target, int pname, IntBuffer params){
        params.put(params.position(), getTexParameterInt(gl, target, pname));
    }

    @Override
    public void glGetUniformfv(int program, int location, FloatBuffer params){
        fillUniformFloat(gl, programs.getRequired(program), uniforms.getRequired(location), params);
    }

    @Override
    public void glGetUniformiv(int program, int location, IntBuffer params){
        fillUniformInt(gl, programs.getRequired(program), uniforms.getRequired(location), params);
    }

    @Override
    public int glGetUniformLocation(int program, String name){
        WebGLUniformLocation location = gl.getUniformLocation(programs.getRequired(program), name);
        return location == null ? -1 : uniforms.add(location);
    }

    @Override
    public void glGetVertexAttribfv(int index, int pname, FloatBuffer params){ fillVertexAttribFloat(gl, index, pname, params); }

    @Override
    public void glGetVertexAttribiv(int index, int pname, IntBuffer params){ fillVertexAttribInt(gl, index, pname, params); }

    @Override
    public boolean glIsBuffer(int buffer){
        WebGLBuffer value = buffers.get(buffer);
        return value != null && gl.isBuffer(value);
    }

    @Override
    public boolean glIsEnabled(int cap){ return gl.isEnabled(cap); }

    @Override
    public boolean glIsFramebuffer(int framebuffer){
        WebGLFramebuffer value = framebuffers.get(framebuffer);
        return value != null && gl.isFramebuffer(value);
    }

    @Override
    public boolean glIsProgram(int program){
        WebGLProgram value = programs.get(program);
        return value != null && gl.isProgram(value);
    }

    @Override
    public boolean glIsRenderbuffer(int renderbuffer){
        WebGLRenderbuffer value = renderbuffers.get(bufferSafeIdFix(renderbuffer));
        return value != null && gl.isRenderbuffer(value);
    }

    private static int bufferSafeIdFix(int value){ return value; }

    @Override
    public boolean glIsShader(int shader){
        WebGLShader value = shaders.get(shader);
        return value != null && gl.isShader(value);
    }

    @Override
    public boolean glIsTexture(int texture){
        WebGLTexture value = textures.get(texture);
        return value != null && gl.isTexture(value);
    }

    @Override
    public void glLinkProgram(int program){ gl.linkProgram(programs.getRequired(program)); }

    @Override
    public void glReleaseShaderCompiler(){
        // WebGL shader compilation is managed by the browser; there is no release hook.
    }

    @Override
    public void glRenderbufferStorage(int target, int internalformat, int width, int height){
        gl.renderbufferStorage(target, internalformat, width, height);
    }

    @Override
    public void glSampleCoverage(float value, boolean invert){ gl.sampleCoverage(value, invert); }

    @Override
    public void glShaderSource(int shader, String string){ gl.shaderSource(shaders.getRequired(shader), string); }

    @Override
    public void glStencilFuncSeparate(int face, int func, int ref, int mask){ gl.stencilFuncSeparate(face, func, ref, mask); }

    @Override
    public void glStencilMaskSeparate(int face, int mask){ gl.stencilMaskSeparate(face, mask); }

    @Override
    public void glStencilOpSeparate(int face, int fail, int zfail, int zpass){ gl.stencilOpSeparate(face, fail, zfail, zpass); }

    @Override
    public void glTexParameterfv(int target, int pname, FloatBuffer params){ gl.texParameterf(target, pname, params.get(params.position())); }

    @Override
    public void glTexParameteri(int target, int pname, int param){ gl.texParameteri(target, pname, param); }

    @Override
    public void glTexParameteriv(int target, int pname, IntBuffer params){ gl.texParameteri(target, pname, params.get(params.position())); }

    @Override
    public void glUniform1f(int location, float x){ if(validUniform(location)) gl.uniform1f(uniforms.get(location), x); }

    @Override
    public void glUniform1fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform1fv(uniforms.get(location), v); }

    @Override
    public void glUniform1fv(int location, int count, float[] v, int offset){ if(validUniform(location)) gl.uniform1fv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniform1i(int location, int x){ if(validUniform(location)) gl.uniform1i(uniforms.get(location), x); }

    @Override
    public void glUniform1iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform1iv(uniforms.get(location), v); }

    @Override
    public void glUniform1iv(int location, int count, int[] v, int offset){ if(validUniform(location)) gl.uniform1iv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniform2f(int location, float x, float y){ if(validUniform(location)) gl.uniform2f(uniforms.get(location), x, y); }

    @Override
    public void glUniform2fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform2fv(uniforms.get(location), v); }

    @Override
    public void glUniform2fv(int location, int count, float[] v, int offset){ if(validUniform(location)) gl.uniform2fv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniform2i(int location, int x, int y){ if(validUniform(location)) gl.uniform2i(uniforms.get(location), x, y); }

    @Override
    public void glUniform2iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform2iv(uniforms.get(location), v); }

    @Override
    public void glUniform2iv(int location, int count, int[] v, int offset){ if(validUniform(location)) gl.uniform2iv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniform3f(int location, float x, float y, float z){ if(validUniform(location)) gl.uniform3f(uniforms.get(location), x, y, z); }

    @Override
    public void glUniform3fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform3fv(uniforms.get(location), v); }

    @Override
    public void glUniform3fv(int location, int count, float[] v, int offset){ if(validUniform(location)) gl.uniform3fv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniform3i(int location, int x, int y, int z){ if(validUniform(location)) gl.uniform3i(uniforms.get(location), x, y, z); }

    @Override
    public void glUniform3iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform3iv(uniforms.get(location), v); }

    @Override
    public void glUniform3iv(int location, int count, int[] v, int offset){ if(validUniform(location)) gl.uniform3iv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniform4f(int location, float x, float y, float z, float w){ if(validUniform(location)) gl.uniform4f(uniforms.get(location), x, y, z, w); }

    @Override
    public void glUniform4fv(int location, int count, FloatBuffer v){ if(validUniform(location)) gl.uniform4fv(uniforms.get(location), v); }

    @Override
    public void glUniform4fv(int location, int count, float[] v, int offset){ if(validUniform(location)) gl.uniform4fv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniform4i(int location, int x, int y, int z, int w){ if(validUniform(location)) gl.uniform4i(uniforms.get(location), x, y, z, w); }

    @Override
    public void glUniform4iv(int location, int count, IntBuffer v){ if(validUniform(location)) gl.uniform4iv(uniforms.get(location), v); }

    @Override
    public void glUniform4iv(int location, int count, int[] v, int offset){ if(validUniform(location)) gl.uniform4iv(uniforms.get(location), slice(v, offset)); }

    @Override
    public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix2fv(uniforms.get(location), transpose, value); }

    @Override
    public void glUniformMatrix2fv(int location, int count, boolean transpose, float[] value, int offset){ if(validUniform(location)) gl.uniformMatrix2fv(uniforms.get(location), transpose, slice(value, offset)); }

    @Override
    public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix3fv(uniforms.get(location), transpose, value); }

    @Override
    public void glUniformMatrix3fv(int location, int count, boolean transpose, float[] value, int offset){ if(validUniform(location)) gl.uniformMatrix3fv(uniforms.get(location), transpose, slice(value, offset)); }

    @Override
    public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value){ if(validUniform(location)) gl.uniformMatrix4fv(uniforms.get(location), transpose, value); }

    @Override
    public void glUniformMatrix4fv(int location, int count, boolean transpose, float[] value, int offset){ if(validUniform(location)) gl.uniformMatrix4fv(uniforms.get(location), transpose, slice(value, offset)); }

    @Override
    public void glUseProgram(int program){ gl.useProgram(programs.get(program)); }

    @Override
    public void glValidateProgram(int program){ gl.validateProgram(programs.getRequired(program)); }

    @Override
    public void glVertexAttrib1f(int indx, float x){ gl.vertexAttrib1f(indx, x); }

    @Override
    public void glVertexAttrib1fv(int indx, FloatBuffer values){ gl.vertexAttrib1fv(indx, values); }

    @Override
    public void glVertexAttrib2f(int indx, float x, float y){ gl.vertexAttrib2f(indx, x, y); }

    @Override
    public void glVertexAttrib2fv(int indx, FloatBuffer values){ gl.vertexAttrib2fv(indx, values); }

    @Override
    public void glVertexAttrib3f(int indx, float x, float y, float z){ gl.vertexAttrib3f(indx, x, y, z); }

    @Override
    public void glVertexAttrib3fv(int indx, FloatBuffer values){ gl.vertexAttrib3fv(indx, values); }

    @Override
    public void glVertexAttrib4f(int indx, float x, float y, float z, float w){ gl.vertexAttrib4f(indx, x, y, z, w); }

    @Override
    public void glVertexAttrib4fv(int indx, FloatBuffer values){ gl.vertexAttrib4fv(indx, values); }

    @Override
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, Buffer ptr){
        throw new ArcRuntimeException("Client-side vertex arrays are not supported by WebGL; bind a VBO and use the offset overload.");
    }

    @Override
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int ptr){
        gl.vertexAttribPointer(indx, size, type, normalized, stride, ptr);
    }

    private boolean validUniform(int location){ return location >= 0 && uniforms.get(location) != null; }

    private static float[] slice(float[] source, int offset){
        if(offset <= 0) return source;
        float[] out = new float[source.length - offset];
        System.arraycopy(source, offset, out, 0, out.length);
        return out;
    }

    private static int[] slice(int[] source, int offset){
        if(offset <= 0) return source;
        int[] out = new int[source.length - offset];
        System.arraycopy(source, offset, out, 0, out.length);
        return out;
    }

    private static final class Registry<T>{
        private final ArrayList<T> values = new ArrayList<>();

        Registry(){ values.add(null); }

        int add(T value){
            if(value == null) return 0;
            values.add(value);
            return values.size() - 1;
        }

        T get(int id){
            return id <= 0 || id >= values.size() ? null : values.get(id);
        }

        T getRequired(int id){
            T value = get(id);
            if(value == null) throw new ArcRuntimeException("Invalid WebGL object handle: " + id);
            return value;
        }

        T remove(int id){
            if(id <= 0 || id >= values.size()) return null;
            T value = values.get(id);
            values.set(id, null);
            return value;
        }
    }

    @JSBody(params = {"gl", "pname", "out"}, script = """
        var value = gl.getParameter(pname);
        if (value == null) return;
        if (pname === 0x86A2) {
            var formats = gl.getParameter(0x86A3);
            out[0] = formats ? formats.length : 0;
            return;
        }
        if (typeof value === 'number') { out[0] = value | 0; return; }
        if (typeof value === 'boolean') { out[0] = value ? 1 : 0; return; }
        if (typeof value.length === 'number') {
            for (var i = 0; i < value.length && i < out.length; i++) out[i] = value[i] | 0;
        }
        """)
    private static native void fillIntParameter(WebGLRenderingContext gl, int pname, IntBuffer out);

    @JSBody(params = {"gl", "pname", "out"}, script = """
        var value = gl.getParameter(pname);
        if (value == null) return;
        if (typeof value === 'number') { out[0] = value; return; }
        if (typeof value === 'boolean') { out[0] = value ? 1 : 0; return; }
        if (typeof value.length === 'number') {
            for (var i = 0; i < value.length && i < out.length; i++) out[i] = value[i];
        }
        """)
    private static native void fillFloatParameter(WebGLRenderingContext gl, int pname, FloatBuffer out);

    @JSBody(params = {"gl", "pname"}, script = "return !!gl.getParameter(pname);")
    private static native boolean getBooleanParameter(WebGLRenderingContext gl, int pname);

    @JSBody(params = {"gl", "name"}, script = """
        if (name === 0x1F03) {
            var extensions = gl.getSupportedExtensions();
            return extensions ? extensions.join(' ') : '';
        }
        var value = gl.getParameter(name);
        return value == null ? '' : String(value);
        """)
    private static native String getStringParameter(WebGLRenderingContext gl, int name);

    @JSBody(params = {"gl", "target", "pname"}, script = "var v=gl.getBufferParameter(target,pname); return typeof v === 'boolean' ? (v?1:0) : (v|0);")
    private static native int getBufferParameterInt(WebGLRenderingContext gl, int target, int pname);

    @JSBody(params = {"gl", "target", "attachment", "pname"}, script = "var v=gl.getFramebufferAttachmentParameter(target,attachment,pname); return typeof v === 'number' ? (v|0) : 0;")
    private static native int getFramebufferAttachmentParameterInt(WebGLRenderingContext gl, int target, int attachment, int pname);

    @JSBody(params = {"gl", "program", "pname"}, script = "var v=gl.getProgramParameter(program,pname); return typeof v === 'boolean' ? (v?1:0) : (v|0);")
    private static native int getProgramParameterInt(WebGLRenderingContext gl, WebGLProgram program, int pname);

    @JSBody(params = {"gl", "target", "pname"}, script = "var v=gl.getRenderbufferParameter(target,pname); return typeof v === 'boolean' ? (v?1:0) : (v|0);")
    private static native int getRenderbufferParameterInt(WebGLRenderingContext gl, int target, int pname);

    @JSBody(params = {"gl", "shader", "pname"}, script = "var v=gl.getShaderParameter(shader,pname); return typeof v === 'boolean' ? (v?1:0) : (v|0);")
    private static native int getShaderParameterInt(WebGLRenderingContext gl, WebGLShader shader, int pname);

    @JSBody(params = {"gl", "target", "pname"}, script = "var v=gl.getTexParameter(target,pname); return Number(v || 0);")
    private static native float getTexParameterFloat(WebGLRenderingContext gl, int target, int pname);

    @JSBody(params = {"gl", "target", "pname"}, script = "var v=gl.getTexParameter(target,pname); return v|0;")
    private static native int getTexParameterInt(WebGLRenderingContext gl, int target, int pname);

    @JSBody(params = {"gl", "program", "location", "out"}, script = """
        var value = gl.getUniform(program, location);
        if (value == null) return;
        if (typeof value === 'number') { out[0] = value; return; }
        for (var i = 0; i < value.length && i < out.length; i++) out[i] = value[i];
        """)
    private static native void fillUniformFloat(WebGLRenderingContext gl, WebGLProgram program, WebGLUniformLocation location, FloatBuffer out);

    @JSBody(params = {"gl", "program", "location", "out"}, script = """
        var value = gl.getUniform(program, location);
        if (value == null) return;
        if (typeof value === 'number' || typeof value === 'boolean') { out[0] = value | 0; return; }
        for (var i = 0; i < value.length && i < out.length; i++) out[i] = value[i] | 0;
        """)
    private static native void fillUniformInt(WebGLRenderingContext gl, WebGLProgram program, WebGLUniformLocation location, IntBuffer out);

    @JSBody(params = {"gl", "index", "pname", "out"}, script = """
        var value = gl.getVertexAttrib(index, pname);
        if (value == null) return;
        if (typeof value === 'number') { out[0] = value; return; }
        if (typeof value === 'boolean') { out[0] = value ? 1 : 0; return; }
        for (var i = 0; i < value.length && i < out.length; i++) out[i] = value[i];
        """)
    private static native void fillVertexAttribFloat(WebGLRenderingContext gl, int index, int pname, FloatBuffer out);

    @JSBody(params = {"gl", "index", "pname", "out"}, script = """
        var value = gl.getVertexAttrib(index, pname);
        if (value == null) return;
        if (typeof value === 'number' || typeof value === 'boolean') { out[0] = value | 0; return; }
        for (var i = 0; i < value.length && i < out.length; i++) out[i] = value[i] | 0;
        """)
    private static native void fillVertexAttribInt(WebGLRenderingContext gl, int index, int pname, IntBuffer out);

    /**
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
    private static native ArrayBufferView texturePixels(Buffer buffer, int type);

    @JSBody(params = {"gl", "x", "y", "width", "height", "format", "type", "pixels"}, script = "gl.readPixels(x,y,width,height,format,type,pixels);")
    private static native void readPixels(WebGLRenderingContext gl, int x, int y, int width, int height, int format, int type, Buffer pixels);
}
