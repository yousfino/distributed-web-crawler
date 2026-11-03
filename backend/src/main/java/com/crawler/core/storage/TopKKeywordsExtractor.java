package com.crawler.core.storage;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TopKKeywordsExtractor {

    public record KeywordRank(String keyword, int frequency) {}

    /**
     * Extracts top K words across a map of aggregated frequencies using a Min-Heap.
     * Time Complexity: O(N log K), where N is total distinct words.
     * Space Complexity: O(K) for the heap.
     */
    public List<KeywordRank> extractTopK(Map<String, Integer> globalFrequencies, int k) {
        if (globalFrequencies == null || globalFrequencies.isEmpty() || k <= 0) {
            return Collections.emptyList();
        }

        // Min-Heap ordered by frequency ascending
        PriorityQueue<KeywordRank> minHeap = new PriorityQueue<>(
                Comparator.comparingInt(KeywordRank::frequency)
                        .thenComparing(KeywordRank::keyword, Comparator.reverseOrder())
        );

        for (Map.Entry<String, Integer> entry : globalFrequencies.entrySet()) {
            KeywordRank candidate = new KeywordRank(entry.getKey(), entry.getValue());
            if (minHeap.size() < k) {
                minHeap.offer(candidate);
            } else if (candidate.frequency() > minHeap.peek().frequency()) {
                minHeap.poll();
                minHeap.offer(candidate);
            }
        }

        // Extract and sort descending
        List<KeywordRank> result = new ArrayList<>(minHeap);
        result.sort(Comparator.comparingInt(KeywordRank::frequency).reversed());
        return result;
    }
}