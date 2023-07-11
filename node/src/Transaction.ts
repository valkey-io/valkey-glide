import {
    SetOptions,
    createCustomCommand,
    createGet,
    createSet,
    createPing,
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

    /// Returns PONG if no argument is provided, otherwise return a copy of the argument
    /// See https://redis.io/commands/ping/ for details.
    public ping(str?: string) {
        this.commands.push(createPing(str));
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
