#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-arc-fi-web.py <Fi.java>")

path = Path(sys.argv[1])
text = path.read_text()
old = '''    public boolean exists(){
        switch(type){
            case internal:
                if(file().exists()) return true;
                // Fall through.
            case classpath:
                return Fi.class.getResource("/" + file.getPath().replace('\\\\', '/')) != null;
        }
        return file().exists();
    }
'''
new = '''    public boolean exists(){
        switch(type){
            case internal:
            case classpath:
                // Web: platform-created packaged handles (BrowserFi) override exists().
                // Do not retain the JVM Class.getResource/File fallback in TeaVM's graph.
                return false;
        }
        return file().exists();
    }
'''
if old not in text:
    raise SystemExit("Arc Fi.exists Web patch no longer matches pinned upstream.")
path.write_text(text.replace(old, new, 1))

# Pixmap's desktop implementation retains UnsafeBuffers and JNI branches even when
# Arc natives are not loaded. TeaVM sees those branches during dependency analysis.
# The Web target uses Arc's pure-Java PNG decoder and TeaVM direct ByteBuffers only.
pixmap_path = path.parent.parent / "graphics/Pixmap.java"
pixmap = pixmap_path.read_text()
replacements = [
    (
'''    private static final boolean supportsBufferCopy = OS.javaVersionNumber >= 16 || (OS.isAndroid && Core.app != null && Core.app.getVersion() >= 35);\n''',
'''    // Web: TeaVM owns direct-buffer memory; no desktop UnsafeBuffers/JNI path is used.\n'''
    ),
    (
'''    static{\n        if(!supportsBufferCopy && !OS.isIos){\n            UnsafeBuffers.checkInit();\n        }\n    }\n\n''',
'''    // Web: no native buffer bootstrap is required.\n\n'''
    ),
    (
'''    @Override\n    public void dispose(){\n        if(handle <= 0) return;\n        free(handle);\n        handle = 0;\n    }\n''',
'''    @Override\n    public void dispose(){\n        // Web pixmaps are backed by TeaVM-managed direct buffers.\n        handle = 0;\n    }\n'''
    ),
    (
'''    private void load(byte[] encodedData, int offset, int len, String file){\n        //use native implementation when possible\n        if(ArcNativesLoader.loaded){\n            try{\n                //read with stb_image, which is slightly faster for large images and supports more formats\n                long[] nativeData = new long[3];\n                pixels = loadJni(nativeData, encodedData, offset, len);\n                if(pixels == null) throw new ArcRuntimeException("Error loading pixmap from image data: " + getFailureReason() + (file == null ? "" : " (" + file + ")"));\n\n                handle = nativeData[0];\n                width = (int)nativeData[1];\n                height = (int)nativeData[2];\n                pixels.position(0).limit(pixels.capacity());\n            }catch(ArcRuntimeException e){\n                //stb_image bug? some PNGs fail with "corrupt JPEG" as the error, try the Java implementation if so\n                if(e.getMessage() != null && e.getMessage().contains("Corrupt JPEG")){\n                    try{\n                        loadJava(encodedData, offset, len, file);\n                        return;\n                    }catch(Exception ignored){\n                        //I did my best, fall through and throw the original exception\n                    }\n                }\n                throw e;\n            }\n        }else{\n            loadJava(encodedData, offset, len, file);\n        }\n    }\n''',
'''    private void load(byte[] encodedData, int offset, int len, String file){\n        // Web: always use Arc's pure-Java PNG reader; native stb_image is unavailable.\n        loadJava(encodedData, offset, len, file);\n    }\n'''
    ),
    (
'''    private void load(int width, int height){\n        //use native implementation when possible\n        if(ArcNativesLoader.loaded){\n            long[] nativeData = new long[3];\n            pixels = createJni(nativeData, width, height);\n            if(pixels == null) throw new ArcRuntimeException("Error creating pixmap (out of memory?)");\n            pixels.limit(pixels.capacity());\n\n            this.handle = nativeData[0];\n            this.width = (int)nativeData[1];\n            this.height = (int)nativeData[2];\n        }else{\n            //use DirectByteBuffer instead\n            this.handle = -1; //handle -1 means non-native buffer\n            this.width = width;\n            this.height = height;\n            this.pixels = ByteBuffer.allocateDirect(width * height * 4);\n        }\n    }\n''',
'''    private void load(int width, int height){\n        // Web: direct buffers are backed by TeaVM-managed memory.\n        this.handle = -1;\n        this.width = width;\n        this.height = height;\n        this.pixels = ByteBuffer.allocateDirect(width * height * 4);\n    }\n'''
    ),
    (
'''    static void copyMem(ByteBuffer src, int srcOffset, ByteBuffer dst, int dstOffset, int len){\n        //Java 16 supports direct byte buffer transfer without modifying state. Older versions (+Android/iOS) don't, and likely never will\n        if(supportsBufferCopy){\n            Java16Buffers.copy(src, srcOffset, dst, dstOffset, len);\n        }else{\n            if(!OS.isIos && !UnsafeBuffers.failed){\n                UnsafeBuffers.copy(src, srcOffset, dst, dstOffset, len);\n            }else{\n                Buffers.copyJni(src, srcOffset, dst, dstOffset, len);\n            }\n        }\n    }\n''',
'''    static void copyMem(ByteBuffer src, int srcOffset, ByteBuffer dst, int dstOffset, int len){\n        // Web: absolute byte copy preserves both buffers' position/limit and avoids JNI.\n        for(int i = 0; i < len; i++){\n            dst.put(dstOffset + i, src.get(srcOffset + i));\n        }\n    }\n'''
    ),
]
for old, new in replacements:
    if old not in pixmap:
        raise SystemExit(f"Arc Pixmap Web patch no longer matches pinned upstream: {old.splitlines()[0]!r}")
    pixmap = pixmap.replace(old, new, 1)
pixmap_path.write_text(pixmap)
