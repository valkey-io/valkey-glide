import {
    InfoOptions,
    SetOptions,
    createCustomCommand,
    createDel,
    createGet,
    createInfo,
    createSet
} from "./Commands";
import { redis_request } from "./ProtobufMessage";

export class Transaction {
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
