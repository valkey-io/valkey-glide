import {
    DEFAULT_TIMEOUT_IN_MILLISECONDS,
    StartSocketConnection,
    valueFromSplitPointer,
} from "babushka-rs-internal";
import * as net from "net";
import { Buffer, BufferWriter, Reader, Writer } from "protobufjs";
import {
    SetOptions,
    createDel,
    createGet,
    createIncr,
    createIncrBy,
    createSet,
} from "./Commands";
import {
    BaseRedisError,
    ClosingError,
    ConnectionError,
    ExecAbortError,
    RedisError,
    TIMEOUT_ERROR,
    TimeoutError,
} from "./Errors";
import { Logger } from "./Logger";
import { connection_request, redis_request, response } from "./ProtobufMessage";

/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type PromiseFunction = (value?: any) => void;
type ErrorFunction = (error: BaseRedisError) => void;
export type ReturnType = "OK" | string | ReturnType[] | number | null;

type AuthenticationOptions = {
    /// The username that will be passed to the cluster's Access Control Layer.
    /// If not supplied, "default" will be used.
    username?: string;
    /// The password that will be passed to the cluster's Access Control Layer.
    password: string;
};

type ReadFromReplicaStrategy =
    | "alwaysFromPrimary" /// Always get from primary, in order to get the freshest data.
    | "roundRobin"; /// Spread the requests between all replicas evenly.

export type ConnectionOptions = {
    /// DNS Addresses and ports of known nodes in the cluster.
    /// If the server is in cluster mode the list can be partial, as the client will attempt to map out the cluster and find all nodes.
    /// If the server is in standalone mode, only nodes whose addresses were provided will be used by the client.
    /// For example, [{address:sample-address-0001.use1.cache.amazonaws.com, port:6379}, {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}].
    addresses: {
        host: string;
        port?: number; /// If port isn't supplied, 6379 will be used
    }[];
    /// True if communication with the cluster should use Transport Level Security.
    useTLS?: boolean;
    /// Credentials for authentication process.
    /// If none are set, the client will not authenticate itself with the server.
    credentials?: AuthenticationOptions;
    /// Number of milliseconds that the client should wait for response before determining that the connection has been severed.
    /// If not set, a default value will be used.
    /// Value must be an integer.
    responseTimeout?: number;
    /// Number of milliseconds that the client should wait for the initial connection attempts before failing to create a client.
    /// If not set, a default value will be used.
    /// Value must be an integer.
    clientCreationTimeout?: number;
    /// Strategy used to determine how and when to retry connecting, in case of connection failures.
    /// The time between attempts grows exponentially, to the formula rand(0 .. factor * (exponentBase ^ N)), where N is the number of failed attempts.
    /// If not set, a default backoff strategy will be used.
    /// ATM this setting isn't supported in cluster mode, only in standalone mode.
    connectionBackoff?: {
        /// Number of retry attempts that the client should perform when disconnected from the server.
        /// Value must be an integer.
        numberOfRetries: number;
        /// Value must be an integer.
        factor: number;
        /// Value must be an integer.
        exponentBase: number;
    };
    /// If not set, `alwaysFromPrimary` will be used.
    readFromReplicaStrategy?: ReadFromReplicaStrategy;
};

function getRequestErrorClass(
    type: response.RequestErrorType | null | undefined
): typeof RedisError {
    if (type === response.RequestErrorType.Disconnect) {
        return ConnectionError;
    }
    if (type === response.RequestErrorType.ExecAbort) {
        return ExecAbortError;
    }
    if (type === response.RequestErrorType.Timeout) {
        return TimeoutError;
    }
    if (type === response.RequestErrorType.Unspecified) {
        return RedisError;
    }

    return RedisError;
}

export class BaseClient {
    private socket: net.Socket;
    private readonly promiseCallbackFunctions: [
        PromiseFunction,
        ErrorFunction
    ][] = [];
    private readonly availableCallbackSlots: number[] = [];
    private requestWriter = new BufferWriter();
    private writeInProgress = false;
    private remainingReadData: Uint8Array | undefined;
    private readonly responseTimeout: number; // Timeout in milliseconds

    private handleReadData(data: Buffer) {
        const buf = this.remainingReadData
            ? Buffer.concat([this.remainingReadData, data])
            : data;
        let lastPos = 0;
        const reader = Reader.create(buf);
        while (reader.pos < reader.len) {
            lastPos = reader.pos;
            let message = undefined;
            try {
                message = response.Response.decodeDelimited(reader);
            } catch (err) {
                if (err instanceof RangeError) {
                    // Partial response received, more data is required
                    this.remainingReadData = buf.slice(lastPos);
                    return;
                } else {
                    // Unhandled error
                    const err_message = `Failed to decode the response: ${err}`;
                    Logger.log("error", "connection", err_message);
                    this.dispose(err_message);
                    return;
                }
            }
            if (message.closingError != null) {
                this.dispose(message.closingError);
                return;
            }
            const [resolve, reject] =
                this.promiseCallbackFunctions[message.callbackIdx];
            this.availableCallbackSlots.push(message.callbackIdx);
            if (message.requestError != null) {
                const errorType = getRequestErrorClass(
                    message.requestError.type
                );
                reject(
                    new errorType(message.requestError.message ?? undefined)
                );
            } else if (message.respPointer != null) {
                const pointer = message.respPointer;
                if (typeof pointer === "number") {
                    resolve(valueFromSplitPointer(0, pointer));
                } else {
                    resolve(valueFromSplitPointer(pointer.high, pointer.low));
                }
            } else if (
                message.constantResponse === response.ConstantResponse.OK
            ) {
                resolve("OK");
            } else {
                resolve(null);
            }
        }
        this.remainingReadData = undefined;
    }

    protected constructor(socket: net.Socket, options?: ConnectionOptions) {
        // if logger has been initialized by the external-user on info level this log will be shown
        Logger.log("info", "Client lifetime", `construct client`);
        this.responseTimeout =
            options?.responseTimeout ?? DEFAULT_TIMEOUT_IN_MILLISECONDS;
        this.socket = socket;
        this.socket
            .on("data", (data) => this.handleReadData(data))
            .on("error", (err) => {
                console.error(`Server closed: ${err}`);
                this.dispose();
            });
    }

    private getCallbackIndex(): number {
        return (
            this.availableCallbackSlots.pop() ??
            this.promiseCallbackFunctions.length
        );
    }

    private writeBufferedRequestsToSocket() {
        this.writeInProgress = true;
        const requests = this.requestWriter.finish();
        this.requestWriter.reset();

        this.socket.write(requests, undefined, () => {
            if (this.requestWriter.len > 0) {
                this.writeBufferedRequestsToSocket();
            } else {
                this.writeInProgress = false;
            }
        });
    }

    protected createWritePromise<T>(
        command: redis_request.Command | redis_request.Command[],
        route?: redis_request.Routes
    ): Promise<T> {
        return new Promise((resolve, reject) => {
            setTimeout(() => {
                reject(TIMEOUT_ERROR);
            }, this.responseTimeout);
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRedisRequest(callbackIndex, command, route);
        });
    }

    private writeOrBufferRedisRequest(
        callbackIdx: number,
        command: redis_request.Command | redis_request.Command[],
        route?: redis_request.Routes
    ) {
        const message = Array.isArray(command)
            ? redis_request.RedisRequest.create({
                  callbackIdx,
                  transaction: redis_request.Transaction.create({
                      commands: command,
                  }),
              })
            : redis_request.RedisRequest.create({
                  callbackIdx,
                  singleCommand: command,
              });
        message.route = route;

        this.writeOrBufferRequest(
            message,
            (message: redis_request.RedisRequest, writer: Writer) => {
                redis_request.RedisRequest.encodeDelimited(message, writer);
            }
        );
    }

    private writeOrBufferRequest<TRequest>(
        message: TRequest,
        encodeDelimited: (message: TRequest, writer: Writer) => void
    ) {
        encodeDelimited(message, this.requestWriter);
        if (this.writeInProgress) {
            return;
        }
        this.writeBufferedRequestsToSocket();
    }

    /** Get the value associated with the given key, or null if no such value exists.
     *  See https://redis.io/commands/get/ for details.
     *
     * @param key - The key to retrieve from the database.
     * @returns If the key exists, returns the value of the key as a string. Otherwise, return null.
     */
    public get(key: string): Promise<string | null> {
        return this.createWritePromise(createGet(key));
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     *  See https://redis.io/commands/set/ for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - The set options.
     * @returns - If the value is successfully set, return OK.
     *          If value isn't set because of only_if_exists or only_if_does_not_exist conditions, return null.
     *          If return_old_value is set, return the old value as a string.
     */
    public set(
        key: string,
        value: string,
        options?: SetOptions
    ): Promise<"OK" | string | null> {
        return this.createWritePromise(createSet(key, value, options));
    }

    /** Removes the specified keys. A key is ignored if it does not exist.
     *  See https://redis.io/commands/del/ for details.
     *
     * @param keys - the keys we wanted to remove.
     * @returns the number of keys that were removed.
     */
    public del(keys: string[]): Promise<number> {
        return this.createWritePromise(createDel(keys));
    }

    /** Increments the number stored at key by one. If the key does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incr/ for details.
     *
     * @param key - The key to increment it's value.
     * @returns the value of key after the increment, An error is returned if the key contains a value
     *  of the wrong type or contains a string that can not be represented as integer.
     */
    public incr(key: string): Promise<number> {
        return this.createWritePromise(createIncr(key));
    }

    /** Increments the number stored at key by increment. If the key does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrby/ for details.
     *
     * @param key - The key to increment it's value.
     * @param increment - The increment to the key's value.
     * @returns the value of key after the increment, An error is returned if the key contains a value
     *  of the wrong type or contains a string that can not be represented as integer.
     */
    public incrBy(key: string, increment: number): Promise<number> {
        return this.createWritePromise(createIncrBy(key, increment));
    }

    private readonly MAP_READ_FROM_REPLICA_STRATEGY: Record<
        ReadFromReplicaStrategy,
        connection_request.ReadFromReplicaStrategy
    > = {
        alwaysFromPrimary:
            connection_request.ReadFromReplicaStrategy.AlwaysFromPrimary,
        roundRobin: connection_request.ReadFromReplicaStrategy.RoundRobin,
    };

    protected createClientRequest(
        options: ConnectionOptions
    ): connection_request.IConnectionRequest {
        const readFromReplicaStrategy = options.readFromReplicaStrategy
            ? this.MAP_READ_FROM_REPLICA_STRATEGY[
                  options.readFromReplicaStrategy
              ]
            : undefined;
        const authenticationInfo =
            options.credentials !== undefined &&
            "password" in options.credentials
                ? {
                      password: options.credentials.password,
                      username: options.credentials.username,
                  }
                : undefined;
        return {
            addresses: options.addresses,
            tlsMode: options.useTLS
                ? connection_request.TlsMode.SecureTls
                : connection_request.TlsMode.NoTls,
            responseTimeout: options.responseTimeout,
            clusterModeEnabled: false,
            clientCreationTimeout: options.clientCreationTimeout,
            readFromReplicaStrategy,
            connectionRetryStrategy: options.connectionBackoff,
            authenticationInfo,
        };
    }

    protected connectToServer(options: ConnectionOptions): Promise<void> {
        return new Promise((resolve, reject) => {
            this.promiseCallbackFunctions[0] = [resolve, reject];

            const message = connection_request.ConnectionRequest.create(
                this.createClientRequest(options)
            );

            this.writeOrBufferRequest(
                message,
                (
                    message: connection_request.ConnectionRequest,
                    writer: Writer
                ) => {
                    connection_request.ConnectionRequest.encodeDelimited(
                        message,
                        writer
                    );
                }
            );
        });
    }

    public dispose(errorMessage?: string): void {
        this.promiseCallbackFunctions.forEach(([, reject]) => {
            reject(new ClosingError(errorMessage));
        });
        Logger.log("info", "Client lifetime", "disposing of client");
        this.socket.end();
    }

    protected static async __createClientInternal<
        TConnection extends BaseClient
    >(
        options: ConnectionOptions,
        connectedSocket: net.Socket,
        constructor: (
            socket: net.Socket,
            options?: ConnectionOptions
        ) => TConnection
    ): Promise<TConnection> {
        const connection = constructor(connectedSocket, options);
        await connection.connectToServer(options);
        Logger.log("info", "Client lifetime", "connected to server");
        return connection;
    }

    protected static GetSocket(path: string): Promise<net.Socket> {
        return new Promise((resolve, reject) => {
            const socket = new net.Socket();
            socket
                .connect(path)
                .once("connect", () => resolve(socket))
                .once("error", reject);
        });
    }

    protected static async createClientInternal<TConnection extends BaseClient>(
        options: ConnectionOptions,
        constructor: (
            socket: net.Socket,
            options?: ConnectionOptions
        ) => TConnection
    ): Promise<TConnection> {
        const path = await StartSocketConnection();
        const socket = await this.GetSocket(path);
        return await this.__createClientInternal<TConnection>(
            options,
            socket,
            constructor
        );
    }
}
