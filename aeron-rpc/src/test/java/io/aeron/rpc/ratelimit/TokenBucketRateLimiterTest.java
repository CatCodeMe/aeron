package io.aeron.rpc.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {
    private TokenBucketRateLimiter rateLimiter;
    private static final double RATE = 10.0;
    private static final double MAX_BURST_SECONDS = 1.0;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter(RATE, MAX_BURST_SECONDS);
    }

    @Test
    void testInitialState() {
        assertEquals(RATE, rateLimiter.getRate());
        assertEquals(RATE * MAX_BURST_SECONDS, rateLimiter.getAvailablePermits(), 0.01);
    }

    @Test
    void testSuccessfulAcquire() throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> result = rateLimiter.tryAcquire();
        assertTrue(result.get());
    }

    @Test
    void testRateLimitExceeded() throws ExecutionException, InterruptedException {
        // Acquire all permits
        for (int i = 0; i < RATE; i++) {
            assertTrue(rateLimiter.tryAcquire().get());
        }
        // Next acquire should fail
        assertFalse(rateLimiter.tryAcquire().get());
    }

    @Test
    void testRefill() throws InterruptedException, ExecutionException {
        // Use all permits
        for (int i = 0; i < RATE; i++) {
            assertTrue(rateLimiter.tryAcquire().get());
        }
        assertFalse(rateLimiter.tryAcquire().get());

        // Wait for refill
        Thread.sleep(1000);

        // Should be able to acquire again
        assertTrue(rateLimiter.tryAcquire().get());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int requestsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                int successCount = 0;
                for (int j = 0; j < requestsPerThread; j++) {
                    try {
                        if (rateLimiter.tryAcquire().get()) {
                            successCount++;
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    }
                }
                return successCount;
            }, executor));
        }

        int totalSuccessful = futures.stream()
            .map(CompletableFuture::join)
            .mapToInt(Integer::intValue)
            .sum();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Total successful requests should not exceed rate limit
        assertTrue(totalSuccessful <= RATE * MAX_BURST_SECONDS);
    }

    @Test
    void testRateUpdate() {
        double newRate = 20.0;
        rateLimiter.setRate(newRate);
        assertEquals(newRate, rateLimiter.getRate());
    }

    @Test
    void testReset() throws ExecutionException, InterruptedException {
        // Use some permits
        for (int i = 0; i < RATE / 2; i++) {
            assertTrue(rateLimiter.tryAcquire().get());
        }

        // Reset
        rateLimiter.reset();

        // Should have full permits again
        assertEquals(RATE * MAX_BURST_SECONDS, rateLimiter.getAvailablePermits(), 0.01);
    }
}
