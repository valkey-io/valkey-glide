/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.json;

/** DefaultGsonObjectMapper compatibility stub for Valkey GLIDE wrapper. */
public class DefaultGsonObjectMapper implements JsonObjectMapper {

    @Override
    public <T> T fromJson(String json, Class<T> clazz) {
        throw new UnsupportedOperationException(
                "JSON operations not implemented in compatibility layer");
    }

    @Override
    public String toJson(Object object) {
        throw new UnsupportedOperationException(
                "JSON operations not implemented in compatibility layer");
    }
}
