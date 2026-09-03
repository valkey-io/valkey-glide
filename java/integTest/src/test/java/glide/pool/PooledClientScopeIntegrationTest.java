/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.pool;

import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.pool.ClientPool;
import glide.api.models.pool.ClientPoolConfig;
import glide.api.models.pool.PooledGlideClient;
import glide.api.models.scope.IsolatedScope;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the pool + scope combination. Before this fix, {@link
 * glide.api.GlideClient#fromPoolHandle} did not thread the pool's serialized ConnectionRequest into
 * the pool-borrowed client, so {@code scopedConnection} failed with "Client not connected" even
 * though the underlying core supported per-borrow isolated scopes.
 */
public class PooledClientScopeIntegrationTest {

    private static boolean standaloneAvailable() {
        return STANDALONE_HOSTS.length > 0 && !STANDALONE_HOSTS[0].isEmpty();
    }

    private static ClientPoolConfig standaloneConfig() {
        assumeTrue(standaloneAvailable(), "No standalone endpoints configured");
        String[] parts = STANDALONE_HOSTS[0].split(":");
        return ClientPoolConfig.builder()
                .maxSize(2)
                .minIdle(1)
                .acquireTimeout(Duration.ofSeconds(10))
                .clientConfig(
                        GlideClientConfiguration.builder()
                                .address(
                                        NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build())
                                .requestTimeout(5000)
                                .build())
                .build();
    }

    private static void waitForPoolReady(ClientPool pool, int minIdle) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (pool.getIdleCount() < minIdle && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    @Test
    public void testScopedConnectionFromPooledClient() throws Exception {
        ClientPool pool = ClientPool.create(standaloneConfig());
        waitForPoolReady(pool, 1);

        PooledGlideClient pooled = pool.acquire().get(10, TimeUnit.SECONDS);
        try {
            IsolatedScope scope =
                    pooled.unwrap().scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS);
            assertNotNull(scope, "scopedConnection must return a scope on a pool-borrowed client");
            assertFalse(scope.isReleased());

            // Full WATCH/GET/MULTI/SET/EXEC to prove the scope holds a dedicated connection.
            String key = "pooled-scope-" + UUID.randomUUID();
            pooled.set(key, "10").get(5, TimeUnit.SECONDS);

            assertEquals("OK", scope.watch(key).get(5, TimeUnit.SECONDS));
            String observed = scope.get(key).get(5, TimeUnit.SECONDS);
            assertEquals("10", observed);
            assertEquals("OK", scope.multi().get(5, TimeUnit.SECONDS));
            scope.set(key, String.valueOf(Integer.parseInt(observed) + 1)).get(5, TimeUnit.SECONDS);
            String execResult = scope.exec().get(5, TimeUnit.SECONDS);
            assertNotNull(execResult, "EXEC must succeed with no conflicting writer");

            String finalVal = pooled.get(key).get(5, TimeUnit.SECONDS);
            assertEquals("11", finalVal);

            scope.close();
            assertTrue(scope.isReleased());

            pooled.unwrap().del(new String[] {key}).get(5, TimeUnit.SECONDS);
        } finally {
            pooled.close();
            pool.close();
        }
    }

    /** Build an ASCII string of exactly {@code size} bytes so byte length equals character length. */
    private static String asciiValue(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }

    /**
     * A value larger than the native layer's 16 KB direct-buffer threshold used to come back through
     * a scope as the buffer's debug string. Read across that boundary and confirm the scope returns
     * the whole value, matching a normal client.
     */
    @Test
    public void testScopedConnectionReadsValuesAcrossDirectBufferThreshold() throws Exception {
        ClientPool pool = ClientPool.create(standaloneConfig());
        waitForPoolReady(pool, 1);

        PooledGlideClient pooled = pool.acquire().get(10, TimeUnit.SECONDS);
        try {
            IsolatedScope scope =
                    pooled.unwrap().scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS);
            try {
                int[] sizes = {16 * 1024, 16 * 1024 + 1, 1024 * 1024};
                for (int size : sizes) {
                    String key = "scope-large-" + size + "-" + UUID.randomUUID();
                    String value = asciiValue(size);
                    pooled.set(key, value).get(5, TimeUnit.SECONDS);

                    String viaScope = scope.get(key).get(5, TimeUnit.SECONDS);
                    String viaClient = pooled.get(key).get(5, TimeUnit.SECONDS);

                    assertEquals(size, viaScope.length(), "scope truncated value of size " + size);
                    assertEquals(viaClient, viaScope, "scope value differs from client at size " + size);

                    pooled.unwrap().del(new String[] {key}).get(5, TimeUnit.SECONDS);
                }
            } finally {
                scope.close();
            }
        } finally {
            pooled.close();
            pool.close();
        }
    }

    /**
     * Large array and map replies (LRANGE, MGET, HGETALL) exceed the direct-buffer threshold too.
     * Read them through a scope and confirm every element matches a normal client, not just strings.
     */
    @Test
    public void testScopedConnectionDecodesLargeAggregates() throws Exception {
        ClientPool pool = ClientPool.create(standaloneConfig());
        waitForPoolReady(pool, 1);

        PooledGlideClient pooled = pool.acquire().get(10, TimeUnit.SECONDS);
        try {
            IsolatedScope scope =
                    pooled.unwrap().scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS);
            try {
                String suffix = UUID.randomUUID().toString();

                // LRANGE: a list whose serialized reply is well past the 16 KB threshold.
                String listKey = "scope-list-" + suffix;
                String[] elements = new String[1000];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = "list-element-" + i + "-" + asciiValue(64);
                }
                pooled.unwrap().rpush(listKey, elements).get(5, TimeUnit.SECONDS);

                Object viaScopeList = scope.command("LRANGE", listKey, "0", "-1").get(5, TimeUnit.SECONDS);
                String[] viaClientList = pooled.unwrap().lrange(listKey, 0, -1).get(5, TimeUnit.SECONDS);
                assertTrue(viaScopeList instanceof Object[], "LRANGE must decode to an array");
                Object[] scopeList = (Object[]) viaScopeList;
                assertEquals(viaClientList.length, scopeList.length, "LRANGE length mismatch");
                for (int i = 0; i < scopeList.length; i++) {
                    assertEquals(viaClientList[i], scopeList[i], "LRANGE element " + i + " mismatch");
                }

                // MGET: several large values in one reply.
                String[] keys = new String[20];
                String[] values = new String[keys.length];
                for (int i = 0; i < keys.length; i++) {
                    keys[i] = "scope-mget-" + i + "-" + suffix;
                    values[i] = asciiValue(2048);
                    pooled.set(keys[i], values[i]).get(5, TimeUnit.SECONDS);
                }
                Object viaScopeMget = scope.command("MGET", keys).get(5, TimeUnit.SECONDS);
                String[] viaClientMget = pooled.unwrap().mget(keys).get(5, TimeUnit.SECONDS);
                assertTrue(viaScopeMget instanceof Object[], "MGET must decode to an array");
                Object[] scopeMget = (Object[]) viaScopeMget;
                assertEquals(viaClientMget.length, scopeMget.length, "MGET length mismatch");
                for (int i = 0; i < scopeMget.length; i++) {
                    assertEquals(viaClientMget[i], scopeMget[i], "MGET element " + i + " mismatch");
                }

                // HGETALL: a hash whose serialized map reply is past the threshold.
                String hashKey = "scope-hash-" + suffix;
                LinkedHashMap<String, String> fields = new LinkedHashMap<>();
                for (int i = 0; i < 500; i++) {
                    fields.put("field-" + i, "value-" + i + "-" + asciiValue(64));
                }
                pooled.unwrap().hset(hashKey, fields).get(5, TimeUnit.SECONDS);

                Object viaScopeHash = scope.command("HGETALL", hashKey).get(5, TimeUnit.SECONDS);
                Map<String, String> viaClientHash =
                        pooled.unwrap().hgetall(hashKey).get(5, TimeUnit.SECONDS);
                assertTrue(viaScopeHash instanceof Map, "HGETALL must decode to a map");
                Map<?, ?> scopeHash = (Map<?, ?>) viaScopeHash;
                assertEquals(viaClientHash.size(), scopeHash.size(), "HGETALL size mismatch");
                for (Map.Entry<String, String> entry : viaClientHash.entrySet()) {
                    assertEquals(
                            entry.getValue(), scopeHash.get(entry.getKey()), "HGETALL field " + entry.getKey());
                }

                pooled.unwrap().del(new String[] {listKey, hashKey}).get(5, TimeUnit.SECONDS);
                pooled.unwrap().del(keys).get(5, TimeUnit.SECONDS);
            } finally {
                scope.close();
            }
        } finally {
            pooled.close();
            pool.close();
        }
    }
}
