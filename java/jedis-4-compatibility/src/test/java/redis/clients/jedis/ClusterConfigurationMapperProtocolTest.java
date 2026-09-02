/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.ProtocolVersion;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClusterConfigurationMapperProtocolTest {

    @Test
    void jedis4LayerForcesResp2EvenWhenConfigRequestsResp3() {
        Set<HostAndPort> nodes = Collections.singleton(new HostAndPort("localhost", 6379));
        GlideClusterClientConfiguration cfg =
                ClusterConfigurationMapper.mapToGlideClusterConfig(
                        nodes,
                        DefaultJedisClientConfig.builder()
                                .socketTimeoutMillis(2000)
                                .protocol(RedisProtocol.RESP3)
                                .build(),
                        false);
        assertEquals(ProtocolVersion.RESP2, cfg.getProtocol());
    }
}
