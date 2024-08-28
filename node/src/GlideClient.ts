/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import * as net from "net";
import {
    BaseClient,
    BaseClientConfiguration,
    Decoder,
    DecoderOption,
    GlideString,
    PubSubMsg,
    ReadFrom, // eslint-disable-line @typescript-eslint/no-unused-vars
    ReturnType,
} from "./BaseClient";
import {
    FlushMode,
    FunctionListOptions,
    FunctionListResponse,
    FunctionRestorePolicy,
    FunctionStatsFullResponse,
    InfoOptions,
    LolwutOptions,
    SortOptions,
    createClientGetName,
    createClientId,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCopy,
    createCustomCommand,
    createDBSize,
    createEcho,
    createFlushAll,
    createFlushDB,
    createFunctionDelete,
    createFunctionDump,
    createFunctionFlush,
    createFunctionKill,
    createFunctionList,
    createFunctionLoad,
    createFunctionRestore,
    createFunctionStats,
    createInfo,
    createLastSave,
    createLolwut,
    createMove,
    createPing,
    createPublish,
    createRandomKey,
    createSelect,
    createSort,
    createSortReadOnly,
    createTime,
    createUnWatch,
} from "./Commands";
import { connection_request } from "./ProtobufMessage";
import { Transaction } from "./Transaction";

/* eslint-disable-next-line @typescript-eslint/no-namespace */
export namespace GlideClientConfiguration {
    /**
     * Enum representing pubsub subscription modes.
     * @see {@link  https://valkey.io/docs/topics/pubsub/|Valkey PubSub Documentation} for more details.
     */
    export enum PubSubChannelModes {
        /**
         * Use exact channel names.
         */
        Exact = 0,

        /**
         * Use channel name patterns.
         */
        Pattern = 1,
    }

    export type PubSubSubscriptions = {
        /**
         * Channels and patterns by modes.
         */
        channelsAndPatterns: Partial<Record<PubSubChannelModes, Set<string>>>;

        /**
         * Optional callback to accept the incoming messages.
         */
        /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
        callback?: (msg: PubSubMsg, context: any) => void;

        /**
         * Arbitrary context to pass to the callback.
         */
        /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
        context?: any;
    };
}

export type GlideClientConfiguration = BaseClientConfiguration & {
    /**
     * index of the logical database to connect to.
     */
    databaseId?: number;
    /**
     * Strategy used to determine how and when to reconnect, in case of connection failures.
     * The time between attempts grows exponentially, to the formula rand(0 .. factor * (exponentBase ^ N)), where N is the number of failed attempts.
     * The client will attempt to reconnect indefinitely. Once the maximum value is reached, that will remain the time between retry attempts until a
     * reconnect attempt is succesful.
     * If not set, a default backoff strategy will be used.
     */
    connectionBackoff?: {
        /**
         * Number of retry attempts that the client should perform when disconnected from the server, where the time between retries increases.
         * Once the retries have reached the maximum value, the time between retries will remain constant until a reconnect attempt is succesful.
         * Value must be an integer.
         */
        numberOfRetries: number;
        /**
         * The multiplier that will be applied to the waiting time between each retry.
         * Value must be an integer.
         */
        factor: number;
        /**
         * The exponent base configured for the strategy.
         * Value must be an integer.
         */
        exponentBase: number;
    };
    /**
     * PubSub subscriptions to be used for the client.
     * Will be applied via SUBSCRIBE/PSUBSCRIBE commands during connection establishment.
     */
    pubsubSubscriptions?: GlideClientConfiguration.PubSubSubscriptions;
};

/**
 * Client used for connection to standalone Redis servers.
 *
 * @see For full documentation refer to {@link https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#standalone|Valkey Glide Wiki}.
 */
export class GlideClient extends BaseClient {
    /**
     * @internal
     */
    protected createClientRequest(
        options: GlideClientConfiguration,
    ): connection_request.IConnectionRequest {
        const configuration = super.createClientRequest(options);
        configuration.databaseId = options.databaseId;
        configuration.connectionRetryStrategy = options.connectionBackoff;
        this.configurePubsub(options, configuration);
        return configuration;
    }

    public static async createClient(
        options: GlideClientConfiguration,
    ): Promise<GlideClient> {
        return super.createClientInternal<GlideClient>(
            options,
            (socket: net.Socket, options?: GlideClientConfiguration) =>
                new GlideClient(socket, options),
        );
    }

    static async __createClient(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
    ): Promise<GlideClient> {
        return this.__createClientInternal(
            options,
            connectedSocket,
            (socket, options) => new GlideClient(socket, options),
        );
    }

    /**
     * Execute a transaction by processing the queued commands.
     *
     * @see {@link https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#transaction|Valkey Glide Wiki} for details on Valkey Transactions.
     *
     * @param transaction - A {@link Transaction} object containing a list of commands to be executed.
     * @param decoder - (Optional) {@link Decoder} type which defines how to handle the response.
     *     If not set, the {@link BaseClientConfiguration.defaultDecoder|default decoder} will be used.
     * @returns A list of results corresponding to the execution of each command in the transaction.
     *     If a command returns a value, it will be included in the list. If a command doesn't return a value,
     *     the list entry will be `null`.
     *     If the transaction failed due to a `WATCH` command, `exec` will return `null`.
     */
    public async exec(
        transaction: Transaction,
        decoder?: Decoder,
    ): Promise<ReturnType[] | null> {
        return this.createWritePromise<ReturnType[] | null>(
            transaction.commands,
            { decoder: decoder },
        ).then((result) =>
            this.processResultWithSetCommands(
                result,
                transaction.setCommandsIndexes,
            ),
        );
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *
     * Note: An error will occur if the string decoder is used with commands that return only bytes as a response.
     *
     * @see {@link https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command|Valkey Glide Wiki} for details on the restrictions and limitations of the custom command API.
     *
     * @example
     * ```typescript
     * // Example usage of customCommand method to retrieve pub/sub clients
     * const result = await client.customCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"]);
     * console.log(result); // Output: Returns a list of all pub/sub clients
     * ```
     */
    public async customCommand(
        args: GlideString[],
        decoder?: Decoder,
    ): Promise<ReturnType> {
        return this.createWritePromise(createCustomCommand(args), {
            decoder: decoder,
        });
    }

    /**
     * Pings the server.
     *
     * @see {@link https://valkey.io/commands/ping/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `message` : a message to include in the `PING` command.
     *   + If not provided, the server will respond with `"PONG"`.
     *   + If provided, the server will respond with a copy of the message.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns `"PONG"` if `message` is not provided, otherwise return a copy of `message`.
     *
     * @example
     * ```typescript
     * // Example usage of ping method without any message
     * const result = await client.ping();
     * console.log(result); // Output: 'PONG'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of ping method with a message
     * const result = await client.ping("Hello");
     * console.log(result); // Output: 'Hello'
     * ```
     */
    public async ping(
        options?: {
            message?: GlideString;
        } & DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(createPing(options?.message), {
            decoder: options?.decoder,
        });
    }

    /**
     * Gets information and statistics about the server.
     *
     * @see {@link https://valkey.io/commands/info/|valkey.io} for details.
     *
     * @param sections - (Optional) A list of {@link InfoOptions} values specifying which sections of information to retrieve.
     *     When no parameter is provided, {@link InfoOptions.Default|Default} is assumed.
     * @returns A string containing the information for the sections requested.
     */
    public async info(sections?: InfoOptions[]): Promise<string> {
        return this.createWritePromise(createInfo(sections), {
            decoder: Decoder.String,
        });
    }

    /**
     * Changes the currently selected database.
     *
     * @see {@link https://valkey.io/commands/select/|valkey.io} for details.
     *
     * @param index - The index of the database to select.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * // Example usage of select method
     * const result = await client.select(2);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async select(index: number): Promise<"OK"> {
        return this.createWritePromise(createSelect(index), {
            decoder: Decoder.String,
        });
    }

    /**
     * Gets the name of the primary's connection.
     *
     * @see {@link https://valkey.io/commands/client-getname/|valkey.io} for more details.
     *
     * @param decoder - (Optional) {@link Decoder} type which defines how to handle the response.
     *     If not set, the {@link BaseClientConfiguration.defaultDecoder|default decoder} will be used.
     * @returns The name of the client connection as a string if a name is set, or `null` if no name is assigned.
     *
     * @example
     * ```typescript
     * // Example usage of client_getname method
     * const result = await client.client_getname();
     * console.log(result); // Output: 'Client Name'
     * ```
     */
    public async clientGetName(decoder?: Decoder): Promise<GlideString | null> {
        return this.createWritePromise(createClientGetName(), {
            decoder: decoder,
        });
    }

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see {@link https://valkey.io/commands/config-rewrite/|valkey.io} for details.
     *
     * @returns "OK" when the configuration was rewritten properly. Otherwise, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configRewrite command
     * const result = await client.configRewrite();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configRewrite(): Promise<"OK"> {
        return this.createWritePromise(createConfigRewrite(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Resets the statistics reported by the server using the `INFO` and `LATENCY HISTOGRAM` commands.
     *
     * @see {@link https://valkey.io/commands/config-resetstat/|valkey.io} for details.
     *
     * @returns always "OK".
     *
     * @example
     * ```typescript
     * // Example usage of configResetStat command
     * const result = await client.configResetStat();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configResetStat(): Promise<"OK"> {
        return this.createWritePromise(createConfigResetStat(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the current connection ID.
     *
     * @see {@link https://valkey.io/commands/client-id/|valkey.io} for details.
     *
     * @returns The ID of the connection.
     *
     * @example
     * ```typescript
     * const result = await client.clientId();
     * console.log("Connection id: " + result);
     * ```
     */
    public async clientId(): Promise<number> {
        return this.createWritePromise(createClientId());
    }

    /**
     * Reads the configuration parameters of the running server.
     *
     * @see {@link https://valkey.io/commands/config-get/|valkey.io} for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     * @param decoder - (Optional) {@link Decoder} type which defines how to handle the response.
     *     If not set, the {@link BaseClientConfiguration.defaultDecoder|default decoder} will be used.
     *
     * @returns A map of values corresponding to the configuration parameters.
     *
     * @example
     * ```typescript
     * // Example usage of configGet method with multiple configuration parameters
     * const result = await client.configGet(["timeout", "maxmemory"]);
     * console.log(result); // Output: {'timeout': '1000', 'maxmemory': '1GB'}
     * ```
     */
    public async configGet(
        parameters: string[],
        decoder?: Decoder,
    ): Promise<Record<string, GlideString>> {
        return this.createWritePromise(createConfigGet(parameters), {
            decoder: decoder,
        });
    }

    /**
     * Sets configuration parameters to the specified values.
     *
     * @see {@link  https://valkey.io/commands/config-set/|valkey.io} for details.
     * @param parameters - A map consisting of configuration parameters and their respective values to set.
     * @returns `"OK"` when the configuration was set properly. Otherwise an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configSet method to set multiple configuration parameters
     * const result = await client.configSet({ timeout: "1000", maxmemory, "1GB" });
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configSet(
        parameters: Record<string, GlideString>,
    ): Promise<"OK"> {
        return this.createWritePromise(createConfigSet(parameters), {
            decoder: Decoder.String,
        });
    }

    /**
     * Echoes the provided `message` back.
     *
     * @see {@link https://valkey.io/commands/echo|valkey.io} for more details.
     *
     * @param message - The message to be echoed back.
     * @param decoder - (Optional) {@link Decoder} type which defines how to handle the response.
     *     If not set, the {@link BaseClientConfiguration.defaultDecoder|default decoder} will be used.
     * @returns The provided `message`.
     *
     * @example
     * ```typescript
     * // Example usage of the echo command
     * const echoedMessage = await client.echo("valkey-glide");
     * console.log(echoedMessage); // Output: 'valkey-glide'
     * ```
     */
    public async echo(
        message: GlideString,
        decoder?: Decoder,
    ): Promise<GlideString> {
        return this.createWritePromise(createEcho(message), {
            decoder,
        });
    }

    /**
     * Returns the server time.
     *
     * @see {@link https://valkey.io/commands/time/|valkey.io} for details.
     *
     * @returns The current server time as an `array` with two items:
     * - A Unix timestamp,
     * - The amount of microseconds already elapsed in the current second.
     *
     * @example
     * ```typescript
     * console.log(await client.time()); // Output: ['1710925775', '913580']
     * ```
     */
    public async time(): Promise<[string, string]> {
        return this.createWritePromise(createTime(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Copies the value stored at the `source` to the `destination` key. If `destinationDB` is specified,
     * the value will be copied to the database specified, otherwise the current database will be used.
     * When `replace` is true, removes the `destination` key first if it already exists, otherwise performs
     * no action.
     *
     * @see {@link https://valkey.io/commands/copy/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source value.
     * @param destination - The key where the value should be copied to.
     * @param destinationDB - (Optional) The alternative logical database index for the destination key.
     *     If not provided, the current database will be used.
     * @param replace - (Optional) If `true`, the `destination` key should be removed before copying the
     *     value to it. If not provided, no action will be performed if the key already exists.
     * @returns `true` if `source` was copied, `false` if the `source` was not copied.
     *
     * @example
     * ```typescript
     * const result = await client.copy("set1", "set2");
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     * ```typescript
     * const result = await client.copy("set1", "set2", { replace: true });
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     * ```typescript
     * const result = await client.copy("set1", "set2", { destinationDB: 1, replace: false });
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     */
    public async copy(
        source: GlideString,
        destination: GlideString,
        options?: { destinationDB?: number; replace?: boolean },
    ): Promise<boolean> {
        return this.createWritePromise(
            createCopy(source, destination, options),
        );
    }

    /**
     * Move `key` from the currently selected database to the database specified by `dbIndex`.
     *
     * @see {@link https://valkey.io/commands/move/|valkey.io} for more details.
     *
     * @param key - The key to move.
     * @param dbIndex - The index of the database to move `key` to.
     * @returns `true` if `key` was moved, or `false` if the `key` already exists in the destination
     *     database or does not exist in the source database.
     *
     * @example
     * ```typescript
     * const result = await client.move("key", 1);
     * console.log(result); // Output: true
     * ```
     */
    public async move(key: GlideString, dbIndex: number): Promise<boolean> {
        return this.createWritePromise(createMove(key, dbIndex));
    }

    /**
     * Displays a piece of generative computer art and the server version.
     *
     * @see {@link https://valkey.io/commands/lolwut/|valkey.io} for more details.
     *
     * @param options - (Optional) The LOLWUT options - see {@link LolwutOptions}.
     * @returns A piece of generative computer art along with the current server version.
     *
     * @example
     * ```typescript
     * const response = await client.lolwut({ version: 6, parameters: [40, 20] });
     * console.log(response); // Output: "Redis ver. 7.2.3" - Indicates the current server version.
     * ```
     */
    public async lolwut(options?: LolwutOptions): Promise<string> {
        return this.createWritePromise(createLolwut(options), {
            decoder: Decoder.String,
        });
    }

    /**
     * Deletes a library and all its functions.
     *
     * @see {@link https://valkey.io/commands/function-delete/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The library name to delete.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionDelete("libName");
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionDelete(libraryCode: GlideString): Promise<"OK"> {
        return this.createWritePromise(createFunctionDelete(libraryCode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Loads a library to Valkey.
     *
     * @see {@link https://valkey.io/commands/function-load/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The source code that implements the library.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `replace`: Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns The library name that was loaded.
     *
     * @example
     * ```typescript
     * const code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * const result = await client.functionLoad(code, true);
     * console.log(result); // Output: 'mylib'
     * ```
     */
    public async functionLoad(
        libraryCode: GlideString,
        options?: { replace?: boolean } & DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(
            createFunctionLoad(libraryCode, options?.replace),
            { decoder: options?.decoder },
        );
    }

    /**
     * Deletes all function libraries.
     *
     * @see {@link https://valkey.io/commands/function-flush/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param mode - (Optional) The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionFlush(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionFlush(mode?: FlushMode): Promise<"OK"> {
        return this.createWritePromise(createFunctionFlush(mode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns information about the functions and libraries.
     *
     * @see {@link https://valkey.io/commands/function-list/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link FunctionListOptions} and {@link DecoderOption}.
     * @returns Info about all or selected libraries and their functions in {@link FunctionListResponse} format.
     *
     * @example
     * ```typescript
     * // Request info for specific library including the source code
     * const result1 = await client.functionList({ libNamePattern: "myLib*", withCode: true });
     * // Request info for all libraries
     * const result2 = await client.functionList();
     * console.log(result2); // Output:
     * // [{
     * //     "library_name": "myLib5_backup",
     * //     "engine": "LUA",
     * //     "functions": [{
     * //         "name": "myfunc",
     * //         "description": null,
     * //         "flags": [ "no-writes" ],
     * //     }],
     * //     "library_code": "#!lua name=myLib5_backup \n redis.register_function('myfunc', function(keys, args) return args[1] end)"
     * // }]
     * ```
     */
    public async functionList(
        options?: FunctionListOptions & DecoderOption,
    ): Promise<FunctionListResponse> {
        return this.createWritePromise(createFunctionList(options), {
            decoder: options?.decoder,
        });
    }

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * FUNCTION STATS runs on all nodes of the server, including primary and replicas.
     * The response includes a mapping from node address to the command response for that node.
     *
     * @see {@link https://valkey.io/commands/function-stats/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param decoder - (Optional) {@link Decoder} type which defines how to handle the response.
     *     If not set, the {@link BaseClientConfiguration.defaultDecoder|default decoder} will be used.
     * @returns A Record where the key is the node address and the value is a Record with two keys:
     *          - `"running_script"`: Information about the running script, or `null` if no script is running.
     *          - `"engines"`: Information about available engines and their stats.
     *          - see example for more details.
     * @example
     * ```typescript
     * const response = await client.functionStats();
     * console.log(response); // Example output:
     * // {
     * //     "127.0.0.1:6379": {                // Response from the primary node
     * //         "running_script": {
     * //             "name": "foo",
     * //             "command": ["FCALL", "foo", "0", "hello"],
     * //             "duration_ms": 7758
     * //         },
     * //         "engines": {
     * //             "LUA": {
     * //                 "libraries_count": 1,
     * //                 "functions_count": 1
     * //             }
     * //         }
     * //     },
     * //     "127.0.0.1:6380": {                // Response from a replica node
     * //         "running_script": null,
     * //         "engines": {
     * //             "LUA": {
     * //                 "libraries_count": 1,
     * //                 "functions_count": 1
     * //             }
     * //         }
     * //     }
     * // }
     * ```
     */
    public async functionStats(
        decoder?: Decoder,
    ): Promise<FunctionStatsFullResponse> {
        return this.createWritePromise(createFunctionStats(), {
            decoder,
        });
    }

    /**
     * Kills a function that is currently executing.
     * `FUNCTION KILL` terminates read-only functions only.
     * `FUNCTION KILL` runs on all nodes of the server, including primary and replicas.
     *
     * @see {@link https://valkey.io/commands/function-kill/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @returns `"OK"` if function is terminated. Otherwise, throws an error.
     * @example
     * ```typescript
     * await client.functionKill();
     * ```
     */
    public async functionKill(): Promise<"OK"> {
        return this.createWritePromise(createFunctionKill(), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the serialized payload of all loaded libraries.
     *
     * @see {@link https://valkey.io/commands/function-dump/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @returns The serialized payload of all loaded libraries.
     *
     * @example
     * ```typescript
     * const data = await client.functionDump();
     * // data can be used to restore loaded functions on any Valkey instance
     * ```
     */
    public async functionDump(): Promise<Buffer> {
        return this.createWritePromise(createFunctionDump(), {
            decoder: Decoder.Bytes,
        });
    }

    /**
     * Restores libraries from the serialized payload returned by {@link functionDump}.
     *
     * @see {@link https://valkey.io/commands/function-restore/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param payload - The serialized data from {@link functionDump}.
     * @param policy - (Optional) A policy for handling existing libraries, see {@link FunctionRestorePolicy}.
     *     {@link FunctionRestorePolicy.APPEND} is used by default.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * await client.functionRestore(data, FunctionRestorePolicy.FLUSH);
     * ```
     */
    public async functionRestore(
        payload: Buffer,
        policy?: FunctionRestorePolicy,
    ): Promise<"OK"> {
        return this.createWritePromise(createFunctionRestore(payload, policy), {
            decoder: Decoder.String,
        });
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see {@link https://valkey.io/commands/flushall/|valkey.io} for more details.
     *
     * @param mode - (Optional) The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * const result = await client.flushall(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushall(mode?: FlushMode): Promise<"OK"> {
        return this.createWritePromise(createFlushAll(mode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see {@link https://valkey.io/commands/flushdb/|valkey.io} for more details.
     *
     * @param mode - (Optional) The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * const result = await client.flushdb(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushdb(mode?: FlushMode): Promise<"OK"> {
        return this.createWritePromise(createFlushDB(mode), {
            decoder: Decoder.String,
        });
    }

    /**
     * Returns the number of keys in the currently selected database.
     *
     * @see {@link https://valkey.io/commands/dbsize/|valkey.io} for more details.
     *
     * @returns The number of keys in the currently selected database.
     *
     * @example
     * ```typescript
     * const numKeys = await client.dbsize();
     * console.log("Number of keys in the current database: ", numKeys);
     * ```
     */
    public async dbsize(): Promise<number> {
        return this.createWritePromise(createDBSize());
    }

    /** Publish a message on pubsub channel.
     *
     * @see {@link https://valkey.io/commands/publish/|valkey.io} for more details.
     *
     * @param message - Message to publish.
     * @param channel - Channel to publish the message on.
     * @returns -  Number of subscriptions in primary node that received the message.
     * Note that this value does not include subscriptions that configured on replicas.
     *
     * @example
     * ```typescript
     * // Example usage of publish command
     * const result = await client.publish("Hi all!", "global-channel");
     * console.log(result); // Output: 1 - This message was posted to 1 subscription which is configured on primary node
     * ```
     */
    public async publish(
        message: GlideString,
        channel: GlideString,
    ): Promise<number> {
        return this.createWritePromise(createPublish(message, channel));
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and returns the result.
     *
     * The `sort` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.
     *
     * To store the result into a new key, see {@link sortStore}.
     *
     * @see {@link https://valkey.io/commands/sort/|valkey.io} for more details.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param options - (Optional) The {@link SortOptions} and {@link DecoderOption}.
     *
     * @returns An `Array` of sorted elements.
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const result = await client.sort("user_ids", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(result); // Output: [ 'Bob', 'Alice' ] - Returns a list of the names sorted by age
     * ```
     */
    public async sort(
        key: GlideString,
        options?: SortOptions & DecoderOption,
    ): Promise<(GlideString | null)[]> {
        return this.createWritePromise(createSort(key, options), {
            decoder: options?.decoder,
        });
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and returns the result.
     *
     * The `sortReadOnly` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.
     *
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @see {@link https://valkey.io/commands/sort/|valkey.io} for more details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param options - (Optional) The {@link SortOptions} and {@link DecoderOption}.
     * @returns An `Array` of sorted elements
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const result = await client.sortReadOnly("user_ids", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(result); // Output: [ 'Bob', 'Alice' ] - Returns a list of the names sorted by age
     * ```
     */
    public async sortReadOnly(
        key: GlideString,
        options?: SortOptions & DecoderOption,
    ): Promise<(GlideString | null)[]> {
        return this.createWritePromise(createSortReadOnly(key, options), {
            decoder: options?.decoder,
        });
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and stores the result in
     * `destination`.
     *
     * The `sort` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements, and store the result in a new key.
     *
     * To get the sort result without storing it into a key, see {@link sort} or {@link sortReadOnly}.
     *
     * @see {@link https://valkey.io/commands/sort|valkey.io} for more details.
     * @remarks When in cluster mode, `destination` and `key` must map to the same hash slot.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param destination - The key where the sorted result will be stored.
     * @param options - (Optional) The {@link SortOptions}.
     * @returns The number of elements in the sorted key stored at `destination`.
     *
     * @example
     * ```typescript
     * await client.hset("user:1", new Map([["name", "Alice"], ["age", "30"]]));
     * await client.hset("user:2", new Map([["name", "Bob"], ["age", "25"]]));
     * await client.lpush("user_ids", ["2", "1"]);
     * const sortedElements = await client.sortStore("user_ids", "sortedList", { byPattern: "user:*->age", getPattern: ["user:*->name"] });
     * console.log(sortedElements); // Output: 2 - number of elements sorted and stored
     * console.log(await client.lrange("sortedList", 0, -1)); // Output: [ 'Bob', 'Alice' ] - Returns a list of the names sorted by age stored in `sortedList`
     * ```
     */
    public async sortStore(
        key: GlideString,
        destination: GlideString,
        options?: SortOptions,
    ): Promise<number> {
        return this.createWritePromise(createSort(key, options, destination));
    }

    /**
     * Returns `UNIX TIME` of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see {@link https://valkey.io/commands/lastsave/|valkey.io} for more details.
     *
     * @returns `UNIX TIME` of the last DB save executed with success.
     *
     * @example
     * ```typescript
     * const timestamp = await client.lastsave();
     * console.log("Last DB save was done at " + timestamp);
     * ```
     */
    public async lastsave(): Promise<number> {
        return this.createWritePromise(createLastSave());
    }

    /**
     * Returns a random existing key name from the currently selected database.
     *
     * @see {@link https://valkey.io/commands/randomkey/|valkey.io} for more details.
     *
     * @param decoder - (Optional) {@link Decoder} type which defines how to handle the response.
     *     If not set, the {@link BaseClientConfiguration.defaultDecoder|default decoder} will be used.
     * @returns A random existing key name from the currently selected database.
     *
     * @example
     * ```typescript
     * const result = await client.randomKey();
     * console.log(result); // Output: "key12" - "key12" is a random existing key name from the currently selected database.
     * ```
     */
    public async randomKey(decoder?: Decoder): Promise<GlideString | null> {
        return this.createWritePromise(createRandomKey(), { decoder: decoder });
    }

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * @see {@link https://valkey.io/commands/unwatch/|valkey.io} and {@link https://valkey.io/topics/transactions/#cas|Valkey Glide Wiki} for more details.
     *
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * let response = await client.watch(["sampleKey"]);
     * console.log(response); // Output: "OK"
     * response = await client.unwatch();
     * console.log(response); // Output: "OK"
     * ```
     */
    public async unwatch(): Promise<"OK"> {
        return this.createWritePromise(createUnWatch(), {
            decoder: Decoder.String,
        });
    }
}
