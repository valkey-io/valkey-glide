package io.valkey.glide.jni.benchmarks;

import io.valkey.glide.jni.client.GlideJniClient;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark comparing JNI vs UDS-like implementation.
 * <p>
 * This benchmark compares:
 * 1. JNI implementation with realistic async patterns (CompletableFuture)
 * 2. UDS-like simulation with protobuf serialization overhead
 * 3. Both implementations use the same API patterns for fair comparison
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JniVsUdsBenchmark {

    private GlideJniClient jniClient;
    private UdsSimulationClient udsClient;

    @Setup
    public void setup() {
        try {
            // Initialize JNI client
            jniClient = new GlideJniClient("redis://localhost:6379");

            // Initialize UDS simulation client
            udsClient = new UdsSimulationClient("redis://localhost:6379");

        } catch (Exception e) {
            throw new RuntimeException("Failed to setup benchmark clients", e);
        }
    }

    @TearDown
    public void tearDown() {
        if (jniClient != null) {
            jniClient.close();
        }
        if (udsClient != null) {
            udsClient.close();
        }
    }

    // JNI Implementation Benchmarks

    @Benchmark
    public String jniPing() throws Exception {
        return jniClient.ping().get();
    }

    @Benchmark
    public String jniGet() throws Exception {
        return jniClient.get("benchmark_key").get();
    }

    @Benchmark
    public String jniSet() throws Exception {
        return jniClient.set("benchmark_key", "benchmark_value").get();
    }

    @Benchmark
    public String jniGetSetCycle() throws Exception {
        jniClient.set("cycle_key", "cycle_value").get();
        return jniClient.get("cycle_key").get();
    }

    // UDS Simulation Benchmarks

    @Benchmark
    public String udsPing() throws Exception {
        return udsClient.ping().get();
    }

    @Benchmark
    public String udsGet() throws Exception {
        return udsClient.get("benchmark_key").get();
    }

    @Benchmark
    public String udsSet() throws Exception {
        return udsClient.set("benchmark_key", "benchmark_value").get();
    }

    @Benchmark
    public String udsGetSetCycle() throws Exception {
        udsClient.set("cycle_key", "cycle_value").get();
        return udsClient.get("cycle_key").get();
    }

    // Mixed workload benchmarks

    @Benchmark
    @OperationsPerInvocation(10)
    public void jniMixedWorkload() throws Exception {
        CompletableFuture<?>[] futures = new CompletableFuture[10];

        // Mix of operations to simulate realistic workload
        futures[0] = jniClient.ping();
        futures[1] = jniClient.set("key1", "value1");
        futures[2] = jniClient.get("key1");
        futures[3] = jniClient.set("key2", "value2");
        futures[4] = jniClient.get("key2");
        futures[5] = jniClient.ping();
        futures[6] = jniClient.set("key3", "value3");
        futures[7] = jniClient.get("key3");
        futures[8] = jniClient.get("nonexistent");
        futures[9] = jniClient.ping();

        // Wait for all operations to complete
        CompletableFuture.allOf(futures).get();
    }

    @Benchmark
    @OperationsPerInvocation(10)
    public void udsMixedWorkload() throws Exception {
        CompletableFuture<?>[] futures = new CompletableFuture[10];

        // Same mixed workload for fair comparison
        futures[0] = udsClient.ping();
        futures[1] = udsClient.set("key1", "value1");
        futures[2] = udsClient.get("key1");
        futures[3] = udsClient.set("key2", "value2");
        futures[4] = udsClient.get("key2");
        futures[5] = udsClient.ping();
        futures[6] = udsClient.set("key3", "value3");
        futures[7] = udsClient.get("key3");
        futures[8] = udsClient.get("nonexistent");
        futures[9] = udsClient.ping();

        // Wait for all operations to complete
        CompletableFuture.allOf(futures).get();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JniVsUdsBenchmark.class.getSimpleName())
                .result("benchmark-results.json")
                .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)
                .build();

        new Runner(opt).run();
    }
}
