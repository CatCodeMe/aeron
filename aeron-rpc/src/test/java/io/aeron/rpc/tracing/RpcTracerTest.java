package io.aeron.rpc.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RpcTracerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private RpcTracer tracer;

    @BeforeEach
    void setUp() {
        OpenTelemetry openTelemetry = otelTesting.getOpenTelemetry();
        tracer = new RpcTracer(openTelemetry);
    }

    @Test
    void testClientServerTrace() {
        // Client side tracing
        try (RpcTracer.SpanContext clientSpan = tracer.startClientSpan("UserService", "getUser")) {
            clientSpan.addEvent("Sending request");
            
            // Simulate RPC call
            simulateNetworkDelay();

            // Server side tracing
            try (RpcTracer.SpanContext serverSpan = tracer.startServerSpan(
                    "UserService", "getUser", otelTesting.getOpenTelemetry().getTracer("")
                        .spanBuilder("test").startSpan().getSpanContext())) {
                
                serverSpan.addEvent("Processing request");
                simulateNetworkDelay();
                serverSpan.addEvent("Request processed");
            }

            clientSpan.addEvent("Response received");
        }

        // Verify traces
        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(2, spans.size());

        SpanData clientSpanData = spans.stream()
            .filter(span -> span.getKind() == SpanKind.CLIENT)
            .findFirst()
            .orElseThrow();

        SpanData serverSpanData = spans.stream()
            .filter(span -> span.getKind() == SpanKind.SERVER)
            .findFirst()
            .orElseThrow();

        // Verify client span
        assertEquals("UserService/getUser", clientSpanData.getName());
        assertEquals(SpanKind.CLIENT, clientSpanData.getKind());
        assertTrue(clientSpanData.getEvents().stream()
            .anyMatch(event -> event.getName().equals("Sending request")));
        assertTrue(clientSpanData.getEvents().stream()
            .anyMatch(event -> event.getName().equals("Response received")));

        // Verify server span
        assertEquals("UserService/getUser", serverSpanData.getName());
        assertEquals(SpanKind.SERVER, serverSpanData.getKind());
        assertTrue(serverSpanData.getEvents().stream()
            .anyMatch(event -> event.getName().equals("Processing request")));
        assertTrue(serverSpanData.getEvents().stream()
            .anyMatch(event -> event.getName().equals("Request processed")));
    }

    @Test
    void testAsyncTrace() {
        CompletableFuture<String> future = new CompletableFuture<>();

        try (RpcTracer.SpanContext parentSpan = tracer.startClientSpan("AsyncService", "asyncOp")) {
            tracer.traceAsync(future,
                (span, result) -> {
                    span.addEvent("Success");
                    span.setStatus(StatusCode.OK, "Operation completed");
                },
                (span, error) -> {
                    span.recordException(error);
                    span.setStatus(StatusCode.ERROR, error.getMessage());
                }
            );

            // Simulate async operation
            CompletableFuture.runAsync(() -> {
                simulateNetworkDelay();
                future.complete("Success");
            });
        }

        // Wait for async operation
        future.join();

        // Verify traces
        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(1, spans.size());

        SpanData asyncSpan = spans.get(0);
        assertEquals("AsyncService/asyncOp", asyncSpan.getName());
        assertEquals(StatusCode.OK, asyncSpan.getStatus().getStatusCode());
    }

    @Test
    void testErrorHandling() {
        Exception testException = new RuntimeException("Test error");

        try (RpcTracer.SpanContext span = tracer.startClientSpan("ErrorService", "errorOp")) {
            span.addEvent("Starting operation");
            span.recordException(testException);
            span.setStatus(StatusCode.ERROR, testException.getMessage());
        }

        // Verify traces
        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(1, spans.size());

        SpanData errorSpan = spans.get(0);
        assertEquals("ErrorService/errorOp", errorSpan.getName());
        assertEquals(StatusCode.ERROR, errorSpan.getStatus().getStatusCode());
        assertEquals("Test error", errorSpan.getStatus().getDescription());
        assertFalse(errorSpan.getEvents().isEmpty());
    }

    @Test
    void testAttributesAndEvents() {
        try (RpcTracer.SpanContext span = tracer.startClientSpan("TestService", "testOp")) {
            // Add custom attributes
            span.addEvent("CustomEvent", Attributes.of(
                AttributeKey.stringKey("custom.key"), "custom value"
            ));

            // Add multiple events
            span.addEvent("Event1");
            simulateNetworkDelay();
            span.addEvent("Event2");
            simulateNetworkDelay();
            span.addEvent("Event3");
        }

        // Verify traces
        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals(4, span.getEvents().size()); // Including custom event
        assertTrue(span.getEvents().stream()
            .anyMatch(event -> event.getName().equals("CustomEvent")));
        assertTrue(span.getEvents().stream()
            .anyMatch(event -> event.getName().equals("Event1")));
        assertTrue(span.getEvents().stream()
            .anyMatch(event -> event.getName().equals("Event2")));
        assertTrue(span.getEvents().stream()
            .anyMatch(event -> event.getName().equals("Event3")));
    }

    private void simulateNetworkDelay() {
        try {
            TimeUnit.MILLISECONDS.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
