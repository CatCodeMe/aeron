package io.aeron.rpc.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for distributed tracing using OpenTelemetry.
 * Supports multiple exporters including Jaeger and OTLP.
 */
public class TracingConfiguration {
    private String serviceName;
    private ExporterType exporterType = ExporterType.JAEGER;
    private String endpoint;
    private double samplingRate = 1.0;
    private int maxQueueSize = 2048;
    private long exporterTimeoutMs = 30000;
    private boolean exportOnlySampled = true;

    public enum ExporterType {
        JAEGER,
        OTLP
    }

    private TracingConfiguration() {}

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create OpenTelemetry instance with current configuration
     *
     * @return Configured OpenTelemetry instance
     */
    public OpenTelemetry buildOpenTelemetry() {
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, serviceName)));

        SpanExporter spanExporter = createSpanExporter();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(maxQueueSize)
                .setExporterTimeout(exporterTimeoutMs, TimeUnit.MILLISECONDS)
                .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                .build())
            .setResource(resource)
            .setSampler(Sampler.traceIdRatioBased(samplingRate))
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
    }

    private SpanExporter createSpanExporter() {
        switch (exporterType) {
            case JAEGER:
                return JaegerGrpcSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .setTimeout(exporterTimeoutMs, TimeUnit.MILLISECONDS)
                    .build();
            case OTLP:
                return OtlpGrpcSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .setTimeout(exporterTimeoutMs, TimeUnit.MILLISECONDS)
                    .build();
            default:
                throw new IllegalStateException("Unknown exporter type: " + exporterType);
        }
    }

    public static class Builder {
        private final TracingConfiguration config = new TracingConfiguration();

        public Builder serviceName(String serviceName) {
            config.serviceName = serviceName;
            return this;
        }

        public Builder exporterType(ExporterType exporterType) {
            config.exporterType = exporterType;
            return this;
        }

        public Builder endpoint(String endpoint) {
            config.endpoint = endpoint;
            return this;
        }

        public Builder samplingRate(double samplingRate) {
            config.samplingRate = samplingRate;
            return this;
        }

        public Builder maxQueueSize(int maxQueueSize) {
            config.maxQueueSize = maxQueueSize;
            return this;
        }

        public Builder exporterTimeout(long timeoutMs) {
            config.exporterTimeoutMs = timeoutMs;
            return this;
        }

        public Builder exportOnlySampled(boolean exportOnlySampled) {
            config.exportOnlySampled = exportOnlySampled;
            return this;
        }

        public TracingConfiguration build() {
            if (config.serviceName == null) {
                throw new IllegalStateException("Service name must be set");
            }
            if (config.endpoint == null) {
                throw new IllegalStateException("Endpoint must be set");
            }
            return config;
        }
    }
}
