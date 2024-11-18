package io.aeron.rpc.benchmark;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.rpc.*;
import io.aeron.rpc.channel.RpcChannelConfig;
import io.aeron.rpc.monitoring.MonitoringService;
import io.aeron.rpc.serialization.JsonSerializer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for RPC framework.
 */
@State(Scope.Benchmark)
public class RpcBenchmark {
    private MediaDriver mediaDriver;
    private Aeron aeron;
    private RpcServer server;
    private RpcClient client;
    private MonitoringService monitoringService;

    @Setup
    public void setup() {
        // Start Media Driver
        mediaDriver = MediaDriver.launch(new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .threadingMode(MediaDriver.ThreadingMode.SHARED));

        // Create Aeron instance
        aeron = Aeron.connect(new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        monitoringService = new MonitoringService();

        // Configure channels
        RpcChannelConfig config = RpcChannelConfig.createIpc(1001, RpcPattern.ONE_TO_ONE);

        // Create server
        server = new RpcServer.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .serializer(new JsonSerializer())
            .build();

        // Create client
        client = new RpcClient.Builder()
            .aeron(aeron)
            .channelConfig(config)
            .monitoringService(monitoringService)
            .serializer(new JsonSerializer())
            .build();

        // Register echo service
        server.registerService("echo", request -> CompletableFuture.completedFuture(request));

        // Start server and client
        server.start();
        client.start();
    }

    @TearDown
    public void tearDown() {
        if (client != null) client.close();
        if (server != null) server.close();
        if (aeron != null) aeron.close();
        if (mediaDriver != null) mediaDriver.close();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void measureThroughput(Blackhole blackhole) throws Exception {
        String response = client.call("echo", "test", String.class).get(1, TimeUnit.SECONDS);
        blackhole.consume(response);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void measureLatency(Blackhole blackhole) throws Exception {
        String response = client.call("echo", "test", String.class).get(1, TimeUnit.SECONDS);
        blackhole.consume(response);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void measureLatencyDistribution(Blackhole blackhole) throws Exception {
        String response = client.call("echo", "test", String.class).get(1, TimeUnit.SECONDS);
        blackhole.consume(response);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Group("concurrent")
    @GroupThreads(4)
    public void measureConcurrentThroughput(Blackhole blackhole) throws Exception {
        String response = client.call("echo", "test", String.class).get(1, TimeUnit.SECONDS);
        blackhole.consume(response);
    }

    @Benchmark
    @BenchmarkMode(Mode.All)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void measureLargePayload(Blackhole blackhole) throws Exception {
        // Create 1MB payload
        byte[] payload = new byte[1024 * 1024];
        String response = client.call("echo", payload, String.class).get(1, TimeUnit.SECONDS);
        blackhole.consume(response);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RpcBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            .build();

        new Runner(opt).run();
    }
}
