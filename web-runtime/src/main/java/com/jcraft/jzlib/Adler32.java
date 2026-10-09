/*
 * Derived from JCraft JZlib's Adler32 implementation.
 * Copyright (c) 2000-2011 ymnk, JCraft, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. The names of the authors may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY OR CONSEQUENTIAL DAMAGES ARISING FROM ITS USE.
 */
package com.jcraft.jzlib;

/**
 * TeaVM replacement for JZlib's Adler32, with the same public API.
 * JZlib originally accumulates every byte into two Java long fields; TeaVM's
 * Long_add lowers to JS BigInt in the main thread during PNG/asset inflation.
 * A 2,048-byte chunk keeps BOTH signed int sums safely below Integer.MAX_VALUE,
 * so only the one-off getValue/combine calls need 64-bit arithmetic.
 */
public class Adler32 implements Checksum{
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
    public Adler32 copy(){
        Adler32 out = new Adler32();
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
