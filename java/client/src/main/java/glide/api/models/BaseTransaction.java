/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.utils.ArrayTransformUtils.convertMapToArgArray;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.HashDel;
import static redis_request.RedisRequestOuterClass.RequestType.HashExists;
import static redis_request.RedisRequestOuterClass.RequestType.HashGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashGetAll;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.HashMGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashSet;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;

import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
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
     * @remarks This function should only be used for single-response commands. Commands that don't
     *     return response (such as <em>SUBSCRIBE</em>), or that return potentially more than a single
     *     response (such as <em>XREAD</em>), or that change the client's behavior (such as entering
     *     <em>pub</em>/<em>sub</em> mode on <em>RESP2</em> connections) shouldn't be called using
     *     this function.
     * @example Returns a list of all pub/sub clients:
     *     <pre>
     * Object result = client.customCommand(new String[]{ "CLIENT", "LIST", "TYPE", "PUBSUB" }).get();
     * </pre>
     *
     * @param args Arguments for the custom command.
     * @return A response from Redis with an <code>Object</code>.
     */
    public T customCommand(String[] args) {

        ArgsArray commandArgs = buildArgs(args);
        protobufTransaction.addCommands(buildCommand(CustomCommand, commandArgs));
        return getThis();
    }

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return A response from Redis with a <code>String</code>.
     */
    public T ping() {
        protobufTransaction.addCommands(buildCommand(Ping));
        return getThis();
    }

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param msg The ping argument that will be returned.
     * @return A response from Redis with a <code>String</code>.
     */
    public T ping(String msg) {
        ArgsArray commandArgs = buildArgs(msg);

        protobufTransaction.addCommands(buildCommand(Ping, commandArgs));
        return getThis();
    }

    /**
     * Get information and statistics about the Redis server using the {@link Section#DEFAULT} option.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return A response from Redis with a <code>String</code>.
     */
    public T info() {
        protobufTransaction.addCommands(buildCommand(Info));
        return getThis();
    }

    /**
     * Get information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link Section} values specifying which sections of information to
     *     retrieve. When no parameter is provided, the {@link Section#DEFAULT} option is assumed.
     * @return Response from Redis with a <code>String</code> containing the requested {@link
     *     Section}s.
     */
    public T info(InfoOptions options) {
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
    public T del(String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);

        protobufTransaction.addCommands(buildCommand(Del, commandArgs));
        return getThis();
    }

    /**
     * Get the value associated with the given key, or null if no such value exists.
     *
     * @see <a href="https://redis.io/commands/get/">redis.io</a> for details.
     * @param key The key to retrieve from the database.
     * @return Response from Redis. <code>key</code> exists, returns the <code>value</code> of <code>
     *     key</code> as a String. Otherwise, return <code>null</code>.
     */
    public T get(String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(GetString, commandArgs));
        return getThis();
    }

    /**
     * Set the given key with the given value.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given <code>key</code>.
     * @return Response from Redis.
     */
    public T set(String key, String value) {
        ArgsArray commandArgs = buildArgs(key, value);

        protobufTransaction.addCommands(buildCommand(SetString, commandArgs));
        return getThis();
    }

    /**
     * Set the given key with the given value. Return value is dependent on the passed options.
     *
     * @see <a href="https://redis.io/commands/set/">redis.io</a> for details.
     * @param key The key to store.
     * @param value The value to store with the given key.
     * @param options The Set options.
     * @return Response from Redis with a <code>String</code> or <code>null</code> response. The old
     *     value as a <code>String</code> if {@link SetOptionsBuilder#returnOldValue(boolean)} is set.
     *     Otherwise, if the value isn't set because of {@link ConditionalSet#ONLY_IF_EXISTS} or
     *     {@link ConditionalSet#ONLY_IF_DOES_NOT_EXIST} conditions, return <code>null</code>.
     *     Otherwise, return <code>OK</code>.
     */
    public T set(String key, String value, SetOptions options) {
        ArgsArray commandArgs =
                buildArgs(ArrayUtils.addAll(new String[] {key, value}, options.toArgs()));

        protobufTransaction.addCommands(buildCommand(SetString, commandArgs));
        return getThis();
    }

    /**
     * Retrieve the values of multiple <code>keys</code>.
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
     * Set multiple keys to multiple values in a single operation.
     *
     * @see <a href="https://redis.io/commands/mset/">redis.io</a> for details.
     * @param keyValueMap A key-value map consisting of keys and their respective values to set.
     * @return Command Response - Always <code>OK</code>.
     */
    public T mset(@NonNull Map<String, String> keyValueMap) {
        String[] args = convertMapToArgArray(keyValueMap);
        ArgsArray commandArgs = buildArgs(args);

        protobufTransaction.addCommands(buildCommand(MSet, commandArgs));
        return getThis();
    }

    /**
     * Increment the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
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
     * Increment the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
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
     * Increment the string representing a floating point number stored at <code>key</code> by <code>
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
     * Decrement the number stored at <code>key</code> by one. If <code>key</code> does not exist, it
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
     * Decrement the number stored at <code>key</code> by <code>amount</code>. If <code>key</code>
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
     * Retrieve the value associated with <code>field</code> in the hash stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/hget/">redis.io</a> for details.
     * @param key The key of the hash.
     * @param field The field in the hash stored at <code>key</code> to retrieve from the database.
     * @return Command Response - The value associated with <code>field</code>, or <code>null</code>
     *     when <code>field
     *     </code> is not present in the hash or <code>key</code> does not exist.
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
                buildArgs(ArrayUtils.addFirst(convertMapToArgArray(fieldValueMap), key));

        protobufTransaction.addCommands(buildCommand(HashSet, commandArgs));
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
     * Increment the string representing a floating point number stored at <code>field</code> in the
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
     * @returns Command Response - The value of <code>field</code> in the hash stored at <code>key
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
     * @return Command Response - The value of the first element. <br>
     *     If <code>key</code> does not exist, null will be returned. <br>
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
     *     If <code>key</code> does not exist, null will be returned.<br>
     */
    public T lpopCount(@NonNull String key, long count) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(count));

        protobufTransaction.addCommands(buildCommand(LPop, commandArgs));
        return getThis();
    }

    /**
     * Add specified members to the set stored at <code>key</code>. Specified members that are already
     * a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/sadd/">redis.io</a> for details.
     * @param key The <code>key</code> where members will be added to its set.
     * @param members A list of members to add to the set stored at <code>key</code>.
     * @return Command Response - The number of members that were added to the set, excluding members
     *     already present.
     * @remarks If <code>key</code> does not exist, a new set is created before adding <code>members
     *     </code>.
     */
    public T sadd(String key, String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));

        protobufTransaction.addCommands(buildCommand(SAdd, commandArgs));
        return getThis();
    }

    /**
     * Remove specified members from the set stored at <code>key</code>. Specified members that are
     * not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/srem/">redis.io</a> for details.
     * @param key The <code>key</code> from which members will be removed.
     * @param members A list of members to remove from the set stored at <code>key</code>.
     * @return Command Response - The number of members that were removed from the set, excluding
     *     non-existing members.
     * @remarks If <code>key</code> does not exist, it is treated as an empty set and this command
     *     returns 0.
     */
    public T srem(String key, String[] members) {
        ArgsArray commandArgs = buildArgs(ArrayUtils.addFirst(members, key));

        protobufTransaction.addCommands(buildCommand(SRem, commandArgs));
        return getThis();
    }

    /**
     * Retrieve all the members of the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smembers/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @return Command Response - A <code>Set</code> of all members of the set.
     * @remarks If <code>key</code> does not exist an empty set will be returned.
     */
    public T smembers(String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(SMembers, commandArgs));
        return getThis();
    }

    /**
     * Retrieve the set cardinality (number of elements) of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/scard/">redis.io</a> for details.
     * @param key The key from which to retrieve the number of set members.
     * @return Command Response - The cardinality (number of elements) of the set, or 0 if the key
     *     does not exist.
     */
    public T scard(String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(SCard, commandArgs));
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
    public T exists(String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);

        protobufTransaction.addCommands(buildCommand(Exists, commandArgs));
        return getThis();
    }

    /**
     * Unlink (delete) multiple <code>keys</code> from the database. A key is ignored if it does not
     * exist. This command, similar to DEL, removes specified keys and ignores non-existent ones.
     * However, this command does not block the server, while <a
     * href="https://redis.io/commands/del/">DEL</a> does.
     *
     * @see <a href="https://redis.io/commands/unlink/">redis.io</a> for details.
     * @param keys The list of keys to unlink.
     * @return Command Response - The number of <code>keys</code> that were unlinked.
     */
    public T unlink(String[] keys) {
        ArgsArray commandArgs = buildArgs(keys);

        protobufTransaction.addCommands(buildCommand(Unlink, commandArgs));
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
