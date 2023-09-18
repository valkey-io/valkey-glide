import {
    InfoOptions,
    SetOptions,
    createConfigResetStat,
    createConfigRewrite,
    createCustomCommand,
    createDel,
    createGet,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createInfo,
    createPing,
    createSelect,
    createSet,
} from "./Commands";
import { redis_request } from "./ProtobufMessage";

/// Base class that includes all the shared commands in Client and ClusterClient.
export class BaseTransaction {
    readonly commands: redis_request.Command[] = [];

    /** Get the value associated with the given key, or null if no such value exists.
     *  See https://redis.io/commands/get/ for details.
     *
     * @param key - The key to retrieve from the database.
     * @returns If the key exists, returns the value of the key as a string. Otherwise, return null.
     */
    public get(key: string) {
        this.commands.push(createGet(key));
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     *  See https://redis.io/commands/set/ for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - The set options.
     * @returns If the value is successfully set, return OK.
     *          If value isn't set because of only_if_exists or only_if_does_not_exist conditions, return null.
     *          If return_old_value is set, return the old value as a string.
     */
    public set(key: string, value: string, options?: SetOptions) {
        this.commands.push(createSet(key, value, options));
    }

    /** Ping the Redis server.
     * See https://redis.io/commands/ping/ for details.
     *
     * @param str - the ping argument that will be returned.
     * Returns PONG if no argument is provided, otherwise return a copy of the argument.
     */
    public ping(str?: string) {
        this.commands.push(createPing(str));
    }

    /** Get information and statistics about the Redis server.
     *  See https://redis.io/commands/info/ for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     *  When no parameter is provided, the default option is assumed.
     * @returns a string containing the information for the sections requested.
     */
    public info(options?: InfoOptions[]) {
        this.commands.push(createInfo(options));
    }

    /** Remove the specified keys. A key is ignored if it does not exist.
     *  See https://redis.io/commands/del/ for details.
     *
     * @param keys - A list of keys to be deleted from the database.
     * @returns the number of keys that were removed.
     */
    public del(keys: string[]) {
        this.commands.push(createDel(keys));
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://redis.io/commands/select/ for details.
     *
     * Returns "OK" when the configuration was rewritten properly, Otherwise an error is raised.
     */
    public configRewrite() {
        this.commands.push(createConfigRewrite());
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
     *
     * Returns always "OK"
     */
    public ConfigResetStat() {
        this.commands.push(createConfigResetStat());
    }

    /** Increments the number stored at key by one. If the key does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incr/ for details.
     *
     * @param key - The key to increment it's value.
     * Returns the value of key after the increment, An error is returned if the key contains a value
     *  of the wrong type or contains a string that can not be represented as integer.
     */
    public incr(key: string) {
        this.commands.push(createIncr(key));
    }

    /** Increments the number stored at key by increment. If the key does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrby/ for details.
     *
     * @param key - The key to increment it's value.
     * @param amount - The amount to increment.
     * Returns the value of key after the increment, An error is returned if the key contains a value
     *  of the wrong type or contains a string that can not be represented as integer.
     */
    public incrBy(key: string, amount: number) {
        this.commands.push(createIncrBy(key, amount));
    }

    /** Increment the string representing a floating point number stored at key by the specified increment.
     * By using a negative increment value, the result is that the value stored at the key is decremented.
     * If the key does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrbyfloat/ for details.
     *
     * @param key - The key to increment it's value.
     * @param amount - The amount to increment.
     * Returns the value of key after the increment as string, An error is returned if the key contains a value of the wrong type.
     *
     */
    public incrByFloat(key: string, amount: number) {
        this.commands.push(createIncrByFloat(key, amount));
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

/// Extends BaseTransaction class for Redis standalone commands.
export class Transaction extends BaseTransaction {
    /// TODO: add MOVE, SLAVEOF and all SENTINEL commands

    /** Change the currently selected Redis database.
     * See https://redis.io/commands/select/ for details.
     *
     * @param index - The index of the database to select.
     * Returns A simple OK response.
     */
    public select(index: number) {
        this.commands.push(createSelect(index));
    }
}

/// Extends BaseTransaction class for cluster mode commands.
export class ClusterTransaction extends BaseTransaction {
    /// TODO: add all CLUSTER commands
}
