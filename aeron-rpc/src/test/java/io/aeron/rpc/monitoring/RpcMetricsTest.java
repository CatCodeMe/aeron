package io.aeron.rpc.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RpcMetricsTest {
    private RpcMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new RpcMetrics();
    }

    @Test
    void testRecordRequest() {
        metrics.recordRequest();
        assertEquals(1, metrics.getTotalRequests());
    }

    @Test
    void testRecordResponse() {
        Duration processingTime = Duration.ofMillis(100);
        metrics.recordResponse(1024, processingTime);

        assertEquals(1, metrics.getTotalResponses());
        assertEquals(1024, metrics.getTotalBytes());
        assertEquals(100, metrics.getMaxProcessingTime());
        assertEquals(100, metrics.getMinProcessingTime());
        assertEquals(100.0, metrics.getAverageProcessingTime());
    }

    @Test
    void testRecordError() {
        metrics.recordError(true);
        metrics.recordError(false);

        assertEquals(2, metrics.getTotalErrors());
        assertEquals(1, metrics.getTotalTimeoutErrors());
    }

    @Test
    void testProcessingTimeStats() {
        metrics.recordResponse(100, Duration.ofMillis(50));
        metrics.recordResponse(100, Duration.ofMillis(150));
        metrics.recordResponse(100, Duration.ofMillis(100));

        assertEquals(150, metrics.getMaxProcessingTime());
        assertEquals(50, metrics.getMinProcessingTime());
        assertEquals(100.0, metrics.getAverageProcessingTime());
    }

    @Test
    void testReset() {
        // Record some metrics
        metrics.recordRequest();
        metrics.recordResponse(1024, Duration.ofMillis(100));
        metrics.recordError(true);

        // Reset
        metrics.reset();

        // Verify all metrics are reset
        assertEquals(0, metrics.getTotalRequests());
        assertEquals(0, metrics.getTotalResponses());
        assertEquals(0, metrics.getTotalErrors());
        assertEquals(0, metrics.getTotalTimeoutErrors());
        assertEquals(0, metrics.getTotalBytes());
        assertEquals(0, metrics.getMaxProcessingTime());
        assertEquals(0, metrics.getMinProcessingTime());
        assertEquals(0.0, metrics.getAverageProcessingTime());
    }

    @Test
    void testAverageProcessingTimeWithNoResponses() {
        assertEquals(0.0, metrics.getAverageProcessingTime());
    }

    @Test
    void testMinProcessingTimeWithNoResponses() {
        assertEquals(0, metrics.getMinProcessingTime());
    }
}
