/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.HGetExOptions;
import glide.api.models.commands.HSetExOptions;
import glide.api.models.commands.HashFieldExpirationConditionOptions;
import glide.api.models.commands.scan.HScanOptions;
import glide.api.models.commands.scan.HScanOptionsBinary;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Hash Commands" group for standalone and cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=hash">Hash Commands</a>
 */
public interface HashBaseCommands {
    /** Valkey API keyword used to query hash members with their values. */
    String WITH_VALUES_VALKEY_API = "WITHVALUES";

    /** Valkey API keyword used to specify fields in hash commands. */
    String FIELDS_VALKEY_API = "FIELDS";

    /**
     * Retrieves the value associated with <code>field</code> in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hget/">valkey.io</a> for details.
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
     * Retrieves the value associated with <code>field</code> in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to retrieve from the database.
     * @return The value associated with <code>field</code>, or <code>null</code> when <code>field
     *     </code> is not present in the hash or <code>key</code> does not exist.
     * @example
     *     <pre>{@code
     * String payload = client.hget(gs("my_hash"), gs("field1")).get();
     * assert payload.equals(gs("value"));
     *
     * String payload = client.hget(gs("my_hash"), gs("nonexistent_field")).get();
     * assert payload.equals(null);
     * }</pre>
     */
    CompletableFuture<GlideString> hget(GlideString key, GlideString field);

    /**
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hset/">valkey.io</a> for details.
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
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hset/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValueMap A field-value map consisting of fields and their corresponding values to
     *     be set in the hash stored at the specified key.
     * @return The number of fields that were added.
     * @example
     *     <pre>{@code
     * Long num = client.hset(gs("my_hash"), Map.of(gs("field"), gs("value"), gs("field2"), gs("value2"))).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> hset(GlideString key, Map<GlideString, GlideString> fieldValueMap);

    /**
     * Sets <code>field</code> in the hash stored at <code>key</code> to <code>value</code>, only if
     * <code>field</code> does not yet exist.<br>
     * If <code>key</code> does not exist, a new key holding a hash is created.<br>
     * If <code>field</code> already exists, this operation has no effect.
     *
     * @see <a href="https://valkey.io/commands/hsetnx/">valkey.io</a> for details.
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
     * Sets <code>field</code> in the hash stored at <code>key</code> to <code>value</code>, only if
     * <code>field</code> does not yet exist.<br>
     * If <code>key</code> does not exist, a new key holding a hash is created.<br>
     * If <code>field</code> already exists, this operation has no effect.
     *
     * @see <a href="https://valkey.io/commands/hsetnx/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to set the value for.
     * @param value The value to set.
     * @return <code>true</code> if the field was set, <code>false</code> if the field already existed
     *     and was not set.
     * @example
     *     <pre>{@code
     * Boolean payload1 = client.hsetnx(gs("myHash"), gs("field"), gs("value")).get();
     * assert payload1; // Indicates that the field "field" was set successfully in the hash "myHash".
     *
     * Boolean payload2 = client.hsetnx(gs("myHash"), gs("field"), gs("newValue")).get();
     * assert !payload2; // Indicates that the field "field" already existed in the hash "myHash" and was not set again.
     * }</pre>
     */
    CompletableFuture<Boolean> hsetnx(GlideString key, GlideString field, GlideString value);

    /**
     * Removes the specified fields from the hash stored at <code>key</code>. Specified fields that do
     * not exist within this hash are ignored.
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
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
     * Removes the specified fields from the hash stored at <code>key</code>. Specified fields that do
     * not exist within this hash are ignored.
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to remove from the hash stored at <code>key</code>.
     * @return The number of fields that were removed from the hash, not including specified but
     *     non-existing fields.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash and it returns 0.<br>
     * @example
     *     <pre>{@code
     * Long num = client.hdel("my_hash", new String[] {gs("field1"), gs("field2")}).get();
     * assert num == 2L; //Indicates that two fields were successfully removed from the hash.
     * }</pre>
     */
    CompletableFuture<Long> hdel(GlideString key, GlideString[] fields);

    /**
     * Returns the number of fields contained in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
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
     * Returns the number of fields contained in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return The number of fields in the hash, or <code>0</code> when the key does not exist.<br>
     *     If <code>key</code> holds a value that is not a hash, an error is returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.hlen(gs("myHash")).get();
     * assert num1 == 3L;
     *
     * Long num2 = client.hlen(gs("nonExistingKey")).get();
     * assert num2 == 0L;
     * }</pre>
     */
    CompletableFuture<Long> hlen(GlideString key);

    /**
     * Returns all values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hvals/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return An <code>array</code> of values in the hash, or an <code>empty array</code> when the
     *     key does not exist.
     * @example
     *     <pre>{@code
     * String[] values = client.hvals("myHash").get();
     * assert Arrays.equals(values, new String[] {"value1", "value2", "value3"}); // Returns all the values stored in the hash "myHash".
     * }</pre>
     */
    CompletableFuture<String[]> hvals(String key);

    /**
     * Returns all values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hvals/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return An <code>array</code> of values in the hash, or an <code>empty array</code> when the
     *     key does not exist.
     * @example
     *     <pre>{@code
     * GlideString[] values = client.hvals(gs("myHash")).get();
     * assert Arrays.equals(values, new GlideString[] {gs("value1"), gs("value2"), gs("value3")}); // Returns all the values stored in the hash "myHash".
     * }</pre>
     */
    CompletableFuture<GlideString[]> hvals(GlideString key);

    /**
     * Returns the values associated with the specified fields in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
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
     * assert Arrays.equals(values, new String[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<String[]> hmget(String key, String[] fields);

    /**
     * Returns the values associated with the specified fields in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields in the hash stored at <code>key</code> to retrieve from the database.
     * @return An array of values associated with the given fields, in the same order as they are
     *     requested.<br>
     *     For every field that does not exist in the hash, a null value is returned.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash, and it returns an array
     *     of null values.<br>
     * @example
     *     <pre>{@code
     * GlideString[] values = client.hmget(gs("my_hash"), new GlideString[] {gs("field1"), gs("field2")}).get()
     * assert Arrays.equals(values, new GlideString[] {gs("value1"), gs("value2")});
     * }</pre>
     */
    CompletableFuture<GlideString[]> hmget(GlideString key, GlideString[] fields);

    /**
     * Returns if <code>field</code> is an existing field in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
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
     * Returns if <code>field</code> is an existing field in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check in the hash stored at <code>key</code>.
     * @return <code>True</code> if the hash contains the specified field. If the hash does not
     *     contain the field, or if the key does not exist, it returns <code>False</code>.
     * @example
     *     <pre>{@code
     * Boolean exists = client.hexists(gs("my_hash"), gs("field1")).get();
     * assert exists;
     *
     * Boolean exists = client.hexists(gs("my_hash"), gs("non_existent_field")).get();
     * assert !exists;
     * }</pre>
     */
    CompletableFuture<Boolean> hexists(GlideString key, GlideString field);

    /**
     * Returns all fields and values of the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hgetall/">valkey.io</a> for details.
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
     * @see <a href="https://valkey.io/commands/hgetall/">valkey.io</a> for details.
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
     * @see <a href="https://valkey.io/commands/hincrby/">valkey.io</a> for details.
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
     * @see <a href="https://valkey.io/commands/hincrby/">valkey.io</a> for details.
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
     * @see <a href="https://valkey.io/commands/hincrbyfloat/">valkey.io</a> for details.
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
     * @see <a href="https://valkey.io/commands/hincrbyfloat/">valkey.io</a> for details.
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
     * assert Arrays.equals(names, new String[] { "field_1", "field_2" });
     * }</pre>
     */
    CompletableFuture<String[]> hkeys(String key);

    /**
     * Returns all field names in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hkeys/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return An <code>array</code> of field names in the hash, or an <code>empty array</code> when
     *     the key does not exist.
     * @example
     *     <pre>{@code
     * GlideString[] names = client.hkeys(gs("my_hash")).get();
     * assert Arrays.equals(names, new GlideString[] { gs("field_1"), gs("field_2") });
     * }</pre>
     */
    CompletableFuture<GlideString[]> hkeys(GlideString key);

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
     * Long strlen = client.hstrlen(gs("my_hash"), gs("my_field")).get();
     * assert strlen >= 0L;
     * }</pre>
     */
    CompletableFuture<Long> hstrlen(GlideString key, GlideString field);

    /**
     * Returns a random field name from the hash value stored at <code>key</code>.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/hrandfield/">valkey.io</a> for details.
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
     * Returns a random field name from the hash value stored at <code>key</code>.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/hrandfield/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return A random field name from the hash stored at <code>key</code>, or <code>null</code> when
     *     the key does not exist.
     * @example
     *     <pre>{@code
     * GlideString field = client.hrandfield(gs("my_hash")).get();
     * System.out.printf("A random field from the hash is '%s'", field);
     * }</pre>
     */
    CompletableFuture<GlideString> hrandfield(GlideString key);

    /**
     * Retrieves up to <code>count</code> random field names from the hash value stored at <code>key
     * </code>.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/hrandfield/">valkey.io</a> for details.
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
     * Retrieves up to <code>count</code> random field names from the hash value stored at <code>key
     * </code>.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/hrandfield/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param count The number of field names to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.
     * @return An <code>array</code> of random field names from the hash stored at <code>key</code>,
     *     or an <code>empty array</code> when the key does not exist.
     * @example
     *     <pre>{@code
     * GlideString[] fields = client.hrandfieldWithCount(gs("my_hash"), 10).get();
     * System.out.printf("Random fields from the hash are '%s'", GlideString.join(", ", fields));
     * }</pre>
     */
    CompletableFuture<GlideString[]> hrandfieldWithCount(GlideString key, long count);

    /**
     * Retrieves up to <code>count</code> random field names along with their values from the hash
     * value stored at <code>key</code>.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/hrandfield/">valkey.io</a> for details.
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

    /**
     * Retrieves up to <code>count</code> random field names along with their values from the hash
     * value stored at <code>key</code>.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/hrandfield/">valkey.io</a> for details.
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
     * GlideString[][] fields = client.hrandfieldWithCountWithValues(gs("my_hash"), 1).get();
     * System.out.printf("A random field from the hash is '%s' and the value is '%s'", fields[0][0], fields[0][1]);
     * }</pre>
     */
    CompletableFuture<GlideString[][]> hrandfieldWithCountWithValues(GlideString key, long count);

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @see <a href="https://valkey.io/commands/hscan">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the result. The second element is always an
     *     <code>Array</code> of the subset of the hash held in <code>key</code>. The array in the
     *     second element is always a flattened series of <code>String</code> pairs, where the key is
     *     at even indices and the value is at odd indices.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * String cursor = "0";
     * Object[] result;
     * do {
     *   result = client.hscan(key1, cursor).get();
     *   cursor = result[0].toString();
     *   Object[] stringResults = (Object[]) result[1];
     *
     *   System.out.println("\nHSCAN iteration:");
     *   for (int i = 0; i < stringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", stringResults[i], stringResults[i + 1]);
     *     if (i + 2 < stringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> hscan(String key, String cursor);

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @see <a href="https://valkey.io/commands/hscan">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the result. The second element is always an
     *     <code>Array</code> of the subset of the hash held in <code>key</code>. The array in the
     *     second element is always a flattened series of <code>String</code> pairs, where the key is
     *     at even indices and the value is at odd indices.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * GlideString cursor = gs("0");
     * Object[] result;
     * do {
     *   result = client.hscan(key1, cursor).get();
     *   cursor = gs(result[0].toString());
     *   Object[] glideStringResults = (Object[]) result[1];
     *
     *   System.out.println("\nHSCAN iteration:");
     *   for (int i = 0; i < glideStringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", glideStringResults[i], glideStringResults[i + 1]);
     *     if (i + 2 < glideStringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> hscan(GlideString key, GlideString cursor);

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @see <a href="https://valkey.io/commands/hscan">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param hScanOptions The {@link HScanOptions}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the result. The second element is always an
     *     <code>Array</code> of the subset of the hash held in <code>key</code>. The array in the
     *     second element is a flattened series of <code>String</code> pairs, where the key is at even
     *     indices and the value is at odd indices. If {@link HScanOptions#noValues()} is set to
     *     <code>true
     *     </code>, the second element will only contain the fields without the values.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * String cursor = "0";
     * Object[] result;
     * do {
     *   result = client.hscan(key1, cursor, HScanOptions.builder().matchPattern("*").count(20L).build()).get();
     *   cursor = result[0].toString();
     *   Object[] stringResults = (Object[]) result[1];
     *
     *   System.out.println("\nHSCAN iteration:");
     *   for (int i = 0; i < stringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", stringResults[i], stringResults[i + 1]);
     *     if (i + 2 < stringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals("0"));
     * }</pre>
     */
    CompletableFuture<Object[]> hscan(String key, String cursor, HScanOptions hScanOptions);

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @see <a href="https://valkey.io/commands/hscan">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param cursor The cursor that points to the next iteration of results. A value of <code>"0"
     *     </code> indicates the start of the search.
     * @param hScanOptions The {@link HScanOptionsBinary}.
     * @return An <code>Array</code> of <code>Objects</code>. The first element is always the <code>
     *     cursor</code> for the next iteration of results. <code>"0"</code> will be the <code>cursor
     *     </code> returned on the last iteration of the result. The second element is always an
     *     <code>Array</code> of the subset of the hash held in <code>key</code>. The array in the
     *     second element is a flattened series of <code>String</code> pairs, where the key is at even
     *     indices and the value is at odd indices. If {@link HScanOptionsBinary#noValues()} is
     *     set to <code>true
     *     </code>, the second element will only contain the fields without the values.
     * @example
     *     <pre>{@code
     * // Assume key contains a set with 200 member-score pairs
     * GlideString cursor = gs("0");
     * Object[] result;
     * do {
     *   result = client.hscan(key1, cursor, HScanOptionsBinary.builder().matchPattern(gs("*")).count(20L).build()).get();
     *   cursor = gs(result[0].toString());
     *   Object[] gslideStringResults = (Object[]) result[1];
     *
     *   System.out.println("\nHSCAN iteration:");
     *   for (int i = 0; i < gslideStringResults.length; i += 2) {
     *     System.out.printf("{%s=%s}", gslideStringResults[i], gslideStringResults[i + 1]);
     *     if (i + 2 < gslideStringResults.length) {
     *       System.out.print(", ");
     *     }
     *   }
     * } while (!cursor.equals(gs("0")));
     * }</pre>
     */
    CompletableFuture<Object[]> hscan(
            GlideString key, GlideString cursor, HScanOptionsBinary hScanOptions);

    /**
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>
     * with optional expiration and conditional options.
     *
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hsetex/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValueMap A field-value map consisting of fields and their corresponding values to
     *     be set in the hash stored at the specified key.
     * @param options Optional parameters for the command including conditional changes and expiry
     *     settings. See {@link HSetExOptions}.
     * @return <code>1</code> if all the fields' values and expiration times were set successfully,
     *     <code>0</code> otherwise.
     * @example
     *     <pre>{@code
     * // Set fields with 60 second expiration
     * HSetExOptions options = HSetExOptions.builder()
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     * Long num = client.hsetex("my_hash", Map.of("field1", "value1", "field2", "value2"), options).get();
     * assert num == 1L;
     *
     * // Set fields only if none exist, with 30 second expiration
     * HSetExOptions conditionalOptions = HSetExOptions.builder()
     *     .onlyIfNoneExist()
     *     .expiry(ExpirySet.Milliseconds(30000L))
     *     .build();
     * Long result = client.hsetex("new_hash", Map.of("field", "value"), conditionalOptions).get();
     * assert result == 1L;
     *
     * // Set fields only if all exist, keeping existing expiration
     * HSetExOptions updateOptions = HSetExOptions.builder()
     *     .onlyIfAllExist()
     *     .expiry(ExpirySet.KeepExisting())
     *     .build();
     * Long updateResult = client.hsetex("existing_hash", Map.of("field1", "newValue"), updateOptions).get();
     * }</pre>
     */
    CompletableFuture<Long> hsetex(
            String key, Map<String, String> fieldValueMap, HSetExOptions options);

    /**
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>
     * with optional expiration and conditional options.
     *
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hsetex/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValueMap A field-value map consisting of fields and their corresponding values to
     *     be set in the hash stored at the specified key.
     * @param options Optional parameters for the command including conditional changes and expiry
     *     settings. See {@link HSetExOptions}.
     * @return <code>1</code> if all the fields' values and expiration times were set successfully,
     *     <code>0</code> otherwise.
     * @example
     *     <pre>{@code
     * // Set fields with 60 second expiration
     * HSetExOptions options = HSetExOptions.builder()
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     * Long num = client.hsetex(gs("my_hash"), Map.of(gs("field1"), gs("value1"), gs("field2"), gs("value2")), options).get();
     * assert num == 1L;
     *
     * // Set fields only if none exist, with 30 second expiration
     * HSetExOptions conditionalOptions = HSetExOptions.builder()
     *     .onlyIfNoneExist()
     *     .expiry(ExpirySet.Milliseconds(30000L))
     *     .build();
     * Long result = client.hsetex(gs("new_hash"), Map.of(gs("field"), gs("value")), conditionalOptions).get();
     * assert result == 1L;
     *
     * // Set fields only if all exist, keeping existing expiration
     * HSetExOptions updateOptions = HSetExOptions.builder()
     *     .onlyIfAllExist()
     *     .expiry(ExpirySet.KeepExisting())
     *     .build();
     * Long updateResult = client.hsetex(gs("existing_hash"), Map.of(gs("field1"), gs("newValue")), updateOptions).get();
     * }</pre>
     */
    CompletableFuture<Long> hsetex(
            GlideString key, Map<GlideString, GlideString> fieldValueMap, HSetExOptions options);

    /**
     * Retrieves the values of specified fields from the hash stored at <code>key</code> and
     * optionally sets their expiration or removes it.
     *
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hgetex/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields in the hash stored at <code>key</code> to retrieve from the database.
     * @param options Optional parameters for the command including expiry settings or persist option.
     *     See {@link HGetExOptions}.
     * @return An array of values associated with the given fields, in the same order as they are
     *     requested.<br>
     *     For every field that does not exist in the hash, a null value is returned.<br>
     *     If <code>key</code> does not exist, it returns <code>null</code>.<br>
     * @example
     *     <pre>{@code
     * // Get fields and set 60 second expiration
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Seconds(60L))
     *     .build();
     * String[] values = client.hgetex("my_hash", new String[] {"field1", "field2"}, options).get();
     * assert Arrays.equals(values, new String[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<String[]> hgetex(String key, String[] fields, HGetExOptions options);

    /**
     * Retrieves the values of specified fields from the hash stored at <code>key</code> and
     * optionally sets their expiration or removes it.
     *
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hgetex/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields in the hash stored at <code>key</code> to retrieve from the database.
     * @param options Optional parameters for the command including expiry settings or persist option.
     *     See {@link HGetExOptions}.
     * @return An array of values associated with the given fields, in the same order as they are
     *     requested.<br>
     *     For every field that does not exist in the hash, a null value is returned.<br>
     *     If <code>key</code> does not exist, it returns <code>null</code>.<br>
     * @example
     *     <pre>{@code
     * // Get fields and set 60 second expiration
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Seconds(60L))
     *     .build();
     * GlideString[] values = client.hgetex(gs("my_hash"), new GlideString[] {gs("field1"), gs("field2")}, options).get();
     * }</pre>
     */
    CompletableFuture<GlideString[]> hgetex(
            GlideString key, GlideString[] fields, HGetExOptions options);

    /**
     * Sets expiration time for hash fields, in seconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hexpire/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param seconds The time to expire in seconds.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 seconds (immediate deletion).
     */
    CompletableFuture<Long[]> hexpire(String key, long seconds, String[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Sets expiration time for hash fields, in seconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hexpire/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param seconds The time to expire in seconds.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 seconds (immediate deletion).
     */
    CompletableFuture<Long[]> hexpire(GlideString key, long seconds, GlideString[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Sets expiration time for hash fields, in milliseconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpexpire/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param milliseconds The time to expire in milliseconds.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 milliseconds (immediate deletion).
     */
    CompletableFuture<Long[]> hpexpire(String key, long milliseconds, String[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Sets expiration time for hash fields, in milliseconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpexpire/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param milliseconds The time to expire in milliseconds.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 milliseconds (immediate deletion).
     */
    CompletableFuture<Long[]> hpexpire(GlideString key, long milliseconds, GlideString[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Sets expiration time for hash fields at a given timestamp, in seconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hexpireat/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param unixSeconds The Unix timestamp (in seconds) when the fields should expire.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 (immediate deletion).
     */
    CompletableFuture<Long[]> hexpireat(String key, long unixSeconds, String[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Sets expiration time for hash fields at a given timestamp, in seconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hexpireat/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param unixSeconds The Unix timestamp (in seconds) when the fields should expire.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 (immediate deletion).
     */
    CompletableFuture<Long[]> hexpireat(GlideString key, long unixSeconds, GlideString[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Sets expiration time for hash fields at a given timestamp, in milliseconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpexpireat/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param unixMilliseconds The Unix timestamp (in milliseconds) when the fields should expire.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 (immediate deletion).
     */
    CompletableFuture<Long[]> hpexpireat(String key, long unixMilliseconds, String[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Sets expiration time for hash fields at a given timestamp, in milliseconds. Creates the hash if it doesn't exist.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpexpireat/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param unixMilliseconds The Unix timestamp (in milliseconds) when the fields should expire.
     * @param fields The fields to set expiration for.
     * @param options Optional conditional expiration settings. See {@link HashFieldExpirationConditionOptions}.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - expiry set successfully.
     *   {@code 0} - field exists but conditions not met.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     *   {@code 2} - expiry time set to 0 (immediate deletion).
     */
    CompletableFuture<Long[]> hpexpireat(GlideString key, long unixMilliseconds, GlideString[] fields, HashFieldExpirationConditionOptions options);

    /**
     * Removes the expiration time for each specified field, turning the field from volatile to persistent.
     *
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpersist/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to remove expiration from.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - field expiration removed successfully.
     *   {@code 0} - field has no expiration time.
     *   {@code -1} - field doesn't exist.
     */
    CompletableFuture<Long[]> hpersist(String key, String[] fields);

    /**
     * Removes the expiration time for each specified field, turning the field from volatile to persistent.
     *
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpersist/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to remove expiration from.
     * @return An array of Long integers indicating the result for each field:
     *   {@code 1} - field expiration removed successfully.
     *   {@code 0} - field has no expiration time.
     *   {@code -1} - field doesn't exist.
     */
    CompletableFuture<Long[]> hpersist(GlideString key, GlideString[] fields);

    /**
     * Returns the remaining time-to-live of hash fields, in seconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/httl/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get TTL for.
     * @return An array of Long integers indicating the TTL for each field:
     *   {@code >0} - remaining TTL in seconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> httl(String key, String[] fields);

    /**
     * Returns the remaining time-to-live of hash fields, in seconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/httl/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get TTL for.
     * @return An array of Long integers indicating the TTL for each field:
     *   {@code >0} - remaining TTL in seconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> httl(GlideString key, GlideString[] fields);

    /**
     * Returns the remaining time-to-live of hash fields, in milliseconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpttl/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get TTL for.
     * @return An array of Long integers indicating the TTL for each field:
     *   {@code >0} - remaining TTL in milliseconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> hpttl(String key, String[] fields);

    /**
     * Returns the remaining time-to-live of hash fields, in milliseconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpttl/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get TTL for.
     * @return An array of Long integers indicating the TTL for each field:
     *   {@code >0} - remaining TTL in milliseconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> hpttl(GlideString key, GlideString[] fields);

    /**
     * Returns the expiration time of hash fields as Unix timestamps, in seconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hexpiretime/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get expiration time for.
     * @return An array of Long integers indicating the expiration time for each field:
     *   {@code >0} - expiration Unix timestamp in seconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> hexpiretime(String key, String[] fields);

    /**
     * Returns the expiration time of hash fields as Unix timestamps, in seconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hexpiretime/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get expiration time for.
     * @return An array of Long integers indicating the expiration time for each field:
     *   {@code >0} - expiration Unix timestamp in seconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> hexpiretime(GlideString key, GlideString[] fields);

    /**
     * Returns the expiration time of hash fields as Unix timestamps, in milliseconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpexpiretime/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get expiration time for.
     * @return An array of Long integers indicating the expiration time for each field:
     *   {@code >0} - expiration Unix timestamp in milliseconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> hpexpiretime(String key, String[] fields);

    /**
     * Returns the expiration time of hash fields as Unix timestamps, in milliseconds.
     * 
     * @since Valkey 9.0 and above.
     * @see <a href="https://valkey.io/commands/hpexpiretime/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get expiration time for.
     * @return An array of Long integers indicating the expiration time for each field:
     *   {@code >0} - expiration Unix timestamp in milliseconds.
     *   {@code -1} - field exists but has no expiration time.
     *   {@code -2} - field doesn't exist.
     */
    CompletableFuture<Long[]> hpexpiretime(GlideString key, GlideString[] fields);
}
