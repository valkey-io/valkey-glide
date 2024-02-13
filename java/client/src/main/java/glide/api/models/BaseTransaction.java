/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;

import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.ConditionalSet;
import glide.api.models.commands.SetOptions.SetOptionsBuilder;
import lombok.Getter;
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
     * Object result = client.customCommand("CLIENT","LIST","TYPE", "PUBSUB").get();
     * </pre>
     *
     * @param args Arguments for the custom command.
     * @return A response from Redis with an <code>Object</code>.
     */
    public T customCommand(String... args) {

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
     * Get information and statistics about the Redis server. No argument is provided, so the {@link
     * Section#DEFAULT} option is assumed.
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
     * Add specified members to the set stored at <code>key</code>. Specified members that are already
     * a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/sadd/">redis.io</a> for details.
     * @param key The <code>key</code> where members will be added to its set.
     * @param members A list of members to add to the set stored at <code>key</code>.
     * @return Command Response - The number of members that were added to the set, excluding members
     *     already present.
     * @remarks
     *     <ul>
     *       <li>If <code>key</code> does not exist, a new set is created before adding <code>members
     *           </code>.
     *       <li>If <code>key</code> holds a value that is not a set, the transaction fails.
     *     </ul>
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
     * @remarks
     *     <ul>
     *       <li>If <code>key</code> does not exist, it is treated as an empty set and this command
     *           returns 0.
     *       <li>If <code>key</code> holds a value that is not a set, the transaction fails.
     *     </ul>
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
     * @remarks
     *     <ul>
     *       <li>If <code>key</code> does not exist an empty set will be returned.
     *       <li>If <code>key</code> holds a value that is not a set, the transaction fails.
     *     </ul>
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
     * @remarks If <code>key</code> holds a value that is not a set, the transaction fails.
     */
    public T scard(String key) {
        ArgsArray commandArgs = buildArgs(key);

        protobufTransaction.addCommands(buildCommand(SCard, commandArgs));
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
