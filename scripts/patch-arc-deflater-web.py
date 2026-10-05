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

new = '''package arc.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

public class FastDeflaterOutputStream extends OutputStream{
    private static final int webInputChunk = 8192;
    private final OutputStream out;
    private final Deflater def = new Deflater();
    private final byte[] buffer = new byte[65536];
    private final byte[] tmp = {0};
    private boolean done;

    public FastDeflaterOutputStream(OutputStream outputStream){
        if(outputStream == null) throw new NullPointerException();
        out = outputStream;
    }

    @Override
    public void write(int value) throws IOException{
        tmp[0] = (byte)(value & 255);
        write(tmp, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException{
        if(done) throw new IOException();
        if(bytes == null) throw new NullPointerException();
        if(offset < 0 || length < 0 || offset > bytes.length - length) throw new IndexOutOfBoundsException();

        int end = offset + length;
        while(offset < end){
            int chunk = Math.min(webInputChunk, end - offset);
            long target = def.getBytesRead() + chunk;
            def.setInput(bytes, offset, chunk);

            while(def.getBytesRead() < target){
                if(!pump() && def.getBytesRead() < target){
                    throw new IOException("Web deflater stalled");
                }
            }
            offset += chunk;
        }
    }

    private boolean pump() throws IOException{
        long beforeIn = def.getBytesRead();
        long beforeOut = def.getBytesWritten();
        try{
            int count = def.deflate(buffer);
            if(count > 0) out.write(buffer, 0, count);
        }catch(RuntimeException error){
            long produced = def.getBytesWritten() - beforeOut;
            if(produced > 0) out.write(buffer, 0, (int)produced);
            if(!isBufferError(error)) throw error;
        }
        return def.getBytesRead() != beforeIn || def.getBytesWritten() != beforeOut;
    }

    private static boolean isBufferError(RuntimeException error){
        String message = error.getMessage();
        return message != null && message.endsWith("-5");
    }

    @Override
    public void flush() throws IOException{
        out.flush();
    }

    public void finish() throws IOException{
        if(done) return;
        def.finish();
        int stalls = 0;
        while(!def.finished()){
            if(pump()){
                stalls = 0;
            }else if(++stalls >= 2){
                throw new IOException("Web deflater finish stalled");
            }
        }
        done = true;
    }

    @Override
    public void close() throws IOException{
        try{
            finish();
        }finally{
            def.end();
            out.close();
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
