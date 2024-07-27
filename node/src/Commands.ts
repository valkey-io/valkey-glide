/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { createLeakedStringVec, MAX_REQUEST_ARGS_LEN } from "glide-rs";
import Long from "long";

/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { BaseClient } from "src/BaseClient";
/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { GlideClient } from "src/GlideClient";
/* eslint-disable-next-line @typescript-eslint/no-unused-vars */
import { GlideClusterClient } from "src/GlideClusterClient";
import { command_request } from "./ProtobufMessage";

import RequestType = command_request.RequestType;

function isLargeCommand(args: BulkString[]) {
    let lenSum = 0;

    for (const arg of args) {
        lenSum += arg.length;

        if (lenSum >= MAX_REQUEST_ARGS_LEN) {
            return true;
        }
    }

    return false;
}

type BulkString = string | Uint8Array;

/**
 * Convert a string array into Uint8Array[]
 */
function toBuffersArray(args: BulkString[]) {
    const argsBytes: Uint8Array[] = [];

    for (const arg of args) {
        if (typeof arg == "string") {
            argsBytes.push(Buffer.from(arg));
        } else {
            argsBytes.push(arg);
        }
    }

    return argsBytes;
}

/**
 * @internal
 */
export function parseInfoResponse(response: string): Record<string, string> {
    const lines = response.split("\n");
    const parsedResponse: Record<string, string> = {};

    for (const line of lines) {
        // Ignore lines that start with '#'
        if (!line.startsWith("#")) {
            const [key, value] = line.trim().split(":");
            parsedResponse[key] = value;
        }
    }

    return parsedResponse;
}

function createCommand(
    requestType: command_request.RequestType,
    args: BulkString[],
): command_request.Command {
    const singleCommand = command_request.Command.create({
        requestType,
    });

    const argsBytes = toBuffersArray(args);

    if (isLargeCommand(args)) {
        // pass as a pointer
        const pointerArr = createLeakedStringVec(argsBytes);
        const pointer = new Long(pointerArr[0], pointerArr[1]);
        singleCommand.argsVecPointer = pointer;
    } else {
        singleCommand.argsArray = command_request.Command.ArgsArray.create({
            args: argsBytes,
        });
    }

    return singleCommand;
}

/**
 * @internal
 */
export function createGet(key: string): command_request.Command {
    return createCommand(RequestType.Get, [key]);
}

/**
 * @internal
 */
export function createGetDel(key: string): command_request.Command {
    return createCommand(RequestType.GetDel, [key]);
}

export type SetOptions = {
    /**
     *  `onlyIfDoesNotExist` - Only set the key if it does not already exist.
     * Equivalent to `NX` in the Redis API. `onlyIfExists` - Only set the key if
     * it already exist. Equivalent to `EX` in the Redis API. if `conditional` is
     * not set the value will be set regardless of prior value existence. If value
     * isn't set because of the condition, return null.
     */
    conditionalSet?: "onlyIfExists" | "onlyIfDoesNotExist";
    /**
     * Return the old string stored at key, or nil if key did not exist. An error
     * is returned and SET aborted if the value stored at key is not a string.
     * Equivalent to `GET` in the Redis API.
     */
    returnOldValue?: boolean;
    /**
     * If not set, no expiry time will be set for the value.
     */
    expiry?: /**
     * Retain the time to live associated with the key. Equivalent to
     * `KEEPTTL` in the Redis API.
     */
    | "keepExisting"
        | {
              type: /**
               * Set the specified expire time, in seconds. Equivalent to
               * `EX` in the Redis API.
               */
              | "seconds"
                  /**
                   * Set the specified expire time, in milliseconds. Equivalent
                   * to `PX` in the Redis API.
                   */
                  | "milliseconds"
                  /**
                   * Set the specified Unix time at which the key will expire,
                   * in seconds. Equivalent to `EXAT` in the Redis API.
                   */
                  | "unixSeconds"
                  /**
                   * Set the specified Unix time at which the key will expire,
                   * in milliseconds. Equivalent to `PXAT` in the Redis API.
                   */
                  | "unixMilliseconds";
              count: number;
          };
};

/**
 * @internal
 */
export function createSet(
    key: BulkString,
    value: BulkString,
    options?: SetOptions,
): command_request.Command {
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
            throw new Error(
                `Received expiry '${JSON.stringify(
                    options.expiry,
                )}'. Count must be an integer`,
            );
        }

        if (options.expiry === "keepExisting") {
            args.push("KEEPTTL");
        } else if (options.expiry?.type === "seconds") {
            args.push("EX", options.expiry.count.toString());
        } else if (options.expiry?.type === "milliseconds") {
            args.push("PX", options.expiry.count.toString());
        } else if (options.expiry?.type === "unixSeconds") {
            args.push("EXAT", options.expiry.count.toString());
        } else if (options.expiry?.type === "unixMilliseconds") {
            args.push("PXAT", options.expiry.count.toString());
        }
    }

    return createCommand(RequestType.Set, args);
}

/**
 * INFO option: a specific section of information:
 * When no parameter is provided, the default option is assumed.
 */
export enum InfoOptions {
    /**
     * SERVER: General information about the Redis server
     */
    Server = "server",
    /**
     * CLIENTS: Client connections section
     */
    Clients = "clients",
    /**
     * MEMORY: Memory consumption related information
     */
    Memory = "memory",
    /**
     * PERSISTENCE: RDB and AOF related information
     */
    Persistence = "persistence",
    /**
     * STATS: General statistics
     */
    Stats = "stats",
    /**
     * REPLICATION: Master/replica replication information
     */
    Replication = "replication",
    /**
     * CPU: CPU consumption statistics
     */
    Cpu = "cpu",
    /**
     * COMMANDSTATS: Redis command statistics
     */
    Commandstats = "commandstats",
    /**
     * LATENCYSTATS: Redis command latency percentile distribution statistics
     */
    Latencystats = "latencystats",
    /**
     * SENTINEL: Redis Sentinel section (only applicable to Sentinel instances)
     */
    Sentinel = "sentinel",
    /**
     * CLUSTER: Redis Cluster section
     */
    Cluster = "cluster",
    /**
     * MODULES: Modules section
     */
    Modules = "modules",
    /**
     * KEYSPACE: Database related statistics
     */
    Keyspace = "keyspace",
    /**
     * ERRORSTATS: Redis error statistics
     */
    Errorstats = "errorstats",
    /**
     * ALL: Return all sections (excluding module generated ones)
     */
    All = "all",
    /**
     * DEFAULT: Return only the default set of sections
     */
    Default = "default",
    /**
     * EVERYTHING: Includes all and modules
     */
    Everything = "everything",
}

/**
 * @internal
 */
export function createPing(str?: string): command_request.Command {
    const args: string[] = str == undefined ? [] : [str];
    return createCommand(RequestType.Ping, args);
}

/**
 * @internal
 */
export function createInfo(options?: InfoOptions[]): command_request.Command {
    const args: string[] = options == undefined ? [] : options;
    return createCommand(RequestType.Info, args);
}

/**
 * @internal
 */
export function createDel(keys: string[]): command_request.Command {
    return createCommand(RequestType.Del, keys);
}

/**
 * @internal
 */
export function createSelect(index: number): command_request.Command {
    return createCommand(RequestType.Select, [index.toString()]);
}

/**
 * @internal
 */
export function createClientGetName(): command_request.Command {
    return createCommand(RequestType.ClientGetName, []);
}

/**
 * @internal
 */
export function createConfigRewrite(): command_request.Command {
    return createCommand(RequestType.ConfigRewrite, []);
}

/**
 * @internal
 */
export function createConfigResetStat(): command_request.Command {
    return createCommand(RequestType.ConfigResetStat, []);
}

/**
 * @internal
 */
export function createMGet(keys: string[]): command_request.Command {
    return createCommand(RequestType.MGet, keys);
}

/**
 * @internal
 */
export function createMSet(
    keyValueMap: Record<string, string>,
): command_request.Command {
    return createCommand(RequestType.MSet, Object.entries(keyValueMap).flat());
}

/**
 * @internal
 */
export function createIncr(key: string): command_request.Command {
    return createCommand(RequestType.Incr, [key]);
}

/**
 * @internal
 */
export function createIncrBy(
    key: string,
    amount: number,
): command_request.Command {
    return createCommand(RequestType.IncrBy, [key, amount.toString()]);
}

/**
 * @internal
 */
export function createIncrByFloat(
    key: string,
    amount: number,
): command_request.Command {
    return createCommand(RequestType.IncrByFloat, [key, amount.toString()]);
}

/**
 * @internal
 */
export function createClientId(): command_request.Command {
    return createCommand(RequestType.ClientId, []);
}

/**
 * @internal
 */
export function createConfigGet(parameters: string[]): command_request.Command {
    return createCommand(RequestType.ConfigGet, parameters);
}

/**
 * @internal
 */
export function createConfigSet(
    parameters: Record<string, string>,
): command_request.Command {
    return createCommand(
        RequestType.ConfigSet,
        Object.entries(parameters).flat(),
    );
}

/**
 * @internal
 */
export function createHGet(
    key: string,
    field: string,
): command_request.Command {
    return createCommand(RequestType.HGet, [key, field]);
}

/**
 * @internal
 */
export function createHSet(
    key: string,
    fieldValueMap: Record<string, string>,
): command_request.Command {
    return createCommand(
        RequestType.HSet,
        [key].concat(Object.entries(fieldValueMap).flat()),
    );
}

/**
 * @internal
 */
export function createHSetNX(
    key: string,
    field: string,
    value: string,
): command_request.Command {
    return createCommand(RequestType.HSetNX, [key, field, value]);
}

/**
 * @internal
 */
export function createDecr(key: string): command_request.Command {
    return createCommand(RequestType.Decr, [key]);
}

/**
 * @internal
 */
export function createDecrBy(
    key: string,
    amount: number,
): command_request.Command {
    return createCommand(RequestType.DecrBy, [key, amount.toString()]);
}

/**
 * Enumeration defining the bitwise operation to use in the {@link BaseClient.bitop|bitop} command. Specifies the
 * bitwise operation to perform between the passed in keys.
 */
export enum BitwiseOperation {
    AND = "AND",
    OR = "OR",
    XOR = "XOR",
    NOT = "NOT",
}

/**
 * @internal
 */
export function createBitOp(
    operation: BitwiseOperation,
    destination: string,
    keys: string[],
): command_request.Command {
    return createCommand(RequestType.BitOp, [operation, destination, ...keys]);
}

/**
 * @internal
 */
export function createGetBit(
    key: string,
    offset: number,
): command_request.Command {
    return createCommand(RequestType.GetBit, [key, offset.toString()]);
}

/**
 * @internal
 */
export function createSetBit(
    key: string,
    offset: number,
    value: number,
): command_request.Command {
    return createCommand(RequestType.SetBit, [
        key,
        offset.toString(),
        value.toString(),
    ]);
}

/**
 * @internal
 */
export function createHDel(
    key: string,
    fields: string[],
): command_request.Command {
    return createCommand(RequestType.HDel, [key].concat(fields));
}

/**
 * @internal
 */
export function createHMGet(
    key: string,
    fields: string[],
): command_request.Command {
    return createCommand(RequestType.HMGet, [key].concat(fields));
}

/**
 * @internal
 */
export function createHExists(
    key: string,
    field: string,
): command_request.Command {
    return createCommand(RequestType.HExists, [key, field]);
}

/**
 * @internal
 */
export function createHGetAll(key: string): command_request.Command {
    return createCommand(RequestType.HGetAll, [key]);
}

/**
 * @internal
 */
export function createLPush(
    key: string,
    elements: string[],
): command_request.Command {
    return createCommand(RequestType.LPush, [key].concat(elements));
}

/**
 * @internal
 */
export function createLPushX(
    key: string,
    elements: string[],
): command_request.Command {
    return createCommand(RequestType.LPushX, [key].concat(elements));
}

/**
 * @internal
 */
export function createLPop(
    key: string,
    count?: number,
): command_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.LPop, args);
}

/**
 * @internal
 */
export function createLRange(
    key: string,
    start: number,
    end: number,
): command_request.Command {
    return createCommand(RequestType.LRange, [
        key,
        start.toString(),
        end.toString(),
    ]);
}

/**
 * @internal
 */
export function createLLen(key: string): command_request.Command {
    return createCommand(RequestType.LLen, [key]);
}

/**
 * Enumeration representing element popping or adding direction for the List Based Commands.
 */
export enum ListDirection {
    /**
     * Represents the option that elements should be popped from or added to the left side of a list.
     */
    LEFT = "LEFT",
    /**
     * Represents the option that elements should be popped from or added to the right side of a list.
     */
    RIGHT = "RIGHT",
}

/**
 * @internal
 */
export function createLMove(
    source: string,
    destination: string,
    whereFrom: ListDirection,
    whereTo: ListDirection,
): command_request.Command {
    return createCommand(RequestType.LMove, [
        source,
        destination,
        whereFrom,
        whereTo,
    ]);
}

/**
 * @internal
 */
export function createLSet(
    key: string,
    index: number,
    element: string,
): command_request.Command {
    return createCommand(RequestType.LSet, [key, index.toString(), element]);
}

/**
 * @internal
 */
export function createLTrim(
    key: string,
    start: number,
    end: number,
): command_request.Command {
    return createCommand(RequestType.LTrim, [
        key,
        start.toString(),
        end.toString(),
    ]);
}

/**
 * @internal
 */
export function createLRem(
    key: string,
    count: number,
    element: string,
): command_request.Command {
    return createCommand(RequestType.LRem, [key, count.toString(), element]);
}

/**
 * @internal
 */
export function createRPush(
    key: string,
    elements: string[],
): command_request.Command {
    return createCommand(RequestType.RPush, [key].concat(elements));
}

/**
 * @internal
 */
export function createRPushX(
    key: string,
    elements: string[],
): command_request.Command {
    return createCommand(RequestType.RPushX, [key].concat(elements));
}

/**
 * @internal
 */
export function createRPop(
    key: string,
    count?: number,
): command_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.RPop, args);
}

/**
 * @internal
 */
export function createSAdd(
    key: string,
    members: string[],
): command_request.Command {
    return createCommand(RequestType.SAdd, [key].concat(members));
}

/**
 * @internal
 */
export function createSRem(
    key: string,
    members: string[],
): command_request.Command {
    return createCommand(RequestType.SRem, [key].concat(members));
}

/**
 * @internal
 */
export function createSMembers(key: string): command_request.Command {
    return createCommand(RequestType.SMembers, [key]);
}

/**
 *
 * @internal
 */
export function createSMove(
    source: string,
    destination: string,
    member: string,
): command_request.Command {
    return createCommand(RequestType.SMove, [source, destination, member]);
}

/**
 * @internal
 */
export function createSCard(key: string): command_request.Command {
    return createCommand(RequestType.SCard, [key]);
}

/**
 * @internal
 */
export function createSInter(keys: string[]): command_request.Command {
    return createCommand(RequestType.SInter, keys);
}

/**
 * @internal
 */
export function createSInterCard(
    keys: string[],
    limit?: number,
): command_request.Command {
    let args: string[] = keys;
    args.unshift(keys.length.toString());

    if (limit != undefined) {
        args = args.concat(["LIMIT", limit.toString()]);
    }

    return createCommand(RequestType.SInterCard, args);
}

/**
 * @internal
 */
export function createSInterStore(
    destination: string,
    keys: string[],
): command_request.Command {
    return createCommand(RequestType.SInterStore, [destination].concat(keys));
}

/**
 * @internal
 */
export function createSDiff(keys: string[]): command_request.Command {
    return createCommand(RequestType.SDiff, keys);
}

/**
 * @internal
 */
export function createSDiffStore(
    destination: string,
    keys: string[],
): command_request.Command {
    return createCommand(RequestType.SDiffStore, [destination].concat(keys));
}

/**
 * @internal
 */
export function createSUnion(keys: string[]): command_request.Command {
    return createCommand(RequestType.SUnion, keys);
}

/**
 * @internal
 */
export function createSUnionStore(
    destination: string,
    keys: string[],
): command_request.Command {
    return createCommand(RequestType.SUnionStore, [destination].concat(keys));
}

/**
 * @internal
 */
export function createSIsMember(
    key: string,
    member: string,
): command_request.Command {
    return createCommand(RequestType.SIsMember, [key, member]);
}

/**
 * @internal
 */
export function createSMIsMember(
    key: string,
    members: string[],
): command_request.Command {
    return createCommand(RequestType.SMIsMember, [key].concat(members));
}

/**
 * @internal
 */
export function createSPop(
    key: string,
    count?: number,
): command_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.SPop, args);
}

/**
 * @internal
 */
export function createCustomCommand(args: string[]) {
    return createCommand(RequestType.CustomCommand, args);
}

/**
 * @internal
 */
export function createHIncrBy(
    key: string,
    field: string,
    amount: number,
): command_request.Command {
    return createCommand(RequestType.HIncrBy, [key, field, amount.toString()]);
}

/**
 * @internal
 */
export function createHIncrByFloat(
    key: string,
    field: string,
    amount: number,
): command_request.Command {
    return createCommand(RequestType.HIncrByFloat, [
        key,
        field,
        amount.toString(),
    ]);
}

/**
 * @internal
 */
export function createHLen(key: string): command_request.Command {
    return createCommand(RequestType.HLen, [key]);
}

/**
 * @internal
 */
export function createHVals(key: string): command_request.Command {
    return createCommand(RequestType.HVals, [key]);
}

/**
 * @internal
 */
export function createExists(keys: string[]): command_request.Command {
    return createCommand(RequestType.Exists, keys);
}

/**
 * @internal
 */
export function createUnlink(keys: string[]): command_request.Command {
    return createCommand(RequestType.Unlink, keys);
}

export enum ExpireOptions {
    /**
     * `HasNoExpiry` - Sets expiry only when the key has no expiry.
     */
    HasNoExpiry = "NX",
    /**
     * `HasExistingExpiry` - Sets expiry only when the key has an existing expiry.
     */
    HasExistingExpiry = "XX",
    /**
     * `NewExpiryGreaterThanCurrent` - Sets expiry only when the new expiry is
     * greater than current one.
     */
    NewExpiryGreaterThanCurrent = "GT",
    /**
     * `NewExpiryLessThanCurrent` - Sets expiry only when the new expiry is less
     * than current one.
     */
    NewExpiryLessThanCurrent = "LT",
}

/**
 * @internal
 */
export function createExpire(
    key: string,
    seconds: number,
    option?: ExpireOptions,
): command_request.Command {
    const args: string[] =
        option == undefined
            ? [key, seconds.toString()]
            : [key, seconds.toString(), option];
    return createCommand(RequestType.Expire, args);
}

/**
 * @internal
 */
export function createExpireAt(
    key: string,
    unixSeconds: number,
    option?: ExpireOptions,
): command_request.Command {
    const args: string[] =
        option == undefined
            ? [key, unixSeconds.toString()]
            : [key, unixSeconds.toString(), option];
    return createCommand(RequestType.ExpireAt, args);
}

/**
 * @internal
 */
export function createPExpire(
    key: string,
    milliseconds: number,
    option?: ExpireOptions,
): command_request.Command {
    const args: string[] =
        option == undefined
            ? [key, milliseconds.toString()]
            : [key, milliseconds.toString(), option];
    return createCommand(RequestType.PExpire, args);
}

/**
 * @internal
 */
export function createPExpireAt(
    key: string,
    unixMilliseconds: number,
    option?: ExpireOptions,
): command_request.Command {
    const args: string[] =
        option == undefined
            ? [key, unixMilliseconds.toString()]
            : [key, unixMilliseconds.toString(), option];
    return createCommand(RequestType.PExpireAt, args);
}

/**
 * @internal
 */
export function createTTL(key: string): command_request.Command {
    return createCommand(RequestType.TTL, [key]);
}

/**
 * Options for updating elements of a sorted set key.
 */
export enum UpdateByScore {
    /** Only update existing elements if the new score is less than the current score. */
    LESS_THAN = "LT",
    /** Only update existing elements if the new score is greater than the current score. */
    GREATER_THAN = "GT",
}

export type ZAddOptions = {
    /**
     * Options for handling existing members.
     */
    conditionalChange?: ConditionalChange;
    /**
     * Options for updating scores.
     */
    updateOptions?: UpdateByScore;
    /**
     * Modify the return value from the number of new elements added, to the total number of elements changed.
     */
    changed?: boolean;
};

/**
 * @internal
 */
export function createZAdd(
    key: string,
    membersScoresMap: Record<string, number>,
    options?: ZAddOptions,
    incr: boolean = false,
): command_request.Command {
    let args = [key];

    if (options) {
        if (options.conditionalChange) {
            if (
                options.conditionalChange ===
                    ConditionalChange.ONLY_IF_DOES_NOT_EXIST &&
                options.updateOptions
            ) {
                throw new Error(
                    `The GT, LT, and NX options are mutually exclusive. Cannot choose both ${options.updateOptions} and NX.`,
                );
            }

            args.push(options.conditionalChange);
        }

        if (options.updateOptions) {
            args.push(options.updateOptions);
        }

        if (options.changed) {
            args.push("CH");
        }
    }

    if (incr) {
        args.push("INCR");
    }

    args = args.concat(
        Object.entries(membersScoresMap).flatMap(([key, value]) => [
            value.toString(),
            key,
        ]),
    );
    return createCommand(RequestType.ZAdd, args);
}

/**
 * `KeyWeight` - pair of variables represents a weighted key for the `ZINTERSTORE` and `ZUNIONSTORE` sorted sets commands.
 */
export type KeyWeight = [string, number];
/**
 * `AggregationType` - representing aggregation types for `ZINTERSTORE` and `ZUNIONSTORE` sorted set commands.
 */
export type AggregationType = "SUM" | "MIN" | "MAX";

/**
 * @internal
 */
export function createZInterstore(
    destination: string,
    keys: string[] | KeyWeight[],
    aggregationType?: AggregationType,
): command_request.Command {
    const args = createZCmdStoreArgs(destination, keys, aggregationType);
    return createCommand(RequestType.ZInterStore, args);
}

function createZCmdStoreArgs(
    destination: string,
    keys: string[] | KeyWeight[],
    aggregationType?: AggregationType,
): string[] {
    const args: string[] = [destination, keys.length.toString()];

    if (typeof keys[0] === "string") {
        args.push(...(keys as string[]));
    } else {
        const weightsKeys = keys.map(([key]) => key);
        args.push(...(weightsKeys as string[]));
        const weights = keys.map(([, weight]) => weight.toString());
        args.push("WEIGHTS", ...weights);
    }

    if (aggregationType) {
        args.push("AGGREGATE", aggregationType);
    }

    return args;
}

/**
 * @internal
 */
export function createZRem(
    key: string,
    members: string[],
): command_request.Command {
    return createCommand(RequestType.ZRem, [key].concat(members));
}

/**
 * @internal
 */
export function createZCard(key: string): command_request.Command {
    return createCommand(RequestType.ZCard, [key]);
}

/**
 * @internal
 */
export function createZInterCard(
    keys: string[],
    limit?: number,
): command_request.Command {
    let args: string[] = keys;
    args.unshift(keys.length.toString());

    if (limit != undefined) {
        args = args.concat(["LIMIT", limit.toString()]);
    }

    return createCommand(RequestType.ZInterCard, args);
}

/**
 * @internal
 */
export function createZDiff(keys: string[]): command_request.Command {
    const args: string[] = keys;
    args.unshift(keys.length.toString());
    return createCommand(RequestType.ZDiff, args);
}

/**
 * @internal
 */
export function createZDiffWithScores(keys: string[]): command_request.Command {
    const args: string[] = keys;
    args.unshift(keys.length.toString());
    args.push("WITHSCORES");
    return createCommand(RequestType.ZDiff, args);
}

/**
 * @internal
 */
export function createZDiffStore(
    destination: string,
    keys: string[],
): command_request.Command {
    const args: string[] = [destination, keys.length.toString(), ...keys];
    return createCommand(RequestType.ZDiffStore, args);
}

/**
 * @internal
 */
export function createZScore(
    key: string,
    member: string,
): command_request.Command {
    return createCommand(RequestType.ZScore, [key, member]);
}

/**
 * @internal
 */
export function createZMScore(
    key: string,
    members: string[],
): command_request.Command {
    return createCommand(RequestType.ZMScore, [key, ...members]);
}

export type ScoreBoundary<T> =
    /**
     * Positive infinity bound for sorted set.
     */
    | `positiveInfinity`
    /**
     * Negative infinity bound for sorted set.
     */
    | `negativeInfinity`
    /**
     *  Represents a specific numeric score boundary in a sorted set.
     */
    | {
          /**
           * The score value.
           */
          value: T;
          /**
           * Whether the score value is inclusive. Defaults to True.
           */
          isInclusive?: boolean;
      };

/**
 * Represents a range by index (rank) in a sorted set.
 * The `start` and `stop` arguments represent zero-based indexes.
 */
export type RangeByIndex = {
    /**
     *  The start index of the range.
     */
    start: number;
    /**
     * The stop index of the range.
     */
    stop: number;
};

/**
 * Represents a range by score or a range by lex in a sorted set.
 * The `start` and `stop` arguments represent score boundaries.
 */
type SortedSetRange<T> = {
    /**
     * The start boundary.
     */
    start: ScoreBoundary<T>;
    /**
     * The stop boundary.
     */
    stop: ScoreBoundary<T>;
    /**
     * The limit argument for a range query.
     * Represents a limit argument for a range query in a sorted set to
     * be used in [ZRANGE](https://valkey.io/commands/zrange) command.
     *
     * The optional LIMIT argument can be used to obtain a sub-range from the
     * matching elements (similar to SELECT LIMIT offset, count in SQL).
     */
    limit?: {
        /**
         * The offset from the start of the range.
         */
        offset: number;
        /**
         * The number of elements to include in the range.
         * A negative count returns all elements from the offset.
         */
        count: number;
    };
};

export type RangeByScore = SortedSetRange<number> & { type: "byScore" };
export type RangeByLex = SortedSetRange<string> & { type: "byLex" };

/**
 * Returns a string representation of a score boundary in Redis protocol format.
 * @param score - The score boundary object containing value and inclusivity
 *     information.
 * @param isLex - Indicates whether to return lexical representation for
 *     positive/negative infinity.
 * @returns A string representation of the score boundary in Redis protocol
 *     format.
 */
function getScoreBoundaryArg(
    score: ScoreBoundary<number> | ScoreBoundary<string>,
    isLex: boolean = false,
): string {
    if (score == "positiveInfinity") {
        return isLex ? "+" : "+inf";
    } else if (score == "negativeInfinity") {
        return isLex ? "-" : "-inf";
    }

    if (score.isInclusive == false) {
        return "(" + score.value.toString();
    }

    const value = isLex ? "[" + score.value.toString() : score.value.toString();
    return value;
}

function createZRangeArgs(
    key: string,
    rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
    reverse: boolean,
    withScores: boolean,
): string[] {
    const args: string[] = [key];

    if (typeof rangeQuery.start != "number") {
        rangeQuery = rangeQuery as RangeByScore | RangeByLex;
        const isLex = rangeQuery.type == "byLex";
        args.push(getScoreBoundaryArg(rangeQuery.start, isLex));
        args.push(getScoreBoundaryArg(rangeQuery.stop, isLex));
        args.push(isLex == true ? "BYLEX" : "BYSCORE");
    } else {
        args.push(rangeQuery.start.toString());
        args.push(rangeQuery.stop.toString());
    }

    if (reverse) {
        args.push("REV");
    }

    if ("limit" in rangeQuery && rangeQuery.limit !== undefined) {
        args.push(
            "LIMIT",
            String(rangeQuery.limit.offset),
            String(rangeQuery.limit.count),
        );
    }

    if (withScores) {
        args.push("WITHSCORES");
    }

    return args;
}

/**
 * @internal
 */
export function createZCount(
    key: string,
    minScore: ScoreBoundary<number>,
    maxScore: ScoreBoundary<number>,
): command_request.Command {
    const args = [key];
    args.push(getScoreBoundaryArg(minScore));
    args.push(getScoreBoundaryArg(maxScore));
    return createCommand(RequestType.ZCount, args);
}

/**
 * @internal
 */
export function createZRange(
    key: string,
    rangeQuery: RangeByIndex | RangeByScore | RangeByLex,
    reverse: boolean = false,
): command_request.Command {
    const args = createZRangeArgs(key, rangeQuery, reverse, false);
    return createCommand(RequestType.ZRange, args);
}

/**
 * @internal
 */
export function createZRangeWithScores(
    key: string,
    rangeQuery: RangeByIndex | RangeByScore | RangeByLex,
    reverse: boolean = false,
): command_request.Command {
    const args = createZRangeArgs(key, rangeQuery, reverse, true);
    return createCommand(RequestType.ZRange, args);
}

/**
 * @internal
 */
export function createType(key: string): command_request.Command {
    return createCommand(RequestType.Type, [key]);
}

/**
 * @internal
 */
export function createStrlen(key: string): command_request.Command {
    return createCommand(RequestType.Strlen, [key]);
}

/**
 * @internal
 */
export function createLIndex(
    key: string,
    index: number,
): command_request.Command {
    return createCommand(RequestType.LIndex, [key, index.toString()]);
}

/**
 * Defines where to insert new elements into a list.
 */
export enum InsertPosition {
    /**
     * Insert new element before the pivot.
     */
    Before = "before",
    /**
     * Insert new element after the pivot.
     */
    After = "after",
}

/**
 * @internal
 */
export function createLInsert(
    key: string,
    position: InsertPosition,
    pivot: string,
    element: string,
): command_request.Command {
    return createCommand(RequestType.LInsert, [key, position, pivot, element]);
}

/**
 * @internal
 */
export function createZPopMin(
    key: string,
    count?: number,
): command_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.ZPopMin, args);
}

/**
 * @internal
 */
export function createZPopMax(
    key: string,
    count?: number,
): command_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.ZPopMax, args);
}

/**
 * @internal
 */
export function createEcho(message: string): command_request.Command {
    return createCommand(RequestType.Echo, [message]);
}

/**
 * @internal
 */
export function createPTTL(key: string): command_request.Command {
    return createCommand(RequestType.PTTL, [key]);
}

/**
 * @internal
 */
export function createZRemRangeByRank(
    key: string,
    start: number,
    stop: number,
): command_request.Command {
    return createCommand(RequestType.ZRemRangeByRank, [
        key,
        start.toString(),
        stop.toString(),
    ]);
}

/**
 * @internal
 */
export function createZRemRangeByScore(
    key: string,
    minScore: ScoreBoundary<number>,
    maxScore: ScoreBoundary<number>,
): command_request.Command {
    const args = [key];
    args.push(getScoreBoundaryArg(minScore));
    args.push(getScoreBoundaryArg(maxScore));
    return createCommand(RequestType.ZRemRangeByScore, args);
}

export function createPersist(key: string): command_request.Command {
    return createCommand(RequestType.Persist, [key]);
}

export function createZRank(
    key: string,
    member: string,
    withScores?: boolean,
): command_request.Command {
    const args = [key, member];

    if (withScores) {
        args.push("WITHSCORE");
    }

    return createCommand(RequestType.ZRank, args);
}

export type StreamTrimOptions = (
    | {
          /**
           * Trim the stream according to entry ID.
           * Equivalent to `MINID` in the Redis API.
           */
          method: "minid";
          threshold: string;
      }
    | {
          /**
           * Trim the stream according to length.
           * Equivalent to `MAXLEN` in the Redis API.
           */
          method: "maxlen";
          threshold: number;
      }
) & {
    /**
     * If `true`, the stream will be trimmed exactly. Equivalent to `=` in the
     * Redis API. Otherwise the stream will be trimmed in a near-exact manner,
     * which is more efficient, equivalent to `~` in the Redis API.
     */
    exact: boolean;
    /**
     * If set, sets the maximal amount of entries that will be deleted.
     */
    limit?: number;
};

export type StreamAddOptions = {
    /**
     * If set, the new entry will be added with this ID.
     */
    id?: string;
    /**
     * If set to `false`, a new stream won't be created if no stream matches the
     * given key. Equivalent to `NOMKSTREAM` in the Redis API.
     */
    makeStream?: boolean;
    /**
     * If set, the add operation will also trim the older entries in the stream.
     */
    trim?: StreamTrimOptions;
};

function addTrimOptions(options: StreamTrimOptions, args: string[]) {
    if (options.method === "maxlen") {
        args.push("MAXLEN");
    } else if (options.method === "minid") {
        args.push("MINID");
    }

    if (options.exact) {
        args.push("=");
    } else {
        args.push("~");
    }

    if (options.method === "maxlen") {
        args.push(options.threshold.toString());
    } else if (options.method === "minid") {
        args.push(options.threshold);
    }

    if (options.limit) {
        args.push("LIMIT");
        args.push(options.limit.toString());
    }
}

export function createXAdd(
    key: string,
    values: [string, string][],
    options?: StreamAddOptions,
): command_request.Command {
    const args = [key];

    if (options?.makeStream === false) {
        args.push("NOMKSTREAM");
    }

    if (options?.trim) {
        addTrimOptions(options.trim, args);
    }

    if (options?.id) {
        args.push(options.id);
    } else {
        args.push("*");
    }

    values.forEach(([field, value]) => {
        args.push(field);
        args.push(value);
    });

    return createCommand(RequestType.XAdd, args);
}

/**
 * @internal
 */
export function createXTrim(
    key: string,
    options: StreamTrimOptions,
): command_request.Command {
    const args = [key];
    addTrimOptions(options, args);
    return createCommand(RequestType.XTrim, args);
}

/**
 * @internal
 */
export function createTime(): command_request.Command {
    return createCommand(RequestType.Time, []);
}

/**
 * @internal
 */
export function createPublish(
    message: string,
    channel: string,
    sharded: boolean = false,
): command_request.Command {
    const request = sharded ? RequestType.SPublish : RequestType.Publish;
    return createCommand(request, [channel, message]);
}

/**
 * @internal
 */
export function createBRPop(
    keys: string[],
    timeout: number,
): command_request.Command {
    const args = [...keys, timeout.toString()];
    return createCommand(RequestType.BRPop, args);
}

/**
 * @internal
 */
export function createBLPop(
    keys: string[],
    timeout: number,
): command_request.Command {
    const args = [...keys, timeout.toString()];
    return createCommand(RequestType.BLPop, args);
}

/**
 * @internal
 */
export function createFCall(
    func: string,
    keys: string[],
    args: string[],
): command_request.Command {
    let params: string[] = [];
    params = params.concat(func, keys.length.toString(), keys, args);
    return createCommand(RequestType.FCall, params);
}

/**
 * @internal
 */
export function createFCallReadOnly(
    func: string,
    keys: string[],
    args: string[],
): command_request.Command {
    let params: string[] = [];
    params = params.concat(func, keys.length.toString(), keys, args);
    return createCommand(RequestType.FCallReadOnly, params);
}

/**
 * @internal
 */
export function createFunctionDelete(
    libraryCode: string,
): command_request.Command {
    return createCommand(RequestType.FunctionDelete, [libraryCode]);
}

/**
 * @internal
 */
export function createFunctionFlush(mode?: FlushMode): command_request.Command {
    if (mode) {
        return createCommand(RequestType.FunctionFlush, [mode.toString()]);
    } else {
        return createCommand(RequestType.FunctionFlush, []);
    }
}

/**
 * @internal
 */
export function createFunctionLoad(
    libraryCode: string,
    replace?: boolean,
): command_request.Command {
    const args = replace ? ["REPLACE", libraryCode] : [libraryCode];
    return createCommand(RequestType.FunctionLoad, args);
}

/**
 * Represents offsets specifying a string interval to analyze in the {@link BaseClient.bitcount|bitcount} command. The offsets are
 * zero-based indexes, with `0` being the first index of the string, `1` being the next index and so on.
 * The offsets can also be negative numbers indicating offsets starting at the end of the string, with `-1` being
 * the last index of the string, `-2` being the penultimate, and so on.
 *
 * See https://valkey.io/commands/bitcount/ for more details.
 */
export type BitOffsetOptions = {
    /** The starting offset index. */
    start: number;
    /** The ending offset index. */
    end: number;
    /**
     * The index offset type. This option can only be specified if you are using server version 7.0.0 or above.
     * Could be either {@link BitmapIndexType.BYTE} or {@link BitmapIndexType.BIT}.
     * If no index type is provided, the indexes will be assumed to be byte indexes.
     */
    indexType?: BitmapIndexType;
};

/**
 * @internal
 */
export function createBitCount(
    key: string,
    options?: BitOffsetOptions,
): command_request.Command {
    const args = [key];

    if (options) {
        args.push(options.start.toString());
        args.push(options.end.toString());
        if (options.indexType) args.push(options.indexType);
    }

    return createCommand(RequestType.BitCount, args);
}

/**
 * Enumeration specifying if index arguments are BYTE indexes or BIT indexes.
 * Can be specified in {@link BitOffsetOptions}, which is an optional argument to the {@link BaseClient.bitcount|bitcount} command.
 * Can also be specified as an optional argument to the {@link BaseClient.bitposInverval|bitposInterval} command.
 *
 * since - Valkey version 7.0.0.
 */
export enum BitmapIndexType {
    /** Specifies that provided indexes are byte indexes. */
    BYTE = "BYTE",
    /** Specifies that provided indexes are bit indexes. */
    BIT = "BIT",
}

/**
 * @internal
 */
export function createBitPos(
    key: string,
    bit: number,
    start?: number,
    end?: number,
    indexType?: BitmapIndexType,
): command_request.Command {
    const args = [key, bit.toString()];

    if (start !== undefined) {
        args.push(start.toString());
    }

    if (end !== undefined) {
        args.push(end.toString());
    }

    if (indexType) {
        args.push(indexType);
    }

    return createCommand(RequestType.BitPos, args);
}

/**
 * Defines flushing mode for {@link GlideClient.flushall}, {@link GlideClusterClient.flushall},
 *      {@link GlideClient.functionFlush}, {@link GlideClusterClient.functionFlush},
 *      {@link GlideClient.flushdb} and {@link GlideClusterClient.flushdb} commands.
 *
 * See https://valkey.io/commands/flushall/ and https://valkey.io/commands/flushdb/ for details.
 */
export enum FlushMode {
    /**
     * Flushes synchronously.
     *
     * since Valkey version 6.2.0.
     */
    SYNC = "SYNC",
    /** Flushes asynchronously. */
    ASYNC = "ASYNC",
}

export type StreamReadOptions = {
    /**
     * If set, the read request will block for the set amount of milliseconds or
     * until the server has the required number of entries. Equivalent to `BLOCK`
     * in the Redis API.
     */
    block?: number;
    /**
     * The maximal number of elements requested.
     * Equivalent to `COUNT` in the Redis API.
     */
    count?: number;
};

function addReadOptions(options: StreamReadOptions, args: string[]) {
    if (options.count !== undefined) {
        args.push("COUNT");
        args.push(options.count.toString());
    }

    if (options.block !== undefined) {
        args.push("BLOCK");
        args.push(options.block.toString());
    }
}

function addStreamsArgs(keys_and_ids: Record<string, string>, args: string[]) {
    args.push("STREAMS");

    const pairs = Object.entries(keys_and_ids);

    for (const [key] of pairs) {
        args.push(key);
    }

    for (const [, id] of pairs) {
        args.push(id);
    }
}

/**
 * @internal
 */
export function createXRead(
    keys_and_ids: Record<string, string>,
    options?: StreamReadOptions,
): command_request.Command {
    const args: string[] = [];

    if (options) {
        addReadOptions(options, args);
    }

    addStreamsArgs(keys_and_ids, args);

    return createCommand(RequestType.XRead, args);
}

/**
 * @internal
 */
export function createXLen(key: string): command_request.Command {
    return createCommand(RequestType.XLen, [key]);
}

/**
 * @internal
 */
export function createRename(
    key: string,
    newKey: string,
): command_request.Command {
    return createCommand(RequestType.Rename, [key, newKey]);
}

/**
 * @internal
 */
export function createRenameNX(
    key: string,
    newKey: string,
): command_request.Command {
    return createCommand(RequestType.RenameNX, [key, newKey]);
}

/**
 * @internal
 */
export function createPfAdd(
    key: string,
    elements: string[],
): command_request.Command {
    const args = [key, ...elements];
    return createCommand(RequestType.PfAdd, args);
}

/**
 * @internal
 */
export function createPfCount(keys: string[]): command_request.Command {
    return createCommand(RequestType.PfCount, keys);
}

/**
 * @internal
 */
export function createObjectEncoding(key: string): command_request.Command {
    return createCommand(RequestType.ObjectEncoding, [key]);
}

/**
 * @internal
 */
export function createObjectFreq(key: string): command_request.Command {
    return createCommand(RequestType.ObjectFreq, [key]);
}

/**
 * @internal
 */
export function createObjectIdletime(key: string): command_request.Command {
    return createCommand(RequestType.ObjectIdleTime, [key]);
}

/**
 * @internal
 */
export function createObjectRefcount(key: string): command_request.Command {
    return createCommand(RequestType.ObjectRefCount, [key]);
}

export type LolwutOptions = {
    /**
     * An optional argument that can be used to specify the version of computer art to generate.
     */
    version?: number;
    /**
     * An optional argument that can be used to specify the output:
     *  For version `5`, those are length of the line, number of squares per row, and number of squares per column.
     *  For version `6`, those are number of columns and number of lines.
     */
    parameters?: number[];
};

/**
 * @internal
 */
export function createLolwut(options?: LolwutOptions): command_request.Command {
    const args: string[] = [];

    if (options) {
        if (options.version !== undefined) {
            args.push("VERSION", options.version.toString());
        }

        if (options.parameters !== undefined) {
            args.push(...options.parameters.map((param) => param.toString()));
        }
    }

    return createCommand(RequestType.Lolwut, args);
}

/**
 * @internal
 */
export function createFlushAll(mode?: FlushMode): command_request.Command {
    if (mode) {
        return createCommand(RequestType.FlushAll, [mode.toString()]);
    } else {
        return createCommand(RequestType.FlushAll, []);
    }
}

/**
 * @internal
 */
export function createFlushDB(mode?: FlushMode): command_request.Command {
    if (mode) {
        return createCommand(RequestType.FlushDB, [mode.toString()]);
    } else {
        return createCommand(RequestType.FlushDB, []);
    }
}

/**
 * Optional arguments to LPOS command.
 *
 * See https://valkey.io/commands/lpos/ for more details.
 */
export type LPosOptions = {
    /** The rank of the match to return. */
    rank?: number;
    /** The specific number of matching indices from a list. */
    count?: number;
    /** The maximum number of comparisons to make between the element and the items in the list. */
    maxLength?: number;
};

/**
 * @internal
 */
export function createLPos(
    key: string,
    element: string,
    options?: LPosOptions,
): command_request.Command {
    const args: string[] = [key, element];

    if (options) {
        if (options.rank !== undefined) {
            args.push("RANK");
            args.push(options.rank.toString());
        }

        if (options.count !== undefined) {
            args.push("COUNT");
            args.push(options.count.toString());
        }

        if (options.maxLength !== undefined) {
            args.push("MAXLEN");
            args.push(options.maxLength.toString());
        }
    }

    return createCommand(RequestType.LPos, args);
}

/**
 * @internal
 */
export function createDBSize(): command_request.Command {
    return createCommand(RequestType.DBSize, []);
}

/**
 * An optional condition to the {@link BaseClient.geoadd} command.
 */
export enum ConditionalChange {
    /**
     * Only update elements that already exist. Don't add new elements. Equivalent to `XX` in the Valkey API.
     */
    ONLY_IF_EXISTS = "XX",

    /**
     * Only add new elements. Don't update already existing elements. Equivalent to `NX` in the Valkey API.
     */
    ONLY_IF_DOES_NOT_EXIST = "NX",
}

/**
 * Represents a geographic position defined by longitude and latitude.
 * The exact limits, as specified by `EPSG:900913 / EPSG:3785 / OSGEO:41001` are the
 * following:
 *
 *   Valid longitudes are from `-180` to `180` degrees.
 *   Valid latitudes are from `-85.05112878` to `85.05112878` degrees.
 */
export type GeospatialData = {
    /** The longitude coordinate. */
    longitude: number;
    /** The latitude coordinate. */
    latitude: number;
};

/**
 * Optional arguments for the GeoAdd command.
 *
 * See https://valkey.io/commands/geoadd/ for more details.
 */
export type GeoAddOptions = {
    /** Options for handling existing members. See {@link ConditionalChange}. */
    updateMode?: ConditionalChange;
    /** If `true`, returns the count of changed elements instead of new elements added. */
    changed?: boolean;
};

/**
 * @internal
 */
export function createGeoAdd(
    key: string,
    membersToGeospatialData: Map<string, GeospatialData>,
    options?: GeoAddOptions,
): command_request.Command {
    let args: string[] = [key];

    if (options) {
        if (options.updateMode) {
            args.push(options.updateMode);
        }

        if (options.changed) {
            args.push("CH");
        }
    }

    membersToGeospatialData.forEach((coord, member) => {
        args = args.concat(
            coord.longitude.toString(),
            coord.latitude.toString(),
            member,
        );
    });
    return createCommand(RequestType.GeoAdd, args);
}

/** Enumeration representing distance units options. */
export enum GeoUnit {
    /** Represents distance in meters. */
    METERS = "m",
    /** Represents distance in kilometers. */
    KILOMETERS = "km",
    /** Represents distance in miles. */
    MILES = "mi",
    /** Represents distance in feet. */
    FEET = "ft",
}

/**
 * @internal
 */
export function createGeoPos(
    key: string,
    members: string[],
): command_request.Command {
    return createCommand(RequestType.GeoPos, [key].concat(members));
}

/**
 * @internal
 */
export function createGeoDist(
    key: string,
    member1: string,
    member2: string,
    geoUnit?: GeoUnit,
): command_request.Command {
    const args: string[] = [key, member1, member2];

    if (geoUnit) {
        args.push(geoUnit);
    }

    return createCommand(RequestType.GeoDist, args);
}

/**
 * @internal
 */
export function createGeoHash(
    key: string,
    members: string[],
): command_request.Command {
    const args: string[] = [key].concat(members);
    return createCommand(RequestType.GeoHash, args);
}

/**
 * Optional parameters for {@link BaseClient.geosearch|geosearch} command which defines what should be included in the
 * search results and how results should be ordered and limited.
 */
export type GeoSearchResultOptions = {
    /** Include the coordinate of the returned items. */
    withCoord?: boolean;
    /**
     * Include the distance of the returned items from the specified center point.
     * The distance is returned in the same unit as specified for the `searchBy` argument.
     */
    withDist?: boolean;
    /** Include the geohash of the returned items. */
    withHash?: boolean;
    /** Indicates the order the result should be sorted in. */
    sortOrder?: SortOrder;
    /** Indicates the number of matches the result should be limited to. */
    count?: number;
    /** Whether to allow returning as enough matches are found. This requires `count` parameter to be set. */
    isAny?: boolean;
};

/** Defines the sort order for nested results. */
export enum SortOrder {
    /** Sort by ascending order. */
    ASC = "ASC",
    /** Sort by descending order. */
    DESC = "DESC",
}

export type GeoSearchShape = GeoCircleShape | GeoBoxShape;

/** Circle search shape defined by the radius value and measurement unit. */
export type GeoCircleShape = {
    /** The radius to search by. */
    radius: number;
    /** The measurement unit of the radius. */
    unit: GeoUnit;
};

/** Rectangle search shape defined by the width and height and measurement unit. */
export type GeoBoxShape = {
    /** The width of the rectangle to search by. */
    width: number;
    /** The height of the rectangle to search by. */
    height: number;
    /** The measurement unit of the width and height. */
    unit: GeoUnit;
};

export type SearchOrigin = CoordOrigin | MemberOrigin;

/** The search origin represented by a {@link GeospatialData} position. */
export type CoordOrigin = {
    /** The pivot location to search from. */
    position: GeospatialData;
};

/** The search origin represented by an existing member. */
export type MemberOrigin = {
    /** Member (location) name stored in the sorted set to use as a search pivot. */
    member: string;
};

/**
 * @internal
 */
export function createGeoSearch(
    key: string,
    searchFrom: SearchOrigin,
    searchBy: GeoSearchShape,
    resultOptions?: GeoSearchResultOptions,
): command_request.Command {
    let args: string[] = [key];

    if ("position" in searchFrom) {
        args = args.concat(
            "FROMLONLAT",
            searchFrom.position.longitude.toString(),
            searchFrom.position.latitude.toString(),
        );
    } else {
        args = args.concat("FROMMEMBER", searchFrom.member);
    }

    if ("radius" in searchBy) {
        args = args.concat(
            "BYRADIUS",
            searchBy.radius.toString(),
            searchBy.unit,
        );
    } else {
        args = args.concat(
            "BYBOX",
            searchBy.width.toString(),
            searchBy.height.toString(),
            searchBy.unit,
        );
    }

    if (resultOptions) {
        if (resultOptions.withCoord) args.push("WITHCOORD");
        if (resultOptions.withDist) args.push("WITHDIST");
        if (resultOptions.withHash) args.push("WITHHASH");

        if (resultOptions.count) {
            args.push("COUNT", resultOptions.count?.toString());

            if (resultOptions.isAny) args.push("ANY");
        }

        if (resultOptions.sortOrder) args.push(resultOptions.sortOrder);
    }

    return createCommand(RequestType.GeoSearch, args);
}

/**
 * @internal
 */
export function createZRevRank(
    key: string,
    member: string,
): command_request.Command {
    return createCommand(RequestType.ZRevRank, [key, member]);
}

/**
 * @internal
 */
export function createZRevRankWithScore(
    key: string,
    member: string,
): command_request.Command {
    return createCommand(RequestType.ZRevRank, [key, member, "WITHSCORE"]);
}

/**
 * Mandatory option for zmpop.
 * Defines which elements to pop from the sorted set.
 */
export enum ScoreFilter {
    /** Pop elements with the highest scores. */
    MAX = "MAX",
    /** Pop elements with the lowest scores. */
    MIN = "MIN",
}

/**
 * @internal
 */
export function createZMPop(
    keys: string[],
    modifier: ScoreFilter,
    count?: number,
): command_request.Command {
    const args: string[] = [keys.length.toString()].concat(keys);
    args.push(modifier);

    if (count !== undefined) {
        args.push("COUNT");
        args.push(count.toString());
    }

    return createCommand(RequestType.ZMPop, args);
}

/**
 * @internal
 */
export function createBZMPop(
    keys: string[],
    modifier: ScoreFilter,
    timeout: number,
    count?: number,
): command_request.Command {
    const args: string[] = [
        timeout.toString(),
        keys.length.toString(),
        ...keys,
        modifier,
    ];

    if (count !== undefined) {
        args.push("COUNT");
        args.push(count.toString());
    }

    return createCommand(RequestType.BZMPop, args);
}

/**
 * @internal
 */
export function createZIncrBy(
    key: string,
    increment: number,
    member: string,
): command_request.Command {
    return createCommand(RequestType.ZIncrBy, [
        key,
        increment.toString(),
        member,
    ]);
}

/**
 * @internal
 */
export function createHStrlen(
    key: string,
    field: string,
): command_request.Command {
    return createCommand(RequestType.HStrlen, [key, field]);
}
