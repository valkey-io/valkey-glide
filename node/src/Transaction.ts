import {
    InfoOptions,
    SetOptions,
    createClientGetName,
    createClientId,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCustomCommand,
    createDecr,
    createDecrBy,
    createDel,
    createGet,
    createHDel,
    createHExists,
    createHGet,
    createHGetAll,
    createHIncrBy,
    createHIncrByFloat,
    createHMGet,
    createHSet,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createInfo,
    createLPop,
    createLPush,
    createLRange,
    createMGet,
    createMSet,
    createPing,
    createRPop,
    createRPush,
    createSAdd,
    createSCard,
    createSMembers,
    createSRem,
    createSelect,
    createSet,
} from "./Commands";
import { redis_request } from "./ProtobufMessage";

/**
 * Base class encompassing shared commands for both standalone and cluster mode implementations in a transaction.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the client exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 * @example
 *       transaction = new BaseTransaction();
 *       transaction.set("key", "value");
 *       transaction.get("key");
 *       await client.exec(transaction);
 *       [OK , "value"]
 */
export class BaseTransaction {
    readonly commands: redis_request.Command[] = [];

    /** Get the value associated with the given key, or null if no such value exists.
     * See https://redis.io/commands/get/ for details.
     *
     * @param key - The key to retrieve from the database.
     *
     * Command Response - If `key` exists, returns the value of `key` as a string. Otherwise, return null.
     */
    public get(key: string) {
        this.commands.push(createGet(key));
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     * See https://redis.io/commands/set/ for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - The set options.
     *
     * Command Response - If the value is successfully set, return OK.
     * If value isn't set because of `onlyIfExists` or `onlyIfDoesNotExist` conditions, return null.
     * If `returnOldValue` is set, return the old value as a string.
     */
    public set(key: string, value: string, options?: SetOptions) {
        this.commands.push(createSet(key, value, options));
    }

    /** Ping the Redis server.
     * See https://redis.io/commands/ping/ for details.
     *
     * @param str - the ping argument that will be returned.
     *
     * Command Response - PONG if no argument is provided, otherwise return a copy of the argument.
     */
    public ping(str?: string) {
        this.commands.push(createPing(str));
    }

    /** Get information and statistics about the Redis server.
     * See https://redis.io/commands/info/ for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     * When no parameter is provided, the default option is assumed.
     *
     * Command Response - a string containing the information for the sections requested.
     */
    public info(options?: InfoOptions[]) {
        this.commands.push(createInfo(options));
    }

    /** Remove the specified keys. A key is ignored if it does not exist.
     * See https://redis.io/commands/del/ for details.
     *
     * @param keys - A list of keys to be deleted from the database.
     *
     * Command Response - the number of keys that were removed.
     */
    public del(keys: string[]) {
        this.commands.push(createDel(keys));
    }

    /** Get the name of the current connection.
     * See https://redis.io/commands/client-getname/ for more details.
     *
     * Command Response - the name of the client connection as a string if a name is set, or null if no name is assigned.
     */
    public clientGetName() {
        this.commands.push(createClientGetName());
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://redis.io/commands/select/ for details.
     *
     * Command Response - "OK" when the configuration was rewritten properly, Otherwise an error is raised.
     */
    public configRewrite() {
        this.commands.push(createConfigRewrite());
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
     *
     * Command Response - always "OK"
     */
    public configResetStat() {
        this.commands.push(createConfigResetStat());
    }

    /** Retrieve the values of multiple keys.
     * See https://redis.io/commands/mget/ for details.
     *
     * @param keys - A list of keys to retrieve values for.
     *
     * Command Response - A list of values corresponding to the provided keys. If a key is not found,
     * its corresponding value in the list will be null.
     */
    public mget(keys: string[]) {
        this.commands.push(createMGet(keys));
    }

    /** Set multiple keys to multiple values in a single atomic operation.
     * See https://redis.io/commands/mset/ for details.
     *
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     *
     * Command Response - always "OK".
     */
    public mset(keyValueMap: Record<string, string>) {
        this.commands.push(createMSet(keyValueMap));
    }

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incr/ for details.
     *
     * @param key - The key to increment its value.
     *
     * Command Response - the value of `key` after the increment, An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public incr(key: string) {
        this.commands.push(createIncr(key));
    }

    /** Increments the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrby/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     *
     * Command Response - the value of `key` after the increment, An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public incrBy(key: string, amount: number) {
        this.commands.push(createIncrBy(key, amount));
    }

    /** Increment the string representing a floating point number stored at `key` by `amount`.
     * By using a negative amount value, the result is that the value stored at `key` is decremented.
     * If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrbyfloat/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     *
     * Command Response - the value of `key` after the increment as string.
     * An error is raised if `key` contains a value of the wrong type,
     * or the current key content is not parsable as a double precision floating point number.
     *
     */
    public incrByFloat(key: string, amount: number) {
        this.commands.push(createIncrByFloat(key, amount));
    }

    /** Returns the current connection id.
     * See https://redis.io/commands/client-id/ for details.
     *
     * Command Response - the id of the client.
     */
    public clientId() {
        this.commands.push(createClientId());
    }

    /** Decrements the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decr/ for details.
     *
     * @param key - The key to decrement its value.
     *
     * Command Response - the value of `key` after the decrement. An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public decr(key: string) {
        this.commands.push(createDecr(key));
    }

    /** Decrements the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decrby/ for details.
     *
     * @param key - The key to decrement its value.
     * @param amount - The amount to decrement.
     *
     * Command Response - the value of `key` after the decrement. An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public decrBy(key: string, amount: number) {
        this.commands.push(createDecrBy(key, amount));
    }

    /** Reads the configuration parameters of a running Redis server.
     * See https://redis.io/commands/config-get/ for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     *
     * Command Response - A list of values corresponding to the configuration parameters.
     *
     */
    public configGet(parameters: string[]) {
        this.commands.push(createConfigGet(parameters));
    }

    /** Set configuration parameters to the specified values.
     * See https://redis.io/commands/config-set/ for details.
     *
     * @param parameters - A List of keyValuePairs consisting of configuration parameters and their respective values to set.
     *
     * Command Response - "OK" when the configuration was set properly. Otherwise an error is raised.
     *
     * @example
     * config_set([("timeout", "1000")], [("maxmemory", "1GB")]) - Returns OK
     *
     */
    public configSet(parameters: Record<string, string>) {
        this.commands.push(createConfigSet(parameters));
    }

    /** Retrieve the value associated with `field` in the hash stored at `key`.
     * See https://redis.io/commands/hget/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash stored at `key` to retrieve from the database.
     *
     * Command Response - the value associated with `field`, or null when `field` is not present in the hash or `key` does not exist.
     */
    public hget(key: string, field: string) {
        this.commands.push(createHGet(key, field));
    }

    /** Sets the specified fields to their respective values in the hash stored at `key`.
     * See https://redis.io/commands/hset/ for details.
     *
     * @param key - The key of the hash.
     * @param fieldValueMap - A field-value map consisting of fields and their corresponding values
     * to be set in the hash stored at the specified key.
     *
     * Command Response - The number of fields that were added.
     */
    public hset(key: string, fieldValueMap: Record<string, string>) {
        this.commands.push(createHSet(key, fieldValueMap));
    }

    /** Removes the specified fields from the hash stored at `key`.
     * Specified fields that do not exist within this hash are ignored.
     * See https://redis.io/commands/hdel/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields to remove from the hash stored at `key`.
     *
     * Command Response - the number of fields that were removed from the hash, not including specified but non existing fields.
     * If `key` does not exist, it is treated as an empty hash and it returns 0.
     */
    public hdel(key: string, fields: string[]) {
        this.commands.push(createHDel(key, fields));
    }

    /** Returns the values associated with the specified fields in the hash stored at `key`.
     * See https://redis.io/commands/hmget/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve from the database.
     *
     * Command Response - a list of values associated with the given fields, in the same order as they are requested.
     * For every field that does not exist in the hash, a null value is returned.
     * If `key` does not exist, it is treated as an empty hash and it returns a list of null values.
     */
    public hmget(key: string, fields: string[]) {
        this.commands.push(createHMGet(key, fields));
    }

    /** Returns if `field` is an existing field in the hash stored at `key`.
     * See https://redis.io/commands/hexists/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field to check in the hash stored at `key`.
     *
     * Command Response - 1 if the hash contains `field`. If the hash does not contain `field`, or if `key` does not exist,
     * the command response will be 0.
     */
    public hexists(key: string, field: string) {
        this.commands.push(createHExists(key, field));
    }

    /** Returns all fields and values of the hash stored at `key`.
     * See https://redis.io/commands/hgetall/ for details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - a list of fields and their values stored in the hash. Every field name in the list is followed by its value.
     * If `key` does not exist, it returns an empty list.
     */
    public hgetall(key: string) {
        this.commands.push(createHGetAll(key));
    }

    /** Increments the number stored at `field` in the hash stored at `key` by `increment`.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/hincrby/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     *
     * Command Response - the value of `field` in the hash stored at `key` after the increment.
     *  An error will be raised if `key` holds a value of an incorrect type (not a string)
     *  or if it contains a string that cannot be represented as an integer.
     */
    public hincrBy(key: string, field: string, amount: number) {
        this.commands.push(createHIncrBy(key, field, amount));
    }

    /** Increment the string representing a floating point number stored at `field` in the hash stored at `key` by `increment`.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/hincrbyfloat/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     *
     * Command Response - the value of `field` in the hash stored at `key` after the increment as string.
     *  An error is raised if `key` contains a value of the wrong type
     *  or the current field content is not parsable as a double precision floating point number.
     *
     */
    public hincrByFloat(key: string, field: string, amount: number) {
        this.commands.push(createHIncrByFloat(key, field, amount));
    }

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/lpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     *
     * Command Response - the length of the list after the push operations.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lpush(key: string, elements: string[]) {
        this.commands.push(createLPush(key, elements));
    }

    /** Removes and returns the first elements of the list stored at `key`.
     * By default, the command pops a single element from the beginning of the list.
     * When `count` is provided, the command pops up to `count` elements, depending on the list's length.
     * See https://redis.io/commands/lpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     *
     * Command Response - The value of the first element if `count` is not provided. If `count` is provided, a list of the popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lpop(key: string, count?: number) {
        this.commands.push(createLPop(key, count));
    }

    /**Returns the specified elements of the list stored at `key`.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * See https://redis.io/commands/lrange/ for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range
     * @param end - The end of the range.
     *
     * Command Response - list of elements in the specified range.
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, an empty list will be returned.
     * If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
     * If `key` does not exist an empty list will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lrange(key: string, start: number, end: number) {
        this.commands.push(createLRange(key, start, end));
    }

    /** Inserts all the specified values at the tail of the list stored at `key`.
     * `elements` are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/rpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     *
     * Command Response - the length of the list after the push operations.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public rpush(key: string, elements: string[]) {
        this.commands.push(createRPush(key, elements));
    }

    /** Removes and returns the last elements of the list stored at `key`.
     * By default, the command pops a single element from the end of the list.
     * When `count` is provided, the command pops up to `count` elements, depending on the list's length.
     * See https://redis.io/commands/rpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     *
     * Command Response - The value of the last element if `count` is not provided. If `count` is provided, list of popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public rpop(key: string, count?: number) {
        this.commands.push(createRPop(key, count));
    }

    /** Adds the specified members to the set stored at `key`. Specified members that are already a member of this set are ignored.
     * If `key` does not exist, a new set is created before adding `members`.
     * See https://redis.io/commands/sadd/ for details.
     *
     * @param key - The key to store the members to its set.
     * @param members - A list of members to add to the set stored at `key`.
     *
     * Command Response - the number of members that were added to the set, not including all the members already present in the set.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public sadd(key: string, members: string[]) {
        this.commands.push(createSAdd(key, members));
    }

    /** Removes the specified members from the set stored at `key`. Specified members that are not a member of this set are ignored.
     * See https://redis.io/commands/srem/ for details.
     *
     * @param key - The key to remove the members from its set.
     * @param members - A list of members to remove from the set stored at `key`.
     *
     * Command Response - the number of members that were removed from the set, not including non existing members.
     * If `key` does not exist, it is treated as an empty set and this command returns 0.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public srem(key: string, members: string[]) {
        this.commands.push(createSRem(key, members));
    }

    /** Returns all the members of the set value stored at `key`.
     * See https://redis.io/commands/smembers/ for details.
     *
     * @param key - The key to return its members.
     *
     * Command Response - all members of the set.
     * If `key` does not exist, it is treated as an empty set and this command returns empty list.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public smembers(key: string) {
        this.commands.push(createSMembers(key));
    }

    /** Returns the set cardinality (number of elements) of the set stored at `key`.
     * See https://redis.io/commands/scard/ for details.
     *
     * @param key - The key to return the number of its members.
     *
     * Command Response - the cardinality (number of elements) of the set, or 0 if key does not exist.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public scard(key: string) {
        this.commands.push(createSCard(key));
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *
     * @example
     * Returns a list of all pub/sub clients:
     * ```ts
     * connection.customCommand("CLIENT", ["LIST","TYPE", "PUBSUB"])
     * ```
     */
    public customCommand(commandName: string, args: string[]) {
        return this.commands.push(createCustomCommand(commandName, args));
    }
}

/**
 * Extends BaseTransaction class for Redis standalone commands.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the RedisClient.exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 * @example
 *       transaction = new Transaction();
 *       transaction.set("key", "value");
 *       transaction.select(1);  /// Standalone command
 *       transaction.get("key");
 *       await RedisClient.exec(transaction);
 *       [OK , OK , null]
 */
export class Transaction extends BaseTransaction {
    /// TODO: add MOVE, SLAVEOF and all SENTINEL commands

    /** Change the currently selected Redis database.
     * See https://redis.io/commands/select/ for details.
     *
     * @param index - The index of the database to select.
     *
     * Command Response - A simple OK response.
     */
    public select(index: number) {
        this.commands.push(createSelect(index));
    }
}

/**
 * Extends BaseTransaction class for cluster mode commands.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the RedisClusterClient.exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 */
export class ClusterTransaction extends BaseTransaction {
    /// TODO: add all CLUSTER commands
}
