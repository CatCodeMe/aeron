package io.aeron.rpc;

/**
 * Enum representing different RPC communication patterns.
 */
public enum RpcPattern {
    /**
     * One-to-One: Single client communicates with single server.
     * Use case: Traditional client-server communication, like database queries.
     */
    ONE_TO_ONE,

    /**
     * Many-to-One: Multiple clients communicate with single server.
     * Use case: Centralized service handling multiple client requests, like a chat server.
     */
    MANY_TO_ONE,

    /**
     * One-to-Many: Single client broadcasts to multiple servers.
     * Use case: Configuration updates, broadcast notifications.
     */
    ONE_TO_MANY,

    /**
     * Many-to-Many: Multiple clients communicate with multiple servers.
     * Use case: Distributed processing, load balancing.
     */
    MANY_TO_MANY,

    /**
     * Pub-Sub: Publishers send messages to topics, subscribers receive from topics.
     * Use case: Event distribution, real-time updates.
     */
    PUB_SUB,

    /**
     * Request-Stream: Client requests a stream of responses.
     * Use case: Real-time data feeds, continuous updates.
     */
    REQUEST_STREAM
}
