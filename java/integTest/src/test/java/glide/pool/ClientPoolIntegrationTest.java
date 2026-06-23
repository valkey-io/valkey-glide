/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.pool;

import static org.junit.jupiter.api.Assertions.*;

import glide.TestConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.pool.ClientPool;
import glide.api.models.pool.ClientPoolConfig;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for Feature 1: Client-Instance Pooling.
 * Requires a Valkey server (uses test infrastructure endpoints).
 */
public class ClientPoolIntegrationTest {

    private ClientPoolConfig poolConfig() {
        String hostPort = TestConfiguration.STANDALONE_HOSTS[0];
        String[] parts = hostPort.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        return ClientPoolConfig.builder()
                .maxSize(3)
                .minIdle(1)
                .acquireTimeout(Duration.ofSeconds(10))
                .clientConfig(GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .requestTimeout(5000)
                        .build())
                .build();
    }

    @Test
    public void testPoolCreateAcquireRelease() throws Exception {
        ClientPool pool = ClientPool.create(poolConfig());
        Thread.sleep(3000); // Wait for min_idle warmup

        assertTrue(pool.getIdleCount() >= 1, "Should have at least 1 idle client");

        // acquire() returns PooledGlideClient — try-with-resources returns to pool
        try (glide.api.models.pool.PooledGlideClient client = pool.acquire().get(10, TimeUnit.SECONDS)) {
            assertNotNull(client);
            assertTrue(client.getClientId() > 0, "client_id should be positive");

            String key = "pool-test-" + UUID.randomUUID();
            client.set(key, "hello").get(5, TimeUnit.SECONDS);
            assertEquals("hello", client.get(key).get(5, TimeUnit.SECONDS));
            client.del(new String[]{key}).get(5, TimeUnit.SECONDS);
        } // auto-released back to pool

        pool.close();
        System.out.println("testPoolCreateAcquireRelease PASSED");
    }

    @Test
    public void testPoolReuse() throws Exception {
        ClientPool pool = ClientPool.create(poolConfig());
        Thread.sleep(3000);

        glide.api.models.pool.PooledGlideClient c1 = pool.acquire().get(10, TimeUnit.SECONDS);
        long id1 = c1.getClientId();
        c1.close(); // returns to pool
        Thread.sleep(100);

        glide.api.models.pool.PooledGlideClient c2 = pool.acquire().get(10, TimeUnit.SECONDS);
        long id2 = c2.getClientId();
        assertEquals(id1, id2, "LIFO: same client_id returned after release");
        c2.close();

        pool.close();
        System.out.println("testPoolReuse PASSED");
    }

    @Test
    public void testPoolMetrics() throws Exception {
        ClientPool pool = ClientPool.create(poolConfig());
        Thread.sleep(3000);

        assertTrue(pool.getIdleCount() >= 1);
        assertEquals(0, pool.getActiveCount());

        long clientId = pool.acquire().get(10, TimeUnit.SECONDS).getClientId();
        // After acquire: idle decreases, active increases.
        pool.release(clientId);

        assertTrue(pool.getIdleCount() >= 1, "After release, idle should be >= 1");
        pool.close();
        System.out.println("testPoolMetrics PASSED");
    }

    @Test
    public void testPoolCloseRejectsAcquire() throws Exception {
        ClientPool pool = ClientPool.create(poolConfig());
        Thread.sleep(2000);

        pool.close();

        assertThrows(Exception.class, () -> pool.acquire().get(2, TimeUnit.SECONDS));
        System.out.println("testPoolCloseRejectsAcquire PASSED");
    }
}
