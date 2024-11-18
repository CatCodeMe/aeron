package io.aeron.rpc;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for RPC services.
 */
public interface RpcService {
    /**
     * Get the name of this service.
     *
     * @return service name
     */
    String getServiceName();

    /**
     * Get the version of this service.
     *
     * @return service version
     */
    String getVersion();

    /**
     * Handle a request and return a future response.
     *
     * @param request the request object
     * @return future containing the response
     */
    CompletableFuture<Object> handleRequest(Object request);

    /**
     * Handle a streaming request.
     *
     * @param request the request object
     * @param subscriber the subscriber to receive stream items
     */
    default void handleStreamingRequest(Object request, RpcStreamSubscriber<?> subscriber) {
        throw new UnsupportedOperationException("Streaming not supported by this service");
    }

    /**
     * Initialize the service.
     */
    default void init() {
        // Optional initialization
    }

    /**
     * Start the service.
     */
    default void start() {
        // Optional start
    }

    /**
     * Stop the service.
     */
    default void stop() {
        // Optional stop
    }

    /**
     * Get service metadata.
     *
     * @return service metadata
     */
    default ServiceMetadata getMetadata() {
        return new ServiceMetadata();
    }
}
