package io.aeron.rpc.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * RPC tracing support using OpenTelemetry.
 * Provides distributed tracing capabilities for RPC calls across service boundaries.
 */
public class RpcTracer {
    private static final String INSTRUMENTATION_NAME = "io.aeron.rpc";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public RpcTracer(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    /**
     * Start a new client span for an RPC call
     *
     * @param serviceName Service being called
     * @param methodName Method being invoked
     * @return Span context wrapper
     */
    public SpanContext startClientSpan(String serviceName, String methodName) {
        String spanName = String.format("%s/%s", serviceName, methodName);
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(AttributeKey.stringKey("rpc.service"), serviceName)
            .setAttribute(AttributeKey.stringKey("rpc.method"), methodName)
            .startSpan();

        return new SpanContext(span, tracer);
    }

    /**
     * Start a new server span for an RPC call
     *
     * @param serviceName Service handling the call
     * @param methodName Method being invoked
     * @param parentContext Parent context from client
     * @return Span context wrapper
     */
    public SpanContext startServerSpan(String serviceName, String methodName, Context parentContext) {
        String spanName = String.format("%s/%s", serviceName, methodName);
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentContext)
            .setAttribute(AttributeKey.stringKey("rpc.service"), serviceName)
            .setAttribute(AttributeKey.stringKey("rpc.method"), methodName)
            .startSpan();

        return new SpanContext(span, tracer);
    }

    /**
     * Extract trace context from carrier (e.g. message headers)
     *
     * @param carrier Carrier containing trace context
     * @param getter Getter to extract context from carrier
     * @return Extracted context
     */
    public Context extract(Object carrier, TextMapGetter<Object> getter) {
        return openTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), carrier, getter);
    }

    /**
     * Inject trace context into carrier (e.g. message headers)
     *
     * @param context Context to inject
     * @param carrier Carrier to inject context into
     * @param setter Setter to inject context into carrier
     */
    public void inject(Context context, Object carrier, TextMapSetter<Object> setter) {
        openTelemetry.getPropagators().getTextMapPropagator()
            .inject(context, carrier, setter);
    }

    /**
     * Wrapper for span context and scope management
     */
    public static class SpanContext implements AutoCloseable {
        private final Span span;
        private final Tracer tracer;
        private final Scope scope;

        private SpanContext(Span span, Tracer tracer) {
            this.span = span;
            this.tracer = tracer;
            this.scope = span.makeCurrent();
        }

        public void addEvent(String name) {
            span.addEvent(name);
        }

        public void addEvent(String name, Attributes attributes) {
            span.addEvent(name, attributes);
        }

        public void setStatus(StatusCode code, String description) {
            span.setStatus(code, description);
        }

        public void recordException(Throwable throwable) {
            span.recordException(throwable);
        }

        @Override
        public void close() {
            try {
                scope.close();
            } finally {
                span.end();
            }
        }
    }

    /**
     * Trace an async operation
     *
     * @param future Future to trace
     * @param onSuccess Success callback
     * @param onError Error callback
     */
    public <T> void traceAsync(CompletableFuture<T> future, 
                              BiConsumer<SpanContext, T> onSuccess,
                              BiConsumer<SpanContext, Throwable> onError) {
        Context context = Context.current();
        SpanContext spanContext = new SpanContext(Span.current(), tracer);

        future.whenComplete((result, error) -> {
            try (Scope scope = context.makeCurrent()) {
                if (error != null) {
                    onError.accept(spanContext, error);
                } else {
                    onSuccess.accept(spanContext, result);
                }
            }
        });
    }

    /**
     * Create traced wrapper for a function
     *
     * @param operation Operation name
     * @param function Function to trace
     * @return Traced function
     */
    public <T, R> Function<T, R> traceFunction(String operation, Function<T, R> function) {
        return input -> {
            try (SpanContext spanContext = startClientSpan("function", operation)) {
                R result = function.apply(input);
                spanContext.setStatus(StatusCode.OK, "Success");
                return result;
            } catch (Exception e) {
                Span.current().recordException(e);
                Span.current().setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            }
        };
    }
}
