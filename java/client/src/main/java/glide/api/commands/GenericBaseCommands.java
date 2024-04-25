/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.Script;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.ScriptOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Generic Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=generic">Generic Commands</a>
 */
public interface GenericBaseCommands {

    /**
     * Removes the specified <code>keys</code> from the database. A key is ignored if it does not
     * exist.
     *
     * @see <a href="https://redis.io/commands/del/">redis.io</a> for details.
     * @param keys The keys we wanted to remove.
     * @return The number of keys that were removed.
     * @example
     *     <pre>{@code
     * Long num = client.del(new String[] {"key1", "key2"}).get();
     * assert num == 2L;
     * }</pre>
     */
    CompletableFuture<Long> del(String[] keys);

    /**
     * Returns the number of keys in <code>keys</code> that exist in the database.
     *
     * @see <a href="https://redis.io/commands/exists/">redis.io</a> for details.
     * @param keys The keys list to check.
     * @return The number of keys that exist. If the same existing key is mentioned in <code>keys
     *     </code> multiple times, it will be counted multiple times.
     * @example
     *     <pre>{@code
     * Long result = client.exists(new String[] {"my_key", "invalid_key"}).get();
     * assert result == 1L;
     * }</pre>
     */
    CompletableFuture<Long> exists(String[] keys);

    /**
     * Unlink (delete) multiple <code>keys</code> from the database. A key is ignored if it does not
     * exist. This command, similar to <a href="https://redis.io/commands/del/">DEL</a>, removes
     * specified keys and ignores non-existent ones. However, this command does not block the server,
     * while <a href="https://redis.io/commands/del/">DEL</a> does.
     *
     * @see <a href="https://redis.io/commands/unlink/">redis.io</a> for details.
     * @param keys The list of keys to unlink.
     * @return The number of <code>keys</code> that were unlinked.
     * @example
     *     <pre>{@code
     * Long result = client.unlink("my_key").get();
     * assert result == 1L;
     * }</pre>
     */
    CompletableFuture<Long> unlink(String[] keys);

    /**
     * Sets a timeout on <code>key</code> in seconds. After the timeout has expired, the <code>key
     * </code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire
     * </code> set, the time to live is updated to the new value.<br>
     * If <code>seconds</code> is a non-positive number, the <code>key</code> will be deleted rather
     * than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expire("my_key", 60).get();
     * assert isSet; //Indicates that a timeout of 60 seconds has been set for "my_key."
     * }</pre>
     */
    CompletableFuture<Boolean> expire(String key, long seconds);

    /**
     * Sets a timeout on <code>key</code> in seconds. After the timeout has expired, the <code>key
     * </code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire
     * </code> set, the time to live is updated to the new value.<br>
     * If <code>seconds</code> is a non-positive number, the <code>key</code> will be deleted rather
     * than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/expire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param seconds The timeout in seconds.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expire("my_key", 60, ExpireOptions.HAS_NO_EXPIRY).get();
     * assert isSet; //Indicates that a timeout of 60 seconds has been set for "my_key."
     * }</pre>
     */
    CompletableFuture<Boolean> expire(String key, long seconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (seconds since January
     * 1, 1970) instead of specifying the number of seconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expireAt("my_key", Instant.now().getEpochSecond() + 10).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> expireAt(String key, long unixSeconds);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (seconds since January
     * 1, 1970) instead of specifying the number of seconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/expireat/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param unixSeconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.expireAt("my_key", Instant.now().getEpochSecond() + 10, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> expireAt(String key, long unixSeconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code> in milliseconds. After the timeout has expired, the <code>
     * key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>
     * expire</code> set, the time to live is updated to the new value.<br>
     * If <code>milliseconds</code> is a non-positive number, the <code>key</code> will be deleted
     * rather than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpire("my_key", 60000).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpire(String key, long milliseconds);

    /**
     * Sets a timeout on <code>key</code> in milliseconds. After the timeout has expired, the <code>
     * key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing expire set, the time to live is updated to the new
     * value.<br>
     * If <code>milliseconds</code> is a non-positive number, the <code>key</code> will be deleted
     * rather than expired.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/pexpire/">redis.io</a> for details.
     * @param key The key to set timeout on it.
     * @param milliseconds The timeout in milliseconds.
     * @param expireOptions The expire options.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpire("my_key", 60000, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpire(String key, long milliseconds, ExpireOptions expireOptions);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (milliseconds since
     * January 1, 1970) instead of specifying the number of milliseconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpireAt("my_key", Instant.now().toEpochMilli() + 10).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpireAt(String key, long unixMilliseconds);

    /**
     * Sets a timeout on <code>key</code>. It takes an absolute Unix timestamp (milliseconds since
     * January 1, 1970) instead of specifying the number of milliseconds.<br>
     * A timestamp in the past will delete the <code>key</code> immediately. After the timeout has
     * expired, the <code>key</code> will automatically be deleted.<br>
     * If <code>key</code> already has an existing <code>expire</code> set, the time to live is
     * updated to the new value.<br>
     * The timeout will only be cleared by commands that delete or overwrite the contents of <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/pexpireat/">redis.io</a> for details.
     * @param key The <code>key</code> to set timeout on it.
     * @param unixMilliseconds The timeout in an absolute Unix timestamp.
     * @param expireOptions The expire option.
     * @return <code>true</code> if the timeout was set. <code>false</code> if the timeout was not
     *     set. e.g. <code>key</code> doesn't exist, or operation skipped due to the provided
     *     arguments.
     * @example
     *     <pre>{@code
     * Boolean isSet = client.pexpireAt("my_key", Instant.now().toEpochMilli() + 10, ExpireOptions.HasNoExpiry).get();
     * assert isSet;
     * }</pre>
     */
    CompletableFuture<Boolean> pexpireAt(
            String key, long unixMilliseconds, ExpireOptions expireOptions);

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in seconds.
     *
     * @see <a href="https://redis.io/commands/ttl/">redis.io</a> for details.
     * @param key The <code>key</code> to return its timeout.
     * @return TTL in seconds, <code>-2</code> if <code>key</code> does not exist, or <code>-1</code>
     *     if <code>key</code> exists but has no associated expire.
     * @example
     *     <pre>{@code
     * Long timeRemaining = client.ttl("my_key").get();
     * assert timeRemaining == 3600L; //Indicates that "my_key" has a remaining time to live of 3600 seconds.
     *
     * Long timeRemaining = client.ttl("nonexistent_key").get();
     * assert timeRemaining == -2L; //Returns -2 for a non-existing key.
     * }</pre>
     */
    CompletableFuture<Long> ttl(String key);

    /**
     * Invokes a Lua script.<br>
     * This method simplifies the process of invoking scripts on a Redis server by using an object
     * that represents a Lua script. The script loading and execution will all be handled internally.
     * If the script has not already been loaded, it will be loaded automatically using the Redis
     * <code>SCRIPT LOAD</code> command. After that, it will be invoked using the Redis <code>EVALSHA
     * </code> command.
     *
     * @see <a href="https://redis.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://redis.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @return a value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return 'Hello'")) {
     *     String result = (String) client.invokeScript(luaScript).get();
     *     assert result.equals("Hello");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script);

    /**
     * Invokes a Lua script with its keys and arguments.<br>
     * This method simplifies the process of invoking scripts on a Redis server by using an object
     * that represents a Lua script. The script loading, argument preparation, and execution will all
     * be handled internally. If the script has not already been loaded, it will be loaded
     * automatically using the Redis <code>SCRIPT LOAD</code> command. After that, it will be invoked
     * using the Redis <code>EVALSHA</code> command.
     *
     * @see <a href="https://redis.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://redis.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param options The script option that contains keys and arguments for the script.
     * @return a value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return { KEYS[1], ARGV[1] }")) {
     *     ScriptOptions scriptOptions = ScriptOptions.builder().key("foo").arg("bar").build();
     *     Object[] result = (Object[]) client.invokeScript(luaScript, scriptOptions).get();
     *     assert result[0].equals("foo");
     *     assert result[1].equals("bar");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script, ScriptOptions options);

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in milliseconds.
     *
     * @see <a href="https://redis.io/commands/pttl/">redis.io</a> for details.
     * @param key The key to return its timeout.
     * @return TTL in milliseconds. <code>-2</code> if <code>key</code> does not exist, <code>-1
     *     </code> if <code>key</code> exists but has no associated expire.
     * @example
     *     <pre>{@code
     * Long timeRemainingMS = client.pttl("my_key").get()
     * assert timeRemainingMS == 5000L // Indicates that "my_key" has a remaining time to live of 5000 milliseconds.
     *
     * Long timeRemainingMS = client.pttl("nonexistent_key").get();
     * assert timeRemainingMS == -2L; // Returns -2 for a non-existing key.
     * }</pre>
     */
    CompletableFuture<Long> pttl(String key);

    /**
     * Removes the existing timeout on <code>key</code>, turning the <code>key</code> from volatile (a
     * <code>key</code> with an expire set) to persistent (a <code>key</code> that will never expire
     * as no timeout is associated).
     *
     * @see <a href="https://redis.io/commands/persist/">redis.io</a> for details.
     * @param key The <code>key</code> to remove the existing timeout on.
     * @return <code>false</code> if <code>key</code> does not exist or does not have an associated
     *     timeout, <code>true</code> if the timeout has been removed.
     * @example
     *     <pre>{@code
     * Boolean timeoutRemoved = client.persist("my_key").get();
     * assert timeoutRemoved; // Indicates that the timeout associated with the key "my_key" was successfully removed.
     * }</pre>
     */
    CompletableFuture<Boolean> persist(String key);

    /**
     * Returns the string representation of the type of the value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/type/>redis.io</a> for details.
     * @param key The <code>key</code> to check its data type.
     * @return If the <code>key</code> exists, the type of the stored value is returned. Otherwise, a
     *     "none" string is returned.
     * @example
     *     <pre>{@code
     * String type = client.type("StringKey").get();
     * assert type.equals("string");
     *
     * type = client.type("ListKey").get();
     * assert type.equals("list");
     * }</pre>
     */
    CompletableFuture<String> type(String key);

    /**
     * Returns the internal encoding for the Redis object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-encoding/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the internal encoding of.
     * @return If <code>key</code> exists, returns the internal encoding of the object stored at
     *     <code>key</code> as a <code>String</code>. Otherwise, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * String encoding = client.objectEncoding("my_hash").get();
     * assert encoding.equals("listpack");
     *
     * encoding = client.objectEncoding("non_existing_key").get();
     * assert encoding == null;
     * }</pre>
     */
    CompletableFuture<String> objectEncoding(String key);

    /**
     * Returns the logarithmic access frequency counter of a Redis object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-freq/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the logarithmic access frequency counter
     *     of.
     * @return If <code>key</code> exists, returns the logarithmic access frequency counter of the
     *     object stored at <code>key</code> as a <code>Long</code>. Otherwise, returns <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * Long frequency = client.objectFreq("my_hash").get();
     * assert frequency == 2L;
     *
     * frequency = client.objectFreq("non_existing_key").get();
     * assert frequency == null;
     * }</pre>
     */
    CompletableFuture<Long> objectFreq(String key);

    /**
     * Returns the time in seconds since the last access to the value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-idletime/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the idle time of.
     * @return If <code>key</code> exists, returns the idle time in seconds. Otherwise, returns <code>
     *     null</code>.
     * @example
     *     <pre>{@code
     * Long idletime = client.objectIdletime("my_hash").get();
     * assert idletime == 2L;
     *
     * idletime = client.objectIdletime("non_existing_key").get();
     * assert idletime == null;
     * }</pre>
     */
    CompletableFuture<Long> objectIdletime(String key);

    /**
     * Returns the reference count of the object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-refcount/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the reference count of.
     * @return If <code>key</code> exists, returns the reference count of the object stored at <code>
     *     key</code> as a <code>Long</code>. Otherwise, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Long refcount = client.objectRefcount("my_hash").get();
     * assert refcount == 2L;
     *
     * refcount = client.objectRefcount("non_existing_key").get();
     * assert refcount == null;
     * }</pre>
     */
    CompletableFuture<Long> objectRefcount(String key);
}
