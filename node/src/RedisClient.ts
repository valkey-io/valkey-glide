import * as net from "net";
import { BaseClient, ConnectionOptions, ReturnType } from "./BaseClient";
import {
    InfoOptions,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCustomCommand,
    createInfo,
    createPing,
    createSelect,
} from "./Commands";
import { Transaction } from "./Transaction";

export class RedisClient extends BaseClient {
    public static createClient(
        options: ConnectionOptions
    ): Promise<RedisClient> {
        return super.createClientInternal<RedisClient>(
            options,
            (socket: net.Socket) => new RedisClient(socket)
        );
    }

    static async __createClient(
        options: ConnectionOptions,
        connectedSocket: net.Socket
    ): Promise<RedisClient> {
        return this.__createClientInternal(
            options,
            connectedSocket,
            (socket, options) => new RedisClient(socket, options)
        );
    }

    public exec(transaction: Transaction): Promise<ReturnType[]> {
        return this.createWritePromise(transaction.commands);
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
    public customCommand(
        commandName: string,
        args: string[]
    ): Promise<ReturnType> {
        return this.createWritePromise(createCustomCommand(commandName, args));
    }

    /** Ping the Redis server.
     * See https://redis.io/commands/ping/ for details.
     *
     * @param str - the ping argument that will be returned.
     * @returns PONG if no argument is provided, otherwise return a copy of the argument.
     */
    public ping(str?: string): Promise<string> {
        return this.createWritePromise(createPing(str));
    }

    /** Get information and statistics about the Redis server.
     *  See https://redis.io/commands/info/ for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     *  When no parameter is provided, the default option is assumed.
     * @returns a string containing the information for the sections requested.
     */
    public info(options?: InfoOptions[]): Promise<string> {
        return this.createWritePromise(createInfo(options));
    }

    /** Change the currently selected Redis database.
     * See https://redis.io/commands/select/ for details.
     *
     * @param index - The index of the database to select.
     * @returns A simple OK response.
     */
    public select(index: number): Promise<"OK"> {
        return this.createWritePromise(createSelect(index));
    }

    /** Rewrite the configuration file with the current configuration.
     * See https://redis.io/commands/config-rewrite/ for details.
     *
     * @returns "OK" when the configuration was rewritten properly, Otherwise an error is raised.
     */
    public configRewrite(): Promise<"OK"> {
        return this.createWritePromise(createConfigRewrite());
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * See https://redis.io/commands/config-resetstat/ for details.
     *
     * @returns always "OK"
     */
    public configResetStat(): Promise<"OK"> {
        return this.createWritePromise(createConfigResetStat());
    }

    /** Reads the configuration parameters of a running Redis server.
     *  See https://redis.io/commands/config-get/ for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     * @param route - The command will be routed automatically, unless `route` is provided, in which
     *  case the client will initially try to route the command to the nodes defined by `route`.
     *
     * @returns A list of values corresponding to the configuration parameters.
     *
     */
    public configGet(parameters: string[]): Promise<string[]> {
        return this.createWritePromise(createConfigGet(parameters));
    }

    /** Set configuration parameters to the specified values.
     *   See https://redis.io/commands/config-set/ for details.
     *
     * @param parameters - A List of keyValuePairs consisting of configuration parameters and their respective values to set.
     * @param route - The command will be routed automatically, unless `route` is provided, in which
     *   case the client will initially try to route the command to the nodes defined by `route`.
     *
     * @returns "OK" when the configuration was set properly. Otherwise an error is raised.
     *
     * @example
     *  config_set([("timeout", "1000")], [("maxmemory", "1GB")]) - Returns OK
     *
     */
    public configSet(parameters: Record<string, string>): Promise<"OK"> {
        return this.createWritePromise(createConfigSet(parameters));
    }
}
