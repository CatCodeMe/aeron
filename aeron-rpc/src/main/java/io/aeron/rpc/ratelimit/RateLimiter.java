package io.aeron.rpc.ratelimit;

import java.util.concurrent.CompletableFuture;

/**
 * Rate limiter interface for controlling request rates in the RPC framework.
 * Implementations should be thread-safe and non-blocking.
 */
public interface RateLimiter {
    
    /**
     * Attempts to acquire a permit for execution.
     * 
     * @return CompletableFuture that completes with true if the permit was acquired,
     *         false if the request should be rate limited
     */
    CompletableFuture<Boolean> tryAcquire();
    
    /**
     * Gets the current rate limit.
     * 
     * @return the current maximum permitted requests per second
     */
    double getRate();
    
    /**
     * Updates the rate limit.
     * 
     * @param newRate the new maximum permitted requests per second
     */
    void setRate(double newRate);
    
    /**
     * Gets the current number of available permits.
     * 
     * @return the number of available permits
     */
    double getAvailablePermits();
    
    /**
     * Resets the rate limiter to its initial state.
     */
    void reset();
}
