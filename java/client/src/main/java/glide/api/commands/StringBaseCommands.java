/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "String Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=string">String Commands</a>
 */
public interface StringBaseCommands {

    /**
     * Gets the value associated with the given <code>key</code>, or <code>null</code> if no such
     * value exists.
     *
     * @see <a href="https://redis.io/commands/get/">redis.io</a> for details.
     * @param key The <code>key</code> to retrieve from the database.
     * @return Response from Redis. If <code>key</code> exists, returns the <code>value</code> of
     *     <code>key</code> as a <code>String</code>. Otherwise, return <code>null</code>.
     * @example
     *     <pre>{@code
     * String value = client.get("key").get();
     * assert value.equals("value");
     *
     * String value = client.get("non_existing_key").get();
     * assert value.equals(null);
     * }</pre>
     */
    CompletableFuture<String> get(String key);

    /**
     * Sets the given <code>key</code> with the given value.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The <code>key</code> to store.
     * @param value The value to store with the given <code>key</code>.
     * @return Response from Redis containing <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * String value = client.set("key", "value").get();
     * assert value.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(String key, String value);

    /**
     * Sets the given key with the given value. Return value is dependent on the passed options.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given key.
     * @param options The Set options.
     * @return Response from Redis containing a <code>String</code> or <code>null</code> response. If
     *     the value is successfully set, return <code>"OK"</code>. If value isn't set because of
     *     {@link ConditionalSet#ONLY_IF_EXISTS} or {@link ConditionalSet#ONLY_IF_DOES_NOT_EXIST}
     *     conditions, return <code>null</code>. If {@link SetOptionsBuilder#returnOldValue(boolean)}
     *     is set, return the old value as a <code>String</code>.
     * @example
     *     <pre>{@code
     * SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).expiry(Seconds(5L)).build();
     * String value = client.set("key", "value", options).get();
     * assert value.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> set(String key, String value, SetOptions options);

    /**
     * Retrieves the values of multiple <code>keys</code>.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different <code>hash slot</code>s.
     * @see <a href="https://redis.io/commands/mget/">redis.io</a> for details.
     * @param keys A list of keys to retrieve values for.
     * @return An array of values corresponding to the provided <code>keys</code>.<br>
     *     If a <code>key</code>is not found, its corresponding value in the list will be <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * String values = client.mget(new String[] {"key1", "key2"}).get();
     * assert values.equals(new String[] {"value1", "value2"});
     * }</pre>
     */
    CompletableFuture<String[]> mget(String[] keys);

    /**
     * Sets multiple keys to multiple values in a single operation.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when keys in <code>
     *     keyValueMap</code> map to different <code>hash slot</code>s.
     * @see <a href="https://redis.io/commands/mset/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return Always <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.mset(Map.of("key1", "value1", "key2", "value2"}).get();
     * assert result.equals("OK"));
     * }</pre>
     */
    CompletableFuture<String> mset(Map<String, String> keyValueMap);

    /**
     * Increments the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incr/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Long num = client.incr("key").get();
     * assert num == 5L;
     * }</pre>
     */
    CompletableFuture<Long> incr(String key);

    /**
     * Increments the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrby/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Long num = client.incrBy("key", 2).get();
     * assert num == 7L;
     * }</pre>
     */
    CompletableFuture<Long> incrBy(String key, long amount);

    /**
     * Increments the string representing a floating point number stored at <code>key</code> by <code>
     * amount</code>. By using a negative increment value, the result is that the value stored at
     * <code>key</code> is decremented. If <code>key</code> does not exist, it is set to 0 before
     * performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrbyfloat/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return The value of <code>key</code> after the increment.
     * @example
     *     <pre>{@code
     * Double num = client.incrByFloat("key", 0.5).get();
     * assert num == 7.5;
     * }</pre>
     */
    CompletableFuture<Double> incrByFloat(String key, double amount);

    /**
     * Decrements the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decr/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @return The value of <code>key</code> after the decrement.
     * @example
     *     <pre>{@code
     * Long num = client.decr("key").get();
     * assert num == 4L;
     * }</pre>
     */
    CompletableFuture<Long> decr(String key);

    /**
     * Decrements the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decrby/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @param amount The amount to decrement.
     * @return The value of <code>key</code> after the decrement.
     * @example
     *     <pre>{@code
     * Long num = client.decrBy("key", 2).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> decrBy(String key, long amount);

    /**
     * Returns the length of the string value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/strlen/">redis.io</a> for details.
     * @param key The key to check its length.
     * @return The length of the string value stored at key.<br>
     *     If <code>key</code> does not exist, it is treated as an empty string, and the command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * client.set("key", "GLIDE").get();
     * Long len = client.strlen("key").get();
     * assert len == 5L;
     *
     * len = client.strlen("non_existing_key").get();
     * assert len == 0L;
     * }</pre>
     */
    CompletableFuture<Long> strlen(String key);

    /**
     * Overwrites part of the string stored at <code>key</code>, starting at the specified <code>
     * offset</code>, for the entire length of <code>value</code>.<br>
     * If the <code>offset</code> is larger than the current length of the string at <code>key</code>,
     * the string is padded with zero bytes to make <code>offset</code> fit. Creates the <code>key
     * </code> if it doesn't exist.
     *
     * @see <a href="https://redis.io/commands/setrange/">redis.io</a> for details.
     * @param key The key of the string to update.
     * @param offset The position in the string where <code>value</code> should be written.
     * @param value The string written with <code>offset</code>.
     * @return The length of the string stored at <code>key</code> after it was modified.
     * @example
     *     <pre>{@code
     * Long len = client.setrange("key", 6, "GLIDE").get();
     * assert len == 11L; // New key was created with length of 11 symbols
     * String value = client.get("key").get();
     * assert value.equals("\0\0\0\0\0\0GLIDE"); // The string was padded with zero bytes
     * }</pre>
     */
    CompletableFuture<Long> setrange(String key, int offset, String value);

    /**
     * Returns the substring of the string value stored at <code>key</code>, determined by the offsets
     * <code>start</code> and <code>end</code> (both are inclusive). Negative offsets can be used in
     * order to provide an offset starting from the end of the string. So <code>-1</code> means the
     * last character, <code>-2</code> the penultimate and so forth.
     *
     * @see <a href="https://redis.io/commands/getrange/">redis.io</a> for details.
     * @param key The key of the string.
     * @param start The starting offset.
     * @param end The ending offset.
     * @return A substring extracted from the value stored at <code>key</code>..
     * @example
     *     <pre>{@code
     * client.set("mykey", "This is a string").get();
     * String substring = client.getrange("mykey", 0, 3).get();
     * assert substring.equals("This");
     * String substring = client.getrange("mykey", -3, -1).get();
     * assert substring.equals("ing"); // extracted last 3 characters of a string
     * }</pre>
     */
    CompletableFuture<String> getrange(String key, int start, int end);
}
