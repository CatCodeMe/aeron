package io.aeron.rpc.monitoring;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for collecting and managing RPC metrics.
 */
public class MonitoringService {
    private final Map<String, RpcMetrics> serviceMetrics = new ConcurrentHashMap<>();
    private final RpcMetrics globalMetrics = new RpcMetrics();

    /**
     * Record a request for a service.
     *
     * @param serviceName name of the service
     */
    public void recordRequest(String serviceName) {
        globalMetrics.recordRequest();
        getOrCreateMetrics(serviceName).recordRequest();
    }

    /**
     * Record a response for a service.
     *
     * @param serviceName name of the service
     * @param bytes number of bytes in response
     * @param processingTime time taken to process request
     */
    public void recordResponse(String serviceName, long bytes, Duration processingTime) {
        globalMetrics.recordResponse(bytes, processingTime);
        getOrCreateMetrics(serviceName).recordResponse(bytes, processingTime);
    }

    /**
     * Record an error for a service.
     *
     * @param serviceName name of the service
     * @param isTimeout whether error was due to timeout
     */
    public void recordError(String serviceName, boolean isTimeout) {
        globalMetrics.recordError(isTimeout);
        getOrCreateMetrics(serviceName).recordError(isTimeout);
    }

    /**
     * Get metrics for a specific service.
     *
     * @param serviceName name of the service
     * @return metrics for the service
     */
    public RpcMetrics getServiceMetrics(String serviceName) {
        return getOrCreateMetrics(serviceName);
    }

    /**
     * Get global metrics across all services.
     *
     * @return global metrics
     */
    public RpcMetrics getGlobalMetrics() {
        return globalMetrics;
    }

    /**
     * Reset metrics for all services.
     */
    public void resetAll() {
        globalMetrics.reset();
        serviceMetrics.values().forEach(RpcMetrics::reset);
    }

    /**
     * Reset metrics for a specific service.
     *
     * @param serviceName name of the service
     */
    public void resetService(String serviceName) {
        RpcMetrics metrics = serviceMetrics.get(serviceName);
        if (metrics != null) {
            metrics.reset();
        }
    }

    private RpcMetrics getOrCreateMetrics(String serviceName) {
        return serviceMetrics.computeIfAbsent(serviceName, k -> new RpcMetrics());
    }
}
