# Aeron RPC Performance Tuning Guide

## Performance Characteristics

### Baseline Performance
- Latency: < 100 microseconds (avg)
- Throughput: > 1M msgs/sec
- Memory: < 1GB heap for typical workloads
- CPU: Linear scaling with cores

## Tuning Parameters

### 1. JVM Options
```bash
# Recommended JVM options for production
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:+ParallelRefProcEnabled
-XX:+PerfDisableSharedMem
-XX:+UseLargePages
-XX:+AggressiveOpts
```

### 2. Network Configuration
```properties
# Aeron media driver options
aeron.mtu.length=8096
aeron.socket.so_sndbuf=2097152
aeron.socket.so_rcvbuf=2097152
aeron.rcv.initial.window.length=2097152
```

### 3. Thread Pool Configuration
```yaml
thread:
  event-loop:
    core-size: 4
    max-size: 8
    queue-size: 1000
  worker:
    core-size: 16
    max-size: 32
    queue-size: 5000
```

## Common Performance Issues

### 1. High Latency

#### Symptoms
- P99 latency > 1ms
- Increasing latency over time
- Latency spikes during GC

#### Solutions
```java
// 1. Use direct buffers
public class DirectBufferPool {
    private final Queue<ByteBuffer> pool;
    
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(8192);
        }
        return buffer;
    }
}

// 2. Implement back pressure
@RpcMethod(pattern = RpcPattern.REQUEST_STREAM)
public Publisher<Data> streamData() {
    return subscriber -> {
        Subscription subscription = new Subscription() {
            public void request(long n) {
                // Only send n items
            }
        };
        subscriber.onSubscribe(subscription);
    };
}

// 3. Batch processing
public void processBatch(List<Request> requests) {
    int batchSize = 100;
    for (int i = 0; i < requests.size(); i += batchSize) {
        List<Request> batch = requests.subList(i, 
            Math.min(i + batchSize, requests.size()));
        processBatchAsync(batch);
    }
}
```

### 2. Low Throughput

#### Symptoms
- Messages/sec below expected rate
- High CPU usage
- Network bottlenecks

#### Solutions
```java
// 1. Use efficient serialization
public class FastSerializer {
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
    
    public void serialize(Object obj) {
        // Direct serialization to buffer
    }
}

// 2. Implement connection pooling
public class ConnectionPool {
    private final ConcurrentLinkedQueue<Connection> pool;
    
    public Connection acquire() {
        Connection conn = pool.poll();
        if (conn == null || !conn.isValid()) {
            conn = createConnection();
        }
        return conn;
    }
}

// 3. Use appropriate buffer sizes
public class BufferConfig {
    public static final int SEND_BUFFER_SIZE = 256 * 1024;
    public static final int RECEIVE_BUFFER_SIZE = 256 * 1024;
    public static final int MESSAGE_LENGTH = 8 * 1024;
}
```

## Monitoring and Profiling

### 1. JVM Metrics
```java
public class JvmMetrics {
    private final MeterRegistry registry;
    
    public void recordGcMetrics() {
        registry.gauge("jvm.gc.pause", 
            ManagementFactory.getGarbageCollectorMXBeans(),
            this::getGcPauseTime);
    }
    
    public void recordMemoryMetrics() {
        registry.gauge("jvm.memory.used",
            ManagementFactory.getMemoryMXBean(),
            this::getUsedMemory);
    }
}
```

### 2. Application Metrics
```java
public class RpcMetrics {
    private final MeterRegistry registry;
    
    public void recordLatency(String method, long startTime) {
        long duration = System.nanoTime() - startTime;
        registry.timer("rpc.latency")
            .tag("method", method)
            .record(duration, TimeUnit.NANOSECONDS);
    }
    
    public void recordThroughput(String method) {
        registry.counter("rpc.throughput")
            .tag("method", method)
            .increment();
    }
}
```

## Load Testing

### 1. Test Scenarios
```java
public class LoadTest {
    @Test
    public void testThroughput() {
        int threads = 8;
        int requestsPerThread = 100_000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Stats>> futures = new ArrayList<>();
        
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                Stats stats = new Stats();
                for (int j = 0; j < requestsPerThread; j++) {
                    long start = System.nanoTime();
                    client.send(createRequest());
                    stats.recordLatency(System.nanoTime() - start);
                }
                return stats;
            }));
        }
        
        // Collect and analyze results
    }
}
```

### 2. Performance Benchmarks
```java
@State(Scope.Thread)
public class RpcBenchmark {
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void measureThroughput(Blackhole blackhole) {
        Response response = client.send(createRequest());
        blackhole.consume(response);
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public void measureLatency(Blackhole blackhole) {
        Response response = client.send(createRequest());
        blackhole.consume(response);
    }
}
```

## Resource Utilization

### 1. Memory Management
```java
public class MemoryManager {
    private final int maxBufferSize;
    private final Queue<ByteBuffer> bufferPool;
    
    public ByteBuffer acquire() {
        ByteBuffer buffer = bufferPool.poll();
        if (buffer == null) {
            if (getTotalAllocated() < maxBufferSize) {
                buffer = ByteBuffer.allocateDirect(8192);
            } else {
                throw new OutOfMemoryError("Buffer pool exhausted");
            }
        }
        return buffer;
    }
    
    public void release(ByteBuffer buffer) {
        buffer.clear();
        bufferPool.offer(buffer);
    }
}
```

### 2. Thread Management
```java
public class ThreadManager {
    private final ThreadPoolExecutor executor;
    private final RejectedExecutionHandler handler;
    
    public ThreadManager(int coreSize, int maxSize) {
        this.handler = new CallerRunsPolicy();
        this.executor = new ThreadPoolExecutor(
            coreSize, maxSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new NamedThreadFactory("RPC-Worker"),
            handler);
    }
    
    public void execute(Runnable task) {
        executor.execute(task);
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

## Production Checklist

### 1. Pre-deployment
- [ ] JVM options configured
- [ ] Network parameters tuned
- [ ] Thread pools sized correctly
- [ ] Monitoring in place
- [ ] Load testing completed

### 2. Post-deployment
- [ ] Monitor GC activity
- [ ] Track latency percentiles
- [ ] Monitor throughput
- [ ] Check resource utilization
- [ ] Analyze error rates

## Troubleshooting Guide

### 1. High Latency Issues
1. Check GC logs
2. Monitor thread pool queues
3. Analyze network latency
4. Review buffer sizes

### 2. Low Throughput Issues
1. Check CPU utilization
2. Monitor network bandwidth
3. Analyze serialization overhead
4. Review connection pooling

### 3. Resource Issues
1. Monitor heap usage
2. Check direct memory usage
3. Review thread states
4. Analyze network connections
