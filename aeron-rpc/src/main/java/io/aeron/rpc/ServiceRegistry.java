package io.aeron.rpc;

import java.util.Set;

/**
 * Interface for service discovery and registration.
 */
public interface ServiceRegistry {
    /**
     * Register a service endpoint.
     *
     * @param serviceName name of the service
     * @param endpoint service endpoint details
     */
    void register(String serviceName, ServiceEndpoint endpoint);

    /**
     * Deregister a service endpoint.
     *
     * @param serviceName name of the service
     * @param endpoint service endpoint to remove
     */
    void deregister(String serviceName, ServiceEndpoint endpoint);

    /**
     * Find all endpoints for a service.
     *
     * @param serviceName name of the service
     * @return set of endpoints for the service
     */
    Set<ServiceEndpoint> findEndpoints(String serviceName);

    /**
     * Start the service registry.
     */
    void start();

    /**
     * Stop the service registry.
     */
    void stop();
}
