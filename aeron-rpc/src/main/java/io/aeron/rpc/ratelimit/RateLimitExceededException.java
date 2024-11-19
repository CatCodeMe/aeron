package io.aeron.rpc.ratelimit;

/**
 * Exception thrown when a rate limit is exceeded.
 */
public class RateLimitExceededException extends RuntimeException {
    private final double currentRate;
    private final double requestedRate;

    public RateLimitExceededException(double currentRate, double requestedRate) {
        super(String.format("Rate limit exceeded: current rate %.2f, requested rate %.2f", currentRate, requestedRate));
        this.currentRate = currentRate;
        this.requestedRate = requestedRate;
    }

    public RateLimitExceededException(String message) {
        super(message);
        this.currentRate = 0;
        this.requestedRate = 0;
    }

    public double getCurrentRate() {
        return currentRate;
    }

    public double getRequestedRate() {
        return requestedRate;
    }
}
