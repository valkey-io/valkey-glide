package io.valkey.glide.jni.benchmarks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.ByteBuffer;

/**
 * UDS simulation client that replicates the overhead of the current implementation.
 * <p>
 * This class simulates the key overhead components of the UDS implementation:
 * 1. Protobuf serialization/deserialization
 * 2. Socket communication latency
 * 3. Thread context switching
 * 4. Multiple buffer copies
 * 5. Async callback dispatch
 * <p>
 * The goal is to provide a realistic baseline for comparing JNI performance
 * without actually requiring a full UDS setup.
 */
public class UdsSimulationClient implements AutoCloseable {

    private final String connectionString;
    private volatile boolean closed = false;
    private final AtomicLong requestCounter = new AtomicLong(0);

    // Simulate realistic processing delays
    private static final int PROTOBUF_SERIALIZATION_DELAY_NANOS = 1000;  // 1μs
    private static final int SOCKET_IO_DELAY_NANOS = 2000;              // 2μs
    private static final int CONTEXT_SWITCH_DELAY_NANOS = 500;          // 0.5μs
    private static final int BUFFER_COPY_DELAY_NANOS = 300;             // 0.3μs

    public UdsSimulationClient(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * Simulate GET command with UDS overhead.
     */
    public CompletableFuture<String> get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        checkNotClosed();

        return CompletableFuture.supplyAsync(() -> {
            long requestId = requestCounter.incrementAndGet();

            // Simulate protobuf command serialization
            byte[] commandProtobuf = simulateProtobufSerialization("GET", key, null);
            simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);

            // Simulate socket write + context switch
            simulateSocketWrite(commandProtobuf);
            simulateDelay(SOCKET_IO_DELAY_NANOS + CONTEXT_SWITCH_DELAY_NANOS);

            // Simulate Rust-side processing
            simulateRustProcessing(commandProtobuf);

            // Simulate socket read + context switch
            byte[] responseProtobuf = simulateSocketRead();
            simulateDelay(SOCKET_IO_DELAY_NANOS + CONTEXT_SWITCH_DELAY_NANOS);

            // Simulate protobuf response deserialization
            simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);
            String result = simulateProtobufDeserialization(responseProtobuf);

            return result;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Simulate SET command with UDS overhead.
     */
    public CompletableFuture<String> set(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }

        checkNotClosed();

        return CompletableFuture.supplyAsync(() -> {
            long requestId = requestCounter.incrementAndGet();

            // Simulate protobuf command serialization (larger payload for SET)
            byte[] commandProtobuf = simulateProtobufSerialization("SET", key, value);
            simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);

            // Simulate socket write + context switch
            simulateSocketWrite(commandProtobuf);
            simulateDelay(SOCKET_IO_DELAY_NANOS + CONTEXT_SWITCH_DELAY_NANOS);

            // Simulate Rust-side processing
            simulateRustProcessing(commandProtobuf);

            // Simulate socket read + context switch
            byte[] responseProtobuf = simulateSocketRead();
            simulateDelay(SOCKET_IO_DELAY_NANOS + CONTEXT_SWITCH_DELAY_NANOS);

            // Simulate protobuf response deserialization
            simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);
            String result = simulateProtobufDeserialization(responseProtobuf);

            return result;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Simulate PING command with UDS overhead.
     */
    public CompletableFuture<String> ping() {
        checkNotClosed();

        return CompletableFuture.supplyAsync(() -> {
            long requestId = requestCounter.incrementAndGet();

            // Simulate protobuf command serialization (minimal payload)
            byte[] commandProtobuf = simulateProtobufSerialization("PING", null, null);
            simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);

            // Simulate socket write + context switch
            simulateSocketWrite(commandProtobuf);
            simulateDelay(SOCKET_IO_DELAY_NANOS + CONTEXT_SWITCH_DELAY_NANOS);

            // Simulate Rust-side processing (minimal for PING)
            simulateRustProcessing(commandProtobuf);

            // Simulate socket read + context switch
            byte[] responseProtobuf = simulateSocketRead();
            simulateDelay(SOCKET_IO_DELAY_NANOS + CONTEXT_SWITCH_DELAY_NANOS);

            // Simulate protobuf response deserialization
            simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);
            String result = simulateProtobufDeserialization(responseProtobuf);

            return result;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Simulate protobuf serialization overhead.
     */
    private byte[] simulateProtobufSerialization(String command, String key, String value) {
        // Create a realistic protobuf-sized message
        int size = 50; // Base protobuf overhead
        if (key != null) size += key.length() + 10;
        if (value != null) size += value.length() + 10;

        byte[] protobuf = new byte[size];

        // Simulate serialization work - write some data
        ByteBuffer buffer = ByteBuffer.wrap(protobuf);
        buffer.putInt(command.hashCode());
        if (key != null) {
            buffer.putInt(key.length());
            buffer.put(key.getBytes(), 0, Math.min(key.length(), buffer.remaining() - 4));
        }
        if (value != null && buffer.remaining() > 4) {
            buffer.putInt(value.length());
            buffer.put(value.getBytes(), 0, Math.min(value.length(), buffer.remaining()));
        }

        return protobuf;
    }

    /**
     * Simulate socket write with buffer copying.
     */
    private void simulateSocketWrite(byte[] data) {
        // Simulate multiple buffer copies like Netty would do
        byte[] copy1 = new byte[data.length];
        System.arraycopy(data, 0, copy1, 0, data.length);
        simulateDelay(BUFFER_COPY_DELAY_NANOS);

        byte[] copy2 = new byte[data.length];
        System.arraycopy(copy1, 0, copy2, 0, data.length);
        simulateDelay(BUFFER_COPY_DELAY_NANOS);
    }

    /**
     * Simulate Rust-side processing.
     */
    private void simulateRustProcessing(byte[] commandData) {
        // Simulate protobuf parsing on Rust side
        simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);

        // Simulate actual Redis command execution
        // (This would be the same for both JNI and UDS)
        simulateDelay(1000); // 1μs for Redis operation

        // Simulate response protobuf creation on Rust side
        simulateDelay(PROTOBUF_SERIALIZATION_DELAY_NANOS);
    }

    /**
     * Simulate socket read with buffer copying.
     */
    private byte[] simulateSocketRead() {
        // Create mock response protobuf
        byte[] response = new byte[30]; // Typical response size

        // Simulate multiple buffer copies during read
        byte[] copy1 = new byte[response.length];
        System.arraycopy(response, 0, copy1, 0, response.length);
        simulateDelay(BUFFER_COPY_DELAY_NANOS);

        return copy1;
    }

    /**
     * Simulate protobuf deserialization to Java objects.
     */
    private String simulateProtobufDeserialization(byte[] protobuf) {
        // Simulate parsing protobuf response
        ByteBuffer buffer = ByteBuffer.wrap(protobuf);

        // Mock responses based on typical Redis responses
        return "mock_response";
    }

    /**
     * Simulate processing delay using busy wait for accuracy.
     */
    private void simulateDelay(int nanos) {
        long start = System.nanoTime();
        while (System.nanoTime() - start < nanos) {
            // Busy wait for accurate timing
            Thread.onSpinWait();
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Client is closed");
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
