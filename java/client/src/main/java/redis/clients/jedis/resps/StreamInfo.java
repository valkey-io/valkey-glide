/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Map;

/** Stream information compatibility class for Valkey GLIDE. Based on original Jedis StreamInfo. */
public class StreamInfo {
    private final Map<String, Object> streamInfo;

    public StreamInfo(Map<String, Object> map) {
        this.streamInfo = map;
    }

    public Map<String, Object> getStreamInfo() {
        return streamInfo;
    }
}
