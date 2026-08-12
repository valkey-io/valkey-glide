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
                                        NodeAddress.builder()
                                                .host(parts[0])
                                                .port(Integer.parseInt(parts[1]))
                                                .build())
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
                    pooled.unwrap()
                            .scopedConnection(Duration.ofSeconds(10))
                            .get(10, TimeUnit.SECONDS);
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
}
