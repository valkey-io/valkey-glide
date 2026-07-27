/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.pool;

import glide.api.GlideClient;
import java.util.concurrent.CompletableFuture;

/**
 * A GlideClient borrowed from a pool. Implements AutoCloseable so that try-with-resources returns
 * the client to the pool instead of destroying it.
 *
 * <pre>{@code
 * try (PooledGlideClient client = pool.acquire().get()) {
 *     client.set("key", "value").get();
 *     String val = client.get("key").get();
 * } // automatically returned to pool
 * }</pre>
 */
public class PooledGlideClient implements AutoCloseable {

    private final GlideClient delegate;
    private final ClientPool pool;
    private final long clientId;
    private volatile boolean released = false;

    PooledGlideClient(GlideClient delegate, ClientPool pool, long clientId) {
        this.delegate = delegate;
        this.pool = pool;
        this.clientId = clientId;
    }

    /** Get the underlying GlideClient for command dispatch. */
    public GlideClient unwrap() {
        if (released) throw new IllegalStateException("Client already returned to pool");
        return delegate;
    }

    /** Get the client_id handle. */
    public long getClientId() {
        return clientId;
    }

    // ========== Delegate common commands for convenience ==========

    public CompletableFuture<String> set(String key, String value) {
        return unwrap().set(key, value);
    }

    public CompletableFuture<String> get(String key) {
        return unwrap().get(key);
    }

    public CompletableFuture<String> ping() {
        return unwrap().ping();
    }

    public CompletableFuture<String> ping(String message) {
        return unwrap().ping(message);
    }

    public CompletableFuture<Long> del(String[] keys) {
        return unwrap().del(keys);
    }

    // ========== AutoCloseable: return to pool ==========

    @Override
    public void close() {
        if (!released) {
            released = true;
            pool.release(clientId);
        }
    }
}
