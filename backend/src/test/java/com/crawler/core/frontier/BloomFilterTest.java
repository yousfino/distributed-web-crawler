package com.crawler.core.frontier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BloomFilterTest {

    @Test
    void shouldNeverProduceFalseNegatives() {
        BloomFilter filter = new BloomFilter(1000, 0.01);

        for (int i = 0; i < 500; i++) {
            filter.add("https://example.com/page/" + i);
        }

        // Must guarantee 100% true recall for inserted elements (Zero false negatives)
        for (int i = 0; i < 500; i++) {
            assertTrue(
                filter.mightContain("https://example.com/page/" + i),
                "Bloom filter returned false for an inserted element!"
            );
        }
    }

    @Test
    void shouldMaintainConfiguredFalsePositiveRateThreshold() {
        int capacity = 5000;
        double targetFpp = 0.05; // 5% tolerance
        BloomFilter filter = new BloomFilter(capacity, targetFpp);

        for (int i = 0; i < capacity; i++) {
            filter.add("https://domain.org/path/" + i);
        }

        int falsePositives = 0;
        int testCount = 5000;
        for (int i = 0; i < testCount; i++) {
            if (filter.mightContain("https://other-domain.net/absent/" + i)) {
                falsePositives++;
            }
        }

        double observedFpp = (double) falsePositives / testCount;
        // Observed false positive rate should be reasonably bounded near target FPP
        assertTrue(
            observedFpp <= 0.08,
            "Observed FPP exceeded acceptable bounds: " + observedFpp
        );
    }
}