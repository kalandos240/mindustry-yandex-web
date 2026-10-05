#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "util" / "io" / "FastDeflaterOutputStream.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Arc FastDeflaterOutputStream source: {PATH}")

text = PATH.read_text(encoding="utf-8")

old = '''public class FastDeflaterOutputStream extends DeflaterOutputStream{
    private final byte[] tmp = {0};

    public FastDeflaterOutputStream(OutputStream outputStream){
        super(outputStream);
    }

    @Override
    public void write(int var1) throws IOException{
        tmp[0] = (byte)(var1 & 255);
        this.write(tmp, 0, 1);
    }
}
'''

new = '''public class FastDeflaterOutputStream extends DeflaterOutputStream{
    private static final int webInputChunk = 8192;
    private final byte[] tmp = {0};

    public FastDeflaterOutputStream(OutputStream outputStream){
        super(outputStream);
    }

    @Override
    public void write(int var1) throws IOException{
        tmp[0] = (byte)(var1 & 255);
        this.write(tmp, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException{
        if(bytes == null) throw new NullPointerException();
        if(offset < 0 || length < 0 || offset > bytes.length - length){
            throw new IndexOutOfBoundsException();
        }

        // TeaVM 0.15 maps java.util.zip.Deflater to JZlib. Very large single inputs
        // can make JZlib return Z_BUF_ERROR (-5), which TeaVM treats as fatal even
        // though the same continuous deflate stream is valid. Preserve the exact zlib
        // stream/SaveIO format, but feed it bounded chunks so each call makes progress.
        int end = offset + length;
        while(offset < end){
            int chunk = Math.min(webInputChunk, end - offset);
            super.write(bytes, offset, chunk);
            offset += chunk;
        }
    }
}
'''

if "webInputChunk = 8192" in text:
    raise SystemExit("FastDeflaterOutputStream Web chunk patch already applied before overlay")
if text.count(old) != 1:
    raise SystemExit("FastDeflaterOutputStream Web patch no longer matches pinned Arc")

PATH.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Applied Web bounded-input FastDeflaterOutputStream patch")
