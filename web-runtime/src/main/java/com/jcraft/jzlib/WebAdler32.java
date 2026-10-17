/* -*-mode:java; c-basic-offset:2; -*- */
/*
Copyright (c) 2000-2011 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright 
     notice, this list of conditions and the following disclaimer in 
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */

package com.jcraft.jzlib;

/**
 * TeaVM-specific implementation of JZlib's Adler32, activated via SubstitutionPolicy, with the same public API.
 * JZlib originally accumulates every byte into two Java long fields; TeaVM's
 * Long_add lowers to JS BigInt in the main thread during PNG/asset inflation.
 * A 2,048-byte chunk keeps BOTH signed int sums safely below Integer.MAX_VALUE,
 * so only the one-off getValue/combine calls need 64-bit arithmetic.
 */
public class WebAdler32 implements Checksum{
    private static final int base = 65521;
    private static final int chunkLimit = 2048;

    private int s1 = 1, s2;

    @Override
    public void reset(long init){
        s1 = (int)init & 65535;
        s2 = (int)(init >>> 16) & 65535;
    }

    @Override
    public void reset(){
        s1 = 1;
        s2 = 0;
    }

    @Override
    public long getValue(){
        return ((long)s2 << 16) | s1;
    }

    @Override
    public void update(byte[] buf, int index, int len){
        int a = s1, b = s2;
        while(len > 0){
            int count = Math.min(len, chunkLimit);
            len -= count;
            for(int i = 0; i < count; i++){
                a += buf[index++] & 255;
                b += a;
            }
            a %= base;
            b %= base;
        }
        s1 = a;
        s2 = b;
    }

    @Override
    public WebAdler32 copy(){
        WebAdler32 out = new WebAdler32();
        out.s1 = s1;
        out.s2 = s2;
        return out;
    }

    // Infrequent stream-concatenation helper kept compatible with upstream JZlib.
    static long combine(long adler1, long adler2, long len2){
        final long b = base;
        long rem = len2 % b;
        long sum1 = adler1 & 65535L;
        long sum2 = rem * sum1 % b;
        sum1 += (adler2 & 65535L) + b - 1;
        sum2 += ((adler1 >>> 16) & 65535L) + ((adler2 >>> 16) & 65535L) + b - rem;
        if(sum1 >= b) sum1 -= b;
        if(sum1 >= b) sum1 -= b;
        if(sum2 >= (b << 1)) sum2 -= b << 1;
        if(sum2 >= b) sum2 -= b;
        return sum1 | sum2 << 16;
    }
}
