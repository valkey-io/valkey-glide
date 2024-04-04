/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_REDIS_API;
import static glide.api.models.commands.RangeOptions.createZrangeArgs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Echo;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.Expire;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.HLen;
import static redis_request.RedisRequestOuterClass.RequestType.HSetNX;
import static redis_request.RedisRequestOuterClass.RequestType.HashDel;
import static redis_request.RedisRequestOuterClass.RequestType.HashExists;
import static redis_request.RedisRequestOuterClass.RequestType.HashGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashGetAll;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.HashMGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashSet;
import static redis_request.RedisRequestOuterClass.RequestType.Hvals;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LLen;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.LPushX;
import static redis_request.RedisRequestOuterClass.RequestType.LRange;
import static redis_request.RedisRequestOuterClass.RequestType.LRem;
import static redis_request.RedisRequestOuterClass.RequestType.LTrim;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.PExpire;
import static redis_request.RedisRequestOuterClass.RequestType.PExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.PTTL;
import static redis_request.RedisRequestOuterClass.RequestType.Persist;
import static redis_request.RedisRequestOuterClass.RequestType.PfAdd;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.RPop;
import static redis_request.RedisRequestOuterClass.RequestType.RPush;
import static redis_request.RedisRequestOuterClass.RequestType.RPushX;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;
import static redis_request.RedisRequestOuterClass.RequestType.Strlen;
import static redis_request.RedisRequestOuterClass.RequestType.TTL;
import static redis_request.RedisRequestOuterClass.RequestType.Time;
import static redis_request.RedisRequestOuterClass.RequestType.Type;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.XAdd;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.ZScore;
import static redis_request.RedisRequestOuterClass.RequestType.Zadd;
import static redis_request.RedisRequestOuterClass.RequestType.Zcard;
import static redis_request.RedisRequestOuterClass.RequestType.Zrange;
import static redis_request.RedisRequestOuterClass.RequestType.Zrank;
import static redis_request.RedisRequestOuterClass.RequestType.Zrem;

import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
import glide.api.models.commands.StreamAddOptions;
import glide.api.models.commands.ZaddOptions;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RequestType;
import redis_request.RedisRequestOuterClass.Transaction;

/**
 * Base class encompassing shared commands for both standalone and cluster mode implementations in a
 * transaction. Transactions allow the execution of a group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client exec command, in the
 * order they were given. Each element in the array represents a command given to the transaction.
 * The response for each command depends on the executed Redis command. Specific response types are
 * documented alongside each method.
 *
 * @param <T> child typing for chaining method calls
 */
@Getter
public abstract class BaseTransaction<T extends BaseTransaction<T>> {
    /** Command class to send a single request to Redis. */
    protected final Transaction.Builder protobufTransaction = Transaction.newBuilder();

    protected abstract T getThis();

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in args.
     *
     * @param args Arguments for the custom command.
     * @return A response from Redis with an <code>Object</code>.
     * @remarks This function should only be used for single-response commands. Commands that don't
     *     return response (such as <em>SUBSCRIBE</em>), or that return potentially more than a single
     *     response (such as <em>XREAD</em>), or that change the client's behavior (such as entering
     *     <em>pub</em>/<em>sub</em> mode on <em>RESP2</em> connections) shouldn't be called using
     *     this function.
     * @example Returns a list of all pub/sub clients:
     *     <pre>{@code
     * Object result = client.customCommand(new String[]{ "CLIENT", "LIST", "TYPE", "PUBSUB" }).get();
     * }</pre>
     */
    public T customCommand(String[] args) {

        ArgsArray commandArgs = buildArgs(args);
        protobufTransaction.addCommands(buildCommand(CustomCommand, commandArgs));
        return getThis();
    }

    /**
     * Echoes the provided <code>message</code> back.
     *
     * @see <a href="https://redis.io/commands/echo>redis.io</a> for details.
     * @param message The message to be echoed back.
     * @return Command Response - The provided <code>message</code>.
     */
    public T echo(@NonNull String message) {
        ArgsArray commandArgs = buildArgs(message);
        protobufTransaction.addCommands(buildCommand(Echo, commandArgs));
        return getThis();
    }

    /**
     * Pings the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return Command Response - A response from Redis with a <code>String</code>.
     */
    public T ping() {
        protobufTransaction.addCommands(buildCommand(Ping));
        return getThis();
    }

    /**
     * Pings the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param msg The ping argument that will be returned.
     * @return Command Response - A response from Redis with a <code>String</code>.
     */
    public T ping(@NonNull String msg) {
        ArgsArray commandArgs = buildArgs(msg);

        protobufTransaction.addCommands(buildCommand(Ping, commandArgs));
        return getThis();
    }

    /**
     * Gets information and statistics about the Redis server using the {@link Section#DEFAULT}
     * option.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return Command Response - A <code>String</code> with server info.
     */
    public T info() {
        protobufTransaction.addCommands(buildCommand(Info));
        return getThis();
    }

    /**
     * Gets information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link Section} values specifying which sections of information to
     *     retrieve. When no parameter is provided, the {@link Section#DEFAULT} option is assumed.
     * @return Command Response - A <code>String</code> containing the requested {@link Section}s.
     */
    public T info(@NonNull InfoOptions options) {
        ArgsArray commandArgs = buildArgs(options.toArgs());

        protobufTransaction.addCommands(buildCommand(Info, commandArgs));
        return getThis();
    }

    /**
     * Removes the specified <code>keys</code> from the database. A key is ignored if it does not
     * exist.
     *
     * @see <a href="https://redis.io/commands/del/">redis.io</a> for details.
     * @param keys The keys we wanted to remove.
     * @return Command Response - The number of keys that were removed.
     */
    public T del(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);

        protobufTransaction.addCommands(buildCommand(Del, commandArgs));
        return getThis();
    }

    /**
     * Gets the value associated with the given key, or null if no such value exists.
     *
     * @see <a href="https://redis.io/commands/get/">redis.io</a> for details.
     * @param key The key to retrieve from the database.
     * @return Command Response - If <code>key</code> exists, returns the <code>value</code> of <code>
     *     key</code> as a String. Otherwise, return <code>null</code>.
     */
    public T get(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(GetString, commandArgs));
        return getThis();
    }

    /**
     * Sets the given key with the given value.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given <code>key</code>.
     * @return Command Response - A response from Redis.
     */
    public T set(@NonNull String key, @NonNull String value) {
        ArgsArray commandArgs = buildArgs(key, value);

        protobufTransaction.addCommands(buildCommand(SetString, commandArgs));
        return getThis();
    }

    /**
     * Sets the given key with the given value. Return value is dependent on the passed options.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given key.
     * @param options The Set options.
     * @return Command Response - A <code>String</code> or <code>null</code> response. The old value
     *     as a <code>String</code> if {@link SetOptionsBuilder#returnOldValue(boolean)} is set.
     *     Otherwise, if the value isn't set because of {@link ConditionalSet#ONLY_IF_EXISTS} or
     *     {@link ConditionalSet#ONLY_IF_DOES_NOT_EXIST} conditions, return <code>null</code>.
     *     Otherwise, return <code>OK</code>.
     */
    public T set(@NonNull String key, @NonNull String value, @NonNull SetOptions options) {
        ArgsArray commandArgs =
                buildArgs(ArrayUtils.addAll(new String[] {key, value}, options.toArgs()));

        protobufTransaction.addCommands(buildCommand(SetString, commandArgs));
        return getThis();
    }

    /**
     * Retrieves the values of multiple <code>keys</code>.
     *
     * @see <a href="https://redis.io/commands/mget/">redis.io</a> for details.
     * @param keys A list of keys to retrieve values for.
     * @return Command Response - An array of values corresponding to the provided <code>keys</code>.
     *     <br>
     *     If a <code>key</code>is not found, its corresponding value in the list will be <code>null
     *     </code>.
     */
    public T mget(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);

        protobufTransaction.addCommands(buildCommand(MGet, commandArgs));
        return getThis();
    }

    /**
     * Sets multiple keys to multiple values in a single operation.
     *
     * @see <a href="https://redis.io/commands/mset/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return Command Response - Always <code>OK</code>.
     */
    public T mset(@NonNull Map<String, String> keyValueMap) {
        String[] args = convertMapToKeyValueStringArray(keyValueMap);
        ArgsArray commandArgs = buildArgs(args);

        protobufTransaction.addCommands(buildCommand(MSet, commandArgs));
        return getThis();
    }

    /**
     * Increments the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incr/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @return Command Response - The value of <code>key</code> after the increment.
     */
    public T incr(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(Incr, commandArgs));
        return getThis();
    }

    /**
     * Increments the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrby/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return Command Response - The value of <code>key</code> after the increment.
     */
    public T incrBy(@NonNull String key, long amount) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(amount));

        protobufTransaction.addCommands(buildCommand(IncrBy, commandArgs));
        return getThis();
    }

    /**
     * Increments the string representing a floating point number stored at <code>key</code> by <code>
     * amount</code>. By using a negative increment value, the result is that the value stored at
     * <code>key</code> is decremented. If <code>key</code> does not exist, it is set to 0 before
     * performing the operation.
     *
     * @see <a href="https://redis.io/commands/incrbyfloat/">redis.io</a> for details.
     * @param key The key to increment its value.
     * @param amount The amount to increment.
     * @return Command Response - The value of <code>key</code> after the increment.
     */
    public T incrByFloat(@NonNull String key, double amount) {
        ArgsArray commandArgs = buildArgs(key, Double.toString(amount));

        protobufTransaction.addCommands(buildCommand(IncrByFloat, commandArgs));
        return getThis();
    }

    /**
     * Decrements the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
     * is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decr/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @return Command Response - The value of <code>key</code> after the decrement.
     */
    public T decr(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(Decr, commandArgs));
        return getThis();
    }

    /**
     * Decrements the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
     * does not exist, it is set to 0 before performing the operation.
     *
     * @see <a href="https://redis.io/commands/decrby/">redis.io</a> for details.
     * @param key The key to decrement its value.
     * @param amount The amount to decrement.
     * @return Command Response - The value of <code>key</code> after the decrement.
     */
    public T decrBy(@NonNull String key, long amount) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(amount));

        protobufTransaction.addCommands(buildCommand(DecrBy, commandArgs));
        return getThis();
    }

    /**
     * Returns the length of the string value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/strlen/">redis.io</a> for details.
     * @param key The key to check its length.
     * @return Command Response - The length of the string value stored at key.<br>
     *     If <code>key</code> does not exist, it is treated as an empty string, and the command
     *     returns <code>0</code>.
     */
    public T strlen(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(Strlen, commandArgs));
        return getThis();
    }

    /**
     * Retrieves the value associated with <code>field</code> in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to retrieve from the database.
     * @return Command Response - The value associated with <code>field</code>, or <code>null</code>
     *     when <code>field</code> is not present in the hash or <code>key</code> does not exist.
     */
    public T hget(@NonNull String key, @NonNull String field) {
        ArgsArray commandArgs = buildArgs(key, field);

        protobufTransaction.addCommands(buildCommand(HashGet, commandArgs));
        return getThis();
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hset/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValueMap A field-value map consisting of fields and their corresponding values to
     *     be set in the hash stored at the specified key.
     * @return Command Response - The number of fields that were added.
     */
    public T hset(@NonNull String key, @NonNull Map<String, String> fieldValueMap) {
        ArgsArray commandArgs =
                buildArgs(ArrayUtils.addFirst(convertMapToKeyValueStringArray(fieldValueMap), key));

        protobufTransaction.addCommands(buildCommand(HashSet, commandArgs));
        return getThis();
    }

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
     * @return Command Response - <code>true</code> if the field was set, <code>false</code> if the
     *     field already existed and was not set.
     */
    public T hsetnx(@NonNull String key, @NonNull String field, @NonNull String value) {
        ArgsArray commandArgs = buildArgs(key, field, value);

        protobufTransaction.addCommands(buildCommand(HSetNX, commandArgs));
        return getThis();
    }

    /**
     * Removes the specified fields from the hash stored at <code>key</code>. Specified fields that do
     * not exist within this hash are ignored.
     *
     * @see <a href="https://redis.io/commands/hdel/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to remove from the hash stored at <code>key</code>.
     * @return Command Response - The number of fields that were removed from the hash, not including
     *     specified but non-existing fields.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash and it returns 0.<br>
     */
    public T hdel(@NonNull String key, @NonNull String[] fields) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(fields, key));

        protobufTransaction.addCommands(buildCommand(HashDel, commandArgs));
        return getThis();
    }

    /**
     * Returns the number of fields contained in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hlen/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return Command Response - The number of fields in the hash, or <code>0</code> when the key
     *     does not exist.<br>
     *     If <code>key</code> holds a value that is not a hash, an error is returned.
     */
    public T hlen(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(HLen, commandArgs));
        return getThis();
    }

    /**
     * Returns all values in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hvals/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return Command Response - An <code>array</code> of values in the hash, or an <code>empty array
     *     </code> when the key does not exist.
     */
    public T hvals(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(Hvals, commandArgs));
        return getThis();
    }

    /**
     * Returns the values associated with the specified fields in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hmget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields in the hash stored at <code>key</code> to retrieve from the database.
     * @return Command Response - An array of values associated with the given fields, in the same
     *     order as they are requested.<br>
     *     For every field that does not exist in the hash, a null value is returned.<br>
     *     If <code>key</code> does not exist, it is treated as an empty hash, and it returns an array
     *     of null values.<br>
     */
    public T hmget(@NonNull String key, @NonNull String[] fields) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(fields, key));

        protobufTransaction.addCommands(buildCommand(HashMGet, commandArgs));
        return getThis();
    }

    /**
     * Returns if <code>field</code> is an existing field in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hexists/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check in the hash stored at <code>key</code>.
     * @return Command Response - <code>True</code> if the hash contains the specified field. If the
     *     hash does not contain the field, or if the key does not exist, it returns <code>False
     *     </code>.
     */
    public T hexists(@NonNull String key, @NonNull String field) {
        ArgsArray commandArgs = buildArgs(key, field);

        protobufTransaction.addCommands(buildCommand(HashExists, commandArgs));
        return getThis();
    }

    /**
     * Returns all fields and values of the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hgetall/">redis.io</a> for details.
     * @param key The key of the hash.
     * @return Command Response - A <code>Map</code> of fields and their values stored in the hash.
     *     Every field name in the map is associated with its corresponding value.<br>
     *     If <code>key</code> does not exist, it returns an empty map.
     */
    public T hgetall(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(HashGetAll, commandArgs));
        return getThis();
    }

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
     * @return Command Response - The value of <code>field</code> in the hash stored at <code>key
     *     </code> after the increment or decrement.
     */
    public T hincrBy(@NonNull String key, @NonNull String field, long amount) {
        ArgsArray commandArgs = buildArgs(key, field, Long.toString(amount));

        protobufTransaction.addCommands(buildCommand(HashIncrBy, commandArgs));
        return getThis();
    }

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
     * @return Command Response - The value of <code>field</code> in the hash stored at <code>key
     *     </code> after the increment or decrement.
     */
    public T hincrByFloat(@NonNull String key, @NonNull String field, double amount) {
        ArgsArray commandArgs = buildArgs(key, field, Double.toString(amount));

        protobufTransaction.addCommands(buildCommand(HashIncrByFloat, commandArgs));
        return getThis();
    }

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>. <code>
     * elements</code> are inserted one after the other to the head of the list, from the leftmost
     * element to the rightmost element. If <code>key</code> does not exist, it is created as an empty
     * list before performing the push operations.
     *
     * @see <a href="https://redis.io/commands/lpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operations.
     */
    public T lpush(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));

        protobufTransaction.addCommands(buildCommand(LPush, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns the first elements of the list stored at <code>key</code>. The command pops
     * a single element from the beginning of the list.
     *
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @return Command Response - The value of the first element.<br>
     *     If <code>key</code> does not exist, null will be returned.
     */
    public T lpop(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(LPop, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> elements of the list stored at <code>key</code>,
     * depending on the list's length.
     *
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @param count The count of the elements to pop from the list.
     * @return Command Response - An array of the popped elements will be returned depending on the
     *     list's length.<br>
     *     If <code>key</code> does not exist, null will be returned.
     */
    public T lpopCount(@NonNull String key, long count) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(count));

        protobufTransaction.addCommands(buildCommand(LPop, commandArgs));
        return getThis();
    }

    /**
     * Returns the specified elements of the list stored at <code>key</code>.<br>
     * The offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on. These
     * offsets can also be negative numbers indicating offsets starting at the end of the list, with
     * <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and
     * so on.
     *
     * @see <a href="https://redis.io/commands/lrange/">redis.io</a> for details.
     * @param key The key of the list.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Command Response - Array of elements in the specified range.<br>
     *     If <code>start</code> exceeds the end of the list, or if <code>start</code> is greater than
     *     <code>end</code>, an empty array will be returned.<br>
     *     If <code>end</code> exceeds the actual end of the list, the range will stop at the actual
     *     end of the list.<br>
     *     If <code>key</code> does not exist an empty array will be returned.
     */
    public T lrange(@NonNull String key, long start, long end) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(start), Long.toString(end));

        protobufTransaction.addCommands(buildCommand(LRange, commandArgs));
        return getThis();
    }

    /**
     * Trims an existing list so that it will contain only the specified range of elements specified.<br>
     * The offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code> being the
     * first element of the list, </code>1<code> being the next element and so on.<br>
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and so on.
     *
     * @see <a href="https://redis.io/commands/ltrim/">redis.io</a> for details.
     * @param key The key of the list.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Command Response - Always <code>OK</code>.<br>
     *     If <code>start</code> exceeds the end of the list, or if <code>start</code> is greater than
     *     <code>end</code>, the result will be an empty list (which causes key to be removed).<br>
     *     If <code>end</code> exceeds the actual end of the list, it will be treated like the last
     *     element of the list.<br>
     *     If <code>key</code> does not exist, OK will be returned without changes to the database.
     */
    public T ltrim(@NonNull String key, long start, long end) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(start), Long.toString(end));

        protobufTransaction.addCommands(buildCommand(LTrim, commandArgs));
        return getThis();
    }

    /**
     * Returns the length of the list stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/llen/">redis.io</a> for details.
     * @param key The key of the list.
     * @return Command Response - The length of the list at <code>key</code>.<br>
     *     If <code>key</code> does not exist, it is interpreted as an empty list and <code>0</code>
     *     is returned.
     */
    public T llen(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(LLen, commandArgs));
        return getThis();
    }

    /**
     * Removes the first <code>count</code> occurrences of elements equal to <code>element</code> from
     * the list stored at <code>key</code>.<br>
     * If <code>count</code> is positive: Removes elements equal to <code>element</code> moving from
     * head to tail.<br>
     * If <code>count</code> is negative: Removes elements equal to <code>element</code> moving from
     * tail to head.<br>
     * If <code>count</code> is 0 or <code>count</code> is greater than the occurrences of elements
     * equal to <code>element</code>, it removes all elements equal to <code>element</code>.
     *
     * @see <a href="https://redis.io/commands/lrem/">redis.io</a> for details.
     * @param key The key of the list.
     * @param count The count of the occurrences of elements equal to <code>element</code> to remove.
     * @param element The element to remove from the list.
     * @return Command Response - The number of the removed elements.<br>
     *     If <code>key</code> does not exist, <code>0</code> is returned.
     */
    public T lrem(@NonNull String key, long count, @NonNull String element) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(count), element);

        protobufTransaction.addCommands(buildCommand(LRem, commandArgs));
        return getThis();
    }

    /**
     * Inserts all the specified values at the tail of the list stored at <code>key</code>.<br>
     * <code>elements</code> are inserted one after the other to the tail of the list, from the
     * leftmost element to the rightmost element. If <code>key</code> does not exist, it is created as
     * an empty list before performing the push operations.
     *
     * @see <a href="https://redis.io/commands/rpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operations.
     */
    public T rpush(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));

        protobufTransaction.addCommands(buildCommand(RPush, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns the last elements of the list stored at <code>key</code>.<br>
     * The command pops a single element from the end of the list.
     *
     * @see <a href="https://redis.io/commands/rpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @return Command Response - The value of the last element.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     */
    public T rpop(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(RPop, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> elements from the list stored at <code>key</code>,
     * depending on the list's length.
     *
     * @see <a href="https://redis.io/commands/rpop/">redis.io</a> for details.
     * @param count The count of the elements to pop from the list.
     * @return Command Response - An array of popped elements will be returned depending on the list's
     *     length.<br>
     *     If <code>key</code> does not exist, <code>null</code> will be returned.
     */
    public T rpopCount(@NonNull String key, long count) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(count));

        protobufTransaction.addCommands(buildCommand(RPop, commandArgs));
        return getThis();
    }

    /**
     * Adds specified members to the set stored at <code>key</code>. Specified members that are
     * already a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/sadd/">redis.io</a> for details.
     * @param key The <code>key</code> where members will be added to its set.
     * @param members A list of members to add to the set stored at <code>key</code>.
     * @return Command Response - The number of members that were added to the set, excluding members
     *     already present.
     * @remarks If <code>key</code> does not exist, a new set is created before adding <code>members
     *     </code>.
     */
    public T sadd(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));

        protobufTransaction.addCommands(buildCommand(SAdd, commandArgs));
        return getThis();
    }

    /**
     * Removes specified members from the set stored at <code>key</code>. Specified members that are
     * not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/srem/">redis.io</a> for details.
     * @param key The <code>key</code> from which members will be removed.
     * @param members A list of members to remove from the set stored at <code>key</code>.
     * @return Command Response - The number of members that were removed from the set, excluding
     *     non-existing members.
     * @remarks If <code>key</code> does not exist, it is treated as an empty set and this command
     *     returns <code>0</code>.
     */
    public T srem(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));

        protobufTransaction.addCommands(buildCommand(SRem, commandArgs));
        return getThis();
    }

    /**
     * Retrieves all the members of the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smembers/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @return Command Response - A <code>Set</code> of all members of the set.
     * @remarks If <code>key</code> does not exist an empty set will be returned.
     */
    public T smembers(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(SMembers, commandArgs));
        return getThis();
    }

    /**
     * Retrieves the set cardinality (number of elements) of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/scard/">redis.io</a> for details.
     * @param key The key from which to retrieve the number of set members.
     * @return Command Response - The cardinality (number of elements) of the set, or 0 if the key
     *     does not exist.
     */
    public T scard(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(SCard, commandArgs));
        return getThis();
    }

    /**
     * Reads the configuration parameters of a running Redis server.
     *
     * @see <a href="https://redis.io/commands/config-get/">redis.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @return Command response - A <code>map</code> of values corresponding to the configuration
     *     parameters.
     */
    public T configGet(@NonNull String[] parameters) {
        ArgsArray commandArgs = buildArgs(parameters);

        protobufTransaction.addCommands(buildCommand(ConfigGet, commandArgs));
        return getThis();
    }

    /**
     * Sets configuration parameters to the specified values.
     *
     * @see <a href="https://redis.io/commands/config-set/">redis.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @return Command response - <code>OK</code> if all configurations have been successfully set.
     *     Otherwise, the transaction fails with an error.
     */
    public T configSet(@NonNull Map<String, String> parameters) {
        ArgsArray commandArgs = buildArgs(convertMapToKeyValueStringArray(parameters));

        protobufTransaction.addCommands(buildCommand(ConfigSet, commandArgs));
        return getThis();
    }

    /**
     * Returns the number of keys in <code>keys</code> that exist in the database.
     *
     * @see <a href="https://redis.io/commands/exists/">redis.io</a> for details.
     * @param keys The keys list to check.
     * @return Command Response - The number of keys that exist. If the same existing key is mentioned
     *     in <code>keys</code> multiple times, it will be counted multiple times.
     */
    public T exists(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);

        protobufTransaction.addCommands(buildCommand(Exists, commandArgs));
        return getThis();
    }

    /**
     * Unlinks (deletes) multiple <code>keys</code> from the database. A key is ignored if it does not
     * exist. This command, similar to DEL, removes specified keys and ignores non-existent ones.
     * However, this command does not block the server, while <a
     * href="https://redis.io/commands/del/">DEL</a> does.
     *
     * @see <a href="https://redis.io/commands/unlink/">redis.io</a> for details.
     * @param keys The list of keys to unlink.
     * @return Command Response - The number of <code>keys</code> that were unlinked.
     */
    public T unlink(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);

        protobufTransaction.addCommands(buildCommand(Unlink, commandArgs));
        return getThis();
    }

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
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. key doesn't exist.
     */
    public T expire(@NonNull String key, long seconds) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(seconds));

        protobufTransaction.addCommands(buildCommand(Expire, commandArgs));
        return getThis();
    }

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
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public T expire(@NonNull String key, long seconds, @NonNull ExpireOptions expireOptions) {
        ArgsArray commandArgs =
                buildArgs(
                        ArrayUtils.addAll(new String[] {key, Long.toString(seconds)}, expireOptions.toArgs()));

        protobufTransaction.addCommands(buildCommand(Expire, commandArgs));
        return getThis();
    }

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
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist.
     */
    public T expireAt(@NonNull String key, long unixSeconds) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(unixSeconds));

        protobufTransaction.addCommands(buildCommand(ExpireAt, commandArgs));
        return getThis();
    }

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
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public T expireAt(@NonNull String key, long unixSeconds, @NonNull ExpireOptions expireOptions) {
        ArgsArray commandArgs =
                buildArgs(
                        ArrayUtils.addAll(
                                new String[] {key, Long.toString(unixSeconds)}, expireOptions.toArgs()));

        protobufTransaction.addCommands(buildCommand(ExpireAt, commandArgs));
        return getThis();
    }

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
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist.
     */
    public T pexpire(@NonNull String key, long milliseconds) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(milliseconds));

        protobufTransaction.addCommands(buildCommand(PExpire, commandArgs));
        return getThis();
    }

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
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public T pexpire(@NonNull String key, long milliseconds, @NonNull ExpireOptions expireOptions) {
        ArgsArray commandArgs =
                buildArgs(
                        ArrayUtils.addAll(
                                new String[] {key, Long.toString(milliseconds)}, expireOptions.toArgs()));

        protobufTransaction.addCommands(buildCommand(PExpire, commandArgs));
        return getThis();
    }

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
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist.
     */
    public T pexpireAt(@NonNull String key, long unixMilliseconds) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(unixMilliseconds));

        protobufTransaction.addCommands(buildCommand(PExpireAt, commandArgs));
        return getThis();
    }

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
     * @param expireOptions The expiration option.
     * @return Command response - <code>true</code> if the timeout was set. <code>false</code> if the
     *     timeout was not set. e.g. <code>key</code> doesn't exist, or operation skipped due to the
     *     provided arguments.
     */
    public T pexpireAt(
            @NonNull String key, long unixMilliseconds, @NonNull ExpireOptions expireOptions) {
        ArgsArray commandArgs =
                buildArgs(
                        ArrayUtils.addAll(
                                new String[] {key, Long.toString(unixMilliseconds)}, expireOptions.toArgs()));

        protobufTransaction.addCommands(buildCommand(PExpireAt, commandArgs));
        return getThis();
    }

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout.
     *
     * @see <a href="https://redis.io/commands/ttl/">redis.io</a> for details.
     * @param key The <code>key</code> to return its timeout.
     * @return Command response - TTL in seconds, <code>-2</code> if <code>key</code> does not exist,
     *     or <code>-1</code> if <code>key</code> exists but has no associated expire.
     */
    public T ttl(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(TTL, commandArgs));
        return getThis();
    }

    /**
     * Gets the current connection id.
     *
     * @see <a href="https://redis.io/commands/client-id/">redis.io</a> for details.
     * @return Command response - The id of the client.
     */
    public T clientId() {
        protobufTransaction.addCommands(buildCommand(ClientId));
        return getThis();
    }

    /**
     * Gets the name of the current connection.
     *
     * @see <a href="https://redis.io/commands/client-getname/">redis.io</a> for details.
     * @return Command response - The name of the client connection as a string if a name is set, or
     *     <code>null</code> if no name is assigned.
     */
    public T clientGetName() {
        protobufTransaction.addCommands(buildCommand(ClientGetName));
        return getThis();
    }

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see <a href="https://redis.io/commands/config-rewrite/">redis.io</a> for details.
     * @return <code>OK</code> is returned when the configuration was rewritten properly. Otherwise,
     *     the transaction fails with an error.
     */
    public T configRewrite() {
        protobufTransaction.addCommands(buildCommand(ConfigRewrite));
        return getThis();
    }

    /**
     * Resets the statistics reported by Redis using the <a
     * href="https://redis.io/commands/info/">INFO</a> and <a
     * href="https://redis.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.
     *
     * @see <a href="https://redis.io/commands/config-resetstat/">redis.io</a> for details.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     */
    public T configResetStat() {
        protobufTransaction.addCommands(buildCommand(ConfigResetStat));
        return getThis();
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The Zadd options.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return Command Response - The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     */
    public T zadd(
            @NonNull String key,
            @NonNull Map<String, Double> membersScoresMap,
            @NonNull ZaddOptions options,
            boolean changed) {
        String[] changedArg = changed ? new String[] {"CH"} : new String[] {};
        String[] membersScores = convertMapToValueKeyStringArray(membersScoresMap);

        String[] arguments =
                concatenateArrays(new String[] {key}, options.toArgs(), changedArg, membersScores);

        ArgsArray commandArgs = buildArgs(arguments);

        protobufTransaction.addCommands(buildCommand(Zadd, commandArgs));
        return getThis();
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The Zadd options.
     * @return Command Response - The number of elements added to the sorted set.
     */
    public T zadd(
            @NonNull String key,
            @NonNull Map<String, Double> membersScoresMap,
            @NonNull ZaddOptions options) {
        return getThis().zadd(key, membersScoresMap, options, false);
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return Command Response - The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     */
    public T zadd(
            @NonNull String key, @NonNull Map<String, Double> membersScoresMap, boolean changed) {
        return getThis().zadd(key, membersScoresMap, ZaddOptions.builder().build(), changed);
    }

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @return Command Response - The number of elements added to the sorted set.
     */
    public T zadd(@NonNull String key, @NonNull Map<String, Double> membersScoresMap) {
        return getThis().zadd(key, membersScoresMap, ZaddOptions.builder().build(), false);
    }

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @param options The Zadd options.
     * @return Command Response - The score of the member.<br>
     *     If there was a conflict with the options, the operation aborts and <code>null</code> is
     *     returned.<br>
     */
    public T zaddIncr(
            @NonNull String key, @NonNull String member, double increment, @NonNull ZaddOptions options) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {key},
                                options.toArgs(),
                                new String[] {"INCR", Double.toString(increment), member}));

        protobufTransaction.addCommands(buildCommand(Zadd, commandArgs));
        return getThis();
    }

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @return Command Response - The score of the member.
     */
    public T zaddIncr(@NonNull String key, @NonNull String member, double increment) {
        return getThis().zaddIncr(key, member, increment, ZaddOptions.builder().build());
    }

    /**
     * Removes the specified members from the sorted set stored at <code>key</code>.<br>
     * Specified members that are not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/zrem/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members to remove from the sorted set.
     * @return Command Response - The number of members that were removed from the sorted set, not
     *     including non-existing members.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     returns <code>0</code>.
     */
    public T zrem(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));
        protobufTransaction.addCommands(buildCommand(Zrem, commandArgs));
        return getThis();
    }

    /**
     * Returns the cardinality (number of elements) of the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zcard/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - The number of elements in the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     return <code>0</code>.
     */
    public T zcard(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(new String[] {key});
        protobufTransaction.addCommands(buildCommand(Zcard, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> members with the lowest scores from the sorted set
     * stored at the specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from lowest to highest.
     * @return Command Response - A map of the removed members and their scores, ordered from the one
     *     with the lowest score to the one with the highest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public T zpopmin(@NonNull String key, long count) {
        ArgsArray commandArgs = buildArgs(new String[] {key, Long.toString(count)});
        protobufTransaction.addCommands(buildCommand(ZPopMin, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns the member with the lowest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public T zpopmin(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(new String[] {key});
        protobufTransaction.addCommands(buildCommand(ZPopMin, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns up to <code>count</code> members with the highest scores from the sorted
     * set stored at the specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from highest to lowest.
     * @return Command Response - A map of the removed members and their scores, ordered from the one
     *     with the highest score to the one with the lowest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public T zpopmax(@NonNull String key, long count) {
        ArgsArray commandArgs = buildArgs(new String[] {key, Long.toString(count)});
        protobufTransaction.addCommands(buildCommand(ZPopMax, commandArgs));
        return getThis();
    }

    /**
     * Removes and returns the member with the highest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     */
    public T zpopmax(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(new String[] {key});
        protobufTransaction.addCommands(buildCommand(ZPopMax, commandArgs));
        return getThis();
    }

    /**
     * Returns the score of <code>member</code> in the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose score is to be retrieved.
     * @return Command Response - The score of the member.<br>
     *     If <code>member</code> does not exist in the sorted set, <code>null</code> is returned.<br>
     *     If <code>key</code> does not exist, <code>null</code> is returned.
     */
    public T zscore(@NonNull String key, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(new String[] {key, member});
        protobufTransaction.addCommands(buildCommand(ZScore, commandArgs));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, with
     * scores ordered from low to high.<br>
     * To get the rank of <code>member</code> with it's score, see <code>zrankWithScore</code>.
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return The rank of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public T zrank(@NonNull String key, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(new String[] {key, member});
        protobufTransaction.addCommands(buildCommand(Zrank, commandArgs));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with it's
     * score, where scores are ordered from the lowest to highest.
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return An array containing the rank (as <code>Long</code>) and score (as <code>Double</code>)
     *     of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public T zrankWithScore(@NonNull String key, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(new String[] {key, member, WITH_SCORE_REDIS_API});
        protobufTransaction.addCommands(buildCommand(Zrank, commandArgs));
        return getThis();
    }

    /**
     * Adds an entry to the specified stream.
     *
     * @see <a href="https://redis.io/commands/xadd/">redis.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @return Command Response - The id of the added entry.
     */
    public T xadd(@NonNull String key, @NonNull Map<String, String> values) {
        this.xadd(key, values, StreamAddOptions.builder().build());
        return getThis();
    }

    /**
     * Adds an entry to the specified stream.
     *
     * @see <a href="https://redis.io/commands/xadd/">redis.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @param options Stream add options.
     * @return Command Response - The id of the added entry, or <code>null</code> if {@link
     *     StreamAddOptions#makeStream} is set to <code>false</code> and no stream with the matching
     *     <code>key</code> exists.
     */
    public T xadd(
            @NonNull String key, @NonNull Map<String, String> values, @NonNull StreamAddOptions options) {
        String[] arguments =
                ArrayUtils.addAll(
                        ArrayUtils.addFirst(options.toArgs(), key), convertMapToKeyValueStringArray(values));
        ArgsArray commandArgs = buildArgs(arguments);
        protobufTransaction.addCommands(buildCommand(XAdd, commandArgs));
        return getThis();
    }

    /**
     * Returns the remaining time to live of <code>key</code> that has a timeout, in milliseconds.
     *
     * @see <a href="https://redis.io/commands/pttl/">redis.io</a> for details.
     * @param key The key to return its timeout.
     * @return Command Response - TTL in milliseconds. <code>-2</code> if <code>key</code> does not
     *     exist, <code>-1</code> if <code>key</code> exists but has no associated expire.
     */
    public T pttl(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(PTTL, commandArgs));
        return getThis();
    }

    /**
     * Removes the existing timeout on <code>key</code>, turning the <code>key</code> from volatile (a
     * <code>key</code> with an expire set) to persistent (a <code>key</code> that will never expire
     * as no timeout is associated).
     *
     * @see <a href="https://redis.io/commands/persist/">redis.io</a> for details.
     * @param key The <code>key</code> to remove the existing timeout on.
     * @return Command Response - <code>false</code> if <code>key</code> does not exist or does not
     *     have an associated timeout, <code>true</code> if the timeout has been removed.
     */
    public T persist(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(new String[] {key});
        protobufTransaction.addCommands(buildCommand(Persist, commandArgs));
        return getThis();
    }

    /**
     * Returns the server time.
     *
     * @see <a href="https://redis.io/commands/time/">redis.io</a> for details.
     * @return Command Response - The current server time as a <code>String</code> array with two
     *     elements: A Unix timestamp and the amount of microseconds already elapsed in the current
     *     second. The returned array is in a <code>[Unix timestamp, Microseconds already elapsed]
     *     </code> format.
     */
    public T time() {
        protobufTransaction.addCommands(buildCommand(Time));
        return getThis();
    }

    /**
     * Returns the string representation of the type of the value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/type/>redis.io</a> for details.
     * @param key The <code>key</code> to check its data type.
     * @return Command Response - If the <code>key</code> exists, the type of the stored value is
     *     returned. Otherwise, a "none" string is returned.
     */
    public T type(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(Type, commandArgs));
        return getThis();
    }

    /**
     * Inserts specified values at the tail of the <code>list</code>, only if <code>key</code> already
     * exists and holds a list.
     *
     * @see <a href="https://redis.io/commands/rpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the tail of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operation.
     */
    public T rpushx(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));
        protobufTransaction.addCommands(buildCommand(RPushX, commandArgs));
        return getThis();
    }

    /**
     * Inserts specified values at the head of the <code>list</code>, only if <code>key</code> already
     * exists and holds a list.
     *
     * @see <a href="https://redis.io/commands/lpushx/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return Command Response - The length of the list after the push operation.
     */
    public T lpushx(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));
        protobufTransaction.addCommands(buildCommand(LPushX, commandArgs));
        return getThis();
    }

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return Command Response - An array of elements within the specified range. If <code>key</code>
     *     does not exist, it is treated as an empty sorted set, and the command returns an empty
     *     array.
     */
    public T zrange(@NonNull String key, @NonNull RangeQuery rangeQuery, boolean reverse) {
        ArgsArray commandArgs = buildArgs(createZrangeArgs(key, rangeQuery, reverse, false));
        protobufTransaction.addCommands(buildCommand(Zrange, commandArgs));
        return getThis();
    }

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return Command Response - An array of elements within the specified range. If <code>key</code>
     *     does not exist, it is treated as an empty sorted set, and the command returns an empty
     *     array.
     */
    public T zrange(@NonNull String key, @NonNull RangeQuery rangeQuery) {
        return getThis().zrange(key, rangeQuery, false);
    }

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return Command Response - A <code>Map</code> of elements and their scores within the specified
     *     range. If <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>Map</code>.
     */
    public T zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery, boolean reverse) {
        ArgsArray commandArgs = buildArgs(createZrangeArgs(key, rangeQuery, reverse, true));
        protobufTransaction.addCommands(buildCommand(Zrange, commandArgs));
        return getThis();
    }

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return Command Response - A <code>Map</code> of elements and their scores within the specified
     *     range. If <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>Map</code>.
     */
    public T zrangeWithScores(@NonNull String key, @NonNull ScoredRangeQuery rangeQuery) {
        return getThis().zrangeWithScores(key, rangeQuery, false);
    }

    /**
     * Adds all elements to the HyperLogLog data structure stored at the specified <code>key</code>.
     * <br>
     * Creates a new structure if the <code>key</code> does not exist.
     *
     * <p>When no <code>elements</code> are provided, and <code>key</code> exists and is a
     * HyperLogLog, then no operation is performed. If <code>key</code> does not exist, then the
     * HyperLogLog structure is created.
     *
     * @see <a href="https://redis.io/commands/pfadd/">redis.io</a> for details.
     * @param key The <code>key</code> of the HyperLogLog data structure to add elements into.
     * @param elements An array of members to add to the HyperLogLog stored at <code>key</code>.
     * @return Command Response - If the HyperLogLog is newly created, or if the HyperLogLog
     *     approximated cardinality is altered, then returns <code>1</code>. Otherwise, returns <code>
     *     0</code>.
     */
    public T pfadd(@NonNull String key, @NonNull String[] elements) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(elements, key));
        protobufTransaction.addCommands(buildCommand(PfAdd, commandArgs));
        return getThis();
    }

    /** Build protobuf {@link Command} object for given command and arguments. */
    protected Command buildCommand(RequestType requestType) {
        return buildCommand(requestType, buildArgs());
    }

    /** Build protobuf {@link Command} object for given command and arguments. */
    protected Command buildCommand(RequestType requestType, ArgsArray args) {
        return Command.newBuilder().setRequestType(requestType).setArgsArray(args).build();
    }

    /** Build protobuf {@link ArgsArray} object for given arguments. */
    protected ArgsArray buildArgs(String... stringArgs) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();

        for (String string : stringArgs) {
            commandArgs.addArgs(string);
        }

        return commandArgs.build();
    }
}
