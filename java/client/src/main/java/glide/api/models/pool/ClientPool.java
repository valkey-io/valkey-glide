/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.pool;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.exceptions.ClosingException;
import glide.ffi.resolvers.GlidePoolResolver;
import glide.internal.GlideNativeBridge;
import glide.managers.ConnectionManager;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-instance pool backed by the shared Rust core.
 *
 * <p>Callers borrow a client via {@link #acquire()}, use it for commands, and return it via {@link
 * #release(long)}. The pool handles creation, LIFO reuse, bounded size, and background client
 * creation.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ClientPoolConfig config = ClientPoolConfig.builder()
 *     .maxSize(10)
 *     .minIdle(2)
 *     .clientConfig(GlideClientConfiguration.builder()
 *         .address(NodeAddress.builder().host("localhost").port(6379).build())
 *         .build())
 *     .build();
 *
 * ClientPool pool = ClientPool.create(config);
 * long clientId = pool.acquire().get();
 * GlideClient client = pool.getClient(clientId);
 * client.set("key", "value").get();
 * pool.release(clientId);
 * pool.close();
 * }</pre>
 */
public class ClientPool implements AutoCloseable {

    private static final int RUNNING = 0;
    private static final int CLOSED = 1;

    private final long poolId;
    private final ClientPoolConfig config;
    private final byte[] connectionRequestBytes;
    private final AtomicInteger state = new AtomicInteger(RUNNING);
    private final java.util.concurrent.ConcurrentHashMap<Long, GlideClient> clientCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private ClientPool(long poolId, ClientPoolConfig config, byte[] connectionRequestBytes) {
        this.poolId = poolId;
        this.config = config;
        this.connectionRequestBytes = connectionRequestBytes;
    }

    /**
     * Create a new pool.
     *
     * @param config pool configuration
     * @return the pool (clients are created asynchronously in the background)
     */
    public static ClientPool create(ClientPoolConfig config) {
        config.validate();

        // Reject pubsub subscriptions — pool state reset doesn't UNSUBSCRIBE
        if (config.getClientConfig().getSubscriptionConfiguration() != null) {
            throw new IllegalArgumentException(
                    "Pool clients cannot have pubsub subscriptions configured. "
                            + "Use the main client's pubsub API instead.");
        }

        // Reject a custom IAM credentials provider — it is a Java callback handed to native per
        // client, and glidePoolCreate takes only the request bytes with no way to forward it. Failing
        // here beats silently falling back to the core's default AWS credential chain (a different
        // principal).
        ServerCredentials credentials = config.getClientConfig().getCredentials();
        if (credentials != null
                && credentials.getIamConfig() != null
                && credentials.getIamConfig().getCredentialsProvider() != null) {
            throw new IllegalArgumentException(
                    "Pool clients cannot use a custom IAM credentials provider. "
                            + "Configure IAM without a provider to use the default credential chain.");
        }

        // Reject a custom address resolver for the same reason: it is a Java callback forwarded to
        // native per client, and glidePoolCreate cannot receive it. The connectivity probe below runs
        // the resolver and could pass, but pooled connections would then use the untranslated address
        // and fail at runtime — so fail fast here instead.
        if (config.getClientConfig().getAddressResolver().isPresent()) {
            throw new IllegalArgumentException(
                    "Pool clients cannot use a custom address resolver. "
                            + "Resolve addresses before configuring the pool.");
        }

        // Ahead of the connectivity probe below, so a static-config mistake surfaces as a
        // ConfigurationError naming the real reason instead of a probe failure. The serializer below
        // delegates to ConnectionManager's shared builder, so a pooled request carries the same fields
        // a directly-created client's does.
        ConnectionManager.validateClientAz(config.getClientConfig());

        byte[] connectionRequestBytes = serializeConnectionRequest(config.getClientConfig());

        long poolId =
                GlidePoolResolver.glidePoolCreate(
                        config.getMaxSize(),
                        config.getMinIdle(),
                        config.getIdleTimeout().toMillis(),
                        config.getRequestTimeout().toMillis(),
                        config.getAbandonTimeout().toMillis(),
                        connectionRequestBytes);

        if (poolId == -1) throw new IllegalArgumentException("Invalid pool configuration");
        if (poolId < 0) throw new RuntimeException("Pool creation failed: " + poolId);

        ClientPool pool = new ClientPool(poolId, config, connectionRequestBytes);

        // Connectivity probe: create one client to validate the config eagerly.
        // If this fails, propagate the actual connection error (not a timeout).
        try {
            AutoCloseable probeClient;
            if (config.getClientConfig() instanceof GlideClusterClientConfiguration) {
                probeClient =
                        GlideClusterClient.createClient(
                                        (GlideClusterClientConfiguration) config.getClientConfig())
                                .get(10, TimeUnit.SECONDS);
            } else {
                probeClient =
                        GlideClient.createClient((GlideClientConfiguration) config.getClientConfig())
                                .get(10, TimeUnit.SECONDS);
            }
            probeClient.close();
        } catch (Exception e) {
            pool.close();
            throw new RuntimeException("Pool connectivity probe failed", e);
        }

        return pool;
    }

    /** Get the native pool handle. */
    public long getPoolId() {
        return poolId;
    }

    /**
     * Acquire a pooled client with default timeout. Returns a pool-aware wrapper that returns the
     * client to the pool on close() (try-with-resources safe).
     */
    public CompletableFuture<PooledGlideClient> acquire() {
        return acquire(config.getAcquireTimeout());
    }

    /**
     * Acquire a pooled client with custom timeout. Retries with exponential backoff.
     *
     * <p>The returned {@link PooledGlideClient} implements AutoCloseable — its close() returns the
     * client to the pool instead of destroying the native connection.
     */
    public CompletableFuture<PooledGlideClient> acquire(Duration timeout) {
        if (state.get() != RUNNING) {
            CompletableFuture<PooledGlideClient> f = new CompletableFuture<>();
            f.completeExceptionally(new ClosingException("Pool is closed"));
            return f;
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    long deadlineNanos = System.nanoTime() + timeout.toNanos();
                    long backoffMs = 1;

                    while (System.nanoTime() < deadlineNanos) {
                        if (state.get() != RUNNING) {
                            throw new RuntimeException(new ClosingException("Pool is closed"));
                        }

                        long clientId = GlidePoolResolver.glidePoolTryAcquire(poolId);
                        if (clientId >= 0) {
                            // test_on_borrow: PING to verify connection health
                            if (config.isTestOnBorrow()) {
                                try {
                                    GlideClient client = getClient(clientId);
                                    client.ping().get(2, java.util.concurrent.TimeUnit.SECONDS);
                                } catch (Exception e) {
                                    // Dead connection — release and retry
                                    release(clientId);
                                    continue;
                                }
                            }
                            return new PooledGlideClient(getClient(clientId), ClientPool.this, clientId);
                        }
                        if (clientId == -2) throw new RuntimeException("Pool was destroyed");

                        long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000;
                        long sleepMs = Math.min(backoffMs, Math.max(remainingMs, 0));
                        if (sleepMs <= 0) break;
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Acquire interrupted", e);
                        }
                        backoffMs = Math.min(backoffMs * 2, 50);
                    }

                    throw new RuntimeException(
                            new java.util.concurrent.TimeoutException(
                                    "Pool exhausted: could not acquire within " + timeout));
                });
    }

    /**
     * Get a usable GlideClient for the given client_id. Cached — no allocation on subsequent calls
     * for the same client_id.
     *
     * <p><b>Important:</b> Do NOT call {@code close()} on the returned client. The pool manages the
     * client's lifecycle. Call {@link #release(long)} instead to return the client to the pool.
     */
    public GlideClient getClient(long clientId) {
        GlideClient cached = clientCache.get(clientId);
        if (cached != null) return cached;
        // Resolve the Java-side inflight limiter and request timeout the same way ConnectionManager
        // does for direct clients, so a pooled client fast-fails excess requests and times out
        // commands per its own config instead of the pool's defaults. The wire ConnectionRequest
        // already carries both values; these govern the host-side AsyncRegistry enforcement.
        //
        // AsyncRegistry keys its inflight counter on the raw native handle, and pooled client ids and
        // JNI handles come from independent counters that can collide, so a pooled client colliding
        // with a live ordinary client shares its counter and can be falsely rejected. The core limiter
        // enforces the real limit regardless, until the id spaces are unified.
        Integer configuredLimit = config.getClientConfig().getInflightRequestsLimit();
        int maxInflight =
                configuredLimit != null
                        ? configuredLimit
                        : GlideNativeBridge.getGlideCoreDefaultMaxInflightRequests();
        Integer configuredTimeout = config.getClientConfig().getRequestTimeout();
        long requestTimeoutMs =
                configuredTimeout != null
                        ? configuredTimeout
                        : GlideNativeBridge.getGlideCoreDefaultRequestTimeoutMs();
        // Carry the config's credentials so the borrowed client's IAM guards (refreshIamToken,
        // updateConnectionPassword) see them, as they do on a directly-created client.
        ServerCredentials credentials = config.getClientConfig().getCredentials();
        return clientCache.computeIfAbsent(
                clientId,
                id ->
                        GlideClient.fromPoolHandle(
                                id, maxInflight, requestTimeoutMs, credentials, connectionRequestBytes));
    }

    /** Release a client back to the pool. */
    public void release(long clientId) {
        GlidePoolResolver.glidePoolRelease(poolId, clientId);
    }

    /** Get pool metrics. */
    public int[] getMetrics() {
        return GlidePoolResolver.glidePoolMetrics(poolId);
    }

    public int getIdleCount() {
        int[] m = getMetrics();
        return m != null ? m[0] : 0;
    }

    public int getActiveCount() {
        int[] m = getMetrics();
        return m != null ? m[1] : 0;
    }

    public int getTotalCount() {
        int[] m = getMetrics();
        return m != null ? m[2] : 0;
    }

    @Override
    public void close() {
        if (state.compareAndSet(RUNNING, CLOSED)) {
            GlidePoolResolver.glidePoolDestroy(poolId);
            clientCache.clear();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal: protobuf serialization
    // ═══════════════════════════════════════════════════════════════════

    private static byte[] serializeConnectionRequest(BaseClientConfiguration config) {
        // Delegate to the one shared builder so a pooled client's request cannot drift from a
        // directly-created one.
        return ConnectionManager.buildConnectionRequest(config).toByteArray();
    }
}
