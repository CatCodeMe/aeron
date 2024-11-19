# Aeron RPC API Documentation

## Core APIs

### RpcService Annotation
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {
    /**
     * Service name, must be unique across the system
     */
    String name();

    /**
     * Service version in semantic versioning format
     */
    String version() default "1.0.0";

    /**
     * Load balancing weight for this service instance
     */
    int weight() default 1;

    /**
     * Whether to enable monitoring for this service
     */
    boolean monitored() default true;
}
```

### RpcMethod Annotation
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcMethod {
    /**
     * Communication pattern for this method
     */
    RpcPattern pattern() default RpcPattern.ONE_TO_ONE;

    /**
     * Timeout in milliseconds
     */
    long timeout() default 5000;

    /**
     * Number of retry attempts
     */
    int retries() default 3;

    /**
     * Whether to enable circuit breaker
     */
    boolean circuitBreaker() default false;
}
```

## Load Balancing

### LoadBalancer Interface
```java
public interface LoadBalancer {
    /**
     * Select an endpoint for the given service
     *
     * @param serviceName Service name
     * @param endpoints Available endpoints
     * @return Selected endpoint
     */
    ServiceEndpoint select(String serviceName, List<ServiceEndpoint> endpoints);

    /**
     * Update connection count for an endpoint
     *
     * @param endpoint Target endpoint
     * @param delta Change in connection count
     */
    void updateConnectionCount(ServiceEndpoint endpoint, int delta);
}
```

### Smooth Weighted Round Robin Implementation
```java
public class SmoothWeightedRoundRobin implements LoadBalancer {
    private final Map<String, Map<ServiceEndpoint, WeightedNode>> serviceNodes;

    /**
     * Select an endpoint using smooth weighted round-robin algorithm
     *
     * @param serviceName Service name
     * @param endpoints Available endpoints
     * @return Selected endpoint
     */
    @Override
    public ServiceEndpoint select(String serviceName, List<ServiceEndpoint> endpoints) {
        // Implementation details in code
    }
}
```

## Rate Limiting

### RateLimiter Interface
```java
public interface RateLimiter {
    /**
     * Try to acquire a permit
     *
     * @param permits Number of permits to acquire
     * @return true if acquired, false otherwise
     */
    boolean tryAcquire(int permits);

    /**
     * Get current rate limit
     *
     * @return Current rate limit
     */
    double getRate();

    /**
     * Set new rate limit
     *
     * @param newRate New rate limit
     */
    void setRate(double newRate);
}
```

## Service Discovery

### ServiceRegistry Interface
```java
public interface ServiceRegistry {
    /**
     * Register a service endpoint
     *
     * @param endpoint Service endpoint to register
     */
    void register(ServiceEndpoint endpoint);

    /**
     * Unregister a service endpoint
     *
     * @param endpoint Service endpoint to unregister
     */
    void unregister(ServiceEndpoint endpoint);

    /**
     * Get all endpoints for a service
     *
     * @param serviceName Service name
     * @return List of endpoints
     */
    List<ServiceEndpoint> getEndpoints(String serviceName);
}
```

## Error Handling

### FallbackHandler Interface
```java
public interface FallbackHandler {
    /**
     * Handle method invocation failure
     *
     * @param method Failed method
     * @param args Method arguments
     * @return Fallback result
     */
    Object handleFailure(Method method, Object[] args);

    /**
     * Check if fallback is available
     *
     * @param method Target method
     * @return true if fallback available
     */
    boolean hasFallback(Method method);
}
```

## Monitoring

### MonitoringService Interface
```java
public interface MonitoringService {
    /**
     * Record method invocation
     *
     * @param serviceName Service name
     * @param methodName Method name
     * @param duration Execution duration
     * @param success Whether invocation succeeded
     */
    void recordInvocation(String serviceName, String methodName, 
                         long duration, boolean success);

    /**
     * Get service metrics
     *
     * @param serviceName Service name
     * @return Service metrics
     */
    ServiceMetrics getMetrics(String serviceName);
}
```

## Best Practices

### Service Implementation
```java
@RpcService(name = "OrderService", version = "1.0.0", weight = 5)
public class OrderService {
    @RpcMethod(pattern = RpcPattern.ONE_TO_ONE, 
               timeout = 1000, 
               retries = 3,
               circuitBreaker = true)
    public Order createOrder(OrderRequest request) {
        // Implementation
    }

    @RpcMethod(pattern = RpcPattern.REQUEST_STREAM)
    public Publisher<OrderStatus> streamOrderStatus(String orderId) {
        // Implementation
    }
}
```

### Client Usage
```java
// Create client
RpcClient client = RpcClient.builder()
    .loadBalancer(new SmoothWeightedRoundRobin())
    .rateLimiter(RateLimiter.create(1000))
    .build();

// Get service proxy
OrderService orderService = client.getService(OrderService.class);

// Make RPC call
Order order = orderService.createOrder(request);
```

## Error Handling Examples
```java
@RpcService(name = "UserService")
public class UserService {
    @RpcMethod(circuitBreaker = true)
    public User getUser(String userId) {
        try {
            return userRepository.findById(userId);
        } catch (Exception e) {
            throw new RpcException("Failed to get user", e);
        }
    }

    @Fallback(method = "getUser")
    public User getUserFallback(String userId) {
        return User.builder()
            .id(userId)
            .status(Status.UNKNOWN)
            .build();
    }
}
```

## Configuration Examples

### Service Configuration
```yaml
rpc:
  service:
    name: OrderService
    version: 1.0.0
    weight: 5
    monitoring: true
  client:
    timeout: 5000
    retries: 3
    circuitBreaker:
      enabled: true
      threshold: 5
      timeout: 10000
  loadBalancer:
    strategy: SMOOTH_WEIGHTED
  rateLimiter:
    enabled: true
    rate: 1000
```

## Security Examples

### JWT Authentication
```java
@RpcService(name = "SecureService")
@Secured
public class SecureService {
    @RpcMethod
    @RequiresRole("ADMIN")
    public void adminOperation() {
        // Implementation
    }
}
```

### TLS Configuration
```java
RpcServer server = RpcServer.builder()
    .tls()
        .keyStore("/path/to/keystore.jks")
        .keyStorePassword("password")
        .trustStore("/path/to/truststore.jks")
        .trustStorePassword("password")
        .build()
    .build();
```
