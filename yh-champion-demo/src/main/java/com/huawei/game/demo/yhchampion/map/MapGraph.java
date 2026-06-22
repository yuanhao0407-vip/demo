package com.huawei.game.demo.yhchampion.map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.PriorityQueue;
import java.util.Set;

public final class MapGraph {
    private static final int BLOCKED_NODE_PENALTY = 10_000;
    private static final Map<String, Integer> ROUTE_COST_MULTIPLIER_BPS = Map.of(
            "ROAD", 135,
            "OFFICIAL_ROAD", 135,
            "WATER", 120,
            "MOUNTAIN", 187,
            "BRANCH", 160,
            "PALACE_ROAD", 135);

    private final Map<String, List<Edge>> adjacency = new HashMap<>();
    private final Set<String> blockedNodes = new HashSet<>();

    public static MapGraph fromStart(JSONObject startData) {
        MapGraph graph = new MapGraph();
        JSONArray edges = null;
        JSONObject map = startData == null ? null : startData.getJSONObject("map");
        if (map != null) {
            edges = map.getJSONArray("edges");
        }
        if (edges == null || edges.isEmpty()) {
            edges = startData == null ? null : startData.getJSONArray("edges");
        }
        if (edges == null) {
            edges = new JSONArray();
        }
        graph.addEdges(edges);
        return graph;
    }

    public void addEdges(JSONArray edges) {
        if (edges == null) {
            return;
        }
        for (int i = 0; i < edges.size(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            if (edge == null) {
                continue;
            }
            String from = firstText(edge, "fromNode", "fromNodeId");
            String to = firstText(edge, "toNode", "toNodeId");
            if (from.isBlank() || to.isBlank()) {
                continue;
            }
            int distance = Math.max(1, edge.getIntValue("distance"));
            String routeType = edge.getString("routeType");
            addDirected(from, to, distance, routeType);
            if (!edge.containsKey("bidirectional") || edge.getBooleanValue("bidirectional")) {
                addDirected(to, from, distance, routeType);
            }
        }
    }

    public void updateBlockedNodes(JSONArray nodes) {
        if (nodes == null) {
            return;
        }
        blockedNodes.clear();
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node != null && node.getBooleanValue("hasObstacle")) {
                String nodeId = node.getString("nodeId");
                if (nodeId != null && !nodeId.isBlank()) {
                    blockedNodes.add(nodeId);
                }
            }
        }
    }

    public Optional<String> nextHop(String currentNodeId, String targetNodeId) {
        return nextHop(currentNodeId, targetNodeId, Set.of());
    }

    public Optional<String> nextHop(String currentNodeId, String targetNodeId, Set<String> penalizedRouteTypes) {
        return nextHop(currentNodeId, targetNodeId, penalizedRouteTypes, false);
    }

    public Optional<String> nextHopAllowingBlocked(String currentNodeId, String targetNodeId,
                                                   Set<String> penalizedRouteTypes) {
        return nextHop(currentNodeId, targetNodeId, penalizedRouteTypes, true);
    }

    public boolean isBlocked(String nodeId) {
        return nodeId != null && blockedNodes.contains(nodeId);
    }

    public boolean hasEdge(String fromNodeId, String toNodeId) {
        if (fromNodeId == null || toNodeId == null || fromNodeId.isBlank() || toNodeId.isBlank()) {
            return false;
        }
        return adjacency.getOrDefault(fromNodeId, List.of()).stream()
                .anyMatch(edge -> toNodeId.equals(edge.to()));
    }

    private Optional<String> nextHop(String currentNodeId, String targetNodeId, Set<String> penalizedRouteTypes,
                                     boolean allowBlocked) {
        if (currentNodeId == null || targetNodeId == null || currentNodeId.isBlank() || targetNodeId.isBlank()) {
            return Optional.empty();
        }
        if (currentNodeId.equals(targetNodeId)) {
            return Optional.empty();
        }

        PriorityQueue<PathNode> queue = new PriorityQueue<>(Comparator.comparingInt(PathNode::distance));
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        distances.put(currentNodeId, 0);
        queue.add(new PathNode(currentNodeId, 0));

        while (!queue.isEmpty()) {
            PathNode current = queue.remove();
            if (current.distance() > distances.getOrDefault(current.nodeId(), Integer.MAX_VALUE)) {
                continue;
            }
            if (current.nodeId().equals(targetNodeId)) {
                return rebuildNextHop(currentNodeId, targetNodeId, previous);
            }
            for (Edge edge : adjacency.getOrDefault(current.nodeId(), List.of())) {
                String next = edge.to();
                if (blockedNodes.contains(next) && !next.equals(targetNodeId)) {
                    if (!allowBlocked) {
                        continue;
                    }
                }
                int blockedPenalty = allowBlocked && blockedNodes.contains(next) ? BLOCKED_NODE_PENALTY : 0;
                int candidate = current.distance() + effectiveDistance(edge, penalizedRouteTypes) + blockedPenalty;
                if (candidate < distances.getOrDefault(next, Integer.MAX_VALUE)) {
                    distances.put(next, candidate);
                    previous.put(next, current.nodeId());
                    queue.add(new PathNode(next, candidate));
                }
            }
        }
        return Optional.empty();
    }

    public OptionalInt distance(String currentNodeId, String targetNodeId, Set<String> penalizedRouteTypes) {
        if (currentNodeId == null || targetNodeId == null || currentNodeId.isBlank() || targetNodeId.isBlank()) {
            return OptionalInt.empty();
        }
        if (currentNodeId.equals(targetNodeId)) {
            return OptionalInt.of(0);
        }

        PriorityQueue<PathNode> queue = new PriorityQueue<>(Comparator.comparingInt(PathNode::distance));
        Map<String, Integer> distances = new HashMap<>();
        distances.put(currentNodeId, 0);
        queue.add(new PathNode(currentNodeId, 0));

        while (!queue.isEmpty()) {
            PathNode current = queue.remove();
            if (current.distance() > distances.getOrDefault(current.nodeId(), Integer.MAX_VALUE)) {
                continue;
            }
            if (current.nodeId().equals(targetNodeId)) {
                return OptionalInt.of(current.distance());
            }
            for (Edge edge : adjacency.getOrDefault(current.nodeId(), List.of())) {
                String next = edge.to();
                if (blockedNodes.contains(next) && !next.equals(targetNodeId)) {
                    continue;
                }
                int candidate = current.distance() + effectiveDistance(edge, penalizedRouteTypes);
                if (candidate < distances.getOrDefault(next, Integer.MAX_VALUE)) {
                    distances.put(next, candidate);
                    queue.add(new PathNode(next, candidate));
                }
            }
        }
        return OptionalInt.empty();
    }

    private void addDirected(String from, String to, int distance, String routeType) {
        adjacency.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new Edge(to, distance, routeType));
    }

    private int effectiveDistance(Edge edge, Set<String> penalizedRouteTypes) {
        int routeCost = routeTickCost(edge);
        if (edge.routeType() != null && penalizedRouteTypes != null && penalizedRouteTypes.contains(edge.routeType())) {
            return routeCost * 2;
        }
        return routeCost;
    }

    private int routeTickCost(Edge edge) {
        String routeType = edge.routeType() == null ? "" : edge.routeType().trim().toUpperCase();
        int multiplierBps = ROUTE_COST_MULTIPLIER_BPS.getOrDefault(routeType, 100);
        // Mirror the server's routeCost multipliers so Dijkstra follows real tick tempo,
        // not just map logical distance. This matters for short-looking BRANCH edges.
        return Math.max(1, (edge.distance() * multiplierBps + 99) / 100);
    }

    private Optional<String> rebuildNextHop(String currentNodeId, String targetNodeId, Map<String, String> previous) {
        String cursor = targetNodeId;
        String parent = previous.get(cursor);
        while (parent != null && !parent.equals(currentNodeId)) {
            cursor = parent;
            parent = previous.get(cursor);
        }
        return parent == null ? Optional.empty() : Optional.of(cursor);
    }

    private String firstText(JSONObject edge, String firstKey, String secondKey) {
        String first = edge.getString(firstKey);
        if (first != null && !first.isBlank()) {
            return first;
        }
        String second = edge.getString(secondKey);
        return second == null ? "" : second;
    }

    private record Edge(String to, int distance, String routeType) {
    }

    private record PathNode(String nodeId, int distance) {
    }
}
