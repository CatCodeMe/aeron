package io.aeron.rpc.monitoring;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for RPC operations.
 */
public class RpcMetrics {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalResponses = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalTimeoutErrors = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong maxProcessingTime = new AtomicLong(0);
    private final AtomicLong minProcessingTime = new AtomicLong(Long.MAX_VALUE);

    /**
     * Record a request being sent/received.
     */
    public void recordRequest() {
        totalRequests.incrementAndGet();
    }

    /**
     * Record a response being sent/received.
     *
     * @param bytes number of bytes in response
     * @param processingTime time taken to process request
     */
    public void recordResponse(long bytes, Duration processingTime) {
        totalResponses.incrementAndGet();
        totalBytes.addAndGet(bytes);
        
        long timeMillis = processingTime.toMillis();
        totalProcessingTime.addAndGet(timeMillis);
        updateMaxProcessingTime(timeMillis);
        updateMinProcessingTime(timeMillis);
    }

    /**
     * Record an error.
     *
     * @param isTimeout whether error was due to timeout
     */
    public void recordError(boolean isTimeout) {
        totalErrors.incrementAndGet();
        if (isTimeout) {
            totalTimeoutErrors.incrementAndGet();
        }
    }

    private void updateMaxProcessingTime(long timeMillis) {
        long currentMax;
        do {
            currentMax = maxProcessingTime.get();
            if (timeMillis <= currentMax) {
                break;
            }
        } while (!maxProcessingTime.compareAndSet(currentMax, timeMillis));
    }

    private void updateMinProcessingTime(long timeMillis) {
        long currentMin;
        do {
            currentMin = minProcessingTime.get();
            if (timeMillis >= currentMin) {
                break;
            }
        } while (!minProcessingTime.compareAndSet(currentMin, timeMillis));
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getTotalResponses() {
        return totalResponses.get();
    }

    public long getTotalErrors() {
        return totalErrors.get();
    }

    public long getTotalTimeoutErrors() {
        return totalTimeoutErrors.get();
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public double getAverageProcessingTime() {
        long responses = totalResponses.get();
        return responses > 0 ? (double) totalProcessingTime.get() / responses : 0.0;
    }

    public long getMaxProcessingTime() {
        return maxProcessingTime.get();
    }

    public long getMinProcessingTime() {
        long min = minProcessingTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        totalRequests.set(0);
        totalResponses.set(0);
        totalErrors.set(0);
        totalTimeoutErrors.set(0);
        totalBytes.set(0);
        totalProcessingTime.set(0);
        maxProcessingTime.set(0);
        minProcessingTime.set(Long.MAX_VALUE);
    }
}
