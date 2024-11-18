package io.aeron.rpc;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC Client for making requests to RPC services.
 */
public class RpcClient implements AutoCloseable {
    private final Publication publication;
    private final Subscription subscription;
    private final IdleStrategy idleStrategy;
    private final AtomicLong nextRequestId = new AtomicLong(0);
    private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final RpcMessage rpcMessage;
    private final MutableDirectBuffer requestBuffer;
    private final RpcConfiguration configuration;
    private final ServiceRegistry serviceRegistry;
    private final ScheduledExecutorService scheduledExecutor;
    private volatile boolean running = true;
    private final Thread responseThread;

    public RpcClient(
        final Aeron aeron,
        final String requestChannel,
        final int requestStreamId,
        final String responseChannel,
        final int responseStreamId,
        final IdleStrategy idleStrategy,
        final RpcConfiguration configuration,
        final ServiceRegistry serviceRegistry) {
        
        this.publication = aeron.addPublication(requestChannel, requestStreamId);
        this.subscription = aeron.addSubscription(responseChannel, responseStreamId);
        this.idleStrategy = idleStrategy;
        this.configuration = configuration;
        this.serviceRegistry = serviceRegistry;
        this.requestBuffer = new UnsafeBuffer(new byte[8192]); // Initial size, will grow if needed
        this.rpcMessage = new RpcMessage(requestBuffer);
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("rpc-timeout-handler");
            return t;
        });

        this.responseThread = new Thread(this::processResponses);
        this.responseThread.setName("rpc-response-handler");
        this.responseThread.start();

        // Start timeout checker
        scheduledExecutor.scheduleAtFixedRate(
            this::checkTimeouts,
            configuration.getRequestTimeout().toMillis(),
            configuration.getRequestTimeout().toMillis(),
            TimeUnit.MILLISECONDS);
    }

    /**
     * Make a synchronous RPC call.
     *
     * @param serviceName name of the service to call
     * @param methodName name of the method to call
     * @param payload request payload
     * @return response payload
     * @throws Exception if there is an error processing the request
     */
    public DirectBuffer call(final String serviceName, final String methodName, final DirectBuffer payload) 
        throws Exception {
        return callAsync(serviceName, methodName, payload)
            .get(configuration.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Make an asynchronous RPC call.
     *
     * @param serviceName name of the service to call
     * @param methodName name of the method to call
     * @param payload request payload
     * @return future that will complete with the response payload
     */
    public CompletableFuture<DirectBuffer> callAsync(
        final String serviceName, 
        final String methodName, 
        final DirectBuffer payload) {
        
        // Find service endpoint
        Set<ServiceEndpoint> endpoints = serviceRegistry.findEndpoints(serviceName);
        if (endpoints.isEmpty()) {
            CompletableFuture<DirectBuffer> future = new CompletableFuture<>();
            future.completeExceptionally(new Exception("No endpoints found for service: " + serviceName));
            return future;
        }
        
        // For now, just use the first endpoint
        ServiceEndpoint endpoint = endpoints.iterator().next();
        
        final long requestId = nextRequestId.getAndIncrement();
        final CompletableFuture<DirectBuffer> future = new CompletableFuture<>();
        final long deadline = System.currentTimeMillis() + configuration.getRequestTimeout().toMillis();
        pendingRequests.put(requestId, new PendingRequest(future, deadline));

        rpcMessage.requestId(requestId);
        rpcMessage.type(RpcMessage.TYPE_REQUEST);
        rpcMessage.serviceName(serviceName);
        rpcMessage.methodName(methodName);
        rpcMessage.payload(payload);

        while (publication.offer(requestBuffer, 0, 
            RpcMessage.computeLength(serviceName, methodName, payload.capacity())) < 0) {
            idleStrategy.idle();
            
            // Check if we've exceeded the timeout while trying to send
            if (System.currentTimeMillis() > deadline) {
                pendingRequests.remove(requestId);
                future.completeExceptionally(new TimeoutException("Timeout while trying to send request"));
                return future;
            }
        }

        return future;
    }

    private void processResponses() {
        final FragmentHandler fragmentHandler = this::onFragment;
        while (running) {
            idleStrategy.idle(subscription.poll(fragmentHandler, 10));
        }
    }

    private void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        final RpcMessage response = new RpcMessage(new UnsafeBuffer(new byte[length]));
        response.wrap(buffer, offset, length);

        final PendingRequest pendingRequest = pendingRequests.remove(response.requestId());
        if (pendingRequest != null) {
            if (response.type() == RpcMessage.TYPE_RESPONSE) {
                pendingRequest.future.complete(response.payload());
            } else if (response.type() == RpcMessage.TYPE_ERROR) {
                pendingRequest.future.completeExceptionally(
                    new Exception(new String(response.payload().byteArray())));
            }
        }
    }

    private void checkTimeouts() {
        final long now = System.currentTimeMillis();
        pendingRequests.forEach((requestId, request) -> {
            if (now > request.deadline) {
                PendingRequest removed = pendingRequests.remove(requestId);
                if (removed != null) {
                    removed.future.completeExceptionally(new TimeoutException("Request timed out"));
                }
            }
        });
    }

    @Override
    public void close() {
        running = false;
        scheduledExecutor.shutdown();
        try {
            scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try {
            responseThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (subscription != null) subscription.close();
        if (publication != null) publication.close();
    }

    private static class PendingRequest {
        final CompletableFuture<DirectBuffer> future;
        final long deadline;

        PendingRequest(CompletableFuture<DirectBuffer> future, long deadline) {
            this.future = future;
            this.deadline = deadline;
        }
    }
}
