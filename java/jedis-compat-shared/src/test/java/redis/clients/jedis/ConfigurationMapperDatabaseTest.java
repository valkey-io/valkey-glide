/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import glide.api.models.configuration.GlideClientConfiguration;
import org.junit.jupiter.api.Test;

class ConfigurationMapperDatabaseTest {

    /** A non-default database must reach GLIDE as {@code databaseId}, not be silently dropped. */
    @Test
    void databaseSelectionIsMappedOntoDatabaseId() {
        GlideClientConfiguration cfg =
                ConfigurationMapper.mapToGlideConfig(
                        "localhost",
                        6379,
                        DefaultJedisClientConfig.builder().database(6).build(),
                        false);
        assertEquals(Integer.valueOf(6), cfg.getDatabaseId());
    }

    /** The default database must leave {@code databaseId} unset, rather than pinning it to zero. */
    @Test
    void defaultDatabaseLeavesDatabaseIdUnset() {
        GlideClientConfiguration cfg =
                ConfigurationMapper.mapToGlideConfig(
                        "localhost",
                        6379,
                        DefaultJedisClientConfig.builder()
                                .database(Protocol.DEFAULT_DATABASE)
                                .build(),
                        false);
        assertNull(cfg.getDatabaseId());
    }
}
