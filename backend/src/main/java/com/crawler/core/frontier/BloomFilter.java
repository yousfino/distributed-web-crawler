package com.crawler.core.frontier;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class BloomFilter {

    private final AtomicIntegerArray bitArray;
    private final int bitSize;
    private final int numHashFunctions;

    public BloomFilter(int expectedInsertions, double fpp) {
        if (expectedInsertions <= 0 || fpp <= 0.0 || fpp >= 1.0) {
            throw new IllegalArgumentException("Invalid Bloom filter parameters");
        }

        // Optimal bit size m = - (n * ln(p)) / (ln(2)^2)
        long m = (long) (-expectedInsertions * Math.log(fpp) / (Math.log(2) * Math.log(2)));
        // Clamp to positive range
        this.bitSize = (int) Math.min(m, Integer.MAX_VALUE - 64);

        // Optimal hash functions k = (m / n) * ln(2)
        this.numHashFunctions = Math.max(1, (int) Math.round(((double) bitSize / expectedInsertions) * Math.log(2)));

        // AtomicIntegerArray (each int holds 32 bits) for lock-free thread safety
        int arrayLength = (bitSize + 31) / 32;
        this.bitArray = new AtomicIntegerArray(arrayLength);
    }

    /**
     * Adds an element to the filter.
     * Uses Kirsch-Mitzenmacher: g_i(x) = h1(x) + i * h2(x)
     */
    public void add(String element) {
        long[] hashes = hash64(element);
        long h1 = hashes[0];
        long h2 = hashes[1];

        for (int i = 0; i < numHashFunctions; i++) {
            long combined = h1 + (i * h2);
            int bitIndex = (int) Math.floorMod(combined, (long) bitSize);
            setBit(bitIndex);
        }
    }

    /**
     * Tests if an element might be in the set.
     * @return false = definitely NOT present; true = possibly present.
     */
    public boolean mightContain(String element) {
        long[] hashes = hash64(element);
        long h1 = hashes[0];
        long h2 = hashes[1];

        for (int i = 0; i < numHashFunctions; i++) {
            long combined = h1 + (i * h2);
            int bitIndex = (int) Math.floorMod(combined, (long) bitSize);
            if (!getBit(bitIndex)) {
                return false;
            }
        }
        return true;
    }

    private void setBit(int bitIndex) {
        int wordIndex = bitIndex / 32;
        int bitOffset = bitIndex % 32;
        int mask = 1 << bitOffset;

        int current;
        int updated;
        do {
            current = bitArray.get(wordIndex);
            updated = current | mask;
            if (current == updated) {
                return;
            }
        } while (!bitArray.compareAndSet(wordIndex, current, updated));
    }

    private boolean getBit(int bitIndex) {
        int wordIndex = bitIndex / 32;
        int bitOffset = bitIndex % 32;
        int mask = 1 << bitOffset;
        return (bitArray.get(wordIndex) & mask) != 0;
    }

    public void clear() {
        for (int i = 0; i < bitArray.length(); i++) {
            bitArray.set(i, 0);
        }
    }

    public int getBitSize() {
        return bitSize;
    }

    public int getNumHashFunctions() {
        return numHashFunctions;
    }

    /**
     * Minimal 64-bit MurmurHash3 implementation generating two 64-bit hashes.
     */
    private static long[] hash64(String input) {
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        long h1 = 0x12345678L;
        long h2 = 0x87654321L;

        for (byte b : data) {
            h1 = 31 * h1 + b;
            h2 = 17 * h2 + b;
        }

        // Avalanche step
        h1 ^= (h1 >>> 33);
        h1 *= 0xff51afd7ed558ccdL;
        h1 ^= (h1 >>> 33);

        h2 ^= (h2 >>> 33);
        h2 *= 0xc4ceb9fe1a85ec53L;
        h2 ^= (h2 >>> 33);

        return new long[]{h1, h2};
    }
}