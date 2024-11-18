package io.aeron.rpc.integration;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.rpc.*;
import io.aeron.rpc.channel.RpcChannelConfig;
import io.aeron.rpc.monitoring.MonitoringService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RpcIntegrationTest {
    private MediaDriver mediaDriver;
    private Aeron aeron;
    private RpcServer server;
    private RpcClient client;
    private MonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        // Start Media Driver
        mediaDriver = MediaDriver.launch(new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .threadingMode(MediaDriver.ThreadingMode.SHARED));

        // Create Aeron instance
        aeron = Aeron.connect(new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        monitoringService = new MonitoringService();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
        if (aeron != null) {
            aeron.close();
        }
        if (mediaDriver != null) {
            mediaDriver.close();
        }
    }

    @Test
    void testOneToOnePattern() throws Exception {
        // Configure channels
        RpcChannelConfig config = RpcChannelConfig.createIpc(1001, RpcPattern.ONE_TO_ONE);

        // Create server
        server = new RpcServer.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .build();

        // Create client
        client = new RpcClient.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .build();

        // Register echo service
        server.registerService("echo", (request) -> CompletableFuture.completedFuture(request));

        // Start server and client
        server.start();
        client.start();

        // Send request
        String request = "Hello RPC!";
        CompletableFuture<String> response = client.call("echo", request, String.class);
        
        assertEquals(request, response.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testManyToOnePattern() throws Exception {
        // Configure channels
        RpcChannelConfig config = RpcChannelConfig.createIpc(1002, RpcPattern.MANY_TO_ONE);

        // Create server
        server = new RpcServer.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .build();

        // Create multiple clients
        int clientCount = 3;
        RpcClient[] clients = new RpcClient[clientCount];
        for (int i = 0; i < clientCount; i++) {
            clients[i] = new RpcClient.Builder()
                .aeron(aeron)
                .channelConfig(config)
                .monitoringService(monitoringService)
                .build();
        }

        // Register counter service
        final int[] counter = {0};
        server.registerService("increment", (request) -> 
            CompletableFuture.completedFuture(++counter[0]));

        // Start server and clients
        server.start();
        for (RpcClient client : clients) {
            client.start();
        }

        // Send concurrent requests
        CountDownLatch latch = new CountDownLatch(clientCount);
        CompletableFuture<Integer>[] responses = new CompletableFuture[clientCount];

        for (int i = 0; i < clientCount; i++) {
            final int clientIndex = i;
            responses[i] = clients[i].call("increment", null, Integer.class)
                .whenComplete((result, ex) -> latch.countDown());
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify responses
        for (CompletableFuture<Integer> response : responses) {
            assertTrue(response.get() > 0 && response.get() <= clientCount);
        }
    }

    @Test
    void testRequestStreamPattern() throws Exception {
        // Configure channels
        RpcChannelConfig config = RpcChannelConfig.createIpc(1003, RpcPattern.REQUEST_STREAM);

        // Create server
        server = new RpcServer.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .build();

        // Create client
        client = new RpcClient.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .build();

        // Register streaming service
        server.registerStreamingService("numbers", (request, subscriber) -> {
            int count = (int) request;
            for (int i = 1; i <= count; i++) {
                subscriber.onNext(i);
            }
            subscriber.onComplete();
        });

        // Start server and client
        server.start();
        client.start();

        // Request stream
        CountDownLatch latch = new CountDownLatch(5);
        final int[] sum = {0};

        client.stream("numbers", 5, Integer.class, new RpcStreamSubscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                sum[0] += value;
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                fail("Stream error: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                // Optional: handle stream completion
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(15, sum[0]); // 1 + 2 + 3 + 4 + 5
    }

    @Test
    void testErrorHandling() throws Exception {
        // Configure channels
        RpcChannelConfig config = RpcChannelConfig.createIpc(1004, RpcPattern.ONE_TO_ONE);

        // Create server
        server = new RpcServer.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .build();

        // Create client
        client = new RpcClient.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .build();

        // Register service that throws exception
        server.registerService("error", (request) -> {
            throw new RuntimeException("Test error");
        });

        // Start server and client
        server.start();
        client.start();

        // Send request and expect exception
        CompletableFuture<Object> response = client.call("error", "test", Object.class);
        
        Exception exception = assertThrows(Exception.class, () -> 
            response.get(5, TimeUnit.SECONDS));
        assertTrue(exception.getCause().getMessage().contains("Test error"));
    }
}
