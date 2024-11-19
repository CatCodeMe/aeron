package io.aeron.rpc.ratelimit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe implementation of RateLimiter using the token bucket algorithm.
 * This implementation is optimized for high-concurrency scenarios with minimal contention.
 */
public class TokenBucketRateLimiter implements RateLimiter {
    private final ReentrantLock lock;
    private final AtomicReference<State> stateRef;
    private volatile double ratePerSecond;
    private final double maxBurstSeconds;

    private static class State {
        final double tokens;
        final long lastRefillTime;

        State(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }

    /**
     * Creates a new TokenBucketRateLimiter.
     *
     * @param ratePerSecond maximum rate of permits per second
     * @param maxBurstSeconds maximum burst size in seconds
     */
    public TokenBucketRateLimiter(double ratePerSecond, double maxBurstSeconds) {
        if (ratePerSecond <= 0 || maxBurstSeconds <= 0) {
            throw new IllegalArgumentException("Rate and burst size must be positive");
        }
        
        this.lock = new ReentrantLock();
        this.ratePerSecond = ratePerSecond;
        this.maxBurstSeconds = maxBurstSeconds;
        this.stateRef = new AtomicReference<>(new State(ratePerSecond * maxBurstSeconds, System.nanoTime()));
    }

    @Override
    public CompletableFuture<Boolean> tryAcquire() {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                State currentState = stateRef.get();
                State newState = refill(currentState);

                if (newState.tokens >= 1.0) {
                    stateRef.set(new State(newState.tokens - 1.0, newState.lastRefillTime));
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        });
    }

    private State refill(State currentState) {
        long now = System.nanoTime();
        double elapsedSeconds = (now - currentState.lastRefillTime) / 1_000_000_000.0;
        
        if (elapsedSeconds <= 0) {
            return currentState;
        }

        double newTokens = Math.min(
            currentState.tokens + elapsedSeconds * ratePerSecond,
            ratePerSecond * maxBurstSeconds
        );

        return new State(newTokens, now);
    }

    @Override
    public double getRate() {
        return ratePerSecond;
    }

    @Override
    public void setRate(double newRate) {
        if (newRate <= 0) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        lock.lock();
        try {
            this.ratePerSecond = newRate;
            State current = stateRef.get();
            double maxTokens = newRate * maxBurstSeconds;
            if (current.tokens > maxTokens) {
                stateRef.set(new State(maxTokens, current.lastRefillTime));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public double getAvailablePermits() {
        State currentState = stateRef.get();
        return refill(currentState).tokens;
    }

    @Override
    public void reset() {
        lock.lock();
        try {
            stateRef.set(new State(ratePerSecond * maxBurstSeconds, System.nanoTime()));
        } finally {
            lock.unlock();
        }
    }
}
