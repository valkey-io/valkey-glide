/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.ServerManagementCommands.VERSION_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_REDIS_API;
import static glide.api.models.commands.RangeOptions.createZRangeArgs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static glide.utils.ArrayTransformUtils.mapGeoDataToArray;
import static redis_request.RedisRequestOuterClass.RequestType.BZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.BZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.Blpop;
import static redis_request.RedisRequestOuterClass.RequestType.Brpop;
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
import static redis_request.RedisRequestOuterClass.RequestType.FlushAll;
import static redis_request.RedisRequestOuterClass.RequestType.FunctionLoad;
import static redis_request.RedisRequestOuterClass.RequestType.GeoAdd;
import static redis_request.RedisRequestOuterClass.RequestType.GeoPos;
import static redis_request.RedisRequestOuterClass.RequestType.GetRange;
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
import static redis_request.RedisRequestOuterClass.RequestType.Hkeys;
import static redis_request.RedisRequestOuterClass.RequestType.Hvals;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LInsert;
import static redis_request.RedisRequestOuterClass.RequestType.LLen;
import static redis_request.RedisRequestOuterClass.RequestType.LOLWUT;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.LPushX;
import static redis_request.RedisRequestOuterClass.RequestType.LRange;
import static redis_request.RedisRequestOuterClass.RequestType.LRem;
import static redis_request.RedisRequestOuterClass.RequestType.LTrim;
import static redis_request.RedisRequestOuterClass.RequestType.LastSave;
import static redis_request.RedisRequestOuterClass.RequestType.Lindex;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectEncoding;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectFreq;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectIdletime;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectRefcount;
import static redis_request.RedisRequestOuterClass.RequestType.PExpire;
import static redis_request.RedisRequestOuterClass.RequestType.PExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.PTTL;
import static redis_request.RedisRequestOuterClass.RequestType.Persist;
import static redis_request.RedisRequestOuterClass.RequestType.PfAdd;
import static redis_request.RedisRequestOuterClass.RequestType.PfCount;
import static redis_request.RedisRequestOuterClass.RequestType.PfMerge;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.RPop;
import static redis_request.RedisRequestOuterClass.RequestType.RPush;
import static redis_request.RedisRequestOuterClass.RequestType.RPushX;
import static redis_request.RedisRequestOuterClass.RequestType.RenameNx;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SDiff;
import static redis_request.RedisRequestOuterClass.RequestType.SDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.SInter;
import static redis_request.RedisRequestOuterClass.RequestType.SInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.SIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SMove;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SUnionStore;
import static redis_request.RedisRequestOuterClass.RequestType.SetRange;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;
import static redis_request.RedisRequestOuterClass.RequestType.Strlen;
import static redis_request.RedisRequestOuterClass.RequestType.TTL;
import static redis_request.RedisRequestOuterClass.RequestType.Time;
import static redis_request.RedisRequestOuterClass.RequestType.Touch;
import static redis_request.RedisRequestOuterClass.RequestType.Type;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.XAdd;
import static redis_request.RedisRequestOuterClass.RequestType.XTrim;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiff;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZLexCount;
import static redis_request.RedisRequestOuterClass.RequestType.ZMScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.ZRandMember;
import static redis_request.RedisRequestOuterClass.RequestType.ZRangeStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByLex;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZRevRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZUnion;
import static redis_request.RedisRequestOuterClass.RequestType.ZUnionStore;
import static redis_request.RedisRequestOuterClass.RequestType.Zadd;
import static redis_request.RedisRequestOuterClass.RequestType.Zcard;
import static redis_request.RedisRequestOuterClass.RequestType.Zcount;
import static redis_request.RedisRequestOuterClass.RequestType.Zrange;
import static redis_request.RedisRequestOuterClass.RequestType.Zrank;
import static redis_request.RedisRequestOuterClass.RequestType.Zrem;

import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.LInsertOptions.InsertPosition;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.LexRange;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.RangeOptions.ScoreRange;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
import glide.api.models.commands.WeightAggregateOptions;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.ZaddOptions;
import glide.api.models.commands.function.FunctionLoadOptions;
import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamAddOptions.StreamAddOptionsBuilder;
import glide.api.models.commands.stream.StreamTrimOptions;
import java.util.Arrays;
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
     * @apiNote See <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#custom-command">Glide
     *     for Redis Wiki</a> for details on the restrictions and limitations of the custom command
     *     API.
     * @param args Arguments for the custom command.
     * @return A response from Redis with an <code>Object</code>.
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
     * @return Command Response - The length of the string stored at <code>key</code> after it was
     *     modified.
     */
    public T setrange(@NonNull String key, int offset, @NonNull String value) {
        ArgsArray commandArgs = buildArgs(key, Integer.toString(offset), value);
        protobufTransaction.addCommands(buildCommand(SetRange, commandArgs));
        return getThis();
    }

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
     * @return Command Response - A substring extracted from the value stored at <code>key</code>.
     */
    public T getrange(@NonNull String key, int start, int end) {
        ArgsArray commandArgs = buildArgs(key, Integer.toString(start), Integer.toString(end));
        protobufTransaction.addCommands(buildCommand(GetRange, commandArgs));
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
     * Returns all field names in the hash stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/hkeys/">redis.io</a> for details
     * @param key The key of the hash.
     * @return Command Response - An <code>array</code> of field names in the hash, or an <code>
     *     empty array</code> when the key does not exist.
     */
    public T hkeys(@NonNull String key) {
        protobufTransaction.addCommands(buildCommand(Hkeys, buildArgs(key)));
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
     * Returns the element at <code>index</code> from the list stored at <code>key</code>.<br>
     * The index is zero-based, so <code>0</code> means the first element, <code>1</code> the second
     * element and so on. Negative indices can be used to designate elements starting at the tail of
     * the list. Here, <code>-1</code> means the last element, <code>-2</code> means the penultimate
     * and so forth.
     *
     * @see <a href="https://redis.io/commands/lindex/">redis.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element in the list to retrieve.
     * @return Command Response - The element at <code>index</code> in the list stored at <code>key
     *     </code>.<br>
     *     If <code>index</code> is out of range or if <code>key</code> does not exist, <code>null
     *     </code> is returned.
     */
    public T lindex(@NonNull String key, long index) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(index));

        protobufTransaction.addCommands(buildCommand(Lindex, commandArgs));
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
     * Returns if <code>member</code> is a member of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/sismember/">redis.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check for existence in the set.
     * @return Command Response - <code>true</code> if the member exists in the set, <code>false
     *     </code> otherwise. If <code>key</code> doesn't exist, it is treated as an <code>empty set
     *     </code> and the command returns <code>false</code>.
     */
    public T sismember(@NonNull String key, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(key, member);
        protobufTransaction.addCommands(buildCommand(SIsMember, commandArgs));
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
     * Computes the difference between the first set and all the successive sets in <code>keys</code>.
     *
     * @see <a href="https://redis.io/commands/sdiff/">redis.io</a> for details.
     * @param keys The keys of the sets to diff.
     * @return Command Response - A <code>Set</code> of elements representing the difference between
     *     the sets.<br>
     *     If the a <code>key</code> does not exist, it is treated as an empty set.
     */
    public T sdiff(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);
        protobufTransaction.addCommands(buildCommand(SDiff, commandArgs));
        return getThis();
    }

    /**
     * Checks whether each member is contained in the members of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smismember/">redis.io</a> for details.
     * @param key The key of the set to check.
     * @param members A list of members to check for existence in the set.
     * @return Command Response - An <code>array</code> of <code>Boolean</code> values, each
     *     indicating if the respective member exists in the set.
     */
    public T smismember(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));
        protobufTransaction.addCommands(buildCommand(SMIsMember, commandArgs));
        return getThis();
    }

    /**
     * Stores the difference between the first set and all the successive sets in <code>keys</code>
     * into a new set at <code>destination</code>.
     *
     * @see <a href="https://redis.io/commands/sdiffstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys of the sets to diff.
     * @return Command Response - The number of elements in the resulting set.
     */
    public T sdiffstore(@NonNull String destination, @NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(keys, destination));
        protobufTransaction.addCommands(buildCommand(SDiffStore, commandArgs));
        return getThis();
    }

    /**
     * Moves <code>member</code> from the set at <code>source</code> to the set at <code>destination
     * </code>, removing it from the source set. Creates a new destination set if needed. The
     * operation is atomic.
     *
     * @see <a href="https://redis.io/commands/smove/">redis.io</a> for details.
     * @param source The key of the set to remove the element from.
     * @param destination The key of the set to add the element to.
     * @param member The set element to move.
     * @return Command response - <code>true</code> on success, or <code>false</code> if the <code>
     *     source</code> set does not exist or the element is not a member of the source set.
     */
    public T smove(@NonNull String source, @NonNull String destination, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(source, destination, member);
        protobufTransaction.addCommands(buildCommand(SMove, commandArgs));
        return getThis();
    }

    /**
     * Gets the intersection of all the given sets.
     *
     * @see <a href="https://redis.io/commands/sinter/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return Command Response - A <code>Set</code> of members which are present in all given sets.
     *     <br>
     *     Missing or empty input sets cause an empty response.
     */
    public T sinter(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);
        protobufTransaction.addCommands(buildCommand(SInter, commandArgs));
        return getThis();
    }

    /**
     * Stores the members of the intersection of all given sets specified by <code>keys</code> into a
     * new set at <code>destination</code>.
     *
     * @see <a href="https://redis.io/commands/sinterstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return Command Response - The number of elements in the resulting set.
     */
    public T sinterstore(@NonNull String destination, @NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(keys, destination));
        protobufTransaction.addCommands(buildCommand(SInterStore, commandArgs));
        return getThis();
    }

    /**
     * Stores the members of the union of all given sets specified by <code>keys</code> into a new set
     * at <code>destination</code>.
     *
     * @see <a href="https://redis.io/commands/sunionstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return Command Response - The number of elements in the resulting set.
     */
    public T sunionstore(@NonNull String destination, @NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(keys, destination));
        protobufTransaction.addCommands(buildCommand(SUnionStore, commandArgs));
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
        return zadd(key, membersScoresMap, options, false);
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
        return zadd(key, membersScoresMap, ZaddOptions.builder().build(), changed);
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
        return zadd(key, membersScoresMap, ZaddOptions.builder().build(), false);
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
        return zaddIncr(key, member, increment, ZaddOptions.builder().build());
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
        ArgsArray commandArgs = buildArgs(key);
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
        ArgsArray commandArgs = buildArgs(key, Long.toString(count));
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
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ZPopMin, commandArgs));
        return getThis();
    }

    /**
     * Returns a random element from the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return Command Response - A <code>String</code> representing a random element from the sorted
     *     set.<br>
     *     If the sorted set does not exist or is empty, the response will be <code>null</code>.
     */
    public T zrandmember(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ZRandMember, commandArgs));
        return getThis();
    }

    /**
     * Retrieves random elements from the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows for duplicates.<br>
     * @return Command Response - An <code>array</code> of elements from the sorted set.<br>
     *     If the sorted set does not exist or is empty, the response will be an empty <code>array
     *     </code>.
     */
    public T zrandmemberWithCount(@NonNull String key, long count) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(count));
        protobufTransaction.addCommands(buildCommand(ZRandMember, commandArgs));
        return getThis();
    }

    /**
     * Retrieves random elements along with their scores from the sorted set stored at <code>key
     * </code>.
     *
     * @see <a href="https://redis.io/commands/zrandmember/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count The number of elements to return.<br>
     *     If <code>count</code> is positive, returns unique elements.<br>
     *     If negative, allows duplicates.<br>
     * @return Command Response - An <code>array</code> of <code>[element, score]</code> <code>arrays
     *     </code>, where element is a <code>String</code> and score is a <code>Double</code>.<br>
     *     If the sorted set does not exist or is empty, the response will be an empty <code>array
     *     </code>.
     */
    public T zrandmemberWithCountWithScores(String key, long count) {
        String[] arguments = new String[] {key, Long.toString(count), WITH_SCORES_REDIS_API};

        ArgsArray commandArgs = buildArgs(arguments);
        protobufTransaction.addCommands(buildCommand(ZRandMember, commandArgs));
        return getThis();
    }

    /**
     * Blocks the connection until it removes and returns a member with the lowest score from the
     * sorted sets stored at the specified <code>keys</code>. The sorted sets are checked in the order
     * they are provided.<br>
     * <code>BZPOPMIN</code> is the blocking variant of {@link #zpopmin(String)}.<br>
     *
     * @see <a href="https://redis.io/commands/bzpopmin/">redis.io</a> for more details.
     * @apiNote <code>BZPOPMIN</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The keys of the sorted sets.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - An <code>array</code> containing the key where the member was popped
     *     out, the member itself, and the member score.<br>
     *     If no member could be popped and the <code>timeout</code> expired, returns </code>null
     *     </code>.
     */
    public T bzpopmin(@NonNull String[] keys, double timeout) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.add(keys, Double.toString(timeout)));
        protobufTransaction.addCommands(buildCommand(BZPopMin, commandArgs));
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
        ArgsArray commandArgs = buildArgs(key, Long.toString(count));
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
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ZPopMax, commandArgs));
        return getThis();
    }

    /**
     * Blocks the connection until it removes and returns a member with the highest score from the
     * sorted sets stored at the specified <code>keys</code>. The sorted sets are checked in the order
     * they are provided.<br>
     * <code>BZPOPMAX</code> is the blocking variant of {@link #zpopmax(String)}.<br>
     *
     * @see <a href="https://redis.io/commands/bzpopmax/">redis.io</a> for more details.
     * @apiNote <code>BZPOPMAX</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The keys of the sorted sets.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - An <code>array</code> containing the key where the member was popped
     *     out, the member itself, and the member score.<br>
     *     If no member could be popped and the <code>timeout</code> expired, returns </code>null
     *     </code>.
     */
    public T bzpopmax(@NonNull String[] keys, double timeout) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.add(keys, Double.toString(timeout)));
        protobufTransaction.addCommands(buildCommand(BZPopMax, commandArgs));
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
        ArgsArray commandArgs = buildArgs(key, member);
        protobufTransaction.addCommands(buildCommand(ZScore, commandArgs));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, with
     * scores ordered from low to high, starting from <code>0</code>.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrankWithScore}.
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return The rank of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public T zrank(@NonNull String key, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(key, member);
        protobufTransaction.addCommands(buildCommand(Zrank, commandArgs));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with its
     * score, where scores are ordered from the lowest to highest, starting from <code>0</code>.<br>
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
        ArgsArray commandArgs = buildArgs(key, member, WITH_SCORE_REDIS_API);
        protobufTransaction.addCommands(buildCommand(Zrank, commandArgs));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, where
     * scores are ordered from the highest to lowest, starting from <code>0</code>.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrevrankWithScore}.
     *
     * @see <a href="https://redis.io/commands/zrevrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return Command Response - The rank of <code>member</code> in the sorted set, where ranks are
     *     ordered from high to low based on scores.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public T zrevrank(@NonNull String key, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(key, member);
        protobufTransaction.addCommands(buildCommand(ZRevRank, commandArgs));
        return getThis();
    }

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with its
     * score, where scores are ordered from the highest to lowest, starting from <code>0</code>.
     *
     * @see <a href="https://redis.io/commands/zrevrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return Command Response - An array containing the rank (as <code>Long</code>) and score (as
     *     <code>Double</code>) of <code>member</code> in the sorted set, where ranks are ordered from
     *     high to low based on scores.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     */
    public T zrevrankWithScore(@NonNull String key, @NonNull String member) {
        ArgsArray commandArgs = buildArgs(key, member, WITH_SCORE_REDIS_API);
        protobufTransaction.addCommands(buildCommand(ZRevRank, commandArgs));
        return getThis();
    }

    /**
     * Returns the scores associated with the specified <code>members</code> in the sorted set stored
     * at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zmscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members in the sorted set.
     * @return Command Response - An <code>Array</code> of scores of the <code>members</code>.<br>
     *     If a <code>member</code> does not exist, the corresponding value in the <code>Array</code>
     *     will be <code>null</code>.
     */
    public T zmscore(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));
        protobufTransaction.addCommands(buildCommand(ZMScore, commandArgs));
        return getThis();
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.<br>
     * To get the elements with their scores, see {@link #zdiffWithScores}.
     *
     * @see <a href="https://redis.io/commands/zdiff/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - An <code>array</code> of elements representing the difference
     *     between the sorted sets. <br>
     *     If the first <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>array</code>.
     */
    public T zdiff(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(keys, Long.toString(keys.length)));
        protobufTransaction.addCommands(buildCommand(ZDiff, commandArgs));
        return getThis();
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.
     *
     * @see <a href="https://redis.io/commands/zdiff/">redis.io</a> for more details.
     * @param keys The keys of the sorted sets.
     * @return Command Response - A <code>Map</code> of elements and their scores representing the
     *     difference between the sorted sets.<br>
     *     If the first <code>key</code> does not exist, it is treated as an empty sorted set, and the
     *     command returns an empty <code>Map</code>.
     */
    public T zdiffWithScores(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Long.toString(keys.length));
        arguments = ArrayUtils.add(arguments, WITH_SCORES_REDIS_API);
        ArgsArray commandArgs = buildArgs(arguments);
        protobufTransaction.addCommands(buildCommand(ZDiff, commandArgs));
        return getThis();
    }

    /**
     * Calculates the difference between the first sorted set and all the successive sorted sets at
     * <code>keys</code> and stores the difference as a sorted set to <code>destination</code>,
     * overwriting it if it already exists. Non-existent keys are treated as empty sets.
     *
     * @see <a href="https://redis.io/commands/zdiffstore/">redis.io</a> for more details.
     * @param destination The key for the resulting sorted set.
     * @param keys The keys of the sorted sets to compare.
     * @return Command Response - The number of members in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public T zdiffstore(@NonNull String destination, @NonNull String[] keys) {
        ArgsArray commandArgs =
                buildArgs(ArrayUtils.addAll(new String[] {destination, Long.toString(keys.length)}, keys));
        protobufTransaction.addCommands(buildCommand(ZDiffStore, commandArgs));
        return getThis();
    }

    /**
     * Returns the number of members in the sorted set stored at <code>key</code> with scores between
     * <code>minScore</code> and <code>maxScore</code>.
     *
     * @see <a href="https://redis.io/commands/zcount/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minScore The minimum score to count from. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @param maxScore The maximum score to count up to. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @return Command Response - The number of members in the specified score range.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>maxScore < minScore</code>, <code>0</code> is returned.
     */
    public T zcount(@NonNull String key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        ArgsArray commandArgs = buildArgs(key, minScore.toArgs(), maxScore.toArgs());
        protobufTransaction.addCommands(buildCommand(Zcount, commandArgs));
        return getThis();
    }

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with rank between <code>start
     * </code> and <code>end</code>. Both <code>start</code> and <code>end</code> are zero-based
     * indexes with <code>0</code> being the element with the lowest score. These indexes can be
     * negative numbers, where they indicate offsets starting at the element with the highest score.
     *
     * @see <a href="https://redis.io/commands/zremrangebyrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param start The starting point of the range.
     * @param end The end of the range.
     * @return Command Response - The number of elements removed.<br>
     *     If <code>start</code> exceeds the end of the sorted set, or if <code>start</code> is
     *     greater than <code>end</code>, <code>0</code> returned.<br>
     *     If <code>end</code> exceeds the actual end of the sorted set, the range will stop at the
     *     actual end of the sorted set.<br>
     *     If <code>key</code> does not exist <code>0</code> will be returned.
     */
    public T zremrangebyrank(@NonNull String key, long start, long end) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(start), Long.toString(end));
        protobufTransaction.addCommands(buildCommand(ZRemRangeByRank, commandArgs));
        return getThis();
    }

    /**
     * Stores a specified range of elements from the sorted set at <code>source</code>, into a new
     * sorted set at <code>destination</code>. If <code>destination</code> doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.<br>
     *
     * @see <a href="https://redis.io/commands/zrangestore/">redis.io</a> for more details.
     * @param destination The key for the destination sorted set.
     * @param source The key of the source sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If <code>true</code>, reverses the sorted set, with index <code>0</code> as the
     *     element with the highest score.
     * @return Command Response - The number of elements in the resulting sorted set.
     */
    public T zrangestore(
            @NonNull String destination,
            @NonNull String source,
            @NonNull RangeQuery rangeQuery,
            boolean reverse) {
        ArgsArray commandArgs =
                buildArgs(RangeOptions.createZRangeStoreArgs(destination, source, rangeQuery, reverse));
        protobufTransaction.addCommands(buildCommand(ZRangeStore, commandArgs));
        return getThis();
    }

    /**
     * Stores a specified range of elements from the sorted set at <code>source</code>, into a new
     * sorted set at <code>destination</code>. If <code>destination</code> doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.<br>
     *
     * @see <a href="https://redis.io/commands/zrangestore/">redis.io</a> for more details.
     * @param destination The key for the destination sorted set.
     * @param source The key of the source sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting sorted set.
     */
    public T zrangestore(
            @NonNull String destination, @NonNull String source, @NonNull RangeQuery rangeQuery) {
        return getThis().zrangestore(destination, source, rangeQuery, false);
    }

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with a lexicographical order
     * between <code>minLex</code> and <code>maxLex</code>.
     *
     * @see <a href="https://redis.io/commands/zremrangebylex/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minLex The minimum bound of the lexicographical range. Can be an implementation of
     *     {@link InfLexBound} representing positive/negative infinity, or {@link LexBoundary}
     *     representing a specific lex and inclusivity.
     * @param maxLex The maximum bound of the lexicographical range. Can be an implementation of
     *     {@link InfLexBound} representing positive/negative infinity, or {@link LexBoundary}
     *     representing a specific lex and inclusivity.
     * @return Command Response - The number of members removed from the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>minLex</code> is greater than <code>maxLex</code>, <code>0</code> is returned.
     */
    public T zremrangebylex(@NonNull String key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        ArgsArray commandArgs = buildArgs(key, minLex.toArgs(), maxLex.toArgs());
        protobufTransaction.addCommands(buildCommand(ZRemRangeByLex, commandArgs));
        return getThis();
    }

    /**
     * Removes all elements in the sorted set stored at <code>key</code> with a score between <code>
     * minScore</code> and <code>maxScore</code>.
     *
     * @see <a href="https://redis.io/commands/zremrangebyscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minScore The minimum score to remove from. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @param maxScore The maximum score to remove to. Can be an implementation of {@link
     *     InfScoreBound} representing positive/negative infinity, or {@link ScoreBoundary}
     *     representing a specific score and inclusivity.
     * @return Command Response - The number of members removed.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>minScore</code> is greater than <code>maxScore</code>, <code>0</code> is returned.
     */
    public T zremrangebyscore(
            @NonNull String key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        ArgsArray commandArgs = buildArgs(key, minScore.toArgs(), maxScore.toArgs());
        protobufTransaction.addCommands(buildCommand(ZRemRangeByScore, commandArgs));
        return getThis();
    }

    /**
     * Returns the number of members in the sorted set stored at <code>key</code> with scores between
     * <code>minLex</code> and <code>maxLex</code>.
     *
     * @see <a href="https://redis.io/commands/zlexcount/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param minLex The minimum lex to count from. Can be an implementation of {@link InfLexBound}
     *     representing positive/negative infinity, or {@link LexBoundary} representing a specific lex
     *     and inclusivity.
     * @param maxLex The maximum lex to count up to. Can be an implementation of {@link InfLexBound}
     *     representing positive/negative infinity, or {@link LexBoundary} representing a specific lex
     *     and inclusivity.
     * @return Command Response - The number of members in the specified lex range.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and the command
     *     returns <code>0</code>.<br>
     *     If <code>maxLex < minLex</code>, <code>0</code> is returned.
     */
    public T zlexcount(@NonNull String key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        ArgsArray commandArgs = buildArgs(key, minLex.toArgs(), maxLex.toArgs());
        protobufTransaction.addCommands(buildCommand(ZLexCount, commandArgs));
        return getThis();
    }

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeys</code>, and
     * stores the result in <code>destination</code>. If <code>destination</code> already exists, it
     * is overwritten. Otherwise, a new sorted set will be created.
     *
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link WeightAggregateOptions.KeyArray} for keys only.
     *       <li>Use {@link WeightAggregateOptions.WeightedKeys} for weighted keys with score
     *           multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public T zunionstore(
            @NonNull String destination,
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {destination}, keysOrWeightedKeys.toArgs(), aggregate.toArgs()));
        protobufTransaction.addCommands(buildCommand(ZUnionStore, commandArgs));
        return getThis();
    }

    /**
     * Computes the union of sorted sets given by the specified <code>KeysOrWeightedKeys</code>, and
     * stores the result in <code>destination</code>. If <code>destination</code> already exists, it
     * is overwritten. Otherwise, a new sorted set will be created.
     *
     * @see <a href="https://redis.io/commands/zunionstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public T zunionstore(
            @NonNull String destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        ArgsArray commandArgs =
                buildArgs(concatenateArrays(new String[] {destination}, keysOrWeightedKeys.toArgs()));
        protobufTransaction.addCommands(buildCommand(ZUnionStore, commandArgs));
        return getThis();
    }

    /**
     * Computes the intersection of sorted sets given by the specified <code>keysOrWeightedKeys</code>
     * , and stores the result in <code>destination</code>. If <code>destination</code> already
     * exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link WeightAggregateOptions.KeyArray} for keys only.
     *       <li>Use {@link WeightAggregateOptions.WeightedKeys} for weighted keys with score
     *           multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public T zinterstore(
            @NonNull String destination,
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys,
            @NonNull Aggregate aggregate) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {destination}, keysOrWeightedKeys.toArgs(), aggregate.toArgs()));
        protobufTransaction.addCommands(buildCommand(ZInterStore, commandArgs));
        return getThis();
    }

    /**
     * Computes the intersection of sorted sets given by the specified <code>KeysOrWeightedKeys</code>
     * , and stores the result in <code>destination</code>. If <code>destination</code> already
     * exists, it is overwritten. Otherwise, a new sorted set will be created.<br>
     * To perform a <code>zinterstore</code> operation while specifying aggregation settings, use
     * {@link #zinterstore(String, KeysOrWeightedKeys, Aggregate)}
     *
     * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
     * @param destination The key of the destination sorted set.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The number of elements in the resulting sorted set stored at <code>
     *     destination</code>.
     */
    public T zinterstore(
            @NonNull String destination, @NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        ArgsArray commandArgs =
                buildArgs(concatenateArrays(new String[] {destination}, keysOrWeightedKeys.toArgs()));
        protobufTransaction.addCommands(buildCommand(ZInterStore, commandArgs));
        return getThis();
    }

    /**
     * Returns the union of members from sorted sets specified by the given <code>keysOrWeightedKeys
     * </code>.<br>
     * To get the elements with their scores, see {@link #zunionWithScores}.
     *
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunion(@NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        ArgsArray commandArgs =
                buildArgs(concatenateArrays(keysOrWeightedKeys.toArgs(), aggregate.toArgs()));
        protobufTransaction.addCommands(buildCommand(ZUnion, commandArgs));
        return getThis();
    }

    /**
     * Returns the union of members from sorted sets specified by the given <code>keysOrWeightedKeys
     * </code>.<br>
     * To perform a <code>zunion</code> operation while specifying aggregation settings, use {@link
     * #zunion(KeysOrWeightedKeys, Aggregate)}.<br>
     * To get the elements with their scores, see {@link #zunionWithScores}.
     *
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunion(@NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        ArgsArray commandArgs = buildArgs(keysOrWeightedKeys.toArgs());
        protobufTransaction.addCommands(buildCommand(ZUnion, commandArgs));
        return getThis();
    }

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.
     *
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @param aggregate Specifies the aggregation strategy to apply when combining the scores of
     *     elements.
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunionWithScores(
            @NonNull KeysOrWeightedKeys keysOrWeightedKeys, @NonNull Aggregate aggregate) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                keysOrWeightedKeys.toArgs(),
                                aggregate.toArgs(),
                                new String[] {WITH_SCORES_REDIS_API}));
        protobufTransaction.addCommands(buildCommand(ZUnion, commandArgs));
        return getThis();
    }

    /**
     * Returns the union of members and their scores from sorted sets specified by the given <code>
     * keysOrWeightedKeys</code>.<br>
     * To perform a <code>zunion</code> operation while specifying aggregation settings, use {@link
     * #zunionWithScores(KeysOrWeightedKeys, Aggregate)}.
     *
     * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
     * @param keysOrWeightedKeys The keys of the sorted sets with possible formats:
     *     <ul>
     *       <li>Use {@link KeyArray} for keys only.
     *       <li>Use {@link WeightedKeys} for weighted keys with score multipliers.
     *     </ul>
     *
     * @return Command Response - The resulting sorted set from the union.
     */
    public T zunionWithScores(@NonNull KeysOrWeightedKeys keysOrWeightedKeys) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(keysOrWeightedKeys.toArgs(), new String[] {WITH_SCORES_REDIS_API}));
        protobufTransaction.addCommands(buildCommand(ZUnion, commandArgs));
        return getThis();
    }

    /**
     * Adds an entry to the specified stream stored at <code>key</code>.<br>
     * If the <code>key</code> doesn't exist, the stream is created.
     *
     * @see <a href="https://redis.io/commands/xadd/">redis.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @return Command Response - The id of the added entry.
     */
    public T xadd(@NonNull String key, @NonNull Map<String, String> values) {
        return xadd(key, values, StreamAddOptions.builder().build());
    }

    /**
     * Adds an entry to the specified stream stored at <code>key</code>.<br>
     * If the <code>key</code> doesn't exist, the stream is created.
     *
     * @see <a href="https://redis.io/commands/xadd/">redis.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @param options Stream add options.
     * @return Command Response - The id of the added entry, or <code>null</code> if {@link
     *     StreamAddOptionsBuilder#makeStream(Boolean)} is set to <code>false</code> and no stream
     *     with the matching <code>key</code> exists.
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
     * Trims the stream by evicting older entries.
     *
     * @see <a href="https://redis.io/commands/xtrim/">redis.io</a> for details.
     * @param key The key of the stream.
     * @param options Stream trim options.
     * @return Command Response - The number of entries deleted from the stream.
     */
    public T xtrim(@NonNull String key, @NonNull StreamTrimOptions options) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(options.toArgs(), key));
        protobufTransaction.addCommands(buildCommand(XTrim, commandArgs));
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
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(Persist, commandArgs));
        return getThis();
    }

    /**
     * Returns the server time.
     *
     * @see <a href="https://redis.io/commands/time/">redis.io</a> for details.
     * @return Command Response - The current server time as a <code>String</code> array with two
     *     elements: A <code>UNIX TIME</code> and the amount of microseconds already elapsed in the
     *     current second. The returned array is in a <code>[UNIX TIME, Microseconds already elapsed]
     *     </code> format.
     */
    public T time() {
        protobufTransaction.addCommands(buildCommand(Time));
        return getThis();
    }

    /**
     * Returns <code>UNIX TIME</code> of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see <a href="https://redis.io/commands/lastsave/">redis.io</a> for details.
     * @return Command Response - <code>UNIX TIME</code> of the last DB save executed with success.
     */
    public T lastsave() {
        protobufTransaction.addCommands(buildCommand(LastSave));
        return getThis();
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return Command Response - <code>OK</code>.
     */
    public T flushall() {
        protobufTransaction.addCommands(buildCommand(FlushAll));
        return getThis();
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return Command Response - <code>OK</code>.
     */
    public T flushall(FlushMode mode) {
        protobufTransaction.addCommands(buildCommand(FlushAll, buildArgs(mode.toString())));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut() {
        protobufTransaction.addCommands(buildCommand(LOLWUT));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>On Redis version <code>5</code>, those are length of the line, number of squares per
     *           row, and number of squares per column.
     *       <li>On Redis version <code>6</code>, those are number of columns and number of lines.
     *       <li>On other versions parameters are ignored.
     *     </ul>
     *
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut(int @NonNull [] parameters) {
        String[] arguments =
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new);
        protobufTransaction.addCommands(buildCommand(LOLWUT, buildArgs(arguments)));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param version Version of computer art to generate.
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut(int version) {
        ArgsArray commandArgs = buildArgs(VERSION_REDIS_API, Integer.toString(version));
        protobufTransaction.addCommands(buildCommand(LOLWUT, commandArgs));
        return getThis();
    }

    /**
     * Displays a piece of generative computer art and the Redis version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://redis.io/commands/lolwut/">redis.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>For version <code>5</code>, those are length of the line, number of squares per row,
     *           and number of squares per column.
     *       <li>For version <code>6</code>, those are number of columns and number of lines.
     *     </ul>
     *
     * @return Command Response - A piece of generative computer art along with the current Redis
     *     version.
     */
    public T lolwut(int version, int @NonNull [] parameters) {
        String[] arguments =
                concatenateArrays(
                        new String[] {VERSION_REDIS_API, Integer.toString(version)},
                        Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        protobufTransaction.addCommands(buildCommand(LOLWUT, buildArgs(arguments)));
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
     * Renames <code>key</code> to <code>newKey</code> if <code>newKey</code> does not yet exist.
     *
     * @see <a href="https://redis.io/commands/renamenx/">redis.io</a> for details.
     * @param key The key to rename.
     * @param newKey The new key name.
     * @return Command Response - <code>true</code> if <code>key</code> was renamed to <code>newKey
     *     </code>, <code>false</code> if <code>newKey</code> already exists.
     */
    public T renamenx(@NonNull String key, @NonNull String newKey) {
        ArgsArray commandArgs = buildArgs(key, newKey);
        protobufTransaction.addCommands(buildCommand(RenameNx, commandArgs));
        return getThis();
    }

    /**
     * Inserts <code>element</code> in the list at <code>key</code> either before or after the <code>
     * pivot</code>.
     *
     * @see <a href="https://redis.io/commands/linsert/">redis.io</a> for details.
     * @param key The key of the list.
     * @param position The relative position to insert into - either {@link InsertPosition#BEFORE} or
     *     {@link InsertPosition#AFTER} the <code>pivot</code>.
     * @param pivot An element of the list.
     * @param element The new element to insert.
     * @return Command Response - The list length after a successful insert operation.<br>
     *     If the <code>key</code> doesn't exist returns <code>-1</code>.<br>
     *     If the <code>pivot</code> wasn't found, returns <code>0</code>.
     */
    public T linsert(
            @NonNull String key,
            @NonNull InsertPosition position,
            @NonNull String pivot,
            @NonNull String element) {
        ArgsArray commandArgs = buildArgs(key, position.toString(), pivot, element);
        protobufTransaction.addCommands(buildCommand(LInsert, commandArgs));
        return getThis();
    }

    /**
     * Pops an element from the tail of the first list that is non-empty, with the given <code>keys
     * </code> being checked in the order that they are given.<br>
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see <a href="https://redis.io/commands/brpop/">redis.io</a> for details.
     * @apiNote <code>BRPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The <code>keys</code> of the lists to pop from.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - A two-element <code>array</code> containing the <code>key</code>
     *     from which the element was popped and the <code>value</code> of the popped element,
     *     formatted as <code>
     *     [key, value]</code>. If no element could be popped and the timeout expired, returns </code>
     *     null</code>.
     */
    public T brpop(@NonNull String[] keys, double timeout) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.add(keys, Double.toString(timeout)));
        protobufTransaction.addCommands(buildCommand(Brpop, commandArgs));
        return getThis();
    }

    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
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
     * Inserts all the specified values at the tail of the list stored at <code>key</code>, only if
     * <code>key</code> exists and holds a list. If <code>key</code> is not a list, this performs no
     * operation.
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
     * Pops an element from the head of the first list that is non-empty, with the given <code>keys
     * </code> being checked in the order that they are given.<br>
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see <a href="https://redis.io/commands/blpop/">redis.io</a> for details.
     * @apiNote <code>BLPOP</code> is a client blocking command, see <a
     *     href="https://github.com/aws/glide-for-redis/wiki/General-Concepts#blocking-commands">Blocking
     *     Commands</a> for more details and best practices.
     * @param keys The <code>keys</code> of the lists to pop from.
     * @param timeout The number of seconds to wait for a blocking operation to complete. A value of
     *     <code>0</code> will block indefinitely.
     * @return Command Response - A two-element <code>array</code> containing the <code>key</code>
     *     from which the element was popped and the <code>value</code> of the popped element,
     *     formatted as <code>
     *     [key, value]</code>. If no element could be popped and the timeout expired, returns </code>
     *     null</code>.
     */
    public T blpop(@NonNull String[] keys, double timeout) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.add(keys, Double.toString(timeout)));
        protobufTransaction.addCommands(buildCommand(Blpop, commandArgs));
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
        ArgsArray commandArgs = buildArgs(createZRangeArgs(key, rangeQuery, reverse, false));
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
        ArgsArray commandArgs = buildArgs(createZRangeArgs(key, rangeQuery, reverse, true));
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

    /**
     * Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
     * calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
     *
     * @see <a href="https://redis.io/commands/pfcount/">redis.io</a> for details.
     * @param keys The keys of the HyperLogLog data structures to be analyzed.
     * @return Command Response - The approximated cardinality of given HyperLogLog data structures.
     *     <br>
     *     The cardinality of a key that does not exist is <code>0</code>.
     */
    public T pfcount(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);
        protobufTransaction.addCommands(buildCommand(PfCount, commandArgs));
        return getThis();
    }

    /**
     * Merges multiple HyperLogLog values into a unique value.<br>
     * If the destination variable exists, it is treated as one of the source HyperLogLog data sets,
     * otherwise a new HyperLogLog is created.
     *
     * @see <a href="https://redis.io/commands/pfmerge/">redis.io</a> for details.
     * @param destination The key of the destination HyperLogLog where the merged data sets will be
     *     stored.
     * @param sourceKeys The keys of the HyperLogLog structures to be merged.
     * @return Command Response - <code>OK</code>.
     */
    public T pfmerge(@NonNull String destination, @NonNull String[] sourceKeys) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(sourceKeys, destination));
        protobufTransaction.addCommands(buildCommand(PfMerge, commandArgs));
        return getThis();
    }

    /**
     * Returns the internal encoding for the Redis object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-encoding/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the internal encoding of.
     * @return Command response - If <code>key</code> exists, returns the internal encoding of the
     *     object stored at <code>key</code> as a <code>String</code>. Otherwise, return <code>null
     *     </code>.
     */
    public T objectEncoding(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectEncoding, commandArgs));
        return getThis();
    }

    /**
     * Returns the logarithmic access frequency counter of a Redis object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-freq/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the logarithmic access frequency counter
     *     of.
     * @return Command response - If <code>key</code> exists, returns the logarithmic access frequency
     *     counter of the object stored at <code>key</code> as a <code>Long</code>. Otherwise, returns
     *     <code>null</code>.
     */
    public T objectFreq(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectFreq, commandArgs));
        return getThis();
    }

    /**
     * Returns the time in seconds since the last access to the value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-idletime/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the idle time of.
     * @return Command response - If <code>key</code> exists, returns the idle time in seconds.
     *     Otherwise, returns <code>null</code>.
     */
    public T objectIdletime(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectIdletime, commandArgs));
        return getThis();
    }

    /**
     * Returns the reference count of the object stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/object-refcount/">redis.io</a> for details.
     * @param key The <code>key</code> of the object to get the reference count of.
     * @return Command response - If <code>key</code> exists, returns the reference count of the
     *     object stored at <code>key</code> as a <code>Long</code>. Otherwise, returns <code>null
     *     </code>.
     */
    public T objectRefcount(@NonNull String key) {
        ArgsArray commandArgs = buildArgs(key);
        protobufTransaction.addCommands(buildCommand(ObjectRefcount, commandArgs));
        return getThis();
    }

    /**
     * Updates the last access time of specified <code>keys</code>.
     *
     * @see <a href="https://redis.io/commands/touch/">redis.io</a> for details.
     * @param keys The keys to update last access time.
     * @return Command Response - The number of keys that were updated.
     */
    public T touch(@NonNull String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);
        protobufTransaction.addCommands(buildCommand(Touch, commandArgs));
        return getThis();
    }

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options The GeoAdd options - see {@link GeoAddOptions}
     * @return Command Response - The number of elements added to the sorted set. If <code>changed
     *     </code> is set to <code>true</code> in the options, returns the number of elements updated
     *     in the sorted set.
     */
    public T geoadd(
            @NonNull String key,
            @NonNull Map<String, GeospatialData> membersToGeospatialData,
            @NonNull GeoAddOptions options) {
        ArgsArray commandArgs =
                buildArgs(
                        concatenateArrays(
                                new String[] {key}, options.toArgs(), mapGeoDataToArray(membersToGeospatialData)));
        protobufTransaction.addCommands(buildCommand(GeoAdd, commandArgs));
        return getThis();
    }

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at <code>key
     * </code>.<br>
     * If a member is already a part of the sorted set, its position is updated.<br>
     * To perform a <code>geoadd</code> operation while specifying optional parameters, use {@link
     * #geoadd(String, Map, GeoAddOptions)}.
     *
     * @see <a href="https://redis.io/commands/geoadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersToGeospatialData A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @return Command Response - The number of elements added to the sorted set.
     */
    public T geoadd(
            @NonNull String key, @NonNull Map<String, GeospatialData> membersToGeospatialData) {
        return geoadd(key, membersToGeospatialData, new GeoAddOptions(false));
    }

    /**
     * Returns the positions (longitude,latitude) of all the specified <code>members</code> of the
     * geospatial index represented by the sorted set at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/geopos">valkey.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members The members for which to get the positions.
     * @return Command Response - A 2D <code>array</code> which represent positions (longitude and
     *     latitude) corresponding to the given members. If a member does not exist, its position will
     *     be <code>null</code>.
     */
    public T geopos(@NonNull String key, @NonNull String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));
        protobufTransaction.addCommands(buildCommand(GeoPos, commandArgs));
        return getThis();
    }

    /**
     * Loads a library to Redis unless a library with the same name exists. Use {@link
     * #functionLoadWithReplace} to replace existing libraries.
     *
     * @since Redis 7.0 and above
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @return Command Response - The library name that was loaded.
     */
    public T functionLoad(@NonNull String libraryCode) {
        ArgsArray commandArgs = buildArgs(libraryCode);
        protobufTransaction.addCommands(buildCommand(FunctionLoad, commandArgs));
        return getThis();
    }

    /**
     * Loads a library to Redis and overwrites a library with the same name if it exists.
     *
     * @since Redis 7.0 and above
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @return Command Response - The library name that was loaded.
     */
    public T functionLoadWithReplace(@NonNull String libraryCode) {
        ArgsArray commandArgs = buildArgs(FunctionLoadOptions.REPLACE.toString(), libraryCode);
        protobufTransaction.addCommands(buildCommand(FunctionLoad, commandArgs));
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
