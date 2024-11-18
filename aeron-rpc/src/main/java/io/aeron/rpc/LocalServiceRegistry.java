package io.aeron.rpc;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local in-memory implementation of ServiceRegistry.
 */
public class LocalServiceRegistry implements ServiceRegistry {
    private final Map<String, Set<ServiceEndpoint>> services = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    @Override
    public void register(String serviceName, ServiceEndpoint endpoint) {
        if (!running) {
            throw new IllegalStateException("Service registry is not running");
        }
        services.computeIfAbsent(serviceName, k -> ConcurrentHashMap.newKeySet())
                .add(endpoint);
    }

    @Override
    public void deregister(String serviceName, ServiceEndpoint endpoint) {
        if (!running) {
            throw new IllegalStateException("Service registry is not running");
        }
        Set<ServiceEndpoint> endpoints = services.get(serviceName);
        if (endpoints != null) {
            endpoints.remove(endpoint);
            if (endpoints.isEmpty()) {
                services.remove(serviceName);
            }
        }
    }

    @Override
    public Set<ServiceEndpoint> findEndpoints(String serviceName) {
        if (!running) {
            throw new IllegalStateException("Service registry is not running");
        }
        return services.getOrDefault(serviceName, Collections.emptySet());
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        services.clear();
    }

    /**
     * Get all registered services and their endpoints.
     *
     * @return map of service names to their endpoints
     */
    public Map<String, Set<ServiceEndpoint>> getAllServices() {
        if (!running) {
            throw new IllegalStateException("Service registry is not running");
        }
        return new HashMap<>(services);
    }
}
