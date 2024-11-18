package io.aeron.rpc.example;

import io.aeron.rpc.annotation.RpcService;
import io.aeron.rpc.annotation.RpcMethod;
import io.aeron.rpc.RpcPattern;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RpcService(
    name = "OrderService",
    version = "1.0.0",
    group = "orders",
    weight = 100,
    monitored = true
)
public class OrderService {
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    @RpcMethod(
        pattern = RpcPattern.ONE_TO_ONE,
        timeout = 5000,
        retry = true,
        maxRetries = 3,
        circuitBreaker = true,
        failureThreshold = 5
    )
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    @RpcMethod(
        pattern = RpcPattern.ONE_TO_ONE,
        async = true,
        retry = true,
        circuitBreaker = true
    )
    public CompletableFuture<Order> createOrder(Order order) {
        return CompletableFuture.supplyAsync(() -> {
            orders.put(order.getId(), order);
            return order;
        });
    }

    @RpcMethod(
        pattern = RpcPattern.ONE_TO_MANY,
        timeout = 1000
    )
    public void updateOrderStatus(String orderId, String status) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.setStatus(status);
            orders.put(orderId, order);
        }
    }

    @RpcMethod(
        pattern = RpcPattern.REQUEST_STREAM
    )
    public void streamOrderUpdates(String orderId, RpcStreamSubscriber<Order> subscriber) {
        Order order = orders.get(orderId);
        if (order != null) {
            subscriber.onNext(order);
        }
        subscriber.onComplete();
    }

    public static class Order {
        private String id;
        private String customerId;
        private double amount;
        private String status;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
