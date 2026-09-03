/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.NodeDiscoveryMode;
import glide.api.models.configuration.ProtocolVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures the complete client-side MGET path against an in-process RESP loopback server.
 *
 * <p>The server is intentionally limited to the connection commands needed by GLIDE plus MGET. It
 * returns pre-built, deterministic RESP2 arrays, so the measurement includes GLIDE's encoding,
 * decoding, completion, and response conversion while excluding Valkey and remote-network
 * variability.
 *
 * <p>Run: {@code GLIDE_DOCKER_SERVERS=true ./gradlew :benchmarks:jmh
 * -PjmhIncludes=MgetLoopbackBenchmark}. The environment variable prevents the integration-test
 * project's global Valkey-process cleanup hook from running.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Threads(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MgetLoopbackBenchmark {

    @Param({"8", "32", "64", "256"})
    public int keyCount;

    // Bytes per returned value: 32 B, 256 B, then 1/2/5/10/25/50/100 KiB.
    @Param({"32", "256", "1024", "2048", "5120", "10240", "25600", "51200", "102400"})
    public int valueBytes;

    private String[] keys;
    private GlideString[] binaryKeys;
    private MockRespServer server;
    private GlideClient glideClient;

    @Setup
    public void setup() throws Exception {
        keys = createKeys(keyCount);
        binaryKeys = createBinaryKeys(keys);
        server = new MockRespServer(keyCount, valueBytes);

        try {
            GlideClientConfiguration glideConfiguration =
                    GlideClientConfiguration.builder()
                            .address(NodeAddress.builder().host("127.0.0.1").port(server.port()).build())
                            .protocol(ProtocolVersion.RESP2)
                            .nodeDiscoveryMode(NodeDiscoveryMode.STATIC)
                            .build();
            glideClient = GlideClient.createClient(glideConfiguration).get(10, TimeUnit.SECONDS);
            verifyResponse(glideClient.mget(keys).get(10, TimeUnit.SECONDS));
            verifyBinaryResponse(glideClient.mget(binaryKeys).get(10, TimeUnit.SECONDS));
        } catch (Exception error) {
            try {
                tearDown();
            } catch (Exception cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    @TearDown
    public void tearDown() throws Exception {
        try {
            if (glideClient != null) {
                glideClient.close();
            }
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }

    @Benchmark
    public String[] glideMget() {
        return glideClient.mget(keys).join();
    }

    @Benchmark
    public GlideString[] glideMgetBinary() {
        return glideClient.mget(binaryKeys).join();
    }

    private String[] createKeys(int count) {
        String[] generatedKeys = new String[count];
        for (int index = 0; index < count; index++) {
            generatedKeys[index] = "key-" + index;
        }
        return generatedKeys;
    }

    private GlideString[] createBinaryKeys(String[] stringKeys) {
        GlideString[] generatedKeys = new GlideString[stringKeys.length];
        for (int index = 0; index < stringKeys.length; index++) {
            generatedKeys[index] = GlideString.of(stringKeys[index]);
        }
        return generatedKeys;
    }

    private void verifyResponse(String[] response) {
        if (response.length != keyCount) {
            throw new IllegalStateException("GLIDE MGET returned " + response.length + " values");
        }
        for (int index = 0; index < response.length; index++) {
            if (!valueFor(index, valueBytes).equals(response[index])) {
                throw new IllegalStateException("GLIDE MGET returned an invalid value at " + index);
            }
        }
    }

    private void verifyBinaryResponse(GlideString[] response) {
        if (response.length != keyCount) {
            throw new IllegalStateException("GLIDE binary MGET returned " + response.length + " values");
        }
        for (int index = 0; index < response.length; index++) {
            String value = new String(response[index].getBytes(), StandardCharsets.UTF_8);
            if (!valueFor(index, valueBytes).equals(value)) {
                throw new IllegalStateException("GLIDE binary MGET returned an invalid value at " + index);
            }
        }
    }

    /** A small, bounded RESP2 server used only by this benchmark. */
    static final class MockRespServer implements AutoCloseable {
        private static final byte[] MGET = "MGET".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] PING = "PING".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] CLIENT = "CLIENT".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] OK = "+OK\r\n".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] PONG = "+PONG\r\n".getBytes(StandardCharsets.US_ASCII);
        private static final int MAX_ARGUMENTS = 1_024;
        private static final int MAX_BULK_LENGTH = 1_048_576;
        private static final int MAX_LINE_LENGTH = 1_024;
        private static final int MAX_CONNECTIONS = 2;
        private static final int SOCKET_READ_TIMEOUT_MILLIS = 5_000;

        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ServerSocket serverSocket;
        private final ThreadPoolExecutor connections =
                new ThreadPoolExecutor(
                        MAX_CONNECTIONS, MAX_CONNECTIONS, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final Semaphore connectionSlots = new Semaphore(MAX_CONNECTIONS);
        private final byte[] mgetResponse;
        private final int expectedKeyCount;
        private final Thread acceptThread;

        MockRespServer(int expectedKeyCount, int valueBytes) throws IOException {
            this.expectedKeyCount = expectedKeyCount;
            this.mgetResponse = createMgetResponse(expectedKeyCount, valueBytes);
            this.serverSocket = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
            this.acceptThread = new Thread(this::acceptConnections, "mget-loopback-acceptor");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void acceptConnections() {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    if (!running.get() || !connectionSlots.tryAcquire()) {
                        socket.close();
                        continue;
                    }
                    sockets.add(socket);
                    try {
                        socket.setTcpNoDelay(true);
                        socket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
                        connections.execute(() -> serve(socket));
                    } catch (IOException | RejectedExecutionException error) {
                        closeTrackedSocket(socket);
                    }
                } catch (SocketException error) {
                    if (running.get()) {
                        throw new IllegalStateException("Loopback server accept failed", error);
                    }
                } catch (IOException error) {
                    if (running.get()) {
                        throw new IllegalStateException("Loopback server accept failed", error);
                    }
                }
            }
        }

        private void serve(Socket socket) {
            try (Socket ignored = socket) {
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                while (running.get()) {
                    byte[] command = readCommand(input);
                    if (command == null) {
                        return;
                    }

                    if (Arrays.equals(command, MGET)) {
                        output.write(mgetResponse);
                    } else if (Arrays.equals(command, PING)) {
                        output.write(PONG);
                    } else if (Arrays.equals(command, CLIENT)) {
                        output.write(OK);
                    } else {
                        output.write("-ERR unsupported command\r\n".getBytes(StandardCharsets.US_ASCII));
                    }
                    output.flush();
                }
            } catch (IOException error) {
                if (running.get()) {
                    throw new IllegalStateException("Loopback server connection failed", error);
                }
            } finally {
                closeTrackedSocket(socket);
            }
        }

        private void closeTrackedSocket(Socket socket) {
            if (sockets.remove(socket)) {
                connectionSlots.release();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // The benchmark server is already closing this connection.
            }
        }

        private byte[] readCommand(InputStream input) throws IOException {
            String arrayHeader = readLine(input);
            if (arrayHeader == null) {
                return null;
            }
            if (!arrayHeader.startsWith("*")) {
                throw new IOException("Expected RESP array request");
            }

            int argumentCount = parseBoundedInteger(arrayHeader.substring(1), MAX_ARGUMENTS);
            if (argumentCount < 1) {
                throw new IOException("RESP command requires at least one argument");
            }

            byte[] command = null;
            for (int index = 0; index < argumentCount; index++) {
                String bulkHeader = readLine(input);
                if (bulkHeader == null || !bulkHeader.startsWith("$")) {
                    throw new IOException("Expected RESP bulk string argument");
                }
                int bulkLength = parseBoundedInteger(bulkHeader.substring(1), MAX_BULK_LENGTH);
                if (bulkLength < 0) {
                    throw new IOException("RESP command arguments cannot be null");
                }

                if (index == 0) {
                    command = readFully(input, bulkLength);
                } else {
                    discardFully(input, bulkLength);
                }
                expectCrLf(input);
            }

            if (Arrays.equals(command, MGET) && argumentCount - 1 != expectedKeyCount) {
                throw new IOException("Unexpected MGET key count: " + (argumentCount - 1));
            }
            return command;
        }

        private static String readLine(InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (line.size() <= MAX_LINE_LENGTH) {
                int current = input.read();
                if (current == -1) {
                    if (line.size() == 0) {
                        return null;
                    }
                    throw new IOException("Unexpected end of RESP line");
                }
                if (current == '\r') {
                    expectByte(input, '\n');
                    return new String(line.toByteArray(), StandardCharsets.US_ASCII);
                }
                line.write(current);
            }
            throw new IOException("RESP line exceeds the maximum length");
        }

        private static int parseBoundedInteger(String value, int maximum) throws IOException {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > maximum) {
                    throw new IOException("RESP value exceeds the maximum: " + parsed);
                }
                return parsed;
            } catch (NumberFormatException error) {
                throw new IOException("Invalid RESP integer: " + value, error);
            }
        }

        private static byte[] readFully(InputStream input, int length) throws IOException {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = input.read(bytes, offset, length - offset);
                if (read == -1) {
                    throw new IOException("Unexpected end of RESP bulk string");
                }
                offset += read;
            }
            return bytes;
        }

        private static void discardFully(InputStream input, int length) throws IOException {
            byte[] buffer = new byte[Math.min(length, 8_192)];
            int remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read == -1) {
                    throw new IOException("Unexpected end of RESP bulk string");
                }
                remaining -= read;
            }
        }

        private static void expectCrLf(InputStream input) throws IOException {
            expectByte(input, '\r');
            expectByte(input, '\n');
        }

        private static void expectByte(InputStream input, int expected) throws IOException {
            if (input.read() != expected) {
                throw new IOException("Invalid RESP terminator");
            }
        }

        private static byte[] createMgetResponse(int keyCount, int valueBytes) {
            ByteArrayOutputStream response = new ByteArrayOutputStream(keyCount * (valueBytes + 16));
            writeAscii(response, "*" + keyCount + "\r\n");
            for (int index = 0; index < keyCount; index++) {
                byte[] value = valueFor(index, valueBytes);
                writeAscii(response, "$" + value.length + "\r\n");
                response.write(value, 0, value.length);
                writeAscii(response, "\r\n");
            }
            return response.toByteArray();
        }

        private static byte[] valueFor(int index, int valueBytes) {
            return MgetLoopbackBenchmark.valueFor(index, valueBytes).getBytes(StandardCharsets.UTF_8);
        }

        private static void writeAscii(ByteArrayOutputStream output, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            output.write(bytes, 0, bytes.length);
        }

        @Override
        public void close() throws IOException, InterruptedException {
            running.set(false);
            serverSocket.close();
            for (Socket socket : sockets) {
                closeTrackedSocket(socket);
            }
            connections.shutdownNow();
            acceptThread.join(TimeUnit.SECONDS.toMillis(5));
            connections.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static String valueFor(int index, int valueBytes) {
        String prefix = "value-" + index + "-";
        StringBuilder value = new StringBuilder(valueBytes);
        while (value.length() < valueBytes) {
            value.append(prefix);
        }
        return value.substring(0, valueBytes);
    }
}
