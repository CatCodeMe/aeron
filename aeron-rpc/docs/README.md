# Aeron RPC Framework Documentation

## Overview

Aeron RPC is a high-performance RPC framework built on top of Aeron, designed for low-latency distributed systems. It provides a robust, feature-rich platform for building distributed applications with advanced messaging patterns, service discovery, and monitoring capabilities.

## Key Features

### 1. Core RPC Framework
- **Event-Driven Architecture**
  - Non-blocking I/O with Reactor pattern
  - Efficient message fragment handling
  - Dynamic event handler registration
  
- **Service Management**
  - Annotation-based service definition (`@RpcService`, `@RpcMethod`)
  - Automatic service discovery and registration
  - Version management with semantic versioning
  - Service metadata extraction

- **Communication Patterns**
  - ONE_TO_ONE: Traditional request-response
  - MANY_TO_ONE: Fan-in pattern
  - ONE_TO_MANY: Fan-out pattern
  - REQUEST_STREAM: Streaming responses
  - PUB_SUB: Publish-subscribe pattern

### 2. Advanced Features

#### Load Balancing
- Multiple strategies:
  - Round Robin
  - Random
  - Weighted Round Robin
  - Smooth Weighted Round Robin (Latest Addition)
  - Least Connections

Implementation Details for SWRR:
```java
// Current weight calculation
currentWeight += effectiveWeight;
if (currentWeight > maxWeight) {
    selected = node;
    maxWeight = currentWeight;
}
// After selection
selected.currentWeight -= totalWeight;
```

Benefits:
- More uniform request distribution
- Better handling of weight differences
- Smoother load transitions

#### Rate Limiting
- Token bucket algorithm implementation
- Configurable rates per service
- Concurrent-safe design

#### Fallback Handling
- Circuit breaker pattern
- Custom fallback strategies
- Service degradation tracking

#### Security
- JWT authentication
- TLS encryption support
- Method-level security annotations

## Performance Characteristics

### Latency Profile
- Average latency: < 100 microseconds
- 99th percentile: < 1ms
- Minimal GC impact

### Throughput
- Sustained message rate: > 1M msgs/sec
- Linear scaling with cores

## Best Practices

### Service Definition
```java
@RpcService(name = "UserService", version = "1.0")
public class UserService {
    @RpcMethod(pattern = RpcPattern.ONE_TO_ONE)
    public User getUser(String userId) {
        // Implementation
    }
}
```

### Load Balancer Usage
```java
LoadBalancer lb = new LoadBalancer(Strategy.SMOOTH_WEIGHTED);
ServiceEndpoint endpoint = lb.select("serviceName", endpoints);
```

## Iteration Plan

### Phase 1: Core Stability (Current)
- [x] Basic RPC functionality
- [x] Service discovery
- [x] Load balancing
- [x] Rate limiting

### Phase 2: Enhanced Features (In Progress)
- [ ] Distributed tracing integration
- [ ] Metrics collection and monitoring
- [ ] Advanced security features
- [ ] Configuration management

### Phase 3: Performance Optimization
- [ ] Zero-copy message handling
- [ ] Custom serialization formats
- [ ] Connection pooling
- [ ] Batch processing support

### Phase 4: Enterprise Features
- [ ] Multi-datacenter support
- [ ] Service mesh integration
- [ ] Advanced monitoring dashboard
- [ ] Disaster recovery features

## Technical Decisions and Rationales

### 1. Why Smooth Weighted Round Robin?
The transition from simple weighted round-robin to smooth weighted round-robin was made to address:
- Uneven request distribution in short time windows
- Better handling of weight differences
- More predictable load patterns

Before (Weighted RR):
```
Weight 5: [5,5,5,5,5] -> Bursty
Weight 2: [2,2]       -> Gaps
```

After (Smooth WRR):
```
Weight 5: [1,1,1,1,1] -> Evenly distributed
Weight 2: [1,1]       -> Better interleaving
```

### 2. Why Reactor Pattern?
Chosen for:
- Non-blocking I/O
- Better resource utilization
- Simplified concurrency model

### 3. Security Design Choices
JWT + TLS combination provides:
- Stateless authentication
- Message integrity
- Transport security

## Testing Strategy

### Unit Tests
- Service-level tests
- Load balancer tests
- Rate limiter tests

### Integration Tests
- End-to-end flow tests
- Performance benchmarks
- Failure scenarios

### Performance Tests
- Latency measurements
- Throughput tests
- Resource utilization

## Future Considerations

### 1. Protocol Evolution
- Backward compatibility
- Version negotiation
- Schema evolution

### 2. Cloud Native Features
- Kubernetes integration
- Service mesh compatibility
- Cloud provider integrations

### 3. Monitoring and Observability
- Prometheus metrics
- Grafana dashboards
- Trace aggregation

## Contributing

### Code Style
- Google Java Style Guide
- Comprehensive JavaDoc
- Unit test coverage > 80%

### Review Process
1. Code review checklist
2. Performance impact assessment
3. Security review for sensitive changes

## Support and Resources

- [API Documentation](./api/README.md)
- [Example Applications](./examples/README.md)
- [Performance Tuning Guide](./tuning/README.md)
- [Security Guide](./security/README.md)
