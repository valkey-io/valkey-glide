/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ProtocolVersion;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
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
 * Measures GLIDE's cluster MGET fan-out against three in-process RESP loopback nodes.
 *
 * <p>The nodes only implement the connection commands, {@code CLUSTER SLOTS}, and {@code MGET}
 * needed by this workload. They return pre-built RESP2 values. This deliberately measures GLIDE's
 * Java-to-Rust request path, slot grouping, per-slot command construction, response aggregation,
 * and Java response conversion without a Valkey process or remote-network variability.
 *
 * <p>{@code single} is a same-slot control. The other modes put every key in a distinct slot;
 * {@code multiSingleNode} keeps all of those slots on one node, while {@code multiBalanced}
 * distributes them round-robin across all three nodes.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Threads(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MgetClusterLoopbackBenchmark {

    private static final int CLUSTER_NODE_COUNT = 3;
    private static final int CLUSTER_SLOT_COUNT = 16_384;
    private static final int MAX_KEY_CANDIDATES_PER_KEY = 100_000;
    private static final String MGET_KEY_BYTES_PROPERTY = "glide.benchmark.mgetKeyBytes";

    @Param({"10", "50", "100"})
    public int keyCount;

    // Bytes per returned value: small control plus the currently relevant 2 KiB case.
    @Param({"32", "2048"})
    public int valueBytes;

    @Param({"single", "multiSingleNode", "multiBalanced"})
    public String slotDistribution;

    private String[] keys;
    private GlideClusterClient glideClient;
    private MockClusterRespServer[] servers;

    @Setup
    public void setup() throws Exception {
        servers = new MockClusterRespServer[CLUSTER_NODE_COUNT];
        try {
            for (int index = 0; index < servers.length; index++) {
                servers[index] = new MockClusterRespServer(keyCount, valueBytes);
            }
            byte[] clusterSlotsResponse = createClusterSlotsResponse(servers);
            for (MockClusterRespServer server : servers) {
                server.setClusterSlotsResponse(clusterSlotsResponse);
            }

            keys = createKeys();
            glideClient =
                    GlideClusterClient.createClient(
                                    GlideClusterClientConfiguration.builder()
                                            .address(
                                                    NodeAddress.builder()
                                                            .host("127.0.0.1")
                                                            .port(servers[0].port())
                                                            .build())
                                            .protocol(ProtocolVersion.RESP2)
                                            .requestTimeout(10_000)
                                            .build())
                            .get(10, TimeUnit.SECONDS);
            verifyResponse(glideClient.mget(keys).get(10, TimeUnit.SECONDS));
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
            if (servers != null) {
                for (MockClusterRespServer server : servers) {
                    if (server != null) {
                        server.close();
                    }
                }
            }
        }
    }

    @Benchmark
    public String[] glideMget() {
        return glideClient.mget(keys).join();
    }

    private String[] createKeys() {
        if ("single".equals(slotDistribution)) {
            return createSingleSlotKeys();
        }
        if ("multiSingleNode".equals(slotDistribution)) {
            return createDistinctSlotKeys(false);
        }
        if ("multiBalanced".equals(slotDistribution)) {
            return createDistinctSlotKeys(true);
        }
        throw new IllegalArgumentException("Unsupported slot distribution: " + slotDistribution);
    }

    private String[] createSingleSlotKeys() {
        String[] generatedKeys = new String[keyCount];
        for (int index = 0; index < keyCount; index++) {
            generatedKeys[index] = keyWithConfiguredLength("id" + index + "-{mget-loopback}");
        }
        return generatedKeys;
    }

    private String[] createDistinctSlotKeys(boolean balanceAcrossNodes) {
        String[] generatedKeys = new String[keyCount];
        Set<Integer> usedSlots = new HashSet<>();
        int candidate = 0;
        for (int index = 0; index < keyCount; index++) {
            int targetNode = balanceAcrossNodes ? index % CLUSTER_NODE_COUNT : 0;
            boolean found = false;
            for (int attempt = 0; attempt < MAX_KEY_CANDIDATES_PER_KEY; attempt++) {
                String key = keyWithConfiguredLength("id" + index + "-" + candidate++);
                int slot = slotFor(key);
                if (nodeForSlot(slot) == targetNode && usedSlots.add(slot)) {
                    generatedKeys[index] = key;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException(
                        "Could not generate a distinct slot for benchmark key " + index);
            }
        }
        return generatedKeys;
    }

    private static String keyWithConfiguredLength(String key) {
        int keyBytes = Integer.getInteger(MGET_KEY_BYTES_PROPERTY, 0);
        if (keyBytes == 0) {
            return key;
        }
        if (keyBytes < key.length()) {
            throw new IllegalArgumentException(
                    MGET_KEY_BYTES_PROPERTY
                            + " must be at least "
                            + key.length()
                            + ", got "
                            + keyBytes);
        }

        int hashTagStart = key.indexOf('{');
        StringBuilder padded = new StringBuilder(keyBytes);
        if (hashTagStart >= 0) {
            padded.append(key, 0, hashTagStart);
            while (padded.length() < keyBytes - (key.length() - hashTagStart)) {
                padded.append('x');
            }
            padded.append(key, hashTagStart, key.length());
        } else {
            padded.append(key);
            while (padded.length() < keyBytes) {
                padded.append('x');
            }
        }
        return padded.toString();
    }

    private void verifyResponse(String[] response) {
        if (response.length != keyCount) {
            throw new IllegalStateException("GLIDE MGET returned " + response.length + " values");
        }
        for (int index = 0; index < response.length; index++) {
            String expected = valueFor(index, valueBytes);
            if (!expected.equals(response[index])) {
                throw new IllegalStateException("GLIDE MGET returned an invalid value at " + index);
            }
        }
    }

    private static int slotFor(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.US_ASCII);
        int tagStart = -1;
        int tagEnd = -1;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == '{' && tagStart < 0) {
                tagStart = index + 1;
            } else if (bytes[index] == '}' && tagStart >= 0) {
                tagEnd = index;
                break;
            }
        }
        int start = tagStart >= 0 && tagEnd > tagStart ? tagStart : 0;
        int end = tagStart >= 0 && tagEnd > tagStart ? tagEnd : bytes.length;
        int crc = 0;
        for (int index = start; index < end; index++) {
            crc ^= (bytes[index] & 0xff) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) == 0 ? crc << 1 : (crc << 1) ^ 0x1021;
            }
        }
        return crc & 0x3fff;
    }

    private static int nodeForSlot(int slot) {
        return Math.min(slot * CLUSTER_NODE_COUNT / CLUSTER_SLOT_COUNT, CLUSTER_NODE_COUNT - 1);
    }

    private static byte[] createClusterSlotsResponse(MockClusterRespServer[] servers) {
        ByteArrayOutputStream response = new ByteArrayOutputStream(256);
        writeAscii(response, "*" + CLUSTER_NODE_COUNT + "\r\n");
        for (int index = 0; index < CLUSTER_NODE_COUNT; index++) {
            int start = index * CLUSTER_SLOT_COUNT / CLUSTER_NODE_COUNT;
            int end = (index + 1) * CLUSTER_SLOT_COUNT / CLUSTER_NODE_COUNT - 1;
            writeAscii(response, "*3\r\n:" + start + "\r\n:" + end + "\r\n*3\r\n");
            writeBulkString(response, "127.0.0.1".getBytes(StandardCharsets.US_ASCII));
            writeAscii(response, ":" + servers[index].port() + "\r\n");
            writeBulkString(response, ("loopback-node-" + index).getBytes(StandardCharsets.US_ASCII));
        }
        return response.toByteArray();
    }

    private static String valueFor(int index, int valueBytes) {
        String prefix = "value-" + index + "-";
        StringBuilder value = new StringBuilder(valueBytes);
        while (value.length() < valueBytes) {
            value.append(prefix);
        }
        return value.substring(0, valueBytes);
    }

    private static void writeBulkString(ByteArrayOutputStream output, byte[] value) {
        writeAscii(output, "$" + value.length + "\r\n");
        output.write(value, 0, value.length);
        writeAscii(output, "\r\n");
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes, 0, bytes.length);
    }

    /** A bounded, deterministic RESP2 node used only by this benchmark. */
    private static final class MockClusterRespServer implements AutoCloseable {
        private static final byte[] MGET = "MGET".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] PING = "PING".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] CLIENT = "CLIENT".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] CLUSTER = "CLUSTER".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] SLOTS = "SLOTS".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] OK = "+OK\r\n".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] PONG = "+PONG\r\n".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] UNSUPPORTED_COMMAND =
                "-ERR unsupported command\r\n".getBytes(StandardCharsets.US_ASCII);
        private static final int END_OF_STREAM = -1;
        private static final int UNKNOWN_COMMAND = 0;
        private static final int MGET_COMMAND = 1;
        private static final int PING_COMMAND = 2;
        private static final int CLIENT_COMMAND = 3;
        private static final int CLUSTER_COMMAND = 4;
        private static final int CLUSTER_SLOTS_COMMAND = 5;
        private static final int MAX_ARGUMENTS = 1_024;
        private static final int MAX_BULK_LENGTH = 1_048_576;
        private static final int MAX_CONNECTIONS = 4;
        private static final int DISCARD_BUFFER_BYTES = 8_192;
        private static final int SOCKET_READ_TIMEOUT_MILLIS = 5_000;

        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ServerSocket serverSocket;
        private final ThreadPoolExecutor connections =
                new ThreadPoolExecutor(
                        MAX_CONNECTIONS,
                        MAX_CONNECTIONS,
                        0,
                        TimeUnit.MILLISECONDS,
                        new SynchronousQueue<>());
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final Semaphore connectionSlots = new Semaphore(MAX_CONNECTIONS);
        private final byte[][] singleKeyResponses;
        private final byte[] singleSlotResponse;
        private final Thread acceptThread;
        private volatile byte[] clusterSlotsResponse;

        MockClusterRespServer(int keyCount, int valueBytes) throws IOException {
            this.singleKeyResponses = new byte[keyCount][];
            for (int index = 0; index < keyCount; index++) {
                singleKeyResponses[index] = createMgetResponse(index, 1, valueBytes);
            }
            this.singleSlotResponse = createMgetResponse(0, keyCount, valueBytes);
            this.serverSocket = new ServerSocket(0, MAX_CONNECTIONS, InetAddress.getByName("127.0.0.1"));
            this.acceptThread = new Thread(this::acceptConnections, "mget-cluster-loopback-acceptor");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setClusterSlotsResponse(byte[] response) {
            clusterSlotsResponse = response;
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
                        throw new IllegalStateException("Loopback cluster node accept failed", error);
                    }
                } catch (IOException error) {
                    if (running.get()) {
                        throw new IllegalStateException("Loopback cluster node accept failed", error);
                    }
                }
            }
        }

        private void serve(Socket socket) {
            try (Socket ignored = socket) {
                InputStream input = new BufferedInputStream(socket.getInputStream(), DISCARD_BUFFER_BYTES);
                OutputStream output = socket.getOutputStream();
                byte[] discardBuffer = new byte[DISCARD_BUFFER_BYTES];
                ParsedCommand command = new ParsedCommand();
                while (running.get()) {
                    try {
                        readCommand(input, discardBuffer, command);
                    } catch (SocketTimeoutException timeout) {
                        // Idle control connections are expected during a multi-node MGET workload.
                        continue;
                    }
                    if (command.type == END_OF_STREAM) {
                        return;
                    }

                    if (command.type == MGET_COMMAND) {
                        output.write(responseFor(command));
                    } else if (command.type == PING_COMMAND) {
                        output.write(PONG);
                    } else if (command.type == CLIENT_COMMAND) {
                        output.write(OK);
                    } else if (command.type == CLUSTER_SLOTS_COMMAND) {
                        byte[] response = clusterSlotsResponse;
                        if (response == null) {
                            throw new IOException("CLUSTER SLOTS requested before topology was configured");
                        }
                        output.write(response);
                    } else {
                        output.write(UNSUPPORTED_COMMAND);
                    }
                    output.flush();
                }
            } catch (IOException error) {
                if (running.get()) {
                    throw new IllegalStateException("Loopback cluster node connection failed", error);
                }
            } finally {
                closeTrackedSocket(socket);
            }
        }

        private byte[] responseFor(ParsedCommand command) throws IOException {
            if (command.keyCount == 1
                    && command.firstKeyIndex >= 0
                    && command.firstKeyIndex < singleKeyResponses.length) {
                return singleKeyResponses[command.firstKeyIndex];
            }
            if (command.keyCount == singleKeyResponses.length
                    && command.firstKeyIndex == 0
                    && command.keysAreSequential) {
                return singleSlotResponse;
            }
            throw new IOException(
                    "Unexpected MGET shape: "
                            + command.keyCount
                            + " keys starting at "
                            + command.firstKeyIndex);
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

        private static void readCommand(InputStream input, byte[] discardBuffer, ParsedCommand result)
                throws IOException {
            result.reset();
            int arrayMarker = input.read();
            if (arrayMarker == END_OF_STREAM) {
                result.type = END_OF_STREAM;
                return;
            }
            if (arrayMarker != '*') {
                throw new IOException("Expected RESP array request");
            }

            int argumentCount = readBoundedIntegerLine(input, MAX_ARGUMENTS);
            if (argumentCount < 1) {
                throw new IOException("RESP command requires at least one argument");
            }

            for (int index = 0; index < argumentCount; index++) {
                if (input.read() != '$') {
                    throw new IOException("Expected RESP bulk string argument");
                }
                int bulkLength = readBoundedIntegerLine(input, MAX_BULK_LENGTH);
                if (bulkLength < 0) {
                    throw new IOException("RESP command arguments cannot be null");
                }

                if (index == 0) {
                    result.type = readCommandType(input, bulkLength);
                } else if (result.type == MGET_COMMAND) {
                    int keyIndex = readMgetKeyIndex(input, bulkLength, discardBuffer);
                    if (index == 1) {
                        result.firstKeyIndex = keyIndex;
                    } else {
                        result.keysAreSequential &= keyIndex == result.firstKeyIndex + index - 1;
                    }
                } else if (result.type == CLUSTER_COMMAND && index == 1) {
                    result.type =
                            readEquals(input, bulkLength, SLOTS)
                                    ? CLUSTER_SLOTS_COMMAND
                                    : UNKNOWN_COMMAND;
                } else {
                    discardFully(input, bulkLength, discardBuffer);
                }
                expectCrLf(input);
            }
            if (result.type == MGET_COMMAND) {
                result.keyCount = argumentCount - 1;
            }
        }

        private static int readCommandType(InputStream input, int length) throws IOException {
            boolean isMget = length == MGET.length;
            boolean isPing = length == PING.length;
            boolean isClient = length == CLIENT.length;
            boolean isCluster = length == CLUSTER.length;
            for (int index = 0; index < length; index++) {
                int current = input.read();
                if (current == END_OF_STREAM) {
                    throw new IOException("Unexpected end of RESP command");
                }
                isMget &= isMget && current == MGET[index];
                isPing &= isPing && current == PING[index];
                isClient &= isClient && current == CLIENT[index];
                isCluster &= isCluster && current == CLUSTER[index];
            }
            if (isMget) {
                return MGET_COMMAND;
            }
            if (isPing) {
                return PING_COMMAND;
            }
            if (isClient) {
                return CLIENT_COMMAND;
            }
            return isCluster ? CLUSTER_COMMAND : UNKNOWN_COMMAND;
        }

        private static boolean readEquals(InputStream input, int length, byte[] expected)
                throws IOException {
            boolean matches = length == expected.length;
            for (int index = 0; index < length; index++) {
                int current = input.read();
                if (current == END_OF_STREAM) {
                    throw new IOException("Unexpected end of RESP command");
                }
                matches &= current == (index < expected.length ? expected[index] : -1);
            }
            return matches;
        }

        private static int readMgetKeyIndex(InputStream input, int length, byte[] scratch)
                throws IOException {
            int prefixLength = Math.min(length, scratch.length);
            readFully(input, scratch, prefixLength);
            discardFully(input, length - prefixLength, scratch);
            if (prefixLength < 4 || scratch[0] != 'i' || scratch[1] != 'd') {
                throw new IOException("Unexpected benchmark key");
            }
            int keyIndex = 0;
            int position = 2;
            while (position < prefixLength && scratch[position] >= '0' && scratch[position] <= '9') {
                keyIndex = Math.addExact(Math.multiplyExact(keyIndex, 10), scratch[position] - '0');
                position++;
            }
            if (position == 2 || position == prefixLength || scratch[position] != '-') {
                throw new IOException("Unexpected benchmark key identifier");
            }
            return keyIndex;
        }

        private static int readBoundedIntegerLine(InputStream input, int maximum) throws IOException {
            int current = input.read();
            boolean negative = current == '-';
            if (negative) {
                current = input.read();
            }
            int value = 0;
            boolean hasDigits = false;
            while (current != '\r') {
                if (current < '0' || current > '9') {
                    throw new IOException("Invalid RESP integer");
                }
                int digit = current - '0';
                if (value > (maximum - digit) / 10) {
                    throw new IOException("RESP value exceeds the maximum");
                }
                value = value * 10 + digit;
                hasDigits = true;
                current = input.read();
            }
            if (!hasDigits) {
                throw new IOException("Invalid RESP integer");
            }
            expectByte(input, '\n');
            return negative ? -value : value;
        }

        private static void readFully(InputStream input, byte[] buffer, int length) throws IOException {
            int offset = 0;
            while (offset < length) {
                int read = input.read(buffer, offset, length - offset);
                if (read == END_OF_STREAM) {
                    throw new IOException("Unexpected end of RESP bulk string");
                }
                offset += read;
            }
        }

        private static void discardFully(InputStream input, int length, byte[] buffer) throws IOException {
            int remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read == END_OF_STREAM) {
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

        private static byte[] createMgetResponse(int firstKeyIndex, int keyCount, int valueBytes) {
            ByteArrayOutputStream response = new ByteArrayOutputStream(keyCount * (valueBytes + 16));
            writeAscii(response, "*" + keyCount + "\r\n");
            for (int index = 0; index < keyCount; index++) {
                byte[] value =
                        valueFor(firstKeyIndex + index, valueBytes)
                                .getBytes(StandardCharsets.UTF_8);
                writeBulkString(response, value);
            }
            return response.toByteArray();
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

        private static final class ParsedCommand {
            int type;
            int firstKeyIndex;
            int keyCount;
            boolean keysAreSequential;

            void reset() {
                type = UNKNOWN_COMMAND;
                firstKeyIndex = -1;
                keyCount = 0;
                keysAreSequential = true;
            }
        }
    }
}
