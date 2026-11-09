/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import glide.api.GlideClient;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Verifies that every connection borrowed from {@link JedisPool} starts on the pool's configured
 * database, following upstream Jedis {@code JedisFactory.activateObject}. The GLIDE client is
 * mocked, so no server is needed.
 */
class JedisPoolDatabaseRestoreTest {

    private static final int CONFIGURED_DATABASE = 9;
    private static final int OTHER_DATABASE = 15;

    private static final JedisClientConfig CONFIG =
            DefaultJedisClientConfig.builder().database(CONFIGURED_DATABASE).build();

    @Test
    void activateObject_selects_configured_database_when_database_differs() throws Exception {
        GlideClient client = clientAcceptingSelect();
        Jedis jedis = new Jedis(client, CONFIG);
        jedis.select(OTHER_DATABASE);

        newFactory().activateObject(new DefaultPooledObject<>(jedis));

        verify(client).select((long) CONFIGURED_DATABASE);
        assertEquals(CONFIGURED_DATABASE, jedis.getDB());
    }

    @Test
    void activateObject_skips_select_when_database_matches() throws Exception {
        GlideClient client = clientAcceptingSelect();
        Jedis jedis = new Jedis(client, CONFIG);

        newFactory().activateObject(new DefaultPooledObject<>(jedis));

        verify(client, never()).select(anyLong());
        assertEquals(CONFIGURED_DATABASE, jedis.getDB());
    }

    @Test
    void activateObject_throws_when_restore_select_fails() {
        GlideClient client = clientAcceptingSelect();
        when(client.select((long) CONFIGURED_DATABASE)).thenReturn(failedSelect());
        Jedis jedis = new Jedis(client, CONFIG);
        jedis.select(OTHER_DATABASE);

        assertThrows(
                JedisException.class,
                () -> newFactory().activateObject(new DefaultPooledObject<>(jedis)),
                "a failed restore SELECT must stop Commons Pool from lending the connection");
    }

    @Test
    void pool_next_borrower_starts_on_configured_database() {
        GlideClient client = clientAcceptingSelect();
        try (JedisPool pool = new JedisPool(singleConnection(), new MockClientFactory(client))) {
            Jedis first;
            try (Jedis jedis = pool.getResource()) {
                first = jedis;
                jedis.select(OTHER_DATABASE);
            }
            try (Jedis jedis = pool.getResource()) {
                assertSame(first, jedis, "maxTotal=1 must reuse the pooled connection");
                assertEquals(CONFIGURED_DATABASE, jedis.getDB());
            }
            verify(client).select((long) CONFIGURED_DATABASE);
            assertEquals(1, pool.getCreatedCount());
            assertEquals(0, pool.getDestroyedCount());
        }
    }

    @Test
    void pool_replaces_connection_when_restore_select_fails() throws Exception {
        GlideClient failingClient = clientAcceptingSelect();
        when(failingClient.select((long) CONFIGURED_DATABASE)).thenReturn(failedSelect());
        GlideClient freshClient = clientAcceptingSelect();
        try (JedisPool pool =
                new JedisPool(singleConnection(), new MockClientFactory(failingClient, freshClient))) {
            Jedis first;
            try (Jedis jedis = pool.getResource()) {
                first = jedis;
                jedis.select(OTHER_DATABASE);
            }
            try (Jedis jedis = pool.getResource()) {
                assertNotSame(first, jedis, "the connection whose restore failed must not be lent");
                assertEquals(CONFIGURED_DATABASE, jedis.getDB());
            }
            assertEquals(2, pool.getCreatedCount());
            assertEquals(1, pool.getDestroyedCount());
            verify(failingClient).close();
            verify(freshClient, never()).select(anyLong());
        }
    }

    private static GlideJedisFactory newFactory() {
        return new GlideJedisFactory("localhost", 6379, CONFIG);
    }

    private static GlideClient clientAcceptingSelect() {
        GlideClient client = mock(GlideClient.class);
        when(client.select(anyLong())).thenReturn(CompletableFuture.completedFuture("OK"));
        return client;
    }

    private static CompletableFuture<String> failedSelect() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new RequestException("NOPERM SELECT"));
        return future;
    }

    private static GenericObjectPoolConfig<Jedis> singleConnection() {
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(1);
        poolConfig.setJmxEnabled(false);
        return poolConfig;
    }

    /** Pool factory that wraps the given GLIDE client mocks, in order, instead of connecting. */
    private static final class MockClientFactory extends GlideJedisFactory {
        private final Deque<GlideClient> clients;

        MockClientFactory(GlideClient... clients) {
            super("localhost", 6379, CONFIG);
            this.clients = new ArrayDeque<>(Arrays.asList(clients));
        }

        @Override
        public PooledObject<Jedis> makeObject() {
            return new DefaultPooledObject<>(createJedis(clients.removeFirst(), getClientConfig()));
        }
    }
}
