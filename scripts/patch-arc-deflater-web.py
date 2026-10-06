#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "util" / "io" / "FastDeflaterOutputStream.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Arc FastDeflaterOutputStream source: {PATH}")

text = PATH.read_text(encoding="utf-8")

old = '''package arc.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

public class FastDeflaterOutputStream extends DeflaterOutputStream{
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

new = '''package arc.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class FastDeflaterOutputStream extends DeflaterOutputStream{
    private static final int webInputChunk = 8192;
    private final byte[] tmp = {0};

    public FastDeflaterOutputStream(OutputStream outputStream){
        super(outputStream, new Deflater(), 65536);
    }

    @Override
    public void write(int value) throws IOException{
        tmp[0] = (byte)(value & 255);
        write(tmp, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException{
        int end = offset + length;
        while(offset < end){
            int chunk = Math.min(webInputChunk, end - offset);
            long target = def.getBytesRead() + chunk;
            def.setInput(bytes, offset, chunk);

            while(def.getBytesRead() < target){
                if(!pump() && def.getBytesRead() < target) throw new IOException();
            }

            def.setInput(buf, 0, 0);
            offset += chunk;
        }
    }

    private boolean pump() throws IOException{
        long beforeIn = def.getBytesRead();
        long beforeOut = def.getBytesWritten();
        try{
            int count = def.deflate(buf, 0, buf.length);
            if(count > 0) out.write(buf, 0, count);
        }catch(RuntimeException error){
            int produced = (int)(def.getBytesWritten() - beforeOut);
            if(produced > 0) out.write(buf, 0, produced);
            String message = error.getMessage();
            if(message == null || !message.endsWith("-5")) throw error;
        }
        return def.getBytesRead() != beforeIn || def.getBytesWritten() != beforeOut;
    }

    @Override
    public void finish() throws IOException{
        if(def.finished()) return;
        def.finish();
        int stalls = 0;
        while(!def.finished()){
            if(pump()) stalls = 0;
            else if(++stalls >= 2) throw new IOException();
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
