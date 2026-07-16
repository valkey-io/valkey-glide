/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.ProtocolVersion;
import org.junit.jupiter.api.Test;

class ConfigurationMapperProtocolTest {

    @Test
    void jedis4LayerForcesResp2EvenWhenConfigRequestsResp3() {
        GlideClientConfiguration cfg =
                ConfigurationMapper.mapToGlideConfig(
                        "localhost",
                        6379,
                        DefaultJedisClientConfig.builder()
                                .socketTimeoutMillis(2000)
                                .protocol(RedisProtocol.RESP3)
                                .build(),
                        false);
        assertEquals(ProtocolVersion.RESP2, cfg.getProtocol());
    }
}
