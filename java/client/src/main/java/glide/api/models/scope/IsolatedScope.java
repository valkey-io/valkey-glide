/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.scope;

import glide.ffi.resolvers.GlideScopeResolver;
import glide.internal.AsyncRegistry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A borrowed dedicated connection for operations requiring per-connection state.
 *
 * <p>Commands bypass the multiplexer and execute on a single TCP connection.
 * Enables WATCH/MULTI/EXEC, CLIENT TRACKING, blocking commands, and pub/sub
 * without interference from other callers.
 *
 * <p><b>Thread Safety:</b> A single IsolatedScope is NOT thread-safe (serial execution).
 * Multiple threads should each acquire their own scope via {@code client.scopedConnection()}.
 *
 * <pre>{@code
 * try (IsolatedScope scope = client.scopedConnection().get()) {
 *     scope.watch("counter").get();
 *     String val = scope.get("counter").get();
 *     scope.multi().get();
 *     scope.set("counter", String.valueOf(Integer.parseInt(val) + 1)).get();
 *     scope.exec().get();
 * }
 * }</pre>
 */
public class IsolatedScope implements AutoCloseable {

    private final long scopeId;
    private final long clientId;
    private final AtomicBoolean released = new AtomicBoolean(false);

    public IsolatedScope(long scopeId, long clientId) {
        this.scopeId = scopeId;
        this.clientId = clientId;
    }

    public long getScopeId() {
        if (released.get()) throw new IllegalStateException("Scope released");
        return scopeId;
    }

    public boolean isReleased() { return released.get(); }

    // ========== Commands ==========

    public CompletableFuture<String> watch(String... keys) { return cmd("WATCH", keys); }
    public CompletableFuture<String> unwatch() { return cmd("UNWATCH"); }
    public CompletableFuture<String> multi() { return cmd("MULTI"); }
    public CompletableFuture<String> exec() { return cmd("EXEC"); }
    public CompletableFuture<String> discard() { return cmd("DISCARD"); }
    public CompletableFuture<String> get(String key) { return cmd("GET", key); }
    public CompletableFuture<String> set(String key, String value) { return cmd("SET", key, value); }
    public CompletableFuture<String> incr(String key) { return cmd("INCR", key); }
    public CompletableFuture<String> ping() { return cmd("PING"); }
    public CompletableFuture<String> select(int db) { return cmd("SELECT", String.valueOf(db)); }

    /** Execute an arbitrary command on this scope. */
    public CompletableFuture<String> executeCommand(String command, String... args) {
        return cmd(command, args);
    }

    public CompletableFuture<String> cmd(String command, String... args) {
        if (released.get()) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException("Scope released"));
            return f;
        }

        CompletableFuture<Object> future = new CompletableFuture<>();
        long callbackId = AsyncRegistry.register(future, 0, clientId, 0);

        byte[] bytes = serialize(command, args);
        int result = GlideScopeResolver.glideScopeExecute(scopeId, bytes, callbackId);

        if (result == -1) future.completeExceptionally(new IllegalStateException("Invalid scope"));
        else if (result == -2) future.completeExceptionally(new IllegalArgumentException("Serialize failed"));

        return future.thenApply(r -> r == null ? null : r.toString());
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            GlideScopeResolver.glideScopeRelease(scopeId, clientId);
        }
    }

    static byte[] serialize(String command, String... args) {
        byte[] cmdBytes = command.getBytes(StandardCharsets.UTF_8);
        int size = 4 + cmdBytes.length + 4;
        byte[][] argBytes = new byte[args.length][];
        for (int i = 0; i < args.length; i++) {
            argBytes[i] = args[i].getBytes(StandardCharsets.UTF_8);
            size += 4 + argBytes[i].length;
        }
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(cmdBytes.length).put(cmdBytes).putInt(args.length);
        for (byte[] a : argBytes) buf.putInt(a.length).put(a);
        return buf.array();
    }
}
