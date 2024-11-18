package io.aeron.rpc.loadbalancer;

import io.aeron.rpc.ServiceEndpoint;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load balancer for RPC services.
 */
public class LoadBalancer {
    public enum Strategy {
        ROUND_ROBIN,
        RANDOM,
        WEIGHTED,
        SMOOTH_WEIGHTED,
        LEAST_CONNECTIONS
    }

    private final Strategy strategy;
    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts;
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters;
    private final SmoothWeightedRoundRobin smoothWeightedRoundRobin;

    public LoadBalancer(Strategy strategy) {
        this.strategy = strategy;
        this.connectionCounts = new ConcurrentHashMap<>();
        this.roundRobinCounters = new ConcurrentHashMap<>();
        this.smoothWeightedRoundRobin = new SmoothWeightedRoundRobin();
    }

    public ServiceEndpoint select(String serviceName, List<ServiceEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return null;
        }

        switch (strategy) {
            case ROUND_ROBIN:
                return selectRoundRobin(serviceName, endpoints);
            case RANDOM:
                return selectRandom(endpoints);
            case WEIGHTED:
                return selectWeighted(endpoints);
            case SMOOTH_WEIGHTED:
                return smoothWeightedRoundRobin.select(serviceName, endpoints);
            case LEAST_CONNECTIONS:
                return selectLeastConnections(endpoints);
            default:
                throw new IllegalStateException("Unknown load balancing strategy: " + strategy);
        }
    }

    private ServiceEndpoint selectRoundRobin(String serviceName, List<ServiceEndpoint> endpoints) {
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(serviceName, 
            k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement() % endpoints.size());
        return endpoints.get(index);
    }

    private ServiceEndpoint selectRandom(List<ServiceEndpoint> endpoints) {
        int index = ThreadLocalRandom.current().nextInt(endpoints.size());
        return endpoints.get(index);
    }

    private ServiceEndpoint selectWeighted(List<ServiceEndpoint> endpoints) {
        int totalWeight = endpoints.stream()
            .mapToInt(ServiceEndpoint::getWeight)
            .sum();
        
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int weightSum = 0;
        
        for (ServiceEndpoint endpoint : endpoints) {
            weightSum += endpoint.getWeight();
            if (random < weightSum) {
                return endpoint;
            }
        }
        
        return endpoints.get(endpoints.size() - 1);
    }

    private ServiceEndpoint selectLeastConnections(List<ServiceEndpoint> endpoints) {
        return endpoints.stream()
            .min((a, b) -> {
                int aCount = connectionCounts.computeIfAbsent(a.getId(), k -> new AtomicInteger(0)).get();
                int bCount = connectionCounts.computeIfAbsent(b.getId(), k -> new AtomicInteger(0)).get();
                return Integer.compare(aCount, bCount);
            })
            .orElse(endpoints.get(0));
    }

    public void incrementConnections(ServiceEndpoint endpoint) {
        connectionCounts.computeIfAbsent(endpoint.getId(), k -> new AtomicInteger(0))
            .incrementAndGet();
    }

    public void decrementConnections(ServiceEndpoint endpoint) {
        connectionCounts.computeIfAbsent(endpoint.getId(), k -> new AtomicInteger(0))
            .decrementAndGet();
    }

    public void clear() {
        connectionCounts.clear();
        roundRobinCounters.clear();
        smoothWeightedRoundRobin.clear();
    }
}

class SmoothWeightedRoundRobin {
    private final ConcurrentHashMap<String, AtomicInteger> currentWeights;

    public SmoothWeightedRoundRobin() {
        this.currentWeights = new ConcurrentHashMap<>();
    }

    public ServiceEndpoint select(String serviceName, List<ServiceEndpoint> endpoints) {
        AtomicInteger currentWeight = currentWeights.computeIfAbsent(serviceName, 
            k -> new AtomicInteger(0));
        int totalWeight = endpoints.stream()
            .mapToInt(ServiceEndpoint::getWeight)
            .sum();
        
        int index = 0;
        int maxWeight = 0;
        for (int i = 0; i < endpoints.size(); i++) {
            int weight = endpoints.get(i).getWeight();
            int current = currentWeight.get();
            if (current < weight) {
                currentWeight.set(current + 1);
                return endpoints.get(i);
            } else if (current == weight) {
                if (weight > maxWeight) {
                    maxWeight = weight;
                    index = i;
                }
            }
        }
        
        currentWeight.set(0);
        return endpoints.get(index);
    }

    public void clear() {
        currentWeights.clear();
    }
}
