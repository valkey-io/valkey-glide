/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.json;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;

/** JsonProtocol compatibility stub for Valkey GLIDE wrapper. */
public class JsonProtocol {

    public enum JsonCommand implements Rawable {
        JSON_GET,
        JSON_SET,
        JSON_DEL,
        JSON_TYPE;

        private final byte[] raw;

        private JsonCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
