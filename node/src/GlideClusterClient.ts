/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { ClusterScanCursor } from "glide-rs";
import { Script } from "index";
import * as net from "net";
import {
    BaseClient,
    BaseClientConfiguration,
    Decoder,
    DecoderOption,
    GlideRecord,
    GlideReturnType,
    GlideString,
    PubSubMsg,
    convertGlideRecordToRecord,
} from "./BaseClient";
import {
    FlushMode,
    FunctionListOptions,
    FunctionListResponse,
    FunctionRestorePolicy,
    FunctionStatsSingleResponse,
    InfoOptions,
    LolwutOptions,
    ScanOptions,
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
    createFCall,
    createFCallReadOnly,
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
    createPing,
    createPubSubShardNumSub,
    createPublish,
    createPubsubShardChannels,
    createRandomKey,
    createScriptExists,
    createScriptFlush,
    createScriptKill,
    createTime,
    createUnWatch,
} from "./Commands";
import { command_request, connection_request } from "./ProtobufMessage";
import { ClusterTransaction } from "./Transaction";

/** An extension to command option types with {@link Routes}. */
export interface RouteOption {
    /**
     * Specifies the routing configuration for the command.
     * The client will route the command to the nodes defined by `route`.
     */
    route?: Routes;
}

/**
 * Represents a manually configured interval for periodic checks.
 */
export interface PeriodicChecksManualInterval {
    /**
     * The duration in seconds for the interval between periodic checks.
     */
    duration_in_sec: number;
}

/**
 * Periodic checks configuration.
 */
export type PeriodicChecks =
    /**
     * Enables the periodic checks with the default configurations.
     */
    | "enabledDefaultConfigs"
    /**
     * Disables the periodic checks.
     */
    | "disabled"
    /**
     * Manually configured interval for periodic checks.
     */
    | PeriodicChecksManualInterval;

/* eslint-disable-next-line @typescript-eslint/no-namespace */
export namespace GlideClusterClientConfiguration {
    /**
     * Enum representing pubsub subscription modes.
     * @see {@link https://valkey.io/docs/topics/pubsub/|Valkey PubSub Documentation} for more details.
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

        /**
         * Use sharded pubsub. Available since Valkey version 7.0.
         */
        Sharded = 2,
    }

    export interface PubSubSubscriptions {
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
    }
}
export type GlideClusterClientConfiguration = BaseClientConfiguration & {
    /**
     * Configure the periodic topology checks.
     * These checks evaluate changes in the cluster's topology, triggering a slot refresh when detected.
     * Periodic checks ensure a quick and efficient process by querying a limited number of nodes.
     * If not set, `enabledDefaultConfigs` will be used.
     */
    periodicChecks?: PeriodicChecks;

    /**
     * PubSub subscriptions to be used for the client.
     * Will be applied via SUBSCRIBE/PSUBSCRIBE/SSUBSCRIBE commands during connection establishment.
     */
    pubsubSubscriptions?: GlideClusterClientConfiguration.PubSubSubscriptions;
};

/**
 * If the command's routing is to one node we will get T as a response type,
 * otherwise, we will get a dictionary of address: nodeResponse, address is of type string and nodeResponse is of type T.
 */
export type ClusterResponse<T> = T | Record<string, T>;

/**
 * @internal
 * Type which returns GLIDE core for commands routed to multiple nodes.
 * Should be converted to {@link ClusterResponse}.
 */
type ClusterGlideRecord<T> = GlideRecord<T> | T;

/**
 * @internal
 * Convert {@link ClusterGlideRecord} to {@link ClusterResponse}.
 *
 * @param res - Value received from Glide core.
 * @param isRoutedToSingleNodeByDefault - Default routing policy.
 * @param route - The route.
 * @returns Converted value.
 */
function convertClusterGlideRecord<T>(
    res: ClusterGlideRecord<T>,
    isRoutedToSingleNodeByDefault: boolean,
    route?: Routes,
): ClusterResponse<T> {
    const isSingleNodeResponse =
        // route not given and command is routed by default to a random node
        (!route && isRoutedToSingleNodeByDefault) ||
        // or route is given and it is a single node route
        (Boolean(route) && route !== "allPrimaries" && route !== "allNodes");

    return isSingleNodeResponse
        ? (res as T)
        : convertGlideRecordToRecord(res as GlideRecord<T>);
}

export interface SlotIdTypes {
    /**
     * `replicaSlotId` overrides the `readFrom` configuration. If it's used the request
     * will be routed to a replica, even if the strategy is `alwaysFromPrimary`.
     */
    type: "primarySlotId" | "replicaSlotId";
    /**
     * Slot number. There are 16384 slots in a Valkey cluster, and each shard manages a slot range.
     * Unless the slot is known, it's better to route using `SlotKeyTypes`
     */
    id: number;
}

export interface SlotKeyTypes {
    /**
     * `replicaSlotKey` overrides the `readFrom` configuration. If it's used the request
     * will be routed to a replica, even if the strategy is `alwaysFromPrimary`.
     */
    type: "primarySlotKey" | "replicaSlotKey";
    /**
     * The request will be sent to nodes managing this key.
     */
    key: string;
}

/// Route command to specific node.
export interface RouteByAddress {
    type: "routeByAddress";
    /**
     *The endpoint of the node. If `port` is not provided, should be in the `${address}:${port}` format, where `address` is the preferred endpoint as shown in the output of the `CLUSTER SLOTS` command.
     */
    host: string;
    /**
     * The port to access on the node. If port is not provided, `host` is assumed to be in the format `${address}:${port}`.
     */
    port?: number;
}

export type Routes =
    | SingleNodeRoute
    /**
     * Route request to all primary nodes.
     */
    | "allPrimaries"
    /**
     * Route request to all nodes.
     */
    | "allNodes";

export type SingleNodeRoute =
    /**
     * Route request to a random node.
     */
    | "randomNode"
    /**
     * Route request to the node that contains the slot with the given id.
     */
    | SlotIdTypes
    /**
     * Route request to the node that contains the slot that the given key matches.
     */
    | SlotKeyTypes
    | RouteByAddress;

/**
 * Client used for connection to cluster servers.
 *
 * @see For full documentation refer to {@link https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#cluster|Valkey Glide Wiki}.
 */
export class GlideClusterClient extends BaseClient {
    /**
     * @internal
     */
    protected createClientRequest(
        options: GlideClusterClientConfiguration,
    ): connection_request.IConnectionRequest {
        const configuration = super.createClientRequest(options);
        configuration.clusterModeEnabled = true;

        // "enabledDefaultConfigs" is the default configuration and doesn't need setting
        if (
            options.periodicChecks !== undefined &&
            options.periodicChecks !== "enabledDefaultConfigs"
        ) {
            if (options.periodicChecks === "disabled") {
                configuration.periodicChecksDisabled =
                    connection_request.PeriodicChecksDisabled.create();
            } else {
                configuration.periodicChecksManualInterval =
                    connection_request.PeriodicChecksManualInterval.create({
                        durationInSec: options.periodicChecks.duration_in_sec,
                    });
            }
        }

        this.configurePubsub(options, configuration);
        return configuration;
    }

    public static async createClient(
        options: GlideClusterClientConfiguration,
    ): Promise<GlideClusterClient> {
        return await super.createClientInternal(
            options,
            (socket: net.Socket, options?: GlideClusterClientConfiguration) =>
                new GlideClusterClient(socket, options),
        );
    }

    static async __createClient(
        options: BaseClientConfiguration,
        connectedSocket: net.Socket,
    ): Promise<GlideClusterClient> {
        return super.__createClientInternal(
            options,
            connectedSocket,
            (socket, options) => new GlideClusterClient(socket, options),
        );
    }

    /**
     * @internal
     */
    protected scanOptionsToProto(
        cursor: string,
        options?: ScanOptions,
    ): command_request.ClusterScan {
        const command = command_request.ClusterScan.create();
        command.cursor = cursor;

        if (options?.match) {
            command.matchPattern = Buffer.from(options.match);
        }

        if (options?.count) {
            command.count = options.count;
        }

        if (options?.type) {
            command.objectType = options.type;
        }

        return command;
    }

    /**
     * @internal
     */
    protected createClusterScanPromise(
        cursor: ClusterScanCursor,
        options?: ScanOptions & DecoderOption,
    ): Promise<[ClusterScanCursor, GlideString[]]> {
        // separate decoder option from scan options
        const { decoder, ...scanOptions } = options || {};
        const cursorId = cursor.getCursor();
        const command = this.scanOptionsToProto(cursorId, scanOptions);
        return this.createWritePromise(command, { decoder });
    }

    /**
     * Incrementally iterates over the keys in the Cluster.
     *
     * This command is similar to the `SCAN` command but designed for Cluster environments.
     * It uses a {@link ClusterScanCursor} object to manage iterations.
     *
     * For each iteration, use the new cursor object to continue the scan.
     * Using the same cursor object for multiple iterations may result in unexpected behavior.
     *
     * For more information about the Cluster Scan implementation, see
     * {@link https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan | Cluster Scan}.
     *
     * This method can iterate over all keys in the database from the start of the scan until it ends.
     * The same key may be returned in multiple scan iterations.
     * The API does not accept `route` as it go through all slots in the cluster.
     *
     * @see {@link https://valkey.io/commands/scan/ | valkey.io} for more details.
     *
     * @param cursor - The cursor object that wraps the scan state.
     *   To start a new scan, create a new empty `ClusterScanCursor` using {@link ClusterScanCursor}.
     * @param options - (Optional) The scan options, see {@link ScanOptions} and  {@link DecoderOption}.
     * @returns A Promise resolving to an array containing the next cursor and an array of keys,
     *   formatted as [`ClusterScanCursor`, `string[]`].
     *
     * @example
     * ```typescript
     * // Iterate over all keys in the cluster
     * await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "key3", value: "value3"}]);
     * let cursor = new ClusterScanCursor();
     * const allKeys: GlideString[] = [];
     * let keys: GlideString[] = [];
     * while (!cursor.isFinished()) {
     *   [cursor, keys] = await client.scan(cursor, { count: 10 });
     *   allKeys.push(...keys);
     * }
     * console.log(allKeys); // ["key1", "key2", "key3"]
     *
     * // Iterate over keys matching a pattern
     * await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "notMykey", value: "value3"}, {key: "somethingElse", value: "value4"}]);
     * let cursor = new ClusterScanCursor();
     * const matchedKeys: GlideString[] = [];
     * while (!cursor.isFinished()) {
     *   const [cursor, keys] = await client.scan(cursor, { match: "*key*", count: 10 });
     *   matchedKeys.push(...keys);
     * }
     * console.log(matchedKeys); // ["key1", "key2", "notMykey"]
     *
     * // Iterate over keys of a specific type
     * await client.mset([{key: "key1", value: "value1"}, {key: "key2", value: "value2"}, {key: "key3", value: "value3"}]);
     * await client.sadd("thisIsASet", ["value4"]);
     * let cursor = new ClusterScanCursor();
     * const stringKeys: GlideString[] = [];
     * while (!cursor.isFinished()) {
     *   const [cursor, keys] = await client.scan(cursor, { type: object.STRING });
     *   stringKeys.push(...keys);
     * }
     * console.log(stringKeys); // ["key1", "key2", "key3"]
     * ```
     */
    public async scan(
        cursor: ClusterScanCursor,
        options?: ScanOptions & DecoderOption,
    ): Promise<[ClusterScanCursor, GlideString[]]> {
        return this.createClusterScanPromise(cursor, options);
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *  The command will be routed automatically based on the passed command's default request policy, unless `route` is provided,
     *  in which case the client will route the command to the nodes defined by `route`.
     *
     * Note: An error will occur if the string decoder is used with commands that return only bytes as a response.
     *
     * @see {@link https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command|Glide for Valkey Wiki} for details on the restrictions and limitations of the custom command API.
     *
     * @param args - A list including the command name and arguments for the custom command.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}
     * @returns The executed custom command return value.
     *
     * @example
     * ```typescript
     * // Example usage of customCommand method to retrieve pub/sub clients with routing to all primary nodes
     * const result = await client.customCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"], {route: "allPrimaries", decoder: Decoder.String});
     * console.log(result); // Output: Returns a list of all pub/sub clients
     * ```
     */
    public async customCommand(
        args: GlideString[],
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        const command = createCustomCommand(args);
        return super.createWritePromise(command, options);
    }

    /**
     * Execute a transaction by processing the queued commands.
     *
     * @see {@link https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper#transaction|Valkey Glide Wiki} for details on Valkey Transactions.
     *
     * @param transaction - A {@link ClusterTransaction} object containing a list of commands to be executed.
     *
     *  @param options - (Optional) Additional parameters:
     * - (Optional) `route`: If `route` is not provided, the transaction will be routed to the slot owner of the first key found in the transaction.
     *     If no key is found, the command will be sent to a random node.
     *     If `route` is provided, the client will route the command to the nodes defined by `route`.
     * - (Optional) `decoder`: See {@link DecoderOption}.
     * @returns A list of results corresponding to the execution of each command in the transaction.
     *     If a command returns a value, it will be included in the list. If a command doesn't return a value,
     *     the list entry will be `null`.
     *     If the transaction failed due to a `WATCH` command, `exec` will return `null`.
     */
    public async exec(
        transaction: ClusterTransaction,
        options?: {
            route?: SingleNodeRoute;
        } & DecoderOption,
    ): Promise<GlideReturnType[] | null> {
        return this.createWritePromise<GlideReturnType[] | null>(
            transaction.commands,
            options,
        ).then((result) =>
            this.processResultWithSetCommands(
                result,
                transaction.setCommandsIndexes,
            ),
        );
    }

    /**
     * Pings the server.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/ping/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `message` : a message to include in the `PING` command.
     *   + If not provided, the server will respond with `"PONG"`.
     *   + If provided, the server will respond with a copy of the message.
     * - (Optional) `route`: see {@link RouteOption}.
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
        } & RouteOption &
            DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(createPing(options?.message), options);
    }

    /**
     * Gets information and statistics about the server.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/info/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `sections`: a list of {@link InfoOptions} values specifying which sections of information to retrieve.
     *     When no parameter is provided, {@link InfoOptions.Default|Default} is assumed.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A string containing the information for the sections requested.
     * When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     */
    public async info(
        options?: { sections?: InfoOptions[] } & RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createInfo(options?.sections),
            { decoder: Decoder.String, ...options },
        ).then((res) => convertClusterGlideRecord(res, false, options?.route));
    }

    /**
     * Gets the name of the connection to which the request is routed.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/client-getname/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     *
     * @returns - The name of the client connection as a string if a name is set, or `null` if no name is assigned.
     *     When specifying a route other than a single node, it returns a dictionary where each address is the key and
     *     its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of client_getname method
     * const result = await client.client_getname();
     * console.log(result); // Output: 'Connection Name'
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of clientGetName method with routing to all nodes
     * const result = await client.clientGetName('allNodes');
     * console.log(result); // Output: {'addr': 'Connection Name', 'addr2': 'Connection Name', 'addr3': 'Connection Name'}
     * ```
     */
    public async clientGetName(
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideString | null>> {
        return this.createWritePromise<ClusterGlideRecord<GlideString | null>>(
            createClientGetName(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * The command will be routed to a all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-rewrite/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns `"OK"` when the configuration was rewritten properly. Otherwise, an error is thrown.
     *
     * @example
     * ```typescript
     * // Example usage of configRewrite command
     * const result = await client.configRewrite();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configRewrite(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createConfigRewrite(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Resets the statistics reported by the server using the `INFO` and `LATENCY HISTOGRAM` commands.
     *
     * The command will be routed to all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-resetstat/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns always `"OK"`.
     *
     * @example
     * ```typescript
     * // Example usage of configResetStat command
     * const result = await client.configResetStat();
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async configResetStat(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createConfigResetStat(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns the current connection ID.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/client-id/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns The ID of the connection. When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * const result = await client.clientId();
     * console.log("Connection id: " + result);
     * ```
     */
    public async clientId(
        options?: RouteOption,
    ): Promise<ClusterResponse<number>> {
        return this.createWritePromise<ClusterGlideRecord<number>>(
            createClientId(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Reads the configuration parameters of the running server.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-get/|valkey.io} for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     *
     * @returns A map of values corresponding to the configuration parameters. When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of config_get method with a single configuration parameter with routing to a random node
     * const result = await client.config_get(["timeout"], "randomNode");
     * console.log(result); // Output: {'timeout': '1000'}
     * ```
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
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<Record<string, GlideString>>> {
        return this.createWritePromise<
            ClusterGlideRecord<GlideRecord<GlideString>>
        >(createConfigGet(parameters), options).then((res) =>
            convertGlideRecordToRecord(res as GlideRecord<string>),
        );
    }

    /**
     * Sets configuration parameters to the specified values.
     *
     * The command will be routed to all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/config-set/|valkey.io} for details.
     *
     * @param parameters - A map consisting of configuration parameters and their respective values to set.
     * @param options - (Optional) See {@link RouteOption}.
     * @returns "OK" when the configuration was set properly. Otherwise an error is thrown.
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
        options?: RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createConfigSet(parameters), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Echoes the provided `message` back.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/echo/|valkey.io} for details.
     *
     * @param message - The message to be echoed back.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns The provided `message`. When specifying a route other than a single node,
     *     it returns a dictionary where each address is the key and its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of the echo command
     * const echoedMessage = await client.echo("valkey-glide");
     * console.log(echoedMessage); // Output: "valkey-glide"
     * ```
     * @example
     * ```typescript
     * // Example usage of the echo command with routing to all nodes
     * const echoedMessage = await client.echo("valkey-glide", "allNodes");
     * console.log(echoedMessage); // Output: {'addr': 'valkey-glide', 'addr2': 'valkey-glide', 'addr3': 'valkey-glide'}
     * ```
     */
    public async echo(
        message: GlideString,
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideString>> {
        return this.createWritePromise<ClusterGlideRecord<GlideString>>(
            createEcho(message),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Returns the server time.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/time/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     *
     * @returns The current server time as an `array` with two items:
     * - A Unix timestamp,
     * - The amount of microseconds already elapsed in the current second.
     *
     * When specifying a route other than a single node, it returns a dictionary where each address is the key and
     * its corresponding node response is the value.
     *
     * @example
     * ```typescript
     * // Example usage of time method without any argument
     * const result = await client.time();
     * console.log(result); // Output: ['1710925775', '913580']
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of time method with routing to all nodes
     * const result = await client.time('allNodes');
     * console.log(result); // Output: {'addr': ['1710925775', '913580'], 'addr2': ['1710925775', '913580'], 'addr3': ['1710925775', '913580']}
     * ```
     */
    public async time(
        options?: RouteOption,
    ): Promise<ClusterResponse<[string, string]>> {
        return this.createWritePromise<ClusterGlideRecord<[string, string]>>(
            createTime(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Copies the value stored at the `source` to the `destination` key. When `replace` is `true`,
     * removes the `destination` key first if it already exists, otherwise performs no action.
     *
     * @see {@link https://valkey.io/commands/copy/|valkey.io} for details.
     * @remarks When in cluster mode, `source` and `destination` must map to the same hash slot.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source value.
     * @param destination - The key where the value should be copied to.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `replace`: if `true`, the `destination` key should be removed before copying the
     *     value to it. If not provided, no action will be performed if the key already exists.
     * @returns `true` if `source` was copied, `false` if the `source` was not copied.
     *
     * @example
     * ```typescript
     * const result = await client.copy("set1", "set2", { replace: true });
     * console.log(result); // Output: true - "set1" was copied to "set2".
     * ```
     */
    public async copy(
        source: GlideString,
        destination: GlideString,
        options?: { replace?: boolean },
    ): Promise<boolean> {
        return this.createWritePromise(
            createCopy(source, destination, options),
        );
    }

    /**
     * Displays a piece of generative computer art and the server version.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/lolwut/|valkey.io} for details.
     *
     * @param options - (Optional) The LOLWUT options - see {@link LolwutOptions} and {@link RouteOption}.
     * @returns A piece of generative computer art along with the current server version.
     *
     * @example
     * ```typescript
     * const response = await client.lolwut({ version: 6, parameters: [40, 20] }, "allNodes");
     * console.log(response); // Output: "Valkey ver. 7.2.3" - Indicates the current server version.
     * ```
     */
    public async lolwut(
        options?: LolwutOptions & RouteOption,
    ): Promise<ClusterResponse<string>> {
        return this.createWritePromise<ClusterGlideRecord<string>>(
            createLolwut(options),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Invokes a previously loaded function.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallWithRoute("Deep_Thought", [], "randomNode");
     * console.log(response); // Output: Returns the function's return value.
     * ```
     */
    public async fcallWithRoute(
        func: GlideString,
        args: GlideString[],
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        return this.createWritePromise<ClusterGlideRecord<GlideReturnType>>(
            createFCall(func, [], args),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns The invoked function's return value.
     *
     * @example
     * ```typescript
     * const response = await client.fcallReadonlyWithRoute("Deep_Thought", ["Answer", "to", "the", "Ultimate",
     *            "Question", "of", "Life,", "the", "Universe,", "and", "Everything"], "randomNode");
     * console.log(response); // Output: 42 # The return value on the function that was execute.
     * ```
     */
    public async fcallReadonlyWithRoute(
        func: GlideString,
        args: GlideString[],
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        return this.createWritePromise<ClusterGlideRecord<GlideReturnType>>(
            createFCallReadOnly(func, [], args),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Deletes a library and all its functions.
     *
     * @see {@link https://valkey.io/commands/function-delete/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The library name to delete.
     * @param route - (Optional) The command will be routed to all primary node, unless `route` is provided, in which
     *     case the client will route the command to the nodes defined by `route`.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionDelete("libName");
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionDelete(
        libraryCode: GlideString,
        options?: RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFunctionDelete(libraryCode), {
            decoder: Decoder.String,
            ...options,
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
     * - (Optional) `replace`: whether the given library should overwrite a library with the same name if it
     *     already exists.
     * - (Optional) `route`: see {@link RouteOption}.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns The library name that was loaded.
     *
     * @example
     * ```typescript
     * const code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * const result = await client.functionLoad(code, true, 'allNodes');
     * console.log(result); // Output: 'mylib'
     * ```
     */
    public async functionLoad(
        libraryCode: GlideString,
        options?: {
            replace?: boolean;
        } & RouteOption &
            DecoderOption,
    ): Promise<GlideString> {
        return this.createWritePromise(
            createFunctionLoad(libraryCode, options?.replace),
            options,
        );
    }

    /**
     * Deletes all function libraries.
     *
     * @see {@link https://valkey.io/commands/function-flush/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * const result = await client.functionFlush(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async functionFlush(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFunctionFlush(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns information about the functions and libraries.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/function-list/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link FunctionListOptions}, {@link DecoderOption}, and {@link RouteOption}.
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
        options?: FunctionListOptions & DecoderOption & RouteOption,
    ): Promise<ClusterResponse<FunctionListResponse>> {
        return this.createWritePromise<
            GlideRecord<unknown> | GlideRecord<unknown>[]
        >(createFunctionList(options), options).then((res) =>
            res.length == 0
                ? (res as FunctionListResponse) // no libs
                : ((Array.isArray(res[0])
                      ? // single node response
                        ((res as GlideRecord<unknown>[]).map(
                            convertGlideRecordToRecord,
                        ) as FunctionListResponse)
                      : // multi node response
                        convertGlideRecordToRecord(
                            res as GlideRecord<unknown>,
                        )) as ClusterResponse<FunctionListResponse>),
        );
    }

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/function-stats/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link DecoderOption} and {@link RouteOption}.
     * @returns A `Record` with two keys:
     *     - `"running_script"` with information about the running script.
     *     - `"engines"` with information about available engines and their stats.
     *     - See example for more details.
     *
     * @example
     * ```typescript
     * const response = await client.functionStats("randomNode");
     * console.log(response); // Output:
     * // {
     * //     "running_script":
     * //     {
     * //         "name": "deep_thought",
     * //         "command": ["fcall", "deep_thought", "0"],
     * //         "duration_ms": 5008
     * //     },
     * //     "engines":
     * //     {
     * //         "LUA":
     * //         {
     * //             "libraries_count": 2,
     * //             "functions_count": 3
     * //         }
     * //     }
     * // }
     * // Output if no scripts running:
     * // {
     * //     "running_script": null
     * //     "engines":
     * //     {
     * //         "LUA":
     * //         {
     * //             "libraries_count": 2,
     * //             "functions_count": 3
     * //         }
     * //     }
     * // }
     * ```
     */
    public async functionStats(
        options?: RouteOption & DecoderOption,
    ): Promise<ClusterResponse<FunctionStatsSingleResponse>> {
        return this.createWritePromise<
            ClusterGlideRecord<GlideRecord<unknown>>
        >(createFunctionStats(), options).then(
            (res) =>
                convertGlideRecordToRecord(
                    res,
                ) as ClusterResponse<FunctionStatsSingleResponse>,
        );
    }

    /**
     * Kills a function that is currently executing.
     * `FUNCTION KILL` terminates read-only functions only.
     *
     * @see {@link https://valkey.io/commands/function-kill/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns `"OK"` if function is terminated. Otherwise, throws an error.
     *
     * @example
     * ```typescript
     * await client.functionKill();
     * ```
     */
    public async functionKill(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createFunctionKill(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns the serialized payload of all loaded libraries.
     *
     * @see {@link https://valkey.io/commands/function-dump/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns The serialized payload of all loaded libraries.
     *
     * @example
     * ```typescript
     * const data = await client.functionDump();
     * // data can be used to restore loaded functions on any Valkey instance
     * ```
     */
    public async functionDump(
        options?: RouteOption,
    ): Promise<ClusterResponse<Buffer>> {
        return this.createWritePromise<ClusterGlideRecord<Buffer>>(
            createFunctionDump(),
            { decoder: Decoder.Bytes, ...options },
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Restores libraries from the serialized payload returned by {@link functionDump}.
     *
     * @see {@link https://valkey.io/commands/function-restore/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param payload - The serialized data from {@link functionDump}.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `policy`: a policy for handling existing libraries, see {@link FunctionRestorePolicy}.
     *     {@link FunctionRestorePolicy.APPEND} is used by default.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns `"OK"`.
     *
     * @example
     * ```typescript
     * await client.functionRestore(data, { policy: FunctionRestorePolicy.FLUSH, route: "allPrimaries" });
     * ```
     */
    public async functionRestore(
        payload: Buffer,
        options?: { policy?: FunctionRestorePolicy } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(
            createFunctionRestore(payload, options?.policy),
            { decoder: Decoder.String, ...options },
        );
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/flushall/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushall(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushall(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFlushAll(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/flushdb/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns `OK`.
     *
     * @example
     * ```typescript
     * const result = await client.flushdb(FlushMode.SYNC);
     * console.log(result); // Output: 'OK'
     * ```
     */
    public async flushdb(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createFlushDB(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Returns the number of keys in the database.
     *
     * The command will be routed to all nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/dbsize/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns The number of keys in the database.
     *     In the case of routing the query to multiple nodes, returns the aggregated number of keys across the different nodes.
     *
     * @example
     * ```typescript
     * const numKeys = await client.dbsize("allPrimaries");
     * console.log("Number of keys across all primary nodes: ", numKeys);
     * ```
     */
    public async dbsize(options?: RouteOption): Promise<number> {
        return this.createWritePromise<number>(createDBSize(), options);
    }

    /** Publish a message on pubsub channel.
     * This command aggregates PUBLISH and SPUBLISH commands functionalities.
     * The mode is selected using the 'sharded' parameter.
     * For both sharded and non-sharded mode, request is routed using hashed channel as key.
     *
     * @see {@link https://valkey.io/commands/publish} and {@link https://valkey.io/commands/spublish} for more details.
     *
     * @param message - Message to publish.
     * @param channel - Channel to publish the message on.
     * @param sharded - Use sharded pubsub mode. Available since Valkey version 7.0.
     * @returns -  Number of subscriptions in primary node that received the message.
     *
     * @example
     * ```typescript
     * // Example usage of publish command
     * const result = await client.publish("Hi all!", "global-channel");
     * console.log(result); // Output: 1 - This message was posted to 1 subscription which is configured on primary node
     * ```
     *
     * @example
     * ```typescript
     * // Example usage of spublish command
     * const result = await client.publish("Hi all!", "global-channel", true);
     * console.log(result); // Output: 2 - Published 2 instances of "Hi to sharded channel1!" message on channel1 using sharded mode
     * ```
     */
    public async publish(
        message: GlideString,
        channel: GlideString,
        sharded = false,
    ): Promise<number> {
        return this.createWritePromise(
            createPublish(message, channel, sharded),
        );
    }

    /**
     * Lists the currently active shard channels.
     * The command is routed to all nodes, and aggregates the response to a single array.
     *
     * @see {@link https://valkey.io/commands/pubsub-shardchannels/|valkey.io} for details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `pattern`: A glob-style pattern to match active shard channels.
     *     If not provided, all active shard channels are returned.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns A list of currently active shard channels matching the given pattern.
     *          If no pattern is specified, all active shard channels are returned.
     *
     * @example
     * ```typescript
     * const allChannels = await client.pubsubShardchannels();
     * console.log(allChannels); // Output: ["channel1", "channel2"]
     *
     * const filteredChannels = await client.pubsubShardchannels("channel*");
     * console.log(filteredChannels); // Output: ["channel1", "channel2"]
     * ```
     */
    public async pubsubShardChannels(
        options?: {
            pattern?: GlideString;
        } & DecoderOption,
    ): Promise<GlideString[]> {
        return this.createWritePromise(
            createPubsubShardChannels(options?.pattern),
            options,
        );
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified shard channels.
     *
     * @see {@link https://valkey.io/commands/pubsub-shardnumsub/|valkey.io} for details.
     * @remarks The command is routed to all nodes, and aggregates the response into a single list.
     *
     * @param channels - The list of shard channels to query for the number of subscribers.
     * @param options - (Optional) see {@link DecoderOption}.
     * @returns A list of the shard channel names and their numbers of subscribers.
     *
     * @example
     * ```typescript
     * const result1 = await client.pubsubShardnumsub(["channel1", "channel2"]);
     * console.log(result1); // Output:
     * // [{ channel: "channel1", numSub: 3}, { channel: "channel2", numSub: 5 }]
     *
     * const result2 = await client.pubsubShardnumsub([]);
     * console.log(result2); // Output: []
     * ```
     */
    public async pubsubShardNumSub(
        channels: GlideString[],
        options?: DecoderOption,
    ): Promise<{ channel: GlideString; numSub: number }[]> {
        return this.createWritePromise<GlideRecord<number>>(
            createPubSubShardNumSub(channels),
            options,
        ).then((res) =>
            res.map((r) => {
                return { channel: r.key, numSub: r.value };
            }),
        );
    }

    /**
     * Returns `UNIX TIME` of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/lastsave/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns `UNIX TIME` of the last DB save executed with success.
     *
     * @example
     * ```typescript
     * const timestamp = await client.lastsave();
     * console.log("Last DB save was done at " + timestamp);
     * ```
     */
    public async lastsave(
        options?: RouteOption,
    ): Promise<ClusterResponse<number>> {
        return this.createWritePromise<ClusterGlideRecord<number>>(
            createLastSave(),
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Returns a random existing key name.
     *
     * The command will be routed to all primary nodes, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/randomkey/|valkey.io} for details.
     *
     * @param options - (Optional) See {@link RouteOption} and {@link DecoderOption}.
     * @returns A random existing key name.
     *
     * @example
     * ```typescript
     * const result = await client.randomKey();
     * console.log(result); // Output: "key12" - "key12" is a random existing key name.
     * ```
     */
    public async randomKey(
        options?: DecoderOption & RouteOption,
    ): Promise<GlideString | null> {
        return this.createWritePromise(createRandomKey(), options);
    }

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * The command will be routed to all primary nodes, unless `route` is provided
     *
     * @see {@link https://valkey.io/commands/unwatch/|valkey.io} and {@link https://valkey.io/topics/transactions/#cas|Valkey Glide Wiki} for more details.
     *
     * @param options - (Optional) See {@link RouteOption}.
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
    public async unwatch(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createUnWatch(), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Invokes a Lua script with arguments.
     * This method simplifies the process of invoking scripts on a Valkey server by using an object that represents a Lua script.
     * The script loading, argument preparation, and execution will all be handled internally. If the script has not already been loaded,
     * it will be loaded automatically using the `SCRIPT LOAD` command. After that, it will be invoked using the `EVALSHA` command.
     *
     * The command will be routed to a random node, unless `route` is provided.
     *
     * @see {@link https://valkey.io/commands/script-load/|SCRIPT LOAD} and {@link https://valkey.io/commands/evalsha/|EVALSHA} on valkey.io for details.
     *
     * @param script - The Lua script to execute.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `args`: the arguments for the script.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A value that depends on the script that was executed.
     *
     * @example
     * ```typescript
     * const luaScript = new Script("return { ARGV[1] }");
     * const result = await invokeScript(luaScript, { args: ["bar"] });
     * console.log(result); // Output: ['bar']
     * ```
     */
    public async invokeScriptWithRoute(
        script: Script,
        options?: { args?: GlideString[] } & DecoderOption & RouteOption,
    ): Promise<ClusterResponse<GlideReturnType>> {
        const scriptInvocation = command_request.ScriptInvocation.create({
            hash: script.getHash(),
            keys: [],
            args: options?.args?.map(Buffer.from),
        });
        return this.createWritePromise<ClusterGlideRecord<GlideReturnType>>(
            scriptInvocation,
            options,
        ).then((res) => convertClusterGlideRecord(res, true, options?.route));
    }

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.
     *
     * @see {@link https://valkey.io/commands/script-exists/|valkey.io} for more details.
     *
     * @param sha1s - List of SHA1 digests of the scripts to check.
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A list of boolean values indicating the existence of each script.
     *
     * @example
     * ```typescript
     * console result = await client.scriptExists(["sha1_digest1", "sha1_digest2"]);
     * console.log(result); // Output: [true, false]
     * ```
     */
    public async scriptExists(
        sha1s: GlideString[],
        options?: RouteOption,
    ): Promise<ClusterResponse<boolean[]>> {
        return this.createWritePromise(createScriptExists(sha1s), options);
    }

    /**
     * Flushes the Lua scripts cache.
     *
     * @see {@link https://valkey.io/commands/script-flush/|valkey.io} for more details.
     *
     * @param options - (Optional) Additional parameters:
     * - (Optional) `mode`: the flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * - (Optional) `route`: see {@link RouteOption}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * console result = await client.scriptFlush(FlushMode.SYNC);
     * console.log(result); // Output: "OK"
     * ```
     */
    public async scriptFlush(
        options?: {
            mode?: FlushMode;
        } & RouteOption,
    ): Promise<"OK"> {
        return this.createWritePromise(createScriptFlush(options?.mode), {
            decoder: Decoder.String,
            ...options,
        });
    }

    /**
     * Kills the currently executing Lua script, assuming no write operation was yet performed by the script.
     *
     * @see {@link https://valkey.io/commands/script-kill/|valkey.io} for more details.
     * @remarks The command is routed to all nodes, and aggregates the response to a single array.
     *
     * @param options - (Optional) See {@link RouteOption}.
     * @returns A simple `"OK"` response.
     *
     * @example
     * ```typescript
     * console result = await client.scriptKill();
     * console.log(result); // Output: "OK"
     * ```
     */
    public async scriptKill(options?: RouteOption): Promise<"OK"> {
        return this.createWritePromise(createScriptKill(), {
            decoder: Decoder.String,
            ...options,
        });
    }
}
