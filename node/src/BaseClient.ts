import {
    DEFAULT_TIMEOUT_IN_MILLISECONDS,
    StartSocketConnection,
    valueFromSplitPointer,
} from "babushka-rs-internal";
import * as net from "net";
import { Buffer, BufferWriter, Reader, Writer } from "protobufjs";
import {
    SetOptions,
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
    createLPop,
    createLPush,
    createLRange,
    createMGet,
    createMSet,
    createRPop,
    createRPush,
    createSAdd,
    createSCard,
    createSMembers,
    createSRem,
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
    /**
     * The username that will be passed to the cluster's Access Control Layer.
     * If not supplied, "default" will be used.
     */
    username?: string;
    /**
     * The password that will be passed to the cluster's Access Control Layer.
     */
    password: string;
};

type ReadFromReplicaStrategy =
    /** Always get from primary, in order to get the freshest data.*/
    | "alwaysFromPrimary"
    /** Spread the requests between all replicas evenly.*/
    | "roundRobin";

export type ConnectionOptions = {
    /**
     * DNS Addresses and ports of known nodes in the cluster.
     * If the server is in cluster mode the list can be partial, as the client will attempt to map out the cluster and find all nodes.
     * If the server is in standalone mode, only nodes whose addresses were provided will be used by the client.
     * @example
     * <code>
     * [
     *   \{ address:sample-address-0001.use1.cache.amazonaws.com, port:6378 \},
     *   \{ address: sample-address-0002.use2.cache.amazonaws.com \}
     *   \{ address: sample-address-0003.use2.cache.amazonaws.com, port:6380 \}
     * ]
     * </code>
     */
    addresses: {
        host: string;
        /**
         * If port isn't supplied, 6379 will be used
         */
        port?: number;
    }[];
    /**
     * True if communication with the cluster should use Transport Level Security.
     */
    useTLS?: boolean;
    /**
     * Credentials for authentication process.
     * If none are set, the client will not authenticate itself with the server.
     */
    credentials?: AuthenticationOptions;
    /**
     * Number of milliseconds that the client should wait for response before determining that the connection has been severed.
     * If not set, a default value will be used.
     * Value must be an integer.
     */
    responseTimeout?: number;
    /**
     * Number of milliseconds that the client should wait for the initial connection attempts before failing to create a client.
     * If not set, a default value will be used.
     * Value must be an integer.
     */
    clientCreationTimeout?: number;
    /**
     * If not set, `alwaysFromPrimary` will be used.
     */
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
     * See https://redis.io/commands/get/ for details.
     *
     * @param key - The key to retrieve from the database.
     * @returns If `key` exists, returns the value of `key` as a string. Otherwise, return null.
     */
    public get(key: string): Promise<string | null> {
        return this.createWritePromise(createGet(key));
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     * See https://redis.io/commands/set/ for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - The set options.
     * @returns - If the value is successfully set, return OK.
     * If value isn't set because of `onlyIfExists` or `onlyIfDoesNotExist` conditions, return null.
     * If `returnOldValue` is set, return the old value as a string.
     */
    public set(
        key: string,
        value: string,
        options?: SetOptions
    ): Promise<"OK" | string | null> {
        return this.createWritePromise(createSet(key, value, options));
    }

    /** Removes the specified keys. A key is ignored if it does not exist.
     * See https://redis.io/commands/del/ for details.
     *
     * @param keys - the keys we wanted to remove.
     * @returns the number of keys that were removed.
     */
    public del(keys: string[]): Promise<number> {
        return this.createWritePromise(createDel(keys));
    }

    /** Retrieve the values of multiple keys.
     * See https://redis.io/commands/mget/ for details.
     *
     * @param keys - A list of keys to retrieve values for.
     * @returns A list of values corresponding to the provided keys. If a key is not found,
     * its corresponding value in the list will be null.
     */
    public mget(keys: string[]): Promise<(string | null)[]> {
        return this.createWritePromise(createMGet(keys));
    }

    /** Set multiple keys to multiple values in a single operation.
     * See https://redis.io/commands/mset/ for details.
     *
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     * @returns always "OK".
     */
    public mset(keyValueMap: Record<string, string>): Promise<"OK"> {
        return this.createWritePromise(createMSet(keyValueMap));
    }

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incr/ for details.
     *
     * @param key - The key to increment its value.
     * @returns the value of `key` after the increment, An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public incr(key: string): Promise<number> {
        return this.createWritePromise(createIncr(key));
    }

    /** Increments the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrby/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     * @returns the value of `key` after the increment, An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public incrBy(key: string, amount: number): Promise<number> {
        return this.createWritePromise(createIncrBy(key, amount));
    }

    /** Increment the string representing a floating point number stored at `key` by `amount`.
     * By using a negative increment value, the result is that the value stored at `key` is decremented.
     * If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/incrbyfloat/ for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     * @returns the value of `key` after the increment as string.
     * An error is raised if `key` contains a value of the wrong type,
     * or the current key content is not parsable as a double precision floating point number.
     *
     */
    public incrByFloat(key: string, amount: number): Promise<string> {
        return this.createWritePromise(createIncrByFloat(key, amount));
    }

    /** Decrements the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decr/ for details.
     *
     * @param key - The key to decrement its value.
     * @returns the value of `key` after the decrement. An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public decr(key: string): Promise<number> {
        return this.createWritePromise(createDecr(key));
    }

    /** Decrements the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/decrby/ for details.
     *
     * @param key - The key to decrement its value.
     * @param amount - The amount to decrement.
     * @returns the value of `key` after the decrement. An error is raised if `key` contains a value
     * of the wrong type or contains a string that can not be represented as integer.
     */
    public decrBy(key: string, amount: number): Promise<number> {
        return this.createWritePromise(createDecrBy(key, amount));
    }

    /** Retrieve the value associated with `field` in the hash stored at `key`.
     * See https://redis.io/commands/hget/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash stored at `key` to retrieve from the database.
     * @returns the value associated with `field`, or null when `field` is not present in the hash or `key` does not exist.
     */
    public hget(key: string, field: string): Promise<string | null> {
        return this.createWritePromise(createHGet(key, field));
    }

    /** Sets the specified fields to their respective values in the hash stored at `key`.
     * See https://redis.io/commands/hset/ for details.
     *
     * @param key - The key of the hash.
     * @param fieldValueMap - A field-value map consisting of fields and their corresponding values
     * to be set in the hash stored at the specified key.
     * @returns The number of fields that were added.
     */
    public hset(
        key: string,
        fieldValueMap: Record<string, string>
    ): Promise<number> {
        return this.createWritePromise(createHSet(key, fieldValueMap));
    }

    /** Removes the specified fields from the hash stored at `key`.
     * Specified fields that do not exist within this hash are ignored.
     * See https://redis.io/commands/hdel/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields to remove from the hash stored at `key`.
     * @returns the number of fields that were removed from the hash, not including specified but non existing fields.
     * If `key` does not exist, it is treated as an empty hash and it returns 0.
     */
    public hdel(key: string, fields: string[]): Promise<number> {
        return this.createWritePromise(createHDel(key, fields));
    }

    /** Returns the values associated with the specified fields in the hash stored at `key`.
     * See https://redis.io/commands/hmget/ for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve from the database.
     * @returns a list of values associated with the given fields, in the same order as they are requested.
     * For every field that does not exist in the hash, a null value is returned.
     * If `key` does not exist, it is treated as an empty hash and it returns a list of null values.
     */
    public hmget(key: string, fields: string[]): Promise<(string | null)[]> {
        return this.createWritePromise(createHMGet(key, fields));
    }

    /** Returns if `field` is an existing field in the hash stored at `key`.
     * See https://redis.io/commands/hexists/ for details.
     *
     * @param key - The key of the hash.
     * @param field - The field to check in the hash stored at `key`.
     * @returns 1 if the hash contains `field`. If the hash does not contain `field`, or if `key` does not exist, it returns 0.
     */
    public hexists(key: string, field: string): Promise<number> {
        return this.createWritePromise(createHExists(key, field));
    }

    /** Returns all fields and values of the hash stored at `key`.
     * See https://redis.io/commands/hgetall/ for details.
     *
     * @param key - The key of the hash.
     * @returns a list of fields and their values stored in the hash. Every field name in the list is followed by its value.
     * If `key` does not exist, it returns an empty list.
     */
    public hgetall(key: string): Promise<string[]> {
        return this.createWritePromise(createHGetAll(key));
    }

    /** Increments the number stored at `field` in the hash stored at `key` by increment.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/hincrby/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     * @returns the value of `field` in the hash stored at `key` after the increment.
     *  An error will be raised if `key` holds a value of an incorrect type (not a string)
     *  or if it contains a string that cannot be represented as an integer.
     */
    public hincrBy(
        key: string,
        field: string,
        amount: number
    ): Promise<number> {
        return this.createWritePromise(createHIncrBy(key, field, amount));
    }

    /** Increment the string representing a floating point number stored at `field` in the hash stored at `key` by increment.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * See https://redis.io/commands/hincrbyfloat/ for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     * @returns the value of `field` in the hash stored at `key` after the increment as string.
     *  An error is raised if `key` contains a value of the wrong type
     *  or the current field content is not parsable as a double precision floating point number.
     *
     */
    public hincrByFloat(
        key: string,
        field: string,
        amount: number
    ): Promise<string> {
        return this.createWritePromise(createHIncrByFloat(key, field, amount));
    }

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/lpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     * @returns the length of the list after the push operations.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lpush(key: string, elements: string[]): Promise<number> {
        return this.createWritePromise(createLPush(key, elements));
    }

    /** Removes and returns the first elements of the list stored at `key`.
     * By default, the command pops a single element from the beginning of the list.
     * When `count` is provided, the command pops up to `count` elements, depending on the list's length.
     * See https://redis.io/commands/lpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @returns The value of the first element if `count` is not provided. If `count` is provided, a list of the popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lpop(
        key: string,
        count?: number
    ): Promise<string | string[] | null> {
        return this.createWritePromise(createLPop(key, count));
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
     * @returns list of elements in the specified range.
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, an empty list will be returned.
     * If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
     * If `key` does not exist an empty list will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public lrange(key: string, start: number, end: number): Promise<string[]> {
        return this.createWritePromise(createLRange(key, start, end));
    }

    /** Inserts all the specified values at the tail of the list stored at `key`.
     * `elements` are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * See https://redis.io/commands/rpush/ for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     * @returns the length of the list after the push operations.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public rpush(key: string, elements: string[]): Promise<number> {
        return this.createWritePromise(createRPush(key, elements));
    }

    /** Removes and returns the last elements of the list stored at `key`.
     * By default, the command pops a single element from the end of the list.
     * When `count` is provided, the command pops up to `count` elements, depending on the list's length.
     * See https://redis.io/commands/rpop/ for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     * @returns The value of the last element if `count` is not provided. If `count` is provided, list of popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     * If `key` holds a value that is not a list, an error is raised.
     */
    public rpop(
        key: string,
        count?: number
    ): Promise<string | string[] | null> {
        return this.createWritePromise(createRPop(key, count));
    }

    /** Adds the specified members to the set stored at `key`. Specified members that are already a member of this set are ignored.
     * If `key` does not exist, a new set is created before adding `members`.
     * See https://redis.io/commands/sadd/ for details.
     *
     * @param key - The key to store the members to its set.
     * @param members - A list of members to add to the set stored at `key`.
     * @returns the number of members that were added to the set, not including all the members already present in the set.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public sadd(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createSAdd(key, members));
    }

    /** Removes the specified members from the set stored at `key`. Specified members that are not a member of this set are ignored.
     * See https://redis.io/commands/srem/ for details.
     *
     * @param key - The key to remove the members from its set.
     * @param members - A list of members to remove from the set stored at `key`.
     * @returns the number of members that were removed from the set, not including non existing members.
     * If `key` does not exist, it is treated as an empty set and this command returns 0.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public srem(key: string, members: string[]): Promise<number> {
        return this.createWritePromise(createSRem(key, members));
    }

    /** Returns all the members of the set value stored at `key`.
     * See https://redis.io/commands/smembers/ for details.
     *
     * @param key - The key to return its members.
     * @returns all members of the set.
     * If `key` does not exist, it is treated as an empty set and this command returns empty list.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public smembers(key: string): Promise<string[]> {
        return this.createWritePromise(createSMembers(key));
    }

    /** Returns the set cardinality (number of elements) of the set stored at `key`.
     * See https://redis.io/commands/scard/ for details.
     *
     * @param key - The key to return the number of its members.
     * @returns the cardinality (number of elements) of the set, or 0 if key does not exist.
     * If `key` holds a value that is not a set, an error is raised.
     */
    public scard(key: string): Promise<number> {
        return this.createWritePromise(createSCard(key));
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
