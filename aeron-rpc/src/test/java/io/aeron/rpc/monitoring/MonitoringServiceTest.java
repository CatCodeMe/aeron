package io.aeron.rpc.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringServiceTest {
    private MonitoringService monitoringService;
    private static final String SERVICE_NAME = "testService";

    @BeforeEach
    void setUp() {
        monitoringService = new MonitoringService();
    }

    @Test
    void testRecordRequest() {
        monitoringService.recordRequest(SERVICE_NAME);

        assertEquals(1, monitoringService.getServiceMetrics(SERVICE_NAME).getTotalRequests());
        assertEquals(1, monitoringService.getGlobalMetrics().getTotalRequests());
    }

    @Test
    void testRecordResponse() {
        Duration processingTime = Duration.ofMillis(100);
        monitoringService.recordResponse(SERVICE_NAME, 1024, processingTime);

        RpcMetrics serviceMetrics = monitoringService.getServiceMetrics(SERVICE_NAME);
        RpcMetrics globalMetrics = monitoringService.getGlobalMetrics();

        assertEquals(1, serviceMetrics.getTotalResponses());
        assertEquals(1024, serviceMetrics.getTotalBytes());
        assertEquals(100.0, serviceMetrics.getAverageProcessingTime());

        assertEquals(1, globalMetrics.getTotalResponses());
        assertEquals(1024, globalMetrics.getTotalBytes());
        assertEquals(100.0, globalMetrics.getAverageProcessingTime());
    }

    @Test
    void testRecordError() {
        monitoringService.recordError(SERVICE_NAME, true);
        monitoringService.recordError(SERVICE_NAME, false);

        RpcMetrics serviceMetrics = monitoringService.getServiceMetrics(SERVICE_NAME);
        RpcMetrics globalMetrics = monitoringService.getGlobalMetrics();

        assertEquals(2, serviceMetrics.getTotalErrors());
        assertEquals(1, serviceMetrics.getTotalTimeoutErrors());

        assertEquals(2, globalMetrics.getTotalErrors());
        assertEquals(1, globalMetrics.getTotalTimeoutErrors());
    }

    @Test
    void testResetService() {
        // Record some metrics
        monitoringService.recordRequest(SERVICE_NAME);
        monitoringService.recordResponse(SERVICE_NAME, 1024, Duration.ofMillis(100));
        monitoringService.recordError(SERVICE_NAME, true);

        // Reset service metrics
        monitoringService.resetService(SERVICE_NAME);

        RpcMetrics serviceMetrics = monitoringService.getServiceMetrics(SERVICE_NAME);
        assertEquals(0, serviceMetrics.getTotalRequests());
        assertEquals(0, serviceMetrics.getTotalResponses());
        assertEquals(0, serviceMetrics.getTotalErrors());

        // Global metrics should remain unchanged
        RpcMetrics globalMetrics = monitoringService.getGlobalMetrics();
        assertEquals(1, globalMetrics.getTotalRequests());
        assertEquals(1, globalMetrics.getTotalResponses());
        assertEquals(1, globalMetrics.getTotalErrors());
    }

    @Test
    void testResetAll() {
        // Record metrics for multiple services
        String service1 = "service1";
        String service2 = "service2";

        monitoringService.recordRequest(service1);
        monitoringService.recordRequest(service2);
        monitoringService.recordError(service1, true);
        monitoringService.recordError(service2, false);

        // Reset all metrics
        monitoringService.resetAll();

        // Verify all metrics are reset
        assertEquals(0, monitoringService.getServiceMetrics(service1).getTotalRequests());
        assertEquals(0, monitoringService.getServiceMetrics(service2).getTotalRequests());
        assertEquals(0, monitoringService.getGlobalMetrics().getTotalRequests());
    }

    @Test
    void testNonExistentService() {
        RpcMetrics metrics = monitoringService.getServiceMetrics("nonexistent");
        assertNotNull(metrics);
        assertEquals(0, metrics.getTotalRequests());
    }

    @Test
    void testMultipleServicesIndependence() {
        String service1 = "service1";
        String service2 = "service2";

        monitoringService.recordRequest(service1);
        monitoringService.recordError(service2, true);

        assertEquals(1, monitoringService.getServiceMetrics(service1).getTotalRequests());
        assertEquals(0, monitoringService.getServiceMetrics(service1).getTotalErrors());
        assertEquals(0, monitoringService.getServiceMetrics(service2).getTotalRequests());
        assertEquals(1, monitoringService.getServiceMetrics(service2).getTotalErrors());
    }
}
