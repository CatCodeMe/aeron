package io.aeron.rpc;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for RPC framework.
 */
public class RpcConfiguration {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_SERVICE_DISCOVERY_INTERVAL = Duration.ofSeconds(1);
    private static final int DEFAULT_MAX_REQUEST_QUEUE_SIZE = 1000;
    private static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private final Duration requestTimeout;
    private final Duration serviceDiscoveryInterval;
    private final int maxRequestQueueSize;
    private final int threadPoolSize;

    private RpcConfiguration(Builder builder) {
        this.requestTimeout = Objects.requireNonNull(builder.requestTimeout, "requestTimeout must not be null");
        this.serviceDiscoveryInterval = Objects.requireNonNull(builder.serviceDiscoveryInterval, 
            "serviceDiscoveryInterval must not be null");
        this.maxRequestQueueSize = builder.maxRequestQueueSize;
        this.threadPoolSize = builder.threadPoolSize;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public Duration getServiceDiscoveryInterval() {
        return serviceDiscoveryInterval;
    }

    public int getMaxRequestQueueSize() {
        return maxRequestQueueSize;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RpcConfiguration getDefault() {
        return builder().build();
    }

    public static class Builder {
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private Duration serviceDiscoveryInterval = DEFAULT_SERVICE_DISCOVERY_INTERVAL;
        private int maxRequestQueueSize = DEFAULT_MAX_REQUEST_QUEUE_SIZE;
        private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder requestTimeout(long timeout, TimeUnit unit) {
            return requestTimeout(Duration.ofMillis(unit.toMillis(timeout)));
        }

        public Builder serviceDiscoveryInterval(Duration serviceDiscoveryInterval) {
            this.serviceDiscoveryInterval = serviceDiscoveryInterval;
            return this;
        }

        public Builder serviceDiscoveryInterval(long interval, TimeUnit unit) {
            return serviceDiscoveryInterval(Duration.ofMillis(unit.toMillis(interval)));
        }

        public Builder maxRequestQueueSize(int maxRequestQueueSize) {
            this.maxRequestQueueSize = maxRequestQueueSize;
            return this;
        }

        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        public RpcConfiguration build() {
            return new RpcConfiguration(this);
        }
    }
}
