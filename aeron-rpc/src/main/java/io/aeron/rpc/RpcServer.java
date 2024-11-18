package io.aeron.rpc;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.rpc.channel.RpcChannelConfig;
import io.aeron.rpc.monitoring.MonitoringService;
import io.aeron.rpc.serialization.Serializer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RPC server implementation.
 */
public class RpcServer implements AutoCloseable {
    private final Aeron aeron;
    private final RpcChannelConfig channelConfig;
    private final MonitoringService monitoringService;
    private final Serializer serializer;
    private final ConcurrentMap<String, RpcService> services;
    private final ThreadPoolExecutor executor;
    private final IdleStrategy idleStrategy;
    private final AtomicReference<State> state;

    private Subscription subscription;
    private Publication publication;
    private Thread pollingThread;

    private RpcServer(Builder builder) {
        this.aeron = builder.aeron;
        this.channelConfig = builder.channelConfig;
        this.monitoringService = builder.monitoringService;
        this.serializer = builder.serializer;
        this.services = new ConcurrentHashMap<>();
        this.executor = createExecutor(builder);
        this.idleStrategy = new SleepingIdleStrategy();
        this.state = new AtomicReference<>(State.NEW);
    }

    public void start() {
        if (!state.compareAndSet(State.NEW, State.STARTING)) {
            throw new IllegalStateException("Server already started");
        }

        // Initialize Aeron resources
        this.subscription = aeron.addSubscription(
            channelConfig.getChannel(), 
            channelConfig.getStreamId()
        );
        
        this.publication = aeron.addPublication(
            channelConfig.getChannel(), 
            channelConfig.getStreamId()
        );

        // Start message polling
        this.pollingThread = new Thread(this::pollMessages, "rpc-server-poller");
        this.pollingThread.start();

        state.set(State.RUNNING);
    }

    @Override
    public void close() {
        if (state.compareAndSet(State.RUNNING, State.CLOSING)) {
            try {
                // Stop polling thread
                if (pollingThread != null) {
                    pollingThread.interrupt();
                    pollingThread.join(5000);
                }

                // Shutdown executor
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }

                // Close Aeron resources
                if (subscription != null) subscription.close();
                if (publication != null) publication.close();

            } catch (Exception e) {
                // Log error
            } finally {
                state.set(State.CLOSED);
            }
        }
    }

    public void registerService(String name, RpcService service) {
        if (services.putIfAbsent(name, service) != null) {
            throw new IllegalArgumentException("Service already registered: " + name);
        }
        service.init();
        if (state.get() == State.RUNNING) {
            service.start();
        }
    }

    public void registerStreamingService(String name, StreamingRpcService service) {
        registerService(name, new StreamingServiceAdapter(service));
    }

    private void pollMessages() {
        while (!Thread.currentThread().isInterrupted() && state.get() == State.RUNNING) {
            int fragmentsRead = subscription.poll(this::onMessage, 10);
            idleStrategy.idle(fragmentsRead);
        }
    }

    private void onMessage(DirectBuffer buffer, int offset, int length, io.aeron.logbuffer.Header header) {
        try {
            RpcMessage message = serializer.deserialize(buffer, offset, length, RpcMessage.class);
            RpcService service = services.get(message.getService());

            if (service == null) {
                sendError(message.getCorrelationId(), new ServiceNotFoundException(message.getService()));
                return;
            }

            monitoringService.recordRequest(message.getService());
            long startTime = System.nanoTime();

            if (message.isStreaming()) {
                handleStreamingRequest(message, service);
            } else {
                handleRequest(message, service, startTime);
            }

        } catch (Exception e) {
            // Log error and send error response
        }
    }

    private void handleRequest(RpcMessage message, RpcService service, long startTime) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return service.handleRequest(message.getPayload());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor).whenComplete((result, error) -> {
            long duration = System.nanoTime() - startTime;
            if (error != null) {
                monitoringService.recordError(message.getService(), false);
                sendError(message.getCorrelationId(), error.getCause());
            } else {
                monitoringService.recordResponse(message.getService(), 
                    result != null ? result.toString().length() : 0,
                    Duration.ofNanos(duration));
                sendResponse(message.getCorrelationId(), result);
            }
        });
    }

    private void handleStreamingRequest(RpcMessage message, RpcService service) {
        RpcStreamSubscriber<?> subscriber = new RpcStreamSubscriber<Object>() {
            @Override
            public void onNext(Object value) {
                sendResponse(message.getCorrelationId(), value);
            }

            @Override
            public void onError(Throwable t) {
                sendError(message.getCorrelationId(), t);
            }

            @Override
            public void onComplete() {
                sendComplete(message.getCorrelationId());
            }
        };

        executor.execute(() -> {
            try {
                service.handleStreamingRequest(message.getPayload(), subscriber);
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    private void sendResponse(long correlationId, Object response) {
        RpcMessage message = new RpcMessage(correlationId, "", response, false);
        publication.offer(serializer.serialize(message));
    }

    private void sendError(long correlationId, Throwable error) {
        RpcMessage message = new RpcMessage(correlationId, "", error, false);
        publication.offer(serializer.serialize(message));
    }

    private void sendComplete(long correlationId) {
        RpcMessage message = new RpcMessage(correlationId, "", null, true);
        publication.offer(serializer.serialize(message));
    }

    private ThreadPoolExecutor createExecutor(Builder builder) {
        return new ThreadPoolExecutor(
            builder.corePoolSize,
            builder.maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(builder.queueCapacity),
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("rpc-worker-" + count++);
                    thread.setDaemon(true);
                    return thread;
                }
            }
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Aeron aeron;
        private RpcChannelConfig channelConfig;
        private MonitoringService monitoringService;
        private Serializer serializer;
        private int corePoolSize = Runtime.getRuntime().availableProcessors();
        private int maxPoolSize = corePoolSize * 2;
        private int queueCapacity = 1000;

        public Builder aeron(Aeron aeron) {
            this.aeron = aeron;
            return this;
        }

        public Builder channelConfig(RpcChannelConfig config) {
            this.channelConfig = config;
            return this;
        }

        public Builder monitoringService(MonitoringService service) {
            this.monitoringService = service;
            return this;
        }

        public Builder serializer(Serializer serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder corePoolSize(int size) {
            this.corePoolSize = size;
            return this;
        }

        public Builder maxPoolSize(int size) {
            this.maxPoolSize = size;
            return this;
        }

        public Builder queueCapacity(int capacity) {
            this.queueCapacity = capacity;
            return this;
        }

        public RpcServer build() {
            validate();
            return new RpcServer(this);
        }

        private void validate() {
            if (aeron == null) throw new IllegalArgumentException("Aeron instance required");
            if (channelConfig == null) throw new IllegalArgumentException("Channel config required");
            if (monitoringService == null) throw new IllegalArgumentException("Monitoring service required");
            if (serializer == null) throw new IllegalArgumentException("Serializer required");
            if (corePoolSize <= 0) throw new IllegalArgumentException("Core pool size must be positive");
            if (maxPoolSize < corePoolSize) throw new IllegalArgumentException("Max pool size must be >= core pool size");
            if (queueCapacity <= 0) throw new IllegalArgumentException("Queue capacity must be positive");
        }
    }

    private enum State {
        NEW,
        STARTING,
        RUNNING,
        CLOSING,
        CLOSED
    }

    @FunctionalInterface
    public interface StreamingRpcService {
        void handleRequest(Object request, RpcStreamSubscriber<?> subscriber);
    }

    private static class StreamingServiceAdapter implements RpcService {
        private final StreamingRpcService streamingService;

        StreamingServiceAdapter(StreamingRpcService streamingService) {
            this.streamingService = streamingService;
        }

        @Override
        public CompletableFuture<Object> handleRequest(Object request) {
            throw new UnsupportedOperationException("This is a streaming-only service");
        }

        @Override
        public void handleStreamingRequest(Object request, RpcStreamSubscriber<?> subscriber) {
            streamingService.handleRequest(request, subscriber);
        }
    }
}
