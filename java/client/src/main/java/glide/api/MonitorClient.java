/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import com.google.protobuf.ByteString;
import connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.NodeAddress;
import connection_request.ConnectionRequestOuterClass.ProtocolVersion;
import connection_request.ConnectionRequestOuterClass.TlsMode;
import glide.api.models.commands.MonitorMsg;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.exceptions.ConnectionException;
import glide.internal.GlideNativeBridge;
import glide.internal.MonitorCallback;
import glide.managers.TlsConfigHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.NonNull;

/**
 * A dedicated client for the MONITOR command. Opens its own connection to the Valkey server and
 * streams all commands processed by the server.
 *
 * <p>Standalone (non-cluster) connections only.
 *
 * <p>Supports two consumption modes:
 *
 * <ul>
 *   <li>Callback mode: supply a {@link Consumer} to {@link #create}; each message is delivered to
 *       the callback on a background thread.
 *   <li>Queue mode: use {@link #getMonitorMessage()} / {@link #tryGetMonitorMessage()} to poll.
 * </ul>
 *
 * @since Valkey 1.0.0.
 */
public class MonitorClient implements AutoCloseable {

    private final long nativeMonitorId;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final BlockingQueue<MonitorMsg> queue;

    private MonitorClient(long nativeMonitorId, BlockingQueue<MonitorMsg> queue) {
        this.nativeMonitorId = nativeMonitorId;
        this.queue = queue;
    }

    /**
     * Creates a new {@link MonitorClient} for the given standalone configuration.
     *
     * @param config The standalone client configuration.
     * @return A new {@link MonitorClient} instance.
     * @throws ConnectionException if the native monitor client cannot be created.
     * @throws NullPointerException if {@code config} is null.
     * @throws IllegalArgumentException Not applicable - cluster configurations are rejected at
     *     compile time since this method only accepts {@link GlideClientConfiguration}.
     */
    public static MonitorClient create(@NonNull GlideClientConfiguration config)
            throws ConnectionException {
        return create(config, null);
    }

    /**
     * Creates a new {@link MonitorClient} with a callback for each monitor message.
     *
     * @param config The standalone client configuration.
     * @param callback Optional callback invoked for each message. If null, messages are queued.
     * @return A new {@link MonitorClient} instance.
     * @throws ConnectionException if the native monitor client cannot be created.
     */
    public static MonitorClient create(
            @NonNull GlideClientConfiguration config, Consumer<MonitorMsg> callback)
            throws ConnectionException {
        byte[] configBytes = serializeConfig(config);

        // Create the queue first so the JNI callback closure captures it directly.
        BlockingQueue<MonitorMsg> queue = new LinkedBlockingQueue<>();
        MonitorCallback jniCallback =
                (timestamp, db, clientAddr, command, argsJson) -> {
                    List<String> args = parseArgsJson(argsJson);
                    MonitorMsg msg = new MonitorMsg(timestamp, db, clientAddr, command, args);
                    if (callback != null) {
                        callback.accept(msg);
                    } else {
                        queue.offer(msg);
                    }
                };

        long monitorId = GlideNativeBridge.createMonitorClient(configBytes, jniCallback);
        if (monitorId == 0) {
            throw new ConnectionException("Failed to create native monitor client");
        }

        return new MonitorClient(monitorId, queue);
    }

    /**
     * Blocks until a monitor message is available.
     *
     * @return The next {@link MonitorMsg}, or {@code null} if the client is closed.
     * @throws InterruptedException if interrupted while waiting.
     */
    public MonitorMsg getMonitorMessage() throws InterruptedException {
        while (!closed.get()) {
            MonitorMsg msg = queue.poll(100, TimeUnit.MILLISECONDS);
            if (msg != null) return msg;
        }
        return null;
    }

    /**
     * Blocks until a monitor message is available or the timeout expires.
     *
     * @param timeoutMs Maximum wait time in milliseconds.
     * @return The next {@link MonitorMsg}, or {@code null} if timed out or closed.
     * @throws InterruptedException if interrupted while waiting.
     */
    public MonitorMsg getMonitorMessage(long timeoutMs) throws InterruptedException {
        if (closed.get()) {
            return null;
        }
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Returns a message immediately if one is available, or {@code null} otherwise. */
    public MonitorMsg tryGetMonitorMessage() {
        if (closed.get()) {
            return null;
        }
        return queue.poll();
    }

    /** Returns {@code true} if {@link #stop()} or {@link #close()} has been called. */
    public boolean isClosed() {
        return closed.get();
    }

    /** Stops monitoring and releases resources. Idempotent. */
    public void stop() {
        if (closed.compareAndSet(false, true)) {
            GlideNativeBridge.closeMonitorClient(nativeMonitorId);
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ---- helpers ----

    private static byte[] serializeConfig(GlideClientConfiguration config) {
        ConnectionRequest.Builder builder = ConnectionRequest.newBuilder();

        for (glide.api.models.configuration.NodeAddress addr : config.getAddresses()) {
            builder.addAddresses(
                    NodeAddress.newBuilder().setHost(addr.getHost()).setPort(addr.getPort()).build());
        }

        boolean insecureTls = TlsConfigHelper.resolveInsecureTls(config);
        if (config.isUseTLS()) {
            builder.setTlsMode(insecureTls ? TlsMode.InsecureTls : TlsMode.SecureTls);
        } else {
            builder.setTlsMode(TlsMode.NoTls);
        }

        byte[] rootCerts = TlsConfigHelper.extractRootCertificates(config);
        if (rootCerts != null) {
            builder.addRootCerts(ByteString.copyFrom(rootCerts));
        }

        ServerCredentials creds = config.getCredentials();
        if (creds != null) {
            AuthenticationInfo.Builder authBuilder = AuthenticationInfo.newBuilder();
            if (creds.getUsername() != null) {
                authBuilder.setUsername(creds.getUsername());
            }
            if (creds.getPassword() != null) {
                authBuilder.setPassword(creds.getPassword());
            }
            builder.setAuthenticationInfo(authBuilder.build());
        }

        if (config.getDatabaseId() != null) {
            builder.setDatabaseId(config.getDatabaseId());
        }

        if (config.getProtocol() != null) {
            if ("RESP2".equals(config.getProtocol().name())) {
                builder.setProtocol(ProtocolVersion.RESP2);
            } else if ("RESP3".equals(config.getProtocol().name())) {
                builder.setProtocol(ProtocolVersion.RESP3);
            }
        }

        return builder.build().toByteArray();
    }

    /**
     * Parse a JSON array string like {@code ["a","b","c"]} into a {@link List}.
     *
     * <p>Uses a simple hand-rolled parser to avoid a JSON library dependency.
     */
    static List<String> parseArgsJson(String json) {
        if (json == null || json.equals("[]")) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        // Strip surrounding brackets
        int i = 1;
        int len = json.length();
        while (i < len - 1) {
            // Skip whitespace / commas
            char c = json.charAt(i);
            if (c == ',' || c == ' ') {
                i++;
                continue;
            }
            if (c == '"') {
                // Read quoted string
                i++; // skip opening quote
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char ch = json.charAt(i++);
                    if (ch == '"') {
                        break;
                    }
                    if (ch == '\\' && i < len) {
                        char escaped = json.charAt(i++);
                        switch (escaped) {
                            case '"':
                                sb.append('"');
                                break;
                            case '\\':
                                sb.append('\\');
                                break;
                            case 'n':
                                sb.append('\n');
                                break;
                            case 'r':
                                sb.append('\r');
                                break;
                            case 't':
                                sb.append('\t');
                                break;
                            case 'u':
                                // Parse 4 hex digits (JSON unicode escape)
                                if (i + 4 <= len) {
                                    String hex = json.substring(i, i + 4);
                                    sb.append((char) Integer.parseInt(hex, 16));
                                    i += 4;
                                }
                                break;
                            default:
                                sb.append(escaped);
                        }
                    } else {
                        sb.append(ch);
                    }
                }
                result.add(sb.toString());
            } else {
                i++;
            }
        }
        return Collections.unmodifiableList(result);
    }
}
