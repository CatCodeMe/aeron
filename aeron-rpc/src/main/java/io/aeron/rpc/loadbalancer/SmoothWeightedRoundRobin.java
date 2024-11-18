package io.aeron.rpc.loadbalancer;

import io.aeron.rpc.ServiceEndpoint;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Smooth Weighted Round-Robin (SWRR) implementation.
 * This algorithm provides better distribution of requests across endpoints
 * compared to simple weighted round-robin.
 */
public class SmoothWeightedRoundRobin {
    private final Map<String, Map<ServiceEndpoint, WeightedNode>> serviceNodes;

    public SmoothWeightedRoundRobin() {
        this.serviceNodes = new ConcurrentHashMap<>();
    }

    public synchronized void updateEndpoints(String serviceName, List<ServiceEndpoint> endpoints) {
        Map<ServiceEndpoint, WeightedNode> nodes = serviceNodes.computeIfAbsent(
            serviceName, k -> new HashMap<>());

        // Remove old endpoints
        nodes.keySet().removeIf(endpoint -> !endpoints.contains(endpoint));

        // Add or update endpoints
        for (ServiceEndpoint endpoint : endpoints) {
            nodes.computeIfAbsent(endpoint, WeightedNode::new);
        }
    }

    public synchronized ServiceEndpoint select(String serviceName, List<ServiceEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return null;
        }

        // Update endpoints if needed
        updateEndpoints(serviceName, endpoints);

        Map<ServiceEndpoint, WeightedNode> nodes = serviceNodes.get(serviceName);
        if (nodes == null || nodes.isEmpty()) {
            return endpoints.get(0);
        }

        WeightedNode selected = null;
        int totalWeight = 0;

        // Calculate total weight and find node with maximum current weight
        for (WeightedNode node : nodes.values()) {
            totalWeight += node.effectiveWeight;
            node.currentWeight += node.effectiveWeight;

            if (selected == null || node.currentWeight > selected.currentWeight) {
                selected = node;
            }
        }

        if (selected == null) {
            return endpoints.get(0);
        }

        // Adjust the current weight
        selected.currentWeight -= totalWeight;

        return selected.endpoint;
    }

    private static class WeightedNode {
        private final ServiceEndpoint endpoint;
        private int currentWeight;
        private int effectiveWeight;

        public WeightedNode(ServiceEndpoint endpoint) {
            this.endpoint = endpoint;
            this.effectiveWeight = endpoint.getWeight();
            this.currentWeight = 0;
        }

        public void adjustWeight(int delta) {
            this.effectiveWeight += delta;
            if (this.effectiveWeight < 0) {
                this.effectiveWeight = 0;
            }
        }
    }

    public void clear() {
        serviceNodes.clear();
    }

    // For testing and monitoring
    public Map<String, Map<ServiceEndpoint, Integer>> getCurrentWeights() {
        Map<String, Map<ServiceEndpoint, Integer>> weights = new HashMap<>();
        serviceNodes.forEach((service, nodes) -> {
            Map<ServiceEndpoint, Integer> serviceWeights = new HashMap<>();
            nodes.forEach((endpoint, node) -> 
                serviceWeights.put(endpoint, node.currentWeight));
            weights.put(service, serviceWeights);
        });
        return weights;
    }
}
