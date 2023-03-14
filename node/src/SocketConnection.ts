import { BabushkaInternal } from "../";
import * as net from "net";
import { Logger } from "./Logger";
import { valueFromSplitPointer } from "babushka-rs-internal";
import { pb_message } from "./ProtobufMessage";
import { BufferWriter, Buffer, Reader } from "protobufjs";
import Long from "long";

const { StartSocketConnection, createLeakedStringVec, MAX_REQUEST_ARGS_LEN } = BabushkaInternal;
const { RequestType } = pb_message;

type PromiseFunction = (value?: any) => void;

type AuthenticationOptions =
    | {
          /// The username that will be passed to the cluster's Access Control Layer.
          /// If not supplied, "default" will be used.
          // TODO - implement usage
          username?: string;
          /// The password that will be passed to the cluster's Access Control Layer.
          password: string;
      }
    | {
          /// a callback that allows the client to receive new pairs of username/password. Should be used when connecting to a server that might change the required credentials, such as AWS IAM.
          // TODO - implement usage
          credentialsProvider: () => [string, string];
      };

type ConnectionRetryStrategy = {
    /// The client will add a random number of milliseconds between 0 and this value to the wait between each connection attempt, in order to prevent the server from receiving multiple connections at the same time, and causing a connection storm.
    /// if not set, will be set to DEFAULT_CONNECTION_RETRY_JITTER.
    /// Value must be an integer.
    // TODO - implement usage
    jitter?: number;
    /// Number of retry attempts that the client should perform when disconnected from the server.
    /// Value must be an integer.
    numberOfRetries: number;
} & (
    | {
          /// A retry strategy where the time between attempts is the same, regardless of the number of performed connection attempts.
          type: "linear";
          /// Number of milliseconds that the client should wait between connection attempts.
          /// Value must be an integer.
          waitDuration: number;
      }
    | {
          /// A retry strategy where the time between attempts grows exponentially, to the formula baselineWaitDuration * (exponentBase ^ N), where N is the number of failed attempts.
          type: "exponentialBackoff";
          /// Value must be an integer.
          baselineWaitDuration: number;
          /// Value must be an integer.
          exponentBase: number;
      }
);

export const DEFAULT_RESPONSE_TIMEOUT = 2000;
export const DEFAULT_CONNECTION_TIMEOUT = 2000;
export const DEFAULT_CONNECTION_RETRY_JITTER = 10;
export const DEFAULT_CONNECTION_RETRY_STRATEGY: ConnectionRetryStrategy = {
    type: "exponentialBackoff",
    numberOfRetries: 5,
    jitter: DEFAULT_CONNECTION_RETRY_JITTER,
    baselineWaitDuration: 10,
    exponentBase: 10,
};

type ConnectionOptions = {
    /// DNS Addresses and ports of known nodes in the cluster.
    /// If the server has Cluster Mode Enabled the list can be partial, as the client will attempt to map out the cluster and find all nodes.
    /// If the server has Cluster Mode Disabled, only nodes whose addresses were provided will be used by the client.
    /// For example, [{address:sample-address-0001.use1.cache.amazonaws.com, port:6379}, {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}].
    // TODO - implement usage of multiple addresses
    addresses: {
        address: string;
        port?: number; /// If port isn't supplied, 6379 will be used
    }[];
    /// True if communication with the cluster should use Transport Level Security.
    useTLS?: boolean;
    /// Credentials for authentication process.
    /// If none are set, the client will not authenticate itself with the server.
    // TODO - implement usage
    credentials?: AuthenticationOptions;
    /// Number of milliseconds that the client should wait for response before determining that the connection has been severed.
    /// If not set, DEFAULT_RESPONSE_TIMEOUT will be used.
    // TODO - implement usage
    /// Value must be an integer.
    responseTimeout?: number;
    /// Number of milliseconds that the client should wait for connection before determining that the connection has been severed.
    /// If not set, DEFAULT_CONNECTION_TIMEOUT will be used.
    // TODO - implement usage
    /// Value must be an integer.
    connectionTimeout?: number;
    /// Strategy used to determine how and when to retry connecting, in case of connection failures.
    /// If not set, DEFAULT_CONNECTION_RETRY_STRATEGY will be used.
    // TODO - implement usage
    connectionRetryStrategy?: ConnectionRetryStrategy;
    /// If not set, `alwaysFromPrimary` will be used.
    readFromReplicaStrategy?:
        | "alwaysFromPrimary" /// Always get from primary, in order to get the freshest data. // TODO - implement usage
        | "roundRobin" /// Spread the request load between all replicas evenly. // TODO - implement usage
        | "lowestLatency" /// Send requests to the replica with the lowest latency. // TODO - implement usage
        | "azAffinity"; /// Send requests to the replica which is in the same AZ as the EC2 instance, otherwise behaves like `lowestLatency`. Only available on AWS ElastiCache. // TODO - implement usage
};

export class SocketConnection {
    private socket: net.Socket;
    private readonly promiseCallbackFunctions: [
        PromiseFunction,
        PromiseFunction
    ][] = [];
    private readonly availableCallbackSlots: number[] = [];
    private requestWriter = new BufferWriter();
    private writeInProgress = false;
    private remainingReadData: Uint8Array | undefined;

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
                message = pb_message.Response.decodeDelimited(reader);
            } catch (err) {
                if (err instanceof RangeError) {
                    // Partial response received, more data is required
                    this.remainingReadData = buf.slice(lastPos);
                    return;
                } else {
                    // Unhandled error
                    const err_message = `Failed to decode the response: ${err}`;
                    Logger.instance.log("error", "connection", err_message);
                    this.dispose(err_message);
                    return;
                }
            }
            if (message.closingError !== null) {
                this.dispose(message.closingError);
                return;
            }
            const [resolve, reject] =
                this.promiseCallbackFunctions[message.callbackIdx];
            this.availableCallbackSlots.push(message.callbackIdx);
            if (message.requestError !== null) {
                reject(message.requestError);
            } else if (message.respPointer) {
                const pointer = message.respPointer;
                if (typeof pointer === "number") {
                    resolve(valueFromSplitPointer(0, pointer));
                } else {
                    resolve(valueFromSplitPointer(pointer.high, pointer.low));
                }
            } else if (
                message.constantResponse === pb_message.ConstantResponse.OK
            ) {
                resolve("OK");
            } else {
                resolve(null);
            }
        }
        this.remainingReadData = undefined;
    }

    private constructor(socket: net.Socket) {
        // if logger has been initialized by the external-user on info level this log will be shown
        Logger.instance.log("info", "connection", `construct socket`);

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

    private is_a_large_request(args: string[]) {
        let len_sum = 0;
        for (const arg of args) {
            len_sum += arg.length;
            if (len_sum >= MAX_REQUEST_ARGS_LEN) {
                return true;
            }
        }
        return false;
    }

    private writeOrBufferRequest(callbackIdx: number, requestType: number, args: string[]) {
        const message = pb_message.Request.create({
            callbackIdx: callbackIdx,
            requestType: requestType,
        });
        if (this.is_a_large_request(args)) {
        // pass as a pointer
        const pointerArr = createLeakedStringVec(args);
        const pointer = new Long(pointerArr[0], pointerArr[1]);
            message.argsVecPointer = pointer;
        } else {
            message.argsArray = pb_message.Request.ArgsArray.create({args: args});
        }

        pb_message.Request.encodeDelimited(message, this.requestWriter);
        if (this.writeInProgress) {
            return;
        }
        this.writeBufferedRequestsToSocket();
    }

    /// Get the value associated with the given key, or null if no such value exists.
    /// See https://redis.io/commands/get/ for details.
    public get(key: string): Promise<string | null> {
        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRequest(callbackIndex, RequestType.GetString, [key]);
        });
    }

    /// Set the given key with the given value. Return value is dependent on the passed options.
    /// See https://redis.io/commands/set/ for details.
    public set(
        key: string,
        value: string,
        options?: {
            /// `onlyIfDoesNotExist` - Only set the key if it does not already exist. Equivalent to `NX` in the Redis API.
            /// `onlyIfExists` - Only set the key if it already exist. Equivalent to `EX` in the Redis API.
            /// if `conditional` is not set the value will be set regardless of prior value existence.
            /// If value isn't set because of the condition, return null.
            conditionalSet?: "onlyIfExists" | "onlyIfDoesNotExist";
            /// Return the old string stored at key, or nil if key did not exist. An error is returned and SET aborted if the value stored at key is not a string. Equivalent to `GET` in the Redis API.
            returnOldValue?: boolean;
            /// If not set, no expiry time will be set for the value.
            expiry?:
                | "keepExisting" /// Retain the time to live associated with the key. Equivalent to `KEEPTTL` in the Redis API.
                | {
                      type:
                          | "seconds" /// Set the specified expire time, in seconds. Equivalent to `EX` in the Redis API.
                          | "milliseconds" ///  Set the specified expire time, in milliseconds. Equivalent to `PX` in the Redis API.
                          | "unixSeconds" /// Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Redis API.
                          | "unixMilliseconds"; /// Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the Redis API.
                      count: number;
                  };
        }
    ): Promise<"OK" | string | null> {
        return new Promise((resolve, reject) => {
            const args = [key, value];
            if (options) {
                if (options.conditionalSet === "onlyIfExists") {
                    args.push("XX");
                } else if (options.conditionalSet === "onlyIfDoesNotExist") {
                    args.push("NX");
                }
                if (options.returnOldValue) {
                    args.push("GET");
                }
                if (
                    options.expiry &&
                    options.expiry !== "keepExisting" &&
                    !Number.isInteger(options.expiry.count)
                ) {
                    reject(
                        `Received expiry '${JSON.stringify(
                            options.expiry
                        )}'. Count must be an integer`
                    );
                }
                if (options.expiry === "keepExisting") {
                    args.push("KEEPTTL");
                } else if (options.expiry?.type === "seconds") {
                    args.push("EX " + options.expiry.count);
                } else if (options.expiry?.type === "milliseconds") {
                    args.push("PX " + options.expiry.count);
                } else if (options.expiry?.type === "unixSeconds") {
                    args.push("EXAT " + options.expiry.count);
                } else if (options.expiry?.type === "unixMilliseconds") {
                    args.push("PXAT " + options.expiry.count);
                }
            }
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRequest(
                callbackIndex,
                RequestType.SetString,
                args
            );
        });
    }

    private setServerAddress(address: string): Promise<void> {
        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRequest(callbackIndex, RequestType.ServerAddress, [address]);
        });
    }

    public dispose(errorMessage?: string): void {
        this.promiseCallbackFunctions.forEach(([_resolve, reject], _index) => {
            reject(errorMessage);
        });
        this.socket.end();
    }

    static async __CreateConnection(
        address: string,
        connectedSocket: net.Socket
    ): Promise<SocketConnection> {
        const connection = new SocketConnection(connectedSocket);
        await connection.setServerAddress(address);
        return connection;
    }

    private static GetSocket(path: string): Promise<net.Socket> {
        return new Promise((resolve, reject) => {
            const socket = new net.Socket();
            socket
                .connect(path)
                .once("connect", () => resolve(socket))
                .once("error", reject);
        });
    }

    private static finalAddresses(options: ConnectionOptions): string[] {
        const protocol = options.useTLS ? "rediss://" : "redis://";
        return options.addresses.map(
            (address) => `${protocol}${address.address}:${address.port ?? 6379}`
        );
    }

    public static async CreateConnection(
        options: ConnectionOptions
    ): Promise<SocketConnection> {
        const path = await StartSocketConnection();
        const socket = await this.GetSocket(path);
        return await this.__CreateConnection(
            SocketConnection.finalAddresses(options)[0],
            socket
        );
    }
}
