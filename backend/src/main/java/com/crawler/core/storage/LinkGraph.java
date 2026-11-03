package com.crawler.core.storage;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class LinkGraph {

    // Adjacency List: Source URI -> Set of Target URIs
    private final Map<String, Set<String>> adjacencyList = new ConcurrentHashMap<>();

    public void addEdge(URI source, URI destination) {
        String src = source.normalize().toString();
        String dst = destination.normalize().toString();

        adjacencyList.computeIfAbsent(src, k -> new CopyOnWriteArraySet<>()).add(dst);
        // Ensure destination vertex exists in adjacency keys
        adjacencyList.computeIfAbsent(dst, k -> new CopyOnWriteArraySet<>());
    }

    public Set<String> getOutboundLinks(String uri) {
        return adjacencyList.getOrDefault(uri, Collections.emptySet());
    }

    public Set<String> getNodes() {
        return Collections.unmodifiableSet(adjacencyList.keySet());
    }

    public int getNodeCount() {
        return adjacencyList.size();
    }

    public int getEdgeCount() {
        return adjacencyList.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Detects cycles across the graph using Depth-First Search with 3-color marking.
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     */
    public boolean hasCycle() {
        Map<String, VertexColor> colors = new HashMap<>();
        for (String node : adjacencyList.keySet()) {
            colors.put(node, VertexColor.WHITE);
        }

        for (String node : adjacencyList.keySet()) {
            if (colors.get(node) == VertexColor.WHITE) {
                if (dfsHasCycle(node, colors)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsHasCycle(String current, Map<String, VertexColor> colors) {
        colors.put(current, VertexColor.GRAY);

        for (String neighbor : adjacencyList.getOrDefault(current, Collections.emptySet())) {
            VertexColor neighborColor = colors.get(neighbor);
            if (neighborColor == VertexColor.GRAY) {
                // Back-edge detected: cycle exists
                return true;
            }
            if (neighborColor == VertexColor.WHITE && dfsHasCycle(neighbor, colors)) {
                return true;
            }
        }

        colors.put(current, VertexColor.BLACK);
        return false;
    }

    public void clear() {
        adjacencyList.clear();
    }

    private enum VertexColor {
        WHITE, // Unvisited
        GRAY,  // Currently in recursion stack
        BLACK  // Fully processed
    }
}