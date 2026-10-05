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
    private static final byte[] empty = {};
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
                long beforeIn = def.getBytesRead();
                long beforeOut = def.getBytesWritten();
                try{
                    int count = def.deflate(buf, 0, buf.length);
                    if(count > 0) out.write(buf, 0, count);
                }catch(RuntimeException error){
                    long produced = def.getBytesWritten() - beforeOut;
                    if(produced > 0) out.write(buf, 0, (int)produced);

                    // TeaVM 0.15 TDeflater throws JZlib Z_BUF_ERROR (-5) before it
                    // updates its private inRead counter. JZlib total_in, exposed by
                    // getBytesRead(), may already have consumed this entire chunk.
                    // Treat that terminal no-progress call as success when total_in
                    // reached our target; only fail if input is still outstanding.
                    String message = error.getMessage();
                    long afterIn = def.getBytesRead();
                    if(message == null || !message.endsWith("-5")
                    || (afterIn < target && afterIn == beforeIn && produced == 0)){
                        throw error;
                    }
                }
            }

            // TeaVM 0.15 updates TDeflater.inRead only on Z_OK; a non-fatal JZlib
            // Z_BUF_ERROR can consume the final input before throwing. Resetting to an
            // empty input synchronizes needsInput() after total_in reached the target.
            def.setInput(empty, 0, 0);
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
