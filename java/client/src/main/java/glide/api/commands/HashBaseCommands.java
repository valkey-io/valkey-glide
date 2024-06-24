/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Hash Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=hash">Hash Commands</a>
 */
public interface HashBaseCommands {
    /** Redis API keyword used to query hash members with their values. */
    String WITH_VALUES_REDIS_API = "WITHVALUES";

    /**
     * Retrieves the value associated with <code>field</code> in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to retrieve from the database.
     * @return The value associated with <code>field</code>, or <code>null</code> when <code>field
     *     </code> is not present in the hash or <code>key</code> does not exist.
     * @example
     *     <pre>{@code
     * String payload = client.hget("my_hash", "field1").get();
     * assert payload.equals("value");
     *
     * String payload = client.hget("my_hash", "nonexistent_field").get();
     * assert payload.equals(null);
     * }</pre>
     */
    CompletableFuture<String> hget(String key, String field);

    /**
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hset/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValueMap A field-value map consisting of fields and their corresponding values to
     *     be set in the hash stored at the specified key.
     * @return The number of fields that were added.
     * @example
     *     <pre>{@code
     * Long num = client.hset("my_hash", Map.of("field", "value", "field2", "value2")).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> hset(String key, Map<String, String> fieldValueMap);

    /**
     * Sets <code>field</code> in the hash stored at <code>key</code> to <code>value</code>, only if
     * <code>field</code> does not yet exist.<br>
     * If <code>key</code> does not exist, a new key holding a hash is created.<br>
     * If <code>field</code> already exists, this operation has no effect.
     *
     * @see <a href="https://redis.io/commands/hsetnx/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to set the value for.
     * @param value The value to set.
     * @return <code>true</code> if the field was set, <code>false</code> if the field already existed
     *     and was not set.
     * @example
     *     <pre>{@code
     * Boolean payload1 = client.hsetnx("myHash", "field", "value").get();
     * assert payload1; // Indicates that the field "field" was set successfully in the hash "myHash".
     *
     * Boolean payload2 = client.hsetnx("myHash", "field", "newValue").get();
     * assert !payload2; // Indicates that the field "field" already existed in the hash "myHash" and was not set again.
     * }</pre>
     */
    CompletableFuture<Boolean> hsetnx(String key, String field, String value);

    /**
     * Removes the specified fields from the hash stored at <code>key</code>. Specified fields that do
     * not exist within this hash are ignored.
     *
     * @see <a href="https://redis.io/commands/hdel/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to remove from the hash stored at <code>key</code>.
     * @return The number of fields that were removed from the hash, not including specified but
     *     non-existing fields.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash and it returns 0.<br>
     * @example
     *     <pre>{@code
     * Long num = client.hdel("my_hash", new String[] {"field1", "field2"}).get();
     * assert num == 2L; //Indicates that two fields were successfully removed from the hash.
     * }</pre>
     */
    CompletableFuture<Long> hdel(String key, String[] fields);

    /**
     * Returns the number of fields contained in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hlen/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return The number of fields in the hash, or <code>0</code> when the key does not exist.<br>
     *     If <code>key</code> holds a value that is not a hash, an error is returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.hlen("myHash").get();
     * assert num1 == 3L;
     *
     * Long num2 = client.hlen("nonExistingKey").get();
     * assert num2 == 0L;
     * }</pre>
     */
    CompletableFuture<Long> hlen(String key);

    /**
     * Returns all values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hvals/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return An <code>array</code> of values in the hash, or an <code>empty array</code> when the
     *     key does not exist.
     * @example
     *     <pre>{@code
     * String[] values = client.hvals("myHash").get();
     * assert values.equals(new String[] {"value1", "value2", "value3"}); // Returns all the values stored in the hash "myHash".
     * }</pre>
     */
    CompletableFuture<String[]> hvals(String key);

    /**
     * Returns the values associated with the specified fields in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hmget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields in the hash stored at <code>key</code> to retrieve from the database.
     * @return An array of values associated with the given fields, in the same order as they are
     *     requested.<br>
     *     For every field that does not exist in the hash, a null value is returned.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash, and it returns an array
     *     of null values.<br>
     * @example
     *     <pre>{@code
     * String[] values = client.hmget("my_hash", new String[] {"field1", "field2"}).get()
     * assert values.equals(new String[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<String[]> hmget(String key, String[] fields);

    /**
     * Returns if <code>field</code> is an existing field in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hexists/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check in the hash stored at <code>key</code>.
     * @return <code>True</code> if the hash contains the specified field. If the hash does not
     *     contain the field, or if the key does not exist, it returns <code>False</code>.
     * @example
     *     <pre>{@code
     * Boolean exists = client.hexists("my_hash", "field1").get();
     * assert exists;
     *
     * Boolean exists = client.hexists("my_hash", "non_existent_field").get();
     * assert !exists;
     * }</pre>
     */
    CompletableFuture<Boolean> hexists(String key, String field);

    /**
     * Returns all fields and values of the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hgetall/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return A <code>Map</code> of fields and their values stored in the hash. Every field name in
     *     the map is associated with its corresponding value.<br>
     *     If <code>key</code> does not exist, it returns an empty map.
     * @example
     *     <pre>{@code
     * Map fieldValueMap = client.hgetall("my_hash").get();
     * assert fieldValueMap.equals(Map.of(field1", "value1", "field2", "value2"));
     * }</pre>
     */
    CompletableFuture<Map<String, String>> hgetall(String key);

    /**
     * Returns all fields and values of the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hgetall/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return A <code>Map</code> of fields and their values stored in the hash. Every field name in
     *     the map is associated with its corresponding value.<br>
     *     If <code>key</code> does not exist, it returns an empty map.
     * @example
     *     <pre>{@code
     * Map fieldValueMap = client.hgetall(gs("my_hash")).get();
     * assert fieldValueMap.equals(Map.of(gs("field1"), gs("value1"), gs("field2"), gs("value2")));
     * }</pre>
     */
    CompletableFuture<Map<GlideString, GlideString>> hgetall(GlideString key);

    /**
     * Increments the number stored at <code>field</code> in the hash stored at <code>key</code> by
     * increment. By using a negative increment value, the value stored at <code>field</code> in the
     * hash stored at <code>key</code> is decremented. If <code>field</code> or <code>key</code> does
     * not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/hincrby/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return The value of <code>field</code> in the hash stored at <code>key</code> after the
     *     increment or decrement.
     * @example
     *     <pre>{@code
     * Long num = client.hincrBy("my_hash", "field1", 5).get();
     * assert num == 5L;
     * }</pre>
     */
    CompletableFuture<Long> hincrBy(String key, String field, long amount);

    /**
     * Increments the number stored at <code>field</code> in the hash stored at <code>key</code> by
     * increment. By using a negative increment value, the value stored at <code>field</code> in the
     * hash stored at <code>key</code> is decremented. If <code>field</code> or <code>key</code> does
     * not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/hincrby/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return The value of <code>field</code> in the hash stored at <code>key</code> after the
     *     increment or decrement.
     * @example
     *     <pre>{@code
     * Long num = client.hincrBy(gs("my_hash"), gs("field1"), 5).get();
     * assert num == 5L;
     * }</pre>
     */
    CompletableFuture<Long> hincrBy(GlideString key, GlideString field, long amount);

    /**
     * Increments the string representing a floating point number stored at <code>field</code> in the
     * hash stored at <code>key</code> by increment. By using a negative increment value, the value
     * stored at <code>field</code> in the hash stored at <code>key</code> is decremented. If <code>
     * field</code> or <code>key</code> does not exist, it is set to 0 before performing the
     * operation.
     *
     * @see <a href="https://redis.io/commands/hincrbyfloat/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return The value of <code>field</code> in the hash stored at <code>key</code> after the
     *     increment or decrement.
     * @example
     *     <pre>{@code
     * Double num = client.hincrByFloat("my_hash", "field1", 2.5).get();
     * assert num == 2.5;
     * }</pre>
     */
    CompletableFuture<Double> hincrByFloat(String key, String field, double amount);

    /**
     * Increments the string representing a floating point number stored at <code>field</code> in the
     * hash stored at <code>key</code> by increment. By using a negative increment value, the value
     * stored at <code>field</code> in the hash stored at <code>key</code> is decremented. If <code>
     * field</code> or <code>key</code> does not exist, it is set to 0 before performing the
     * operation.
     *
     * @see <a href="https://redis.io/commands/hincrbyfloat/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to increment or decrement its
     *     value.
     * @param amount The amount by which to increment or decrement the field's value. Use a negative
     *     value to decrement.
     * @return The value of <code>field</code> in the hash stored at <code>key</code> after the
     *     increment or decrement.
     * @example
     *     <pre>{@code
     * Double num = client.hincrByFloat(gs("my_hash"), gs("field1"), 2.5).get();
     * assert num == 2.5;
     * }</pre>
     */
    CompletableFuture<Double> hincrByFloat(GlideString key, GlideString field, double amount);

    /**
     * Returns all field names in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hkeys/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return An <code>array</code> of field names in the hash, or an <code>empty array</code> when
     *     the key does not exist.
     * @example
     *     <pre>{@code
     * String[] names = client.hkeys("my_hash").get();
     * assert names.equals(new String[] { "field_1", "field_2" });
     * }</pre>
     */
    CompletableFuture<String[]> hkeys(String key);

    /**
     * Returns the string length of the value associated with <code>field</code> in the hash stored at
     * <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hstrlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash.
     * @return The string length or <code>0</code> if <code>field</code> or <code>key</code> does not
     *     exist.
     * @example
     *     <pre>{@code
     * Long strlen = client.hstrlen("my_hash", "my_field").get();
     * assert strlen >= 0L;
     * }</pre>
     */
    CompletableFuture<Long> hstrlen(String key, String field);

    /**
     * Returns a random field name from the hash value stored at <code>key</code>.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/hrandfield/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return A random field name from the hash stored at <code>key</code>, or <code>null</code> when
     *     the key does not exist.
     * @example
     *     <pre>{@code
     * String field = client.hrandfield("my_hash").get();
     * System.out.printf("A random field from the hash is '%s'", field);
     * }</pre>
     */
    CompletableFuture<String> hrandfield(String key);

    /**
     * Retrieves up to <code>count</code> random field names from the hash value stored at <code>key
     * </code>.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/hrandfield/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param count The number of field names to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.
     * @return An <code>array</code> of random field names from the hash stored at <code>key</code>,
     *     or an <code>empty array</code> when the key does not exist.
     * @example
     *     <pre>{@code
     * String[] fields = client.hrandfieldWithCount("my_hash", 10).get();
     * System.out.printf("Random fields from the hash are '%s'", String.join(", ", fields));
     * }</pre>
     */
    CompletableFuture<String[]> hrandfieldWithCount(String key, long count);

    /**
     * Retrieves up to <code>count</code> random field names along with their values from the hash
     * value stored at <code>key</code>.
     *
     * @since Redis 6.2 and above.
     * @see <a href="https://redis.io/commands/hrandfield/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param count The number of field names to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.
     * @return A 2D <code>array</code> of <code>[fieldName, value]</code> <code>arrays</code>, where
     *     <code>fieldName</code> is a random field name from the hash and <code>value</code> is the
     *     associated value of the field name.<br>
     *     If the hash does not exist or is empty, the response will be an empty <code>array</code>.
     * @example
     *     <pre>{@code
     * String[][] fields = client.hrandfieldWithCountWithValues("my_hash", 1).get();
     * System.out.printf("A random field from the hash is '%s' and the value is '%s'", fields[0][0], fields[0][1]);
     * }</pre>
     */
    CompletableFuture<String[][]> hrandfieldWithCountWithValues(String key, long count);
}
