import {
    InfoOptions,
    SetOptions,
    createConfigResetStat,
    createCustomCommand,
    createDel,
    createGet,
    createInfo,
    createSelect,
    createSet
} from "./Commands";
import { redis_request } from "./ProtobufMessage";

/// Base class that includes all the shared commands in Client and ClusterClient.
export class BaseTransaction {
    readonly commands: redis_request.Command[] = [];

    /// Get the value associated with the given key, or null if no such value exists.
    /// See https://redis.io/commands/get/ for details.
    public get(key: string) {
        this.commands.push(createGet(key));
    }

    /// Set the given key with the given value. Return value is dependent on the passed options.
    /// See https://redis.io/commands/set/ for details.
    public set(key: string, value: string, options?: SetOptions) {
        this.commands.push(createSet(key, value, options));
    }

    /// Returns information and statistics about the server according to the given arguments.
    /// See https://redis.io/commands/info/ for details.
    public info(options?: InfoOptions[]) {
        this.commands.push(createInfo(options));
    }

    /// Removes the specified keys. A key is ignored if it does not exist.
    /// Returns the number of keys that were removed.
    /// See https://redis.io/commands/del/ for details.
    public del(keys: string[]) {
        this.commands.push(createDel(keys));
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
     * 
     * Returns always "OK"
    */
    public ConfigResetStat() {
        this.commands.push(createConfigResetStat());
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
export class Transaction extends BaseTransaction{
    /// TODO: add MOVE, SLAVEOF and all SENTINEL commands

    /** Change the currently selected Redis database.
     * See https://redis.io/commands/select/ for details.
     * 
     * @param index : The index of the database to select.
     * @CommandResponse :  A simple OK response.
     */
    public select(index: number) {
        this.commands.push(createSelect(index));
    }
}

/// Extends BaseTransaction class for cluster mode commands.
export class ClusterTransaction extends BaseTransaction{
    /// TODO: add all CLUSTER commands
}
