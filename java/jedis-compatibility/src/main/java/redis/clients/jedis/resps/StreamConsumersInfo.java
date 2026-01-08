/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Map;

/**
 * Stream consumers information compatibility class for Valkey GLIDE. Based on original Jedis
 * StreamConsumersInfo.
 *
 * @deprecated Use {@link StreamConsumerInfo}.
 */
@Deprecated
public class StreamConsumersInfo extends StreamConsumerInfo {

    public StreamConsumersInfo(Map<String, Object> map) {
        super(map);
    }
}
