/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.json;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * JSON protocol commands and keywords for Valkey GLIDE compatibility layer. Based on original Jedis
 * JsonProtocol.
 */
public class JsonProtocol {

    public enum JsonCommand implements ProtocolCommand {
        DEL("JSON.DEL"),
        GET("JSON.GET"),
        MGET("JSON.MGET"),
        MERGE("JSON.MERGE"),
        SET("JSON.SET"),
        TYPE("JSON.TYPE"),
        STRAPPEND("JSON.STRAPPEND"),
        STRLEN("JSON.STRLEN"),
        NUMINCRBY("JSON.NUMINCRBY"),
        ARRAPPEND("JSON.ARRAPPEND"),
        ARRINDEX("JSON.ARRINDEX"),
        ARRINSERT("JSON.ARRINSERT"),
        ARRLEN("JSON.ARRLEN"),
        ARRPOP("JSON.ARRPOP"),
        ARRTRIM("JSON.ARRTRIM"),
        CLEAR("JSON.CLEAR"),
        TOGGLE("JSON.TOGGLE"),
        OBJKEYS("JSON.OBJKEYS"),
        OBJLEN("JSON.OBJLEN"),
        DEBUG("JSON.DEBUG"),
        RESP("JSON.RESP");

        private final byte[] raw;

        JsonCommand(String alt) {
            raw = alt.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
