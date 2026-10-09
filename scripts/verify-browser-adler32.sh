#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT_DIR/web-runtime/src/main/java/com/jcraft/jzlib/Adler32.java"
BUNDLE="$ROOT_DIR/web-runtime/build/web/mindustry.js"
LICENSE="$ROOT_DIR/web-runtime/build/web/licenses/JZlib-BSD-3-Clause.txt"
test -s "$SOURCE"
test -s "$BUNDLE"
test -s "$LICENSE"

# Compile the implementation against JZlib's unmodified interface and compare
# every chunk size against the JVM's known-good java.util.zip.Adler32.
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
mkdir -p "$dir/com/jcraft/jzlib" "$dir/classes"
cat > "$dir/com/jcraft/jzlib/Checksum.java" <<'EOF'
package com.jcraft.jzlib;
interface Checksum {
    void update(byte[] buf, int index, int len);
    void reset();
    void reset(long init);
    long getValue();
    Checksum copy();
}
EOF
cat > "$dir/com/jcraft/jzlib/Adler32Regression.java" <<'EOF'
package com.jcraft.jzlib;

import java.util.Random;
public class Adler32Regression{
    private static void assertEqual(long a, long b, String label){
        if(a != b) throw new AssertionError(label + ": 0x"
            + Long.toHexString(a) + " != 0x" + Long.toHexString(b));
    }

    private static void check(int count, int mode){
        byte[] data = new byte[count];
        new Random(0xADEF32L + count).nextBytes(data);
        Adler32 actual = new Adler32();
        java.util.zip.Adler32 reference = new java.util.zip.Adler32();
        int pos = 0;
        while(pos < count){
            int batch = mode == 0 ? count - pos
                : mode == 1 ? 1
                : mode == 2 ? 137
                : mode == 3 ? 2048
                : mode == 4 ? 5552 : 8191;
            batch = Math.min(batch, count - pos);
            actual.update(data, pos, batch);
            reference.update(data, pos, batch);
            pos += batch;
        }
        assertEqual(actual.getValue(), reference.getValue(), "size=" + count + " mode=" + mode);
        Adler32 copy = actual.copy();
        assertEqual(copy.getValue(), reference.getValue(), "copy");
        actual.reset(reference.getValue());
        assertEqual(actual.getValue(), reference.getValue(), "reset(long)");
        actual.reset();
        assertEqual(actual.getValue(), 1L, "reset()");
        actual.update(data, 0, count);
        assertEqual(actual.getValue(), reference.getValue(), "reset+update");
    }

    private static long checksum(byte[] data, int from, int length){
        Adler32 a = new Adler32();
        a.update(data, from, length);
        return a.getValue();
    }

    public static void main(String[] args){
        int[] lengths = {0, 1, 2, 31, 127, 137, 2047, 2048, 2049, 5552,
            8191, 32768, 65536, 262145, 1048576};
        int checks = 0;
        for(int len : lengths){
            for(int mode = 0; mode < 6; mode++){
                check(len, mode);
                checks++;
            }
        }
        byte[] combined = new byte[32768];
        new Random(19645).nextBytes(combined);
        for(int pos : new int[]{0, 1, 2048, 16000, 32768}){
            long joined = Adler32.combine(
                checksum(combined, 0, pos),
                checksum(combined, pos, combined.length - pos),
                combined.length - pos);
            assertEqual(joined, checksum(combined, 0, combined.length), "combine " + pos);
            checks++;
        }
        System.out.println("Optimized Adler32/JDK Adler32 regression: " + checks + " cases PASS");
    }
}
EOF
javac -d "$dir/classes" "$dir/com/jcraft/jzlib/Checksum.java" "$SOURCE" "$dir/com/jcraft/jzlib/Adler32Regression.java"
java -cp "$dir/classes" com.jcraft.jzlib.Adler32Regression

# The JVM test alone cannot establish which duplicate class TeaVM linked.
# Inspect the ACTUAL emitted function and reject the old BigInt per-byte loop.
python3 - "$BUNDLE" <<'PY'
from pathlib import Path
import re
import sys
js = Path(sys.argv[1]).read_text(encoding="utf-8")
match = re.search(r"\bcjj_Adler32_update\s*=\s*\([^)]*\)\s*=>\s*\{(.*?)\n\};", js, re.S)
if match is None:
    raise SystemExit("TeaVM Adler32.update not found in generated JS")
body = match.group(1)
if re.search(r"\bLong_(?:add|rem|fromInt|mul|div|sub)\b", body):
    raise SystemExit("TeaVM still generated BigInt arithmetic in Adler32.update")
if not re.search(r"2048|chunkLimit", body):
    raise SystemExit("TeaVM did not select the optimized 2048-byte Adler32 loop")
print("TeaVM emitted Adler32.update without per-byte BigInt operations: PASS")
PY
