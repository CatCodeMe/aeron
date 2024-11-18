package io.aeron.rpc.loadbalancer;

import io.aeron.rpc.ServiceEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SmoothWeightedRoundRobinTest {
    private SmoothWeightedRoundRobin loadBalancer;
    private ServiceEndpoint endpoint1;
    private ServiceEndpoint endpoint2;
    private ServiceEndpoint endpoint3;
    private List<ServiceEndpoint> endpoints;

    @BeforeEach
    void setUp() {
        loadBalancer = new SmoothWeightedRoundRobin();
        
        // Create test endpoints with different weights
        endpoint1 = new ServiceEndpoint.Builder()
            .id("1")
            .weight(5)
            .build();
            
        endpoint2 = new ServiceEndpoint.Builder()
            .id("2")
            .weight(1)
            .build();
            
        endpoint3 = new ServiceEndpoint.Builder()
            .id("3")
            .weight(3)
            .build();
            
        endpoints = Arrays.asList(endpoint1, endpoint2, endpoint3);
    }

    @Test
    void testWeightDistribution() {
        int totalRequests = 900;
        Map<String, Integer> distribution = new HashMap<>();

        // Make requests and count distribution
        for (int i = 0; i < totalRequests; i++) {
            ServiceEndpoint selected = loadBalancer.select("test", endpoints);
            distribution.merge(selected.getId(), 1, Integer::sum);
        }

        // Calculate expected distribution
        int totalWeight = endpoints.stream().mapToInt(ServiceEndpoint::getWeight).sum();
        Map<String, Integer> expectedDistribution = new HashMap<>();
        for (ServiceEndpoint endpoint : endpoints) {
            int expected = (totalRequests * endpoint.getWeight()) / totalWeight;
            expectedDistribution.put(endpoint.getId(), expected);
        }

        // Verify distribution is within 5% of expected
        for (ServiceEndpoint endpoint : endpoints) {
            int actual = distribution.get(endpoint.getId());
            int expected = expectedDistribution.get(endpoint.getId());
            double ratio = (double) actual / expected;
            assertTrue(ratio >= 0.95 && ratio <= 1.05,
                String.format("Distribution for endpoint %s is off: expected %d, got %d",
                    endpoint.getId(), expected, actual));
        }
    }

    @Test
    void testSequence() {
        // Test first 9 selections to verify sequence
        String[] expectedSequence = {"1", "1", "3", "1", "2", "3", "1", "3", "1"};
        
        for (String expectedId : expectedSequence) {
            ServiceEndpoint selected = loadBalancer.select("test", endpoints);
            assertEquals(expectedId, selected.getId(),
                "Wrong endpoint selected in sequence");
        }
    }

    @Test
    void testEmptyEndpoints() {
        assertNull(loadBalancer.select("test", null));
        assertNull(loadBalancer.select("test", Arrays.asList()));
    }

    @Test
    void testSingleEndpoint() {
        ServiceEndpoint endpoint = new ServiceEndpoint.Builder()
            .id("single")
            .weight(1)
            .build();
            
        List<ServiceEndpoint> singleEndpoint = Arrays.asList(endpoint);
        
        for (int i = 0; i < 10; i++) {
            assertEquals(endpoint, loadBalancer.select("test", singleEndpoint));
        }
    }

    @Test
    void testWeightUpdates() {
        // Initial selection
        assertEquals("1", loadBalancer.select("test", endpoints).getId());
        
        // Update endpoint1 weight
        endpoint1 = new ServiceEndpoint.Builder()
            .id("1")
            .weight(1)
            .build();
            
        List<ServiceEndpoint> updatedEndpoints = Arrays.asList(endpoint1, endpoint2, endpoint3);
        
        // Verify new distribution
        Map<String, Integer> distribution = new HashMap<>();
        int totalRequests = 500;
        
        for (int i = 0; i < totalRequests; i++) {
            ServiceEndpoint selected = loadBalancer.select("test", updatedEndpoints);
            distribution.merge(selected.getId(), 1, Integer::sum);
        }
        
        // Verify new weights are respected
        assertTrue(distribution.get("1") < distribution.get("3"),
            "Endpoint1 should have less selections after weight reduction");
    }
}
