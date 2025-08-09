/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.json;

/** JsonObjectMapper compatibility interface for Valkey GLIDE wrapper. */
public interface JsonObjectMapper {

    <T> T fromJson(String json, Class<T> clazz);

    String toJson(Object object);
}
