package io.aeron.rpc.example;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.rpc.*;
import io.aeron.rpc.annotation.RpcServiceScanner;
import io.aeron.rpc.channel.RpcChannelConfig;
import io.aeron.rpc.fallback.FallbackHandler;
import io.aeron.rpc.limiter.RpcRateLimiter;
import io.aeron.rpc.loadbalancer.LoadBalancer;
import io.aeron.rpc.monitoring.MonitoringService;
import io.aeron.rpc.serialization.JsonSerializer;

import java.util.concurrent.TimeUnit;

/**
 * Example application demonstrating RPC framework features.
 */
public class ExampleApplication {
    public static void main(String[] args) throws Exception {
        // Start Media Driver
        MediaDriver mediaDriver = MediaDriver.launch(new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true));

        // Create Aeron instance
        Aeron aeron = Aeron.connect(new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        try {
            // Create monitoring service
            MonitoringService monitoringService = new MonitoringService();

            // Create rate limiter
            RpcRateLimiter rateLimiter = new RpcRateLimiter();
            rateLimiter.setRate("OrderService", 1000.0); // 1000 requests per second

            // Create load balancer
            LoadBalancer loadBalancer = new LoadBalancer(LoadBalancer.Strategy.WEIGHTED);

            // Create fallback handler
            FallbackHandler fallbackHandler = new FallbackHandler();
            fallbackHandler.registerFallback("OrderService", "getOrder",
                FallbackHandler.CommonFallbacks.returnNull());

            // Configure channels
            RpcChannelConfig config = RpcChannelConfig.createIpc(1001, RpcPattern.ONE_TO_ONE);

            // Create server
            RpcServer server = new RpcServer.Builder()
                .aeron(aeron)
                .channelConfig(config)
                .monitoringService(monitoringService)
                .serializer(new JsonSerializer())
                .build();

            // Create client
            RpcClient client = new RpcClient.Builder()
                .aeron(aeron)
                .channelConfig(config)
                .monitoringService(monitoringService)
                .serializer(new JsonSerializer())
                .build();

            // Scan and register services
            RpcServiceScanner scanner = new RpcServiceScanner(server, "io.aeron.rpc.example");
            scanner.scan();

            // Start server and client
            server.start();
            client.start();

            // Example usage
            OrderService.Order order = new OrderService.Order();
            order.setId("12345");
            order.setCustomerId("customer1");
            order.setAmount(100.0);
            order.setStatus("PENDING");

            // Create order
            client.call("OrderService", "createOrder", order, OrderService.Order.class)
                .thenAccept(createdOrder -> {
                    System.out.println("Order created: " + createdOrder.getId());

                    // Get order
                    try {
                        OrderService.Order retrievedOrder = client.call(
                            "OrderService",
                            "getOrder",
                            createdOrder.getId(),
                            OrderService.Order.class
                        ).get(1, TimeUnit.SECONDS);

                        System.out.println("Retrieved order: " + retrievedOrder.getId());

                        // Update order status
                        client.call(
                            "OrderService",
                            "updateOrderStatus",
                            new Object[]{retrievedOrder.getId(), "COMPLETED"},
                            void.class
                        );

                        System.out.println("Order status updated");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            // Keep application running
            Thread.sleep(5000);

        } finally {
            // Cleanup
            aeron.close();
            mediaDriver.close();
        }
    }
}
