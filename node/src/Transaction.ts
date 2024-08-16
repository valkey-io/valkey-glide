/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    BaseClient, // eslint-disable-line @typescript-eslint/no-unused-vars
    GlideString,
    ReadFrom, // eslint-disable-line @typescript-eslint/no-unused-vars
} from "./BaseClient";

import {
    AggregationType,
    BaseScanOptions,
    BitFieldGet,
    BitFieldIncrBy, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldOverflow, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldSet, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitFieldSubCommands,
    BitOffset, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitOffsetMultiplier, // eslint-disable-line @typescript-eslint/no-unused-vars
    BitOffsetOptions,
    BitmapIndexType,
    BitwiseOperation,
    Boundary,
    CoordOrigin, // eslint-disable-line @typescript-eslint/no-unused-vars
    ExpireOptions,
    FlushMode,
    FunctionListOptions,
    FunctionListResponse, // eslint-disable-line @typescript-eslint/no-unused-vars
    FunctionStatsResponse, // eslint-disable-line @typescript-eslint/no-unused-vars
    GeoAddOptions,
    GeoBoxShape, // eslint-disable-line @typescript-eslint/no-unused-vars
    GeoCircleShape, // eslint-disable-line @typescript-eslint/no-unused-vars
    GeoSearchResultOptions,
    GeoSearchShape,
    GeoSearchStoreResultOptions,
    GeoUnit,
    GeospatialData,
    InfoOptions,
    InsertPosition,
    KeyWeight,
    LPosOptions,
    ListDirection,
    LolwutOptions,
    MemberOrigin, // eslint-disable-line @typescript-eslint/no-unused-vars
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ReturnTypeXinfoStream, // eslint-disable-line @typescript-eslint/no-unused-vars
    ScoreFilter,
    SearchOrigin,
    SetOptions,
    SortClusterOptions,
    SortOptions,
    StreamAddOptions,
    StreamClaimOptions,
    StreamGroupOptions,
    StreamPendingOptions,
    StreamReadOptions,
    StreamTrimOptions,
    TimeUnit,
    ZAddOptions,
    createAppend,
    createBLMPop,
    createBLMove,
    createBLPop,
    createBRPop,
    createBZMPop,
    createBZPopMax,
    createBZPopMin,
    createBitCount,
    createBitField,
    createBitOp,
    createBitPos,
    createClientGetName,
    createClientId,
    createConfigGet,
    createConfigResetStat,
    createConfigRewrite,
    createConfigSet,
    createCopy,
    createCustomCommand,
    createDBSize,
    createDecr,
    createDecrBy,
    createDel,
    createEcho,
    createExists,
    createExpire,
    createExpireAt,
    createExpireTime,
    createFCall,
    createFCallReadOnly,
    createFlushAll,
    createFlushDB,
    createFunctionDelete,
    createFunctionFlush,
    createFunctionList,
    createFunctionLoad,
    createFunctionStats,
    createGeoAdd,
    createGeoDist,
    createGeoHash,
    createGeoPos,
    createGeoSearch,
    createGeoSearchStore,
    createGet,
    createGetBit,
    createGetDel,
    createGetEx,
    createGetRange,
    createHDel,
    createHExists,
    createHGet,
    createHGetAll,
    createHIncrBy,
    createHIncrByFloat,
    createHKeys,
    createHLen,
    createHMGet,
    createHRandField,
    createHScan,
    createHSet,
    createHSetNX,
    createHStrlen,
    createHVals,
    createIncr,
    createIncrBy,
    createIncrByFloat,
    createInfo,
    createLCS,
    createLIndex,
    createLInsert,
    createLLen,
    createLMPop,
    createLMove,
    createLPop,
    createLPos,
    createLPush,
    createLPushX,
    createLRange,
    createLRem,
    createLSet,
    createLTrim,
    createLastSave,
    createLolwut,
    createMGet,
    createMSet,
    createMSetNX,
    createMove,
    createObjectEncoding,
    createObjectFreq,
    createObjectIdletime,
    createObjectRefcount,
    createPExpire,
    createPExpireAt,
    createPExpireTime,
    createPTTL,
    createPersist,
    createPfAdd,
    createPfCount,
    createPfMerge,
    createPing,
    createPubSubChannels,
    createPubSubNumPat,
    createPubSubNumSub,
    createPubSubShardNumSub,
    createPublish,
    createPubsubShardChannels,
    createRPop,
    createRPush,
    createRPushX,
    createRandomKey,
    createRename,
    createRenameNX,
    createSAdd,
    createSCard,
    createSDiff,
    createSDiffStore,
    createSInter,
    createSInterCard,
    createSInterStore,
    createSIsMember,
    createSMIsMember,
    createSMembers,
    createSMove,
    createSPop,
    createSRandMember,
    createSRem,
    createSScan,
    createSUnion,
    createSUnionStore,
    createSelect,
    createSet,
    createSetBit,
    createSetRange,
    createSort,
    createSortReadOnly,
    createStrlen,
    createTTL,
    createTime,
    createTouch,
    createType,
    createUnlink,
    createWait,
    createXAdd,
    createXAutoClaim,
    createXClaim,
    createXDel,
    createXGroupCreate,
    createXGroupCreateConsumer,
    createXGroupDelConsumer,
    createXGroupDestroy,
    createXInfoConsumers,
    createXInfoGroups,
    createXInfoStream,
    createXLen,
    createXPending,
    createXRange,
    createXRead,
    createXTrim,
    createZAdd,
    createZCard,
    createZCount,
    createZDiff,
    createZDiffStore,
    createZDiffWithScores,
    createZIncrBy,
    createZInterCard,
    createZInterstore,
    createZLexCount,
    createZMPop,
    createZMScore,
    createZPopMax,
    createZPopMin,
    createZRandMember,
    createZRange,
    createZRangeStore,
    createZRangeWithScores,
    createZRank,
    createZRem,
    createZRemRangeByLex,
    createZRemRangeByRank,
    createZRemRangeByScore,
    createZRevRank,
    createZRevRankWithScore,
    createZScan,
    createZScore,
} from "./Commands";
import { command_request } from "./ProtobufMessage";

/**
 * Base class encompassing shared commands for both standalone and cluster mode implementations in a transaction.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the client exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 * @example
 * ```typescript
 * const transaction = new BaseTransaction()
 *    .set("key", "value")
 *    .get("key");
 * const result = await client.exec(transaction);
 * console.log(result); // Output: ['OK', 'value']
 * ```
 */
export class BaseTransaction<T extends BaseTransaction<T>> {
    /**
     * @internal
     */
    readonly commands: command_request.Command[] = [];
    /**
     * Array of command indexes indicating commands that need to be converted into a `Set` within the transaction.
     * @internal
     */
    readonly setCommandsIndexes: number[] = [];

    /**
     * Adds a command to the transaction and returns the transaction instance.
     * @param command - The command to add.
     * @param shouldConvertToSet - Indicates if the command should be converted to a `Set`.
     * @returns The updated transaction instance.
     */
    protected addAndReturn(
        command: command_request.Command,
        shouldConvertToSet: boolean = false,
    ): T {
        if (shouldConvertToSet) {
            // The command's index within the transaction is saved for later conversion of its response to a Set type.
            this.setCommandsIndexes.push(this.commands.length);
        }

        this.commands.push(command);
        return this as unknown as T;
    }

    /** Get the value associated with the given key, or null if no such value exists.
     * @see {@link https://valkey.io/commands/get/|valkey.io} for details.
     *
     * @param key - The key to retrieve from the database.
     *
     * Command Response - If `key` exists, returns the value of `key`. Otherwise, return null.
     */
    public get(key: GlideString): T {
        return this.addAndReturn(createGet(key));
    }

    /**
     * Get the value of `key` and optionally set its expiration. `GETEX` is similar to {@link get}.
     *
     * @see {@link https://valkey.io/commands/getex/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key to retrieve from the database.
     * @param options - (Optional) set expiriation to the given key.
     *                  "persist" will retain the time to live associated with the key. Equivalent to `PERSIST` in the VALKEY API.
     *                  Otherwise, a {@link TimeUnit} and duration of the expire time should be specified.
     *
     * Command Response - If `key` exists, returns the value of `key` as a `string`. Otherwise, return `null`.
     */
    public getex(
        key: string,
        options?: "persist" | { type: TimeUnit; duration: number },
    ): T {
        return this.addAndReturn(createGetEx(key, options));
    }

    /**
     * Gets a string value associated with the given `key`and deletes the key.
     *
     * @see {@link https://valkey.io/commands/getdel/|valkey.io} for details.
     *
     * @param key - The key to retrieve from the database.
     *
     * Command Response - If `key` exists, returns the `value` of `key`. Otherwise, return `null`.
     */
    public getdel(key: GlideString): T {
        return this.addAndReturn(createGetDel(key));
    }

    /**
     * Returns the substring of the string value stored at `key`, determined by the offsets
     * `start` and `end` (both are inclusive). Negative offsets can be used in order to provide
     * an offset starting from the end of the string. So `-1` means the last character, `-2` the
     * penultimate and so forth. If `key` does not exist, an empty string is returned. If `start`
     * or `end` are out of range, returns the substring within the valid range of the string.
     *
     * @see {@link https://valkey.io/commands/getrange/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param start - The starting offset.
     * @param end - The ending offset.
     *
     * Command Response - substring extracted from the value stored at `key`.
     */
    public getrange(key: string, start: number, end: number): T {
        return this.addAndReturn(createGetRange(key, start, end));
    }

    /** Set the given key with the given value. Return value is dependent on the passed options.
     * @see {@link https://valkey.io/commands/set/|valkey.io} for details.
     *
     * @param key - The key to store.
     * @param value - The value to store with the given key.
     * @param options - The set options.
     *
     * Command Response - If the value is successfully set, return OK.
     * If `value` isn't set because of `onlyIfExists` or `onlyIfDoesNotExist` conditions, return null.
     * If `returnOldValue` is set, return the old value as a string.
     */
    public set(key: string, value: string, options?: SetOptions): T {
        return this.addAndReturn(createSet(key, value, options));
    }

    /** Ping the Redis server.
     * @see {@link https://valkey.io/commands/ping/|valkey.io} for details.
     *
     * @param message - An optional message to include in the PING command.
     * If not provided, the server will respond with "PONG".
     * If provided, the server will respond with a copy of the message.
     *
     * Command Response - "PONG" if `message` is not provided, otherwise return a copy of `message`.
     */
    public ping(message?: GlideString): T {
        return this.addAndReturn(createPing(message));
    }

    /** Get information and statistics about the Redis server.
     * @see {@link https://valkey.io/commands/info/|valkey.io} for details.
     *
     * @param options - A list of InfoSection values specifying which sections of information to retrieve.
     * When no parameter is provided, the default option is assumed.
     *
     * Command Response - a string containing the information for the sections requested.
     */
    public info(options?: InfoOptions[]): T {
        return this.addAndReturn(createInfo(options));
    }

    /** Remove the specified keys. A key is ignored if it does not exist.
     * @see {@link https://valkey.io/commands/del/|valkey.io} for details.
     *
     * @param keys - A list of keys to be deleted from the database.
     *
     * Command Response - the number of keys that were removed.
     */
    public del(keys: string[]): T {
        return this.addAndReturn(createDel(keys));
    }

    /** Get the name of the connection on which the transaction is being executed.
     * @see {@link https://valkey.io/commands/client-getname/|valkey.io} for details.
     *
     * Command Response - the name of the client connection as a string if a name is set, or null if no name is assigned.
     */
    public clientGetName(): T {
        return this.addAndReturn(createClientGetName());
    }

    /** Rewrite the configuration file with the current configuration.
     * @see {@link https://valkey.io/commands/select/|valkey.io} for details.
     *
     * Command Response - "OK" when the configuration was rewritten properly. Otherwise, the transaction fails with an error.
     */
    public configRewrite(): T {
        return this.addAndReturn(createConfigRewrite());
    }

    /** Resets the statistics reported by Redis using the INFO and LATENCY HISTOGRAM commands.
     * @see {@link https://valkey.io/commands/config-resetstat/|valkey.io} for details.
     *
     * Command Response - always "OK".
     */
    public configResetStat(): T {
        return this.addAndReturn(createConfigResetStat());
    }

    /** Retrieve the values of multiple keys.
     * @see {@link https://valkey.io/commands/mget/|valkey.io} for details.
     *
     * @param keys - A list of keys to retrieve values for.
     *
     * Command Response - A list of values corresponding to the provided keys. If a key is not found,
     * its corresponding value in the list will be null.
     */
    public mget(keys: string[]): T {
        return this.addAndReturn(createMGet(keys));
    }

    /** Set multiple keys to multiple values in a single atomic operation.
     * @see {@link https://valkey.io/commands/mset/|valkey.io} for details.
     *
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     *
     * Command Response - always "OK".
     */
    public mset(keyValueMap: Record<string, string>): T {
        return this.addAndReturn(createMSet(keyValueMap));
    }

    /**
     * Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or
     * more keys already exist, the entire operation fails.
     *
     * @see {@link https://valkey.io/commands/msetnx/|valkey.io} for details.
     *
     * @param keyValueMap - A key-value map consisting of keys and their respective values to set.
     * Command Response - `true` if all keys were set. `false` if no key was set.
     */
    public msetnx(keyValueMap: Record<string, string>): T {
        return this.addAndReturn(createMSetNX(keyValueMap));
    }

    /** Increments the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * @see {@link https://valkey.io/commands/incr/|valkey.io} for details.
     *
     * @param key - The key to increment its value.
     *
     * Command Response - the value of `key` after the increment.
     */
    public incr(key: string): T {
        return this.addAndReturn(createIncr(key));
    }

    /** Increments the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * @see {@link https://valkey.io/commands/incrby/|valkey.io} for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     *
     * Command Response - the value of `key` after the increment.
     */
    public incrBy(key: string, amount: number): T {
        return this.addAndReturn(createIncrBy(key, amount));
    }

    /** Increment the string representing a floating point number stored at `key` by `amount`.
     * By using a negative amount value, the result is that the value stored at `key` is decremented.
     * If `key` does not exist, it is set to 0 before performing the operation.
     * @see {@link https://valkey.io/commands/incrbyfloat/|valkey.io} for details.
     *
     * @param key - The key to increment its value.
     * @param amount - The amount to increment.
     *
     * Command Response - the value of `key` after the increment.
     *
     */
    public incrByFloat(key: string, amount: number): T {
        return this.addAndReturn(createIncrByFloat(key, amount));
    }

    /** Returns the current connection id.
     * @see {@link https://valkey.io/commands/client-id/|valkey.io} for details.
     *
     * Command Response - the id of the client.
     */
    public clientId(): T {
        return this.addAndReturn(createClientId());
    }

    /** Decrements the number stored at `key` by one. If `key` does not exist, it is set to 0 before performing the operation.
     * @see {@link https://valkey.io/commands/decr/|valkey.io} for details.
     *
     * @param key - The key to decrement its value.
     *
     * Command Response - the value of `key` after the decrement.
     */
    public decr(key: string): T {
        return this.addAndReturn(createDecr(key));
    }

    /** Decrements the number stored at `key` by `amount`. If `key` does not exist, it is set to 0 before performing the operation.
     * @see {@link https://valkey.io/commands/decrby/|valkey.io} for details.
     *
     * @param key - The key to decrement its value.
     * @param amount - The amount to decrement.
     *
     * Command Response - the value of `key` after the decrement.
     */
    public decrBy(key: string, amount: number): T {
        return this.addAndReturn(createDecrBy(key, amount));
    }

    /**
     * Perform a bitwise operation between multiple keys (containing string values) and store the result in the
     * `destination`.
     *
     * @see {@link https://valkey.io/commands/bitop/|valkey.io} for details.
     *
     * @param operation - The bitwise operation to perform.
     * @param destination - The key that will store the resulting string.
     * @param keys - The list of keys to perform the bitwise operation on.
     *
     * Command Response - The size of the string stored in `destination`.
     */
    public bitop(
        operation: BitwiseOperation,
        destination: string,
        keys: string[],
    ): T {
        return this.addAndReturn(createBitOp(operation, destination, keys));
    }

    /**
     * Returns the bit value at `offset` in the string value stored at `key`. `offset` must be greater than or equal
     * to zero.
     *
     * @see {@link https://valkey.io/commands/getbit/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param offset - The index of the bit to return.
     *
     * Command Response - The bit at the given `offset` of the string. Returns `0` if the key is empty or if the
     * `offset` exceeds the length of the string.
     */
    public getbit(key: string, offset: number): T {
        return this.addAndReturn(createGetBit(key, offset));
    }

    /**
     * Sets or clears the bit at `offset` in the string value stored at `key`. The `offset` is a zero-based index, with
     * `0` being the first element of the list, `1` being the next element, and so on. The `offset` must be less than
     * `2^32` and greater than or equal to `0`. If a key is non-existent then the bit at `offset` is set to `value` and
     * the preceding bits are set to `0`.
     *
     * @see {@link https://valkey.io/commands/setbit/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param offset - The index of the bit to be set.
     * @param value - The bit value to set at `offset`. The value must be `0` or `1`.
     *
     * Command Response - The bit value that was previously stored at `offset`.
     */
    public setbit(key: string, offset: number, value: number): T {
        return this.addAndReturn(createSetBit(key, offset, value));
    }

    /**
     * Returns the position of the first bit matching the given `bit` value. The optional starting offset
     * `start` is a zero-based index, with `0` being the first byte of the list, `1` being the next byte and so on.
     * The offset can also be a negative number indicating an offset starting at the end of the list, with `-1` being
     * the last byte of the list, `-2` being the penultimate, and so on.
     *
     * @see {@link https://valkey.io/commands/bitpos/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param bit - The bit value to match. Must be `0` or `1`.
     * @param start - (Optional) The starting offset. If not supplied, the search will start at the beginning of the string.
     *
     * Command Response - The position of the first occurrence of `bit` in the binary value of the string held at `key`.
     *      If `start` was provided, the search begins at the offset indicated by `start`.
     */
    public bitpos(key: string, bit: number, start?: number): T {
        return this.addAndReturn(createBitPos(key, bit, start));
    }

    /**
     * Returns the position of the first bit matching the given `bit` value. The offsets are zero-based indexes, with
     * `0` being the first element of the list, `1` being the next, and so on. These offsets can also be negative
     * numbers indicating offsets starting at the end of the list, with `-1` being the last element of the list, `-2`
     * being the penultimate, and so on.
     *
     * If you are using Valkey 7.0.0 or above, the optional `indexType` can also be provided to specify whether the
     * `start` and `end` offsets specify BIT or BYTE offsets. If `indexType` is not provided, BYTE offsets
     * are assumed. If BIT is specified, `start=0` and `end=2` means to look at the first three bits. If BYTE is
     * specified, `start=0` and `end=2` means to look at the first three bytes.
     *
     * @see {@link https://valkey.io/commands/bitpos/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param bit - The bit value to match. Must be `0` or `1`.
     * @param start - The starting offset.
     * @param end - The ending offset.
     * @param indexType - (Optional) The index offset type. This option can only be specified if you are using Valkey
     *      version 7.0.0 or above. Could be either {@link BitmapIndexType.BYTE} or {@link BitmapIndexType.BIT}. If no
     *      index type is provided, the indexes will be assumed to be byte indexes.
     *
     * Command Response - The position of the first occurrence from the `start` to the `end` offsets of the `bit` in the
     *      binary value of the string held at `key`.
     */
    public bitposInterval(
        key: string,
        bit: number,
        start: number,
        end: number,
        indexType?: BitmapIndexType,
    ): T {
        return this.addAndReturn(createBitPos(key, bit, start, end, indexType));
    }

    /**
     * Reads or modifies the array of bits representing the string that is held at `key` based on the specified
     * `subcommands`.
     *
     * @see {@link https://valkey.io/commands/bitfield/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param subcommands - The subcommands to be performed on the binary value of the string at `key`, which could be
     *      any of the following:
     *
     * - {@link BitFieldGet}
     * - {@link BitFieldSet}
     * - {@link BitFieldIncrBy}
     * - {@link BitFieldOverflow}
     *
     * Command Response - An array of results from the executed subcommands:
     *
     * - {@link BitFieldGet} returns the value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldSet} returns the old value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldIncrBy} returns the new value in {@link BitOffset} or {@link BitOffsetMultiplier}.
     * - {@link BitFieldOverflow} determines the behavior of the {@link BitFieldSet} and {@link BitFieldIncrBy}
     *   subcommands when an overflow or underflow occurs. {@link BitFieldOverflow} does not return a value and
     *   does not contribute a value to the array response.
     */
    public bitfield(key: string, subcommands: BitFieldSubCommands[]): T {
        return this.addAndReturn(createBitField(key, subcommands));
    }

    /**
     * Reads the array of bits representing the string that is held at `key` based on the specified `subcommands`.
     *
     * @see {@link https://valkey.io/commands/bitfield_ro/|valkey.io} for details.
     * @remarks Since Valkey version 6.0.0.
     *
     * @param key - The key of the string.
     * @param subcommands - The {@link BitFieldGet} subcommands to be performed.
     *
     * Command Response - An array of results from the {@link BitFieldGet} subcommands.
     *
     */
    public bitfieldReadOnly(key: string, subcommands: BitFieldGet[]): T {
        return this.addAndReturn(createBitField(key, subcommands, true));
    }

    /** Reads the configuration parameters of a running Redis server.
     * @see {@link https://valkey.io/commands/config-get/|valkey.io} for details.
     *
     * @param parameters - A list of configuration parameter names to retrieve values for.
     *
     * Command Response - A map of values corresponding to the configuration parameters.
     *
     */
    public configGet(parameters: string[]): T {
        return this.addAndReturn(createConfigGet(parameters));
    }

    /** Set configuration parameters to the specified values.
     * @see {@link https://valkey.io/commands/config-set/|valkey.io} for details.
     *
     * @param parameters - A List of keyValuePairs consisting of configuration parameters and their respective values to set.
     *
     * Command Response - "OK" when the configuration was set properly. Otherwise, the transaction fails with an error.
     */
    public configSet(parameters: Record<string, string>): T {
        return this.addAndReturn(createConfigSet(parameters));
    }

    /** Retrieve the value associated with `field` in the hash stored at `key`.
     * @see {@link https://valkey.io/commands/hget/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash stored at `key` to retrieve from the database.
     *
     * Command Response - the value associated with `field`, or null when `field` is not present in the hash or `key` does not exist.
     */
    public hget(key: string, field: string): T {
        return this.addAndReturn(createHGet(key, field));
    }

    /** Sets the specified fields to their respective values in the hash stored at `key`.
     * @see {@link https://valkey.io/commands/hset/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fieldValueMap - A field-value map consisting of fields and their corresponding values
     * to be set in the hash stored at the specified key.
     *
     * Command Response - The number of fields that were added.
     */
    public hset(key: string, fieldValueMap: Record<string, string>): T {
        return this.addAndReturn(createHSet(key, fieldValueMap));
    }

    /**
     * Returns all field names in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hkeys/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - A list of field names for the hash, or an empty list when the key does not exist.
     */
    public hkeys(key: string): T {
        return this.addAndReturn(createHKeys(key));
    }

    /** Sets `field` in the hash stored at `key` to `value`, only if `field` does not yet exist.
     * If `key` does not exist, a new key holding a hash is created.
     * If `field` already exists, this operation has no effect.
     * @see {@link https://valkey.io/commands/hsetnx/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param field - The field to set the value for.
     * @param value - The value to set.
     *
     * Command Response - `true` if the field was set, `false` if the field already existed and was not set.
     */
    public hsetnx(key: string, field: string, value: string): T {
        return this.addAndReturn(createHSetNX(key, field, value));
    }

    /** Removes the specified fields from the hash stored at `key`.
     * Specified fields that do not exist within this hash are ignored.
     * @see {@link https://valkey.io/commands/hdel/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields to remove from the hash stored at `key`.
     *
     * Command Response - the number of fields that were removed from the hash, not including specified but non existing fields.
     * If `key` does not exist, it is treated as an empty hash and it returns 0.
     */
    public hdel(key: string, fields: string[]): T {
        return this.addAndReturn(createHDel(key, fields));
    }

    /** Returns the values associated with the specified fields in the hash stored at `key`.
     * @see {@link https://valkey.io/commands/hmget/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param fields - The fields in the hash stored at `key` to retrieve from the database.
     *
     * Command Response - a list of values associated with the given fields, in the same order as they are requested.
     * For every field that does not exist in the hash, a null value is returned.
     * If `key` does not exist, it is treated as an empty hash and it returns a list of null values.
     */
    public hmget(key: string, fields: string[]): T {
        return this.addAndReturn(createHMGet(key, fields));
    }

    /** Returns if `field` is an existing field in the hash stored at `key`.
     * @see {@link https://valkey.io/commands/hexists/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param field - The field to check in the hash stored at `key`.
     *
     * Command Response - `true` if the hash contains `field`. If the hash does not contain `field`, or if `key` does not exist,
     * the command response will be `false`.
     */
    public hexists(key: string, field: string): T {
        return this.addAndReturn(createHExists(key, field));
    }

    /** Returns all fields and values of the hash stored at `key`.
     * @see {@link https://valkey.io/commands/hgetall/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - a map of fields and their values stored in the hash. Every field name in the map is followed by its value.
     * If `key` does not exist, it returns an empty map.
     */
    public hgetall(key: string): T {
        return this.addAndReturn(createHGetAll(key));
    }

    /** Increments the number stored at `field` in the hash stored at `key` by `increment`.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * @see {@link https://valkey.io/commands/hincrby/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     *
     * Command Response - the value of `field` in the hash stored at `key` after the increment.
     */
    public hincrBy(key: string, field: string, amount: number): T {
        return this.addAndReturn(createHIncrBy(key, field, amount));
    }

    /** Increment the string representing a floating point number stored at `field` in the hash stored at `key` by `increment`.
     * By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
     * If `field` or `key` does not exist, it is set to 0 before performing the operation.
     * @see {@link https://valkey.io/commands/hincrbyfloat/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param amount - The amount to increment.
     * @param field - The field in the hash stored at `key` to increment its value.
     *
     * Command Response - the value of `field` in the hash stored at `key` after the increment.
     */
    public hincrByFloat(key: string, field: string, amount: number): T {
        return this.addAndReturn(createHIncrByFloat(key, field, amount));
    }

    /** Returns the number of fields contained in the hash stored at `key`.
     * @see {@link https://valkey.io/commands/hlen/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - The number of fields in the hash, or 0 when the key does not exist.
     */
    public hlen(key: string): T {
        return this.addAndReturn(createHLen(key));
    }

    /** Returns all values in the hash stored at key.
     * @see {@link https://valkey.io/commands/hvals/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     *
     * Command Response - a list of values in the hash, or an empty list when the key does not exist.
     */
    public hvals(key: string): T {
        return this.addAndReturn(createHVals(key));
    }

    /**
     * Returns the string length of the value associated with `field` in the hash stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hstrlen/|valkey.io} for details.
     *
     * @param key - The key of the hash.
     * @param field - The field in the hash.
     *
     * Command Response - The string length or `0` if `field` or `key` does not exist.
     */
    public hstrlen(key: string, field: string): T {
        return this.addAndReturn(createHStrlen(key, field));
    }

    /**
     * Returns a random field name from the hash value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hrandfield/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     *
     * Command Response - A random field name from the hash stored at `key`, or `null` when
     *     the key does not exist.
     */
    public hrandfield(key: string): T {
        return this.addAndReturn(createHRandField(key));
    }

    /**
     * Iterates incrementally over a hash.
     *
     * @see {@link https://valkey.io/commands/hscan/|valkey.io} for more details.
     *
     * @param key - The key of the set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of the search.
     * @param options - (Optional) The {@link BaseScanOptions}.
     *
     * Command Response -  An array of the `cursor` and the subset of the hash held by `key`.
     * The first element is always the `cursor` for the next iteration of results. `"0"` will be the `cursor`
     * returned on the last iteration of the hash. The second element is always an array of the subset of the
     * hash held in `key`. The array in the second element is always a flattened series of string pairs,
     * where the value is at even indices and the value is at odd indices.
     */
    public hscan(key: string, cursor: string, options?: BaseScanOptions): T {
        return this.addAndReturn(createHScan(key, cursor, options));
    }

    /**
     * Retrieves up to `count` random field names from the hash value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hrandfield/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @param count - The number of field names to return.
     *
     *     If `count` is positive, returns unique elements. If negative, allows for duplicates.
     *
     * Command Response - An `array` of random field names from the hash stored at `key`,
     *     or an `empty array` when the key does not exist.
     */
    public hrandfieldCount(key: string, count: number): T {
        return this.addAndReturn(createHRandField(key, count));
    }

    /**
     * Retrieves up to `count` random field names along with their values from the hash
     * value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/hrandfield/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the hash.
     * @param count - The number of field names to return.
     *
     *     If `count` is positive, returns unique elements. If negative, allows for duplicates.
     *
     * Command Response - A 2D `array` of `[fieldName, value]` `arrays`, where `fieldName` is a random
     *     field name from the hash and `value` is the associated value of the field name.
     *     If the hash does not exist or is empty, the response will be an empty `array`.
     */
    public hrandfieldWithValues(key: string, count: number): T {
        return this.addAndReturn(createHRandField(key, count, true));
    }

    /** Inserts all the specified values at the head of the list stored at `key`.
     * `elements` are inserted one after the other to the head of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * @see {@link https://valkey.io/commands/lpush/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     *
     * Command Response - the length of the list after the push operations.
     */
    public lpush(key: string, elements: string[]): T {
        return this.addAndReturn(createLPush(key, elements));
    }

    /**
     * Inserts specified values at the head of the `list`, only if `key` already
     * exists and holds a list.
     *
     * @see {@link https://valkey.io/commands/lpushx/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the head of the list stored at `key`.
     *
     * Command Response - The length of the list after the push operation.
     */
    public lpushx(key: string, elements: string[]): T {
        return this.addAndReturn(createLPushX(key, elements));
    }

    /** Removes and returns the first elements of the list stored at `key`.
     * The command pops a single element from the beginning of the list.
     * @see {@link https://valkey.io/commands/lpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     *
     * Command Response - The value of the first element.
     * If `key` does not exist null will be returned.
     */
    public lpop(key: string): T {
        return this.addAndReturn(createLPop(key));
    }

    /** Removes and returns up to `count` elements of the list stored at `key`, depending on the list's length.
     * @see {@link https://valkey.io/commands/lpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     *
     * Command Response - A list of the popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     */
    public lpopCount(key: string, count: number): T {
        return this.addAndReturn(createLPop(key, count));
    }

    /** Returns the specified elements of the list stored at `key`.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * @see {@link https://valkey.io/commands/lrange/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     *
     * Command Response - list of elements in the specified range.
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, an empty list will be returned.
     * If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
     * If `key` does not exist an empty list will be returned.
     */
    public lrange(key: string, start: number, end: number): T {
        return this.addAndReturn(createLRange(key, start, end));
    }

    /** Returns the length of the list stored at `key`.
     * @see {@link https://valkey.io/commands/llen/|valkey.io} for details.
     *
     * @param key - The key of the list.
     *
     * Command Response - the length of the list at `key`.
     * If `key` does not exist, it is interpreted as an empty list and 0 is returned.
     */
    public llen(key: string): T {
        return this.addAndReturn(createLLen(key));
    }

    /**
     * Atomically pops and removes the left/right-most element to the list stored at `source`
     * depending on `whereFrom`, and pushes the element at the first/last element of the list
     * stored at `destination` depending on `whereTo`, see {@link ListDirection}.
     *
     * @see {@link https://valkey.io/commands/lmove/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source list.
     * @param destination - The key to the destination list.
     * @param whereFrom - The {@link ListDirection} to remove the element from.
     * @param whereTo - The {@link ListDirection} to add the element to.
     *
     * Command Response - The popped element, or `null` if `source` does not exist.
     */
    public lmove(
        source: string,
        destination: string,
        whereFrom: ListDirection,
        whereTo: ListDirection,
    ): T {
        return this.addAndReturn(
            createLMove(source, destination, whereFrom, whereTo),
        );
    }

    /**
     *
     * Blocks the connection until it pops atomically and removes the left/right-most element to the
     * list stored at `source` depending on `whereFrom`, and pushes the element at the first/last element
     * of the list stored at `destination` depending on `whereTo`.
     * `BLMOVE` is the blocking variant of {@link lmove}.
     *
     * @see {@link https://valkey.io/commands/blmove/|valkey.io} for details.
     * @remarks When in cluster mode, both `source` and `destination` must map to the same hash slot.
     * @remarks `BLMOVE` is a client blocking command, see {@link https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands|Valkey Glide Wiki} for more details and best practices.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source list.
     * @param destination - The key to the destination list.
     * @param whereFrom - The {@link ListDirection} to remove the element from.
     * @param whereTo - The {@link ListDirection} to add the element to.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
     *
     * Command Response - The popped element, or `null` if `source` does not exist or if the operation timed-out.
     */
    public blmove(
        source: string,
        destination: string,
        whereFrom: ListDirection,
        whereTo: ListDirection,
        timeout: number,
    ): T {
        return this.addAndReturn(
            createBLMove(source, destination, whereFrom, whereTo, timeout),
        );
    }

    /**
     * Sets the list element at `index` to `element`.
     * The index is zero-based, so `0` means the first element, `1` the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of
     * the list. Here, `-1` means the last element, `-2` means the penultimate and so forth.
     *
     * @see {@link https://valkey.io/commands/lset/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param index - The index of the element in the list to be set.
     * @param element - The new element to set at the specified index.
     *
     * Command Response - Always "OK".
     */
    public lset(key: string, index: number, element: string): T {
        return this.addAndReturn(createLSet(key, index, element));
    }

    /** Trim an existing list so that it will contain only the specified range of elements specified.
     * The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next element and so on.
     * These offsets can also be negative numbers indicating offsets starting at the end of the list,
     * with -1 being the last element of the list, -2 being the penultimate, and so on.
     * @see {@link https://valkey.io/commands/ltrim/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     *
     * Command Response - always "OK".
     * If `start` exceeds the end of the list, or if `start` is greater than `end`, the result will be an empty list (which causes key to be removed).
     * If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
     * If `key` does not exist the command will be ignored.
     */
    public ltrim(key: string, start: number, end: number): T {
        return this.addAndReturn(createLTrim(key, start, end));
    }

    /** Removes the first `count` occurrences of elements equal to `element` from the list stored at `key`.
     * If `count` is positive : Removes elements equal to `element` moving from head to tail.
     * If `count` is negative : Removes elements equal to `element` moving from tail to head.
     * If `count` is 0 or `count` is greater than the occurrences of elements equal to `element`: Removes all elements equal to `element`.
     *
     * @param key - The key of the list.
     * @param count - The count of the occurrences of elements equal to `element` to remove.
     * @param element - The element to remove from the list.
     *
     * Command Response - the number of the removed elements.
     * If `key` does not exist, 0 is returned.
     */
    public lrem(key: string, count: number, element: string): T {
        return this.addAndReturn(createLRem(key, count, element));
    }

    /** Inserts all the specified values at the tail of the list stored at `key`.
     * `elements` are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
     * If `key` does not exist, it is created as empty list before performing the push operations.
     * @see {@link https://valkey.io/commands/rpush/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     *
     * Command Response - the length of the list after the push operations.
     */
    public rpush(key: string, elements: string[]): T {
        return this.addAndReturn(createRPush(key, elements));
    }

    /**
     * Inserts specified values at the tail of the `list`, only if `key` already
     * exists and holds a list.
     *
     * @see {@link https://valkey.io/commands/rpushx/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param elements - The elements to insert at the tail of the list stored at `key`.
     *
     * Command Response - The length of the list after the push operation.
     */
    public rpushx(key: string, elements: string[]): T {
        return this.addAndReturn(createRPushX(key, elements));
    }

    /** Removes and returns the last elements of the list stored at `key`.
     * The command pops a single element from the end of the list.
     * @see {@link https://valkey.io/commands/rpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     *
     * Command Response - The value of the last element.
     * If `key` does not exist null will be returned.
     */
    public rpop(key: string): T {
        return this.addAndReturn(createRPop(key));
    }

    /** Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
     * @see {@link https://valkey.io/commands/rpop/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param count - The count of the elements to pop from the list.
     *
     * Command Response - A list of popped elements will be returned depending on the list's length.
     * If `key` does not exist null will be returned.
     */
    public rpopCount(key: string, count: number): T {
        return this.addAndReturn(createRPop(key, count));
    }

    /** Adds the specified members to the set stored at `key`. Specified members that are already a member of this set are ignored.
     * If `key` does not exist, a new set is created before adding `members`.
     * @see {@link https://valkey.io/commands/sadd/|valkey.io} for details.
     *
     * @param key - The key to store the members to its set.
     * @param members - A list of members to add to the set stored at `key`.
     *
     * Command Response - the number of members that were added to the set, not including all the members already present in the set.
     */
    public sadd(key: string, members: string[]): T {
        return this.addAndReturn(createSAdd(key, members));
    }

    /** Removes the specified members from the set stored at `key`. Specified members that are not a member of this set are ignored.
     * @see {@link https://valkey.io/commands/srem/|valkey.io} for details.
     *
     * @param key - The key to remove the members from its set.
     * @param members - A list of members to remove from the set stored at `key`.
     *
     * Command Response - the number of members that were removed from the set, not including non existing members.
     * If `key` does not exist, it is treated as an empty set and this command returns 0.
     */
    public srem(key: string, members: string[]): T {
        return this.addAndReturn(createSRem(key, members));
    }

    /**
     * Iterates incrementally over a set.
     *
     * @see {@link https://valkey.io/commands/sscan} for details.
     *
     * @param key - The key of the set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of the search.
     * @param options - The (Optional) {@link BaseScanOptions}.
     *
     * Command Response -  An array of the cursor and the subset of the set held by `key`. The first element is always the `cursor` and for the next iteration of results.
     * The `cursor` will be `"0"` on the last iteration of the set. The second element is always an array of the subset of the set held in `key`.
     */
    public sscan(key: string, cursor: string, options?: BaseScanOptions): T {
        return this.addAndReturn(createSScan(key, cursor, options));
    }

    /** Returns all the members of the set value stored at `key`.
     * @see {@link https://valkey.io/commands/smembers/|valkey.io} for details.
     *
     * @param key - The key to return its members.
     *
     * Command Response - all members of the set.
     * If `key` does not exist, it is treated as an empty set and this command returns empty list.
     */
    public smembers(key: string): T {
        return this.addAndReturn(createSMembers(key), true);
    }

    /** Moves `member` from the set at `source` to the set at `destination`, removing it from the source set.
     * Creates a new destination set if needed. The operation is atomic.
     * @see {@link https://valkey.io/commands/smove/|valkey.io} for more details.
     *
     * @param source - The key of the set to remove the element from.
     * @param destination - The key of the set to add the element to.
     * @param member - The set element to move.
     *
     * Command Response - `true` on success, or `false` if the `source` set does not exist or the element is not a member of the source set.
     */
    public smove(source: string, destination: string, member: string): T {
        return this.addAndReturn(createSMove(source, destination, member));
    }

    /** Returns the set cardinality (number of elements) of the set stored at `key`.
     * @see {@link https://valkey.io/commands/scard/|valkey.io} for details.
     *
     * @param key - The key to return the number of its members.
     *
     * Command Response - the cardinality (number of elements) of the set, or 0 if key does not exist.
     */
    public scard(key: string): T {
        return this.addAndReturn(createSCard(key));
    }

    /** Gets the intersection of all the given sets.
     * When in cluster mode, all `keys` must map to the same hash slot.
     * @see {@link https://valkey.io/commands/sinter/|valkey.io} for details.
     *
     * @param keys - The `keys` of the sets to get the intersection.
     *
     * Command Response - A set of members which are present in all given sets.
     * If one or more sets do not exist, an empty set will be returned.
     */
    public sinter(keys: string[]): T {
        return this.addAndReturn(createSInter(keys), true);
    }

    /**
     * Gets the cardinality of the intersection of all the given sets.
     *
     * @see {@link https://valkey.io/commands/sintercard/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sets.
     *
     * Command Response - The cardinality of the intersection result. If one or more sets do not exist, `0` is returned.
     */
    public sintercard(keys: string[], limit?: number): T {
        return this.addAndReturn(createSInterCard(keys, limit));
    }

    /**
     * Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`.
     *
     * @see {@link https://valkey.io/commands/sinterstore/|valkey.io} for details.
     *
     * @param destination - The key of the destination set.
     * @param keys - The keys from which to retrieve the set members.
     *
     * Command Response - The number of elements in the resulting set.
     */
    public sinterstore(destination: string, keys: string[]): T {
        return this.addAndReturn(createSInterStore(destination, keys));
    }

    /**
     * Computes the difference between the first set and all the successive sets in `keys`.
     *
     * @see {@link https://valkey.io/commands/sdiff/|valkey.io} for details.
     *
     * @param keys - The keys of the sets to diff.
     *
     * Command Response - A `Set` of elements representing the difference between the sets.
     * If a key in `keys` does not exist, it is treated as an empty set.
     */
    public sdiff(keys: string[]): T {
        return this.addAndReturn(createSDiff(keys), true);
    }

    /**
     * Stores the difference between the first set and all the successive sets in `keys` into a new set at `destination`.
     *
     * @see {@link https://valkey.io/commands/sdiffstore/|valkey.io} for details.
     *
     * @param destination - The key of the destination set.
     * @param keys - The keys of the sets to diff.
     *
     * Command Response - The number of elements in the resulting set.
     */
    public sdiffstore(destination: string, keys: string[]): T {
        return this.addAndReturn(createSDiffStore(destination, keys));
    }

    /**
     * Gets the union of all the given sets.
     *
     * @see {@link https://valkey.io/commands/sunion/|valkey.io} for details.
     *
     * @param keys - The keys of the sets.
     *
     * Command Response - A `Set` of members which are present in at least one of the given sets.
     * If none of the sets exist, an empty `Set` will be returned.
     */
    public sunion(keys: string[]): T {
        return this.addAndReturn(createSUnion(keys), true);
    }

    /**
     * Stores the members of the union of all given sets specified by `keys` into a new set
     * at `destination`.
     *
     * @see {@link https://valkey.io/commands/sunionstore/|valkey.io} for details.
     *
     * @param destination - The key of the destination set.
     * @param keys - The keys from which to retrieve the set members.
     *
     * Command Response - The number of elements in the resulting set.
     */
    public sunionstore(destination: string, keys: string[]): T {
        return this.addAndReturn(createSUnionStore(destination, keys));
    }

    /** Returns if `member` is a member of the set stored at `key`.
     * @see {@link https://valkey.io/commands/sismember/|valkey.io} for details.
     *
     * @param key - The key of the set.
     * @param member - The member to check for existence in the set.
     *
     * Command Response - `true` if the member exists in the set, `false` otherwise.
     * If `key` doesn't exist, it is treated as an empty set and the command returns `false`.
     */
    public sismember(key: string, member: string): T {
        return this.addAndReturn(createSIsMember(key, member));
    }

    /**
     * Checks whether each member is contained in the members of the set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/smismember/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the set to check.
     * @param members - A list of members to check for existence in the set.
     *
     * Command Response - An `array` of `boolean` values, each indicating if the respective member exists in the set.
     */
    public smismember(key: string, members: string[]): T {
        return this.addAndReturn(createSMIsMember(key, members));
    }

    /** Removes and returns one random member from the set value store at `key`.
     * @see {@link https://valkey.io/commands/spop/|valkey.io} for details.
     * To pop multiple members, see `spopCount`.
     *
     * @param key - The key of the set.
     *
     * Command Response - the value of the popped member.
     * If `key` does not exist, null will be returned.
     */
    public spop(key: string): T {
        return this.addAndReturn(createSPop(key));
    }

    /** Removes and returns up to `count` random members from the set value store at `key`, depending on the set's length.
     * @see {@link https://valkey.io/commands/spop/|valkey.io} for details.
     *
     * @param key - The key of the set.
     * @param count - The count of the elements to pop from the set.
     *
     * Command Response - A list of popped elements will be returned depending on the set's length.
     * If `key` does not exist, empty list will be returned.
     */
    public spopCount(key: string, count: number): T {
        return this.addAndReturn(createSPop(key, count), true);
    }

    /** Returns a random element from the set value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/srandmember/|valkey.io} for more details.
     *
     * @param key - The key from which to retrieve the set member.
     * Command Response - A random element from the set, or null if `key` does not exist.
     */
    public srandmember(key: string): T {
        return this.addAndReturn(createSRandMember(key));
    }

    /** Returns one or more random elements from the set value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/srandmember/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - The number of members to return.
     *                If `count` is positive, returns unique members.
     *                If `count` is negative, allows for duplicates members.
     * Command Response - A list of members from the set. If the set does not exist or is empty, an empty list will be returned.
     */
    public srandmemberCount(key: string, count: number): T {
        return this.addAndReturn(createSRandMember(key, count));
    }

    /** Returns the number of keys in `keys` that exist in the database.
     * @see {@link https://valkey.io/commands/exists/|valkey.io} for details.
     *
     * @param keys - The keys list to check.
     *
     * Command Response - the number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
     * it will be counted multiple times.
     */
    public exists(keys: string[]): T {
        return this.addAndReturn(createExists(keys));
    }

    /** Removes the specified keys. A key is ignored if it does not exist.
     * This command, similar to DEL, removes specified keys and ignores non-existent ones.
     * However, this command does not block the server, while [DEL](https://valkey.io/commands/del) does.
     * @see {@link https://valkey.io/commands/unlink/|valkey.io} for details.
     *
     * @param keys - The keys we wanted to unlink.
     *
     * Command Response - the number of keys that were unlinked.
     */
    public unlink(keys: string[]): T {
        return this.addAndReturn(createUnlink(keys));
    }

    /** Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `seconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * @see {@link https://valkey.io/commands/expire/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param seconds - The timeout in seconds.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public expire(key: string, seconds: number, option?: ExpireOptions): T {
        return this.addAndReturn(createExpire(key, seconds, option));
    }

    /** Sets a timeout on `key`. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of specifying the number of seconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * @see {@link https://valkey.io/commands/expireat/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixSeconds - The timeout in an absolute Unix timestamp.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public expireAt(
        key: string,
        unixSeconds: number,
        option?: ExpireOptions,
    ): T {
        return this.addAndReturn(createExpireAt(key, unixSeconds, option));
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given `key` will expire, in seconds.
     * To get the expiration with millisecond precision, use {@link pexpiretime}.
     *
     * @see {@link https://valkey.io/commands/expiretime/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key - The `key` to determine the expiration value of.
     *
     * Command Response - The expiration Unix timestamp in seconds, `-2` if `key` does not exist or `-1` if `key` exists but has no associated expire.
     */
    public expireTime(key: string): T {
        return this.addAndReturn(createExpireTime(key));
    }

    /** Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * If `milliseconds` is non-positive number, the key will be deleted rather than expired.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * @see {@link https://valkey.io/commands/pexpire/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param milliseconds - The timeout in milliseconds.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public pexpire(
        key: string,
        milliseconds: number,
        option?: ExpireOptions,
    ): T {
        return this.addAndReturn(createPExpire(key, milliseconds, option));
    }

    /** Sets a timeout on `key`. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of specifying the number of milliseconds.
     * A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be deleted.
     * If `key` already has an existing expire set, the time to live is updated to the new value.
     * The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
     * @see {@link https://valkey.io/commands/pexpireat/|valkey.io} for details.
     *
     * @param key - The key to set timeout on it.
     * @param unixMilliseconds - The timeout in an absolute Unix timestamp.
     * @param option - The expire option.
     *
     * Command Response - `true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
     * or operation skipped due to the provided arguments.
     */
    public pexpireAt(
        key: string,
        unixMilliseconds: number,
        option?: ExpireOptions,
    ): T {
        return this.addAndReturn(
            createPExpireAt(key, unixMilliseconds, option),
        );
    }

    /**
     * Returns the absolute Unix timestamp (since January 1, 1970) at which the given `key` will expire, in milliseconds.
     *
     * @see {@link https://valkey.io/commands/pexpiretime/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key - The `key` to determine the expiration value of.
     *
     * Command Response - The expiration Unix timestamp in seconds, `-2` if `key` does not exist or `-1` if `key` exists but has no associated expire.
     */
    public pexpireTime(key: string): T {
        return this.addAndReturn(createPExpireTime(key));
    }

    /** Returns the remaining time to live of `key` that has a timeout.
     * @see {@link https://valkey.io/commands/ttl/|valkey.io} for details.
     *
     * @param key - The key to return its timeout.
     *
     * Command Response -  TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.
     */
    public ttl(key: string): T {
        return this.addAndReturn(createTTL(key));
    }

    /** Adds members with their scores to the sorted set stored at `key`.
     * If a member is already a part of the sorted set, its score is updated.
     * @see {@link https://valkey.io/commands/zadd/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param membersScoresMap - A mapping of members to their corresponding scores.
     * @param options - The ZAdd options.
     *
     * Command Response - The number of elements added to the sorted set.
     * If `changed` is set, returns the number of elements updated in the sorted set.
     */
    public zadd(
        key: string,
        membersScoresMap: Record<string, number>,
        options?: ZAddOptions,
    ): T {
        return this.addAndReturn(createZAdd(key, membersScoresMap, options));
    }

    /** Increments the score of member in the sorted set stored at `key` by `increment`.
     * If `member` does not exist in the sorted set, it is added with `increment` as its score (as if its previous score was 0.0).
     * If `key` does not exist, a new sorted set with the specified member as its sole member is created.
     * @see {@link https://valkey.io/commands/zadd/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param member - A member in the sorted set to increment.
     * @param increment - The score to increment the member.
     * @param options - The ZAdd options.
     *
     * Command Response - The score of the member.
     * If there was a conflict with the options, the operation aborts and null is returned.
     */
    public zaddIncr(
        key: string,
        member: string,
        increment: number,
        options?: ZAddOptions,
    ): T {
        return this.addAndReturn(
            createZAdd(key, { [member]: increment }, options, true),
        );
    }

    /** Removes the specified members from the sorted set stored at `key`.
     * Specified members that are not a member of this set are ignored.
     * @see {@link https://valkey.io/commands/zrem/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param members - A list of members to remove from the sorted set.
     *
     * Command Response - The number of members that were removed from the sorted set, not including non-existing members.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns 0.
     */
    public zrem(key: string, members: string[]): T {
        return this.addAndReturn(createZRem(key, members));
    }

    /** Returns the cardinality (number of elements) of the sorted set stored at `key`.
     * @see {@link https://valkey.io/commands/zcard/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     *
     * Command Response - The number of elements in the sorted set.
     * If `key` does not exist, it is treated as an empty sorted set, and this command returns 0.
     */
    public zcard(key: string): T {
        return this.addAndReturn(createZCard(key));
    }

    /**
     * Returns the cardinality of the intersection of the sorted sets specified by `keys`.
     *
     * @see {@link https://valkey.io/commands/zintercard/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sorted sets to intersect.
     * @param limit - An optional argument that can be used to specify a maximum number for the
     * intersection cardinality. If limit is not supplied, or if it is set to `0`, there will be no limit.
     *
     * Command Response - The cardinality of the intersection of the given sorted sets.
     */
    public zintercard(keys: string[], limit?: number): T {
        return this.addAndReturn(createZInterCard(keys, limit));
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets.
     * To get the elements with their scores, see {@link zdiffWithScores}.
     *
     * @see {@link https://valkey.io/commands/zdiff/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param keys - The keys of the sorted sets.
     *
     * Command Response - An `array` of elements representing the difference between the sorted sets.
     * If the first key does not exist, it is treated as an empty sorted set, and the command returns an empty `array`.
     */
    public zdiff(keys: string[]): T {
        return this.addAndReturn(createZDiff(keys));
    }

    /**
     * Returns the difference between the first sorted set and all the successive sorted sets, with the associated
     * scores.
     *
     * @see {@link https://valkey.io/commands/zdiff/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param keys - The keys of the sorted sets.
     *
     * Command Response - A map of elements and their scores representing the difference between the sorted sets.
     * If the first key does not exist, it is treated as an empty sorted set, and the command returns an empty `array`.
     */
    public zdiffWithScores(keys: string[]): T {
        return this.addAndReturn(createZDiffWithScores(keys));
    }

    /**
     * Calculates the difference between the first sorted set and all the successive sorted sets in `keys` and stores
     * the difference as a sorted set to `destination`, overwriting it if it already exists. Non-existent keys are
     * treated as empty sets.
     *
     * @see {@link https://valkey.io/commands/zdiffstore/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param destination - The key for the resulting sorted set.
     * @param keys - The keys of the sorted sets to compare.
     *
     * Command Response - The number of members in the resulting sorted set stored at `destination`.
     */
    public zdiffstore(destination: string, keys: string[]): T {
        return this.addAndReturn(createZDiffStore(destination, keys));
    }

    /** Returns the score of `member` in the sorted set stored at `key`.
     * @see {@link https://valkey.io/commands/zscore/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose score is to be retrieved.
     *
     * Command Response - The score of the member.
     * If `member` does not exist in the sorted set, null is returned.
     * If `key` does not exist, null is returned.
     */
    public zscore(key: string, member: string): T {
        return this.addAndReturn(createZScore(key, member));
    }

    /**
     * Returns the scores associated with the specified `members` in the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zmscore/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the sorted set.
     * @param members - A list of members in the sorted set.
     *
     * Command Response - An `array` of scores corresponding to `members`.
     * If a member does not exist in the sorted set, the corresponding value in the list will be `null`.
     */
    public zmscore(key: string, members: string[]): T {
        return this.addAndReturn(createZMScore(key, members));
    }

    /** Returns the number of members in the sorted set stored at `key` with scores between `minScore` and `maxScore`.
     * @see {@link https://valkey.io/commands/zcount/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to count from. Can be positive/negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to count up to. Can be positive/negative infinity, or specific score and inclusivity.
     *
     * Command Response - The number of members in the specified score range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minScore` is greater than `maxScore`, 0 is returned.
     */
    public zcount(
        key: string,
        minScore: Boundary<number>,
        maxScore: Boundary<number>,
    ): T {
        return this.addAndReturn(createZCount(key, minScore, maxScore));
    }

    /** Returns the specified range of elements in the sorted set stored at `key`.
     * ZRANGE can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
     *
     * @see {@link https://valkey.io/commands/zrange/|valkey.io} for details.
     * To get the elements with their scores, see `zrangeWithScores`.
     *
     * @param key - The key of the sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * - For range queries by index (rank), use {@link RangeByIndex}.
     * - For range queries by lexicographical order, use {@link RangeByLex}.
     * - For range queries by score, use {@link RangeByScore}.
     * @param reverse - If `true`, reverses the sorted set, with index `0` as the element with the highest score.
     *
     * Command Response - A list of elements within the specified range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty array.
     */
    public zrange(
        key: string,
        rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
        reverse: boolean = false,
    ): T {
        return this.addAndReturn(createZRange(key, rangeQuery, reverse));
    }

    /** Returns the specified range of elements with their scores in the sorted set stored at `key`.
     * Similar to ZRANGE but with a WITHSCORE flag.
     * @see {@link https://valkey.io/commands/zrange/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * - For range queries by index (rank), use {@link RangeByIndex}.
     * - For range queries by lexicographical order, use {@link RangeByLex}.
     * - For range queries by score, use {@link RangeByScore}.
     * @param reverse - If `true`, reverses the sorted set, with index `0` as the element with the highest score.
     *
     * Command Response - A map of elements and their scores within the specified range.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns an empty map.
     */
    public zrangeWithScores(
        key: string,
        rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
        reverse: boolean = false,
    ): T {
        return this.addAndReturn(
            createZRangeWithScores(key, rangeQuery, reverse),
        );
    }

    /**
     * Stores a specified range of elements from the sorted set at `source`, into a new
     * sorted set at `destination`. If `destination` doesn't exist, a new sorted
     * set is created; if it exists, it's overwritten.
     *
     * @see {@link https://valkey.io/commands/zrangestore/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param destination - The key for the destination sorted set.
     * @param source - The key of the source sorted set.
     * @param rangeQuery - The range query object representing the type of range query to perform.
     * - For range queries by index (rank), use {@link RangeByIndex}.
     * - For range queries by lexicographical order, use {@link RangeByLex}.
     * - For range queries by score, use {@link RangeByScore}.
     * @param reverse - If `true`, reverses the sorted set, with index `0` as the element with the highest score.
     *
     * Command Response - The number of elements in the resulting sorted set.
     */
    public zrangeStore(
        destination: string,
        source: string,
        rangeQuery: RangeByScore | RangeByLex | RangeByIndex,
        reverse: boolean = false,
    ): T {
        return this.addAndReturn(
            createZRangeStore(destination, source, rangeQuery, reverse),
        );
    }

    /**
     * Computes the intersection of sorted sets given by the specified `keys` and stores the result in `destination`.
     * If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * When in cluster mode, `destination` and all keys in `keys` must map to the same hash slot.
     *
     * @see {@link https://valkey.io/commands/zinterstore/|valkey.io} for details.
     *
     * @param destination - The key of the destination sorted set.
     * @param keys - The keys of the sorted sets with possible formats:
     *  string[] - for keys only.
     *  KeyWeight[] - for weighted keys with score multipliers.
     * @param aggregationType - Specifies the aggregation strategy to apply when combining the scores of elements. See `AggregationType`.
     * Command Response - The number of elements in the resulting sorted set stored at `destination`.
     */
    public zinterstore(
        destination: string,
        keys: string[] | KeyWeight[],
        aggregationType?: AggregationType,
    ): T {
        return this.addAndReturn(
            createZInterstore(destination, keys, aggregationType),
        );
    }

    /**
     * Returns a random member from the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zrandmember/|valkey.io} for details.
     *
     * @param keys - The key of the sorted set.
     * Command Response - A string representing a random member from the sorted set.
     *     If the sorted set does not exist or is empty, the response will be `null`.
     */
    public zrandmember(key: string): T {
        return this.addAndReturn(createZRandMember(key));
    }

    /**
     * Returns random members from the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zrandmember/|valkey.io} for details.
     *
     * @param keys - The key of the sorted set.
     * @param count - The number of members to return.
     *     If `count` is positive, returns unique members.
     *     If negative, allows for duplicates.
     * Command Response - An `array` of members from the sorted set.
     *     If the sorted set does not exist or is empty, the response will be an empty `array`.
     */
    public zrandmemberWithCount(key: string, count: number): T {
        return this.addAndReturn(createZRandMember(key, count));
    }

    /**
     * Returns random members with scores from the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/zrandmember/|valkey.io} for details.
     *
     * @param keys - The key of the sorted set.
     * @param count - The number of members to return.
     *     If `count` is positive, returns unique members.
     *     If negative, allows for duplicates.
     * Command Response - A 2D `array` of `[member, score]` `arrays`, where
     *     member is a `string` and score is a `number`.
     *     If the sorted set does not exist or is empty, the response will be an empty `array`.
     */
    public zrandmemberWithCountWithScores(key: string, count: number): T {
        return this.addAndReturn(createZRandMember(key, count, true));
    }

    /** Returns the string representation of the type of the value stored at `key`.
     * @see {@link https://valkey.io/commands/type/|valkey.io} for details.
     *
     * @param key - The key to check its data type.
     *
     * Command Response - If the key exists, the type of the stored value is returned. Otherwise, a "none" string is returned.
     */
    public type(key: string): T {
        return this.addAndReturn(createType(key));
    }

    /** Returns the length of the string value stored at `key`.
     * @see {@link https://valkey.io/commands/strlen/|valkey.io} for details.
     *
     * @param key - The `key` to check its length.
     *
     * Command Response - The length of the string value stored at `key`
     * If `key` does not exist, it is treated as an empty string, and the command returns 0.
     */
    public strlen(key: string): T {
        return this.addAndReturn(createStrlen(key));
    }

    /** Removes and returns the members with the lowest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the lowest scores are removed and returned.
     * Otherwise, only one member with the lowest score is removed and returned.
     * @see {@link https://valkey.io/commands/zpopmin/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - Specifies the quantity of members to pop. If not specified, pops one member.
     *
     * Command Response - A map of the removed members and their scores, ordered from the one with the lowest score to the one with the highest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores.
     */
    public zpopmin(key: string, count?: number): T {
        return this.addAndReturn(createZPopMin(key, count));
    }

    /**
     * Blocks the connection until it removes and returns a member with the lowest score from the
     * first non-empty sorted set, with the given `key` being checked in the order they
     * are provided.
     * `BZPOPMIN` is the blocking variant of {@link zpopmin}.
     *
     * @see {@link https://valkey.io/commands/bzpopmin/|valkey.io} for details.
     *
     * @param keys - The keys of the sorted sets.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of
     *     `0` will block indefinitely. Since Valkey version 6.0.0: timeout is interpreted as a double instead of an integer.
     *
     * Command Response - An `array` containing the key where the member was popped out, the member, itself, and the member score.
     *     If no member could be popped and the `timeout` expired, returns `null`.
     */
    public bzpopmin(keys: string[], timeout: number): T {
        return this.addAndReturn(createBZPopMin(keys, timeout));
    }

    /** Removes and returns the members with the highest scores from the sorted set stored at `key`.
     * If `count` is provided, up to `count` members with the highest scores are removed and returned.
     * Otherwise, only one member with the highest score is removed and returned.
     * @see {@link https://valkey.io/commands/zpopmax/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param count - Specifies the quantity of members to pop. If not specified, pops one member.
     *
     * Command Response - A map of the removed members and their scores, ordered from the one with the highest score to the one with the lowest.
     * If `key` doesn't exist, it will be treated as an empty sorted set and the command returns an empty map.
     * If `count` is higher than the sorted set's cardinality, returns all members and their scores, ordered from highest to lowest.
     */
    public zpopmax(key: string, count?: number): T {
        return this.addAndReturn(createZPopMax(key, count));
    }

    /**
     * Blocks the connection until it removes and returns a member with the highest score from the
     * first non-empty sorted set, with the given `key` being checked in the order they
     * are provided.
     * `BZPOPMAX` is the blocking variant of {@link zpopmax}.
     *
     * @see {@link https://valkey.io/commands/bzpopmax/|valkey.io} for details.
     *
     * @param keys - The keys of the sorted sets.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of
     *     `0` will block indefinitely. Since 6.0.0: timeout is interpreted as a double instead of an integer.
     *
     * Command Response - An `array` containing the key where the member was popped out, the member, itself, and the member score.
     *     If no member could be popped and the `timeout` expired, returns `null`.
     */
    public bzpopmax(keys: string[], timeout: number): T {
        return this.addAndReturn(createBZPopMax(keys, timeout));
    }

    /** Echoes the provided `message` back.
     * @see {@link https://valkey.io/commands/echo/|valkey.io} for more details.
     *
     * @param message - The message to be echoed back.
     *
     * Command Response - The provided `message`.
     */
    public echo(message: string): T {
        return this.addAndReturn(createEcho(message));
    }

    /** Returns the remaining time to live of `key` that has a timeout, in milliseconds.
     * @see {@link https://valkey.io/commands/pttl/|valkey.io} for more details.
     *
     * @param key - The key to return its timeout.
     *
     * Command Response - TTL in milliseconds. -2 if `key` does not exist, -1 if `key` exists but has no associated expire.
     */
    public pttl(key: string): T {
        return this.addAndReturn(createPTTL(key));
    }

    /** Removes all elements in the sorted set stored at `key` with rank between `start` and `end`.
     * Both `start` and `end` are zero-based indexes with 0 being the element with the lowest score.
     * These indexes can be negative numbers, where they indicate offsets starting at the element with the highest score.
     * @see {@link https://valkey.io/commands/zremrangebyrank/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param start - The starting point of the range.
     * @param end - The end of the range.
     *
     * Command Response - The number of members removed.
     * If `start` exceeds the end of the sorted set, or if `start` is greater than `end`, 0 returned.
     * If `end` exceeds the actual end of the sorted set, the range will stop at the actual end of the sorted set.
     * If `key` does not exist 0 will be returned.
     */
    public zremRangeByRank(key: string, start: number, end: number): T {
        return this.addAndReturn(createZRemRangeByRank(key, start, end));
    }

    /**
     * Removes all elements in the sorted set stored at `key` with lexicographical order between `minLex` and `maxLex`.
     *
     * @see {@link https://valkey.io/commands/zremrangebylex/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param minLex - The minimum lex to count from. Can be positive/negative infinity, or a specific lex and inclusivity.
     * @param maxLex - The maximum lex to count up to. Can be positive/negative infinity, or a specific lex and inclusivity.
     *
     * Command Response - The number of members removed.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minLex` is greater than `maxLex`, 0 is returned.
     */
    public zremRangeByLex(
        key: string,
        minLex: Boundary<string>,
        maxLex: Boundary<string>,
    ): T {
        return this.addAndReturn(createZRemRangeByLex(key, minLex, maxLex));
    }

    /** Removes all elements in the sorted set stored at `key` with a score between `minScore` and `maxScore`.
     * @see {@link https://valkey.io/commands/zremrangebyscore/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param minScore - The minimum score to remove from. Can be positive/negative infinity, or specific score and inclusivity.
     * @param maxScore - The maximum score to remove to. Can be positive/negative infinity, or specific score and inclusivity.
     *
     * Command Response - the number of members removed.
     * If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
     * If `minScore` is greater than `maxScore`, 0 is returned.
     */
    public zremRangeByScore(
        key: string,
        minScore: Boundary<number>,
        maxScore: Boundary<number>,
    ): T {
        return this.addAndReturn(
            createZRemRangeByScore(key, minScore, maxScore),
        );
    }

    /**
     * Returns the number of members in the sorted set stored at 'key' with scores between 'minLex' and 'maxLex'.
     *
     * @see {@link https://valkey.io/commands/zlexcount/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param minLex - The minimum lex to count from. Can be positive/negative infinity, or a specific lex and inclusivity.
     * @param maxLex - The maximum lex to count up to. Can be positive/negative infinity, or a specific lex and inclusivity.
     *
     * Command Response - The number of members in the specified lex range.
     * If 'key' does not exist, it is treated as an empty sorted set, and the command returns '0'.
     * If maxLex is less than minLex, '0' is returned.
     */
    public zlexcount(
        key: string,
        minLex: Boundary<string>,
        maxLex: Boundary<string>,
    ): T {
        return this.addAndReturn(createZLexCount(key, minLex, maxLex));
    }

    /** Returns the rank of `member` in the sorted set stored at `key`, with scores ordered from low to high.
     * @see {@link https://valkey.io/commands/zrank/|valkey.io} for more details.
     * To get the rank of `member` with its score, see `zrankWithScore`.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     *
     * Command Response - The rank of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     */
    public zrank(key: string, member: string): T {
        return this.addAndReturn(createZRank(key, member));
    }

    /** Returns the rank of `member` in the sorted set stored at `key` with its score, where scores are ordered from the lowest to highest.
     *
     * @see {@link https://valkey.io/commands/zrank/|valkey.io} for more details.
     * @remarks Since Valkey version 7.2.0.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     *
     * Command Response - A list containing the rank and score of `member` in the sorted set.
     * If `key` doesn't exist, or if `member` is not present in the set, null will be returned.
     */
    public zrankWithScore(key: string, member: string): T {
        return this.addAndReturn(createZRank(key, member, true));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key`, where
     * scores are ordered from the highest to lowest, starting from 0.
     * To get the rank of `member` with its score, see {@link zrevrankWithScore}.
     *
     * @see {@link https://valkey.io/commands/zrevrank/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     *
     * Command Response - The rank of `member` in the sorted set, where ranks are ordered from high to low based on scores.
     *     If `key` doesn't exist, or if `member` is not present in the set, `null` will be returned.
     */
    public zrevrank(key: string, member: string): T {
        return this.addAndReturn(createZRevRank(key, member));
    }

    /**
     * Returns the rank of `member` in the sorted set stored at `key` with its
     * score, where scores are ordered from the highest to lowest, starting from 0.
     *
     * @see {@link https://valkey.io/commands/zrevrank/|valkey.io} for details.
     * @remarks Since Valkey version 7.2.0.
     *
     * @param key - The key of the sorted set.
     * @param member - The member whose rank is to be retrieved.
     *
     * Command Response -  A list containing the rank and score of `member` in the sorted set, where ranks
     *     are ordered from high to low based on scores.
     *     If `key` doesn't exist, or if `member` is not present in the set, `null` will be returned.
     */
    public zrevrankWithScore(key: string, member: string): T {
        return this.addAndReturn(createZRevRankWithScore(key, member));
    }

    /** Remove the existing timeout on `key`, turning the key from volatile (a key with an expire set) to
     * persistent (a key that will never expire as no timeout is associated).
     * @see {@link https://valkey.io/commands/persist/|valkey.io} for details.
     *
     * @param key - The key to remove the existing timeout on.
     *
     * Command Response - `false` if `key` does not exist or does not have an associated timeout, `true` if the timeout has been removed.
     */
    public persist(key: string): T {
        return this.addAndReturn(createPersist(key));
    }

    /** Executes a single command, without checking inputs. Every part of the command, including subcommands,
     *  should be added as a separate value in args.
     *
     * @see {@link https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command|Valkey Glide Wiki} for details on the restrictions and limitations of the custom command API.
     *
     * Command Response - A response from Redis with an `Object`.
     */
    public customCommand(args: GlideString[]): T {
        return this.addAndReturn(createCustomCommand(args));
    }

    /** Returns the element at index `index` in the list stored at `key`.
     * The index is zero-based, so 0 means the first element, 1 the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of the list.
     * Here, -1 means the last element, -2 means the penultimate and so forth.
     * @see {@link https://valkey.io/commands/lindex/|valkey.io} for details.
     *
     * @param key - The `key` of the list.
     * @param index - The `index` of the element in the list to retrieve.
     * Command Response - The element at index in the list stored at `key`.
     * If `index` is out of range or if `key` does not exist, null is returned.
     */
    public lindex(key: string, index: number): T {
        return this.addAndReturn(createLIndex(key, index));
    }

    /**
     * Inserts `element` in the list at `key` either before or after the `pivot`.
     *
     * @see {@link https://valkey.io/commands/linsert/|valkey.io} for details.
     *
     * @param key - The key of the list.
     * @param position - The relative position to insert into - either `InsertPosition.Before` or
     *     `InsertPosition.After` the `pivot`.
     * @param pivot - An element of the list.
     * @param element - The new element to insert.
     *
     * Command Response - The list length after a successful insert operation.
     * If the `key` doesn't exist returns `-1`.
     * If the `pivot` wasn't found, returns `0`.
     */
    public linsert(
        key: string,
        position: InsertPosition,
        pivot: string,
        element: string,
    ): T {
        return this.addAndReturn(createLInsert(key, position, pivot, element));
    }

    /**
     * Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
     * @see {@link https://valkey.io/commands/xadd/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param values - field-value pairs to be added to the entry.
     * @param options - (Optional) Stream add options.
     *
     * Command Response - The id of the added entry, or `null` if `options.makeStream` is set to `false` and no stream with the matching `key` exists.
     */
    public xadd(
        key: string,
        values: [string, string][],
        options?: StreamAddOptions,
    ): T {
        return this.addAndReturn(createXAdd(key, values, options));
    }

    /**
     * Removes the specified entries by id from a stream, and returns the number of entries deleted.
     *
     * @see {@link https://valkey.io/commands/xdel/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param ids - An array of entry ids.
     *
     * Command Response - The number of entries removed from the stream. This number may be less than the number of entries in
     *      `ids`, if the specified `ids` don't exist in the stream.
     */
    public xdel(key: string, ids: string[]): T {
        return this.addAndReturn(createXDel(key, ids));
    }

    /**
     * Trims the stream stored at `key` by evicting older entries.
     * @see {@link https://valkey.io/commands/xtrim/|valkey.io} for details.
     *
     * @param key - the key of the stream
     * @param options - options detailing how to trim the stream.
     *
     * Command Response - The number of entries deleted from the stream. If `key` doesn't exist, 0 is returned.
     */
    public xtrim(key: string, options: StreamTrimOptions): T {
        return this.addAndReturn(createXTrim(key, options));
    }

    /**
     * Returns information about the stream stored at `key`.
     *
     * @param key - The key of the stream.
     * @param fullOptions - If `true`, returns verbose information with a limit of the first 10 PEL entries.
     * If `number` is specified, returns verbose information limiting the returned PEL entries.
     * If `0` is specified, returns verbose information with no limit.
     *
     * Command Response - A {@link ReturnTypeXinfoStream} of detailed stream information for the given `key`.
     *     See example of {@link BaseClient.xinfoStream} for more details.
     */
    public xinfoStream(key: string, fullOptions?: boolean | number): T {
        return this.addAndReturn(createXInfoStream(key, fullOptions ?? false));
    }

    /**
     * Returns the list of all consumer groups and their attributes for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xinfo-groups/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     *
     * Command Response -  An `Array` of `Records`, where each mapping represents the
     *     attributes of a consumer group for the stream at `key`.
     */
    public xinfoGroups(key: string): T {
        return this.addAndReturn(createXInfoGroups(key));
    }

    /** Returns the server time.
     * @see {@link https://valkey.io/commands/time/|valkey.io} for details.
     *
     * Command Response - The current server time as a two items `array`:
     * A Unix timestamp and the amount of microseconds already elapsed in the current second.
     * The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.
     */
    public time(): T {
        return this.addAndReturn(createTime());
    }

    /**
     * Returns stream entries matching a given range of entry IDs.
     *
     * @see {@link https://valkey.io/commands/xrange/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param start - The starting stream entry ID bound for the range.
     *     - Use `value` to specify a stream entry ID.
     *     - Use `isInclusive: false` to specify an exclusive bounded stream entry ID. This is only available starting with Valkey version 6.2.0.
     *     - Use `InfBoundary.NegativeInfinity` to start with the minimum available ID.
     * @param end - The ending stream ID bound for the range.
     *     - Use `value` to specify a stream entry ID.
     *     - Use `isInclusive: false` to specify an exclusive bounded stream entry ID. This is only available starting with Valkey version 6.2.0.
     *     - Use `InfBoundary.PositiveInfinity` to end with the maximum available ID.
     * @param count - An optional argument specifying the maximum count of stream entries to return.
     *     If `count` is not provided, all stream entries in the range will be returned.
     *
     * Command Response - A map of stream entry ids, to an array of entries, or `null` if `count` is negative.
     */
    public xrange(
        key: string,
        start: Boundary<string>,
        end: Boundary<string>,
        count?: number,
    ): T {
        return this.addAndReturn(createXRange(key, start, end, count));
    }

    /**
     * Reads entries from the given streams.
     * @see {@link https://valkey.io/commands/xread/|valkey.io} for details.
     *
     * @param keys_and_ids - pairs of keys and entry ids to read from. A pair is composed of a stream's key and the id of the entry after which the stream will be read.
     * @param options - options detailing how to read the stream.
     *
     * Command Response - A map between a stream key, and an array of entries in the matching key. The entries are in an [id, fields[]] format.
     */
    public xread(
        keys_and_ids: Record<string, string>,
        options?: StreamReadOptions,
    ): T {
        return this.addAndReturn(createXRead(keys_and_ids, options));
    }

    /**
     * Returns the number of entries in the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xlen/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     *
     * Command Response - The number of entries in the stream. If `key` does not exist, returns `0`.
     */
    public xlen(key: string): T {
        return this.addAndReturn(createXLen(key));
    }

    /**
     * Returns stream message summary information for pending messages matching a given range of IDs.
     *
     * @see {@link https://valkey.io/commands/xpending/|valkey.io} for details.
     * Returns the list of all consumers and their attributes for the given consumer group of the
     * stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xinfo-consumers/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     *
     * Command Response - An `array` that includes the summary of the pending messages.
     * See example of {@link BaseClient.xpending|xpending} for more details.
     */
    public xpending(key: string, group: string): T {
        return this.addAndReturn(createXPending(key, group));
    }

    /**
     * Returns stream message summary information for pending messages matching a given range of IDs.
     *
     * @see {@link https://valkey.io/commands/xpending/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param options - Additional options to filter entries, see {@link StreamPendingOptions}.
     *
     * Command Response - A 2D-`array` of 4-tuples containing extended message information.
     * See example of {@link BaseClient.xpendingWithOptions|xpendingWithOptions} for more details.
     */
    public xpendingWithOptions(
        key: string,
        group: string,
        options: StreamPendingOptions,
    ): T {
        return this.addAndReturn(createXPending(key, group, options));
    }

    /**
     * Returns the list of all consumers and their attributes for the given consumer group of the
     * stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xinfo-consumers/|valkey.io} for details.
     *
     * Command Response - An `Array` of `Records`, where each mapping contains the attributes
     *     of a consumer for the given consumer group of the stream at `key`.
     */
    public xinfoConsumers(key: string, group: string): T {
        return this.addAndReturn(createXInfoConsumers(key, group));
    }

    /**
     * Changes the ownership of a pending message.
     *
     * @see {@link https://valkey.io/commands/xclaim/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param ids - An array of entry ids.
     * @param options - (Optional) Stream claim options {@link StreamClaimOptions}.
     *
     * Command Response - A `Record` of message entries that are claimed by the consumer.
     */
    public xclaim(
        key: string,
        group: string,
        consumer: string,
        minIdleTime: number,
        ids: string[],
        options?: StreamClaimOptions,
    ): T {
        return this.addAndReturn(
            createXClaim(key, group, consumer, minIdleTime, ids, options),
        );
    }

    /**
     * Changes the ownership of a pending message. This function returns an `array` with
     * only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
     *
     * @see {@link https://valkey.io/commands/xclaim/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param ids - An array of entry ids.
     * @param options - (Optional) Stream claim options {@link StreamClaimOptions}.
     *
     * Command Response - An `array` of message ids claimed by the consumer.
     */
    public xclaimJustId(
        key: string,
        group: string,
        consumer: string,
        minIdleTime: number,
        ids: string[],
        options?: StreamClaimOptions,
    ): T {
        return this.addAndReturn(
            createXClaim(key, group, consumer, minIdleTime, ids, options, true),
        );
    }

    /**
     * Transfers ownership of pending stream entries that match the specified criteria.
     *
     * @see {@link https://valkey.io/commands/xautoclaim/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param start - Filters the claimed entries to those that have an ID equal or greater than the
     *     specified value.
     * @param count - (Optional) Limits the number of claimed entries to the specified value.
     *
     * Command Response - An `array` containing the following elements:
     *   - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
     *     equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
     *     the entire stream was scanned.
     *   - A mapping of the claimed entries.
     *   - If you are using Valkey 7.0.0 or above, the response list will also include a list containing
     *     the message IDs that were in the Pending Entries List but no longer exist in the stream.
     *     These IDs are deleted from the Pending Entries List.
     */
    public xautoclaim(
        key: string,
        group: string,
        consumer: string,
        minIdleTime: number,
        start: string,
        count?: number,
    ): T {
        return this.addAndReturn(
            createXAutoClaim(key, group, consumer, minIdleTime, start, count),
        );
    }

    /**
     * Transfers ownership of pending stream entries that match the specified criteria.
     *
     * @see {@link https://valkey.io/commands/xautoclaim/|valkey.io} for more details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the stream.
     * @param group - The consumer group name.
     * @param consumer - The group consumer.
     * @param minIdleTime - The minimum idle time for the message to be claimed.
     * @param start - Filters the claimed entries to those that have an ID equal or greater than the
     *     specified value.
     * @param count - (Optional) Limits the number of claimed entries to the specified value.
     *
     * Command Response - An `array` containing the following elements:
     *   - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
     *     equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
     *     the entire stream was scanned.
     *   - A list of the IDs for the claimed entries.
     *   - If you are using Valkey 7.0.0 or above, the response list will also include a list containing
     *     the message IDs that were in the Pending Entries List but no longer exist in the stream.
     *     These IDs are deleted from the Pending Entries List.
     */
    public xautoclaimJustId(
        key: string,
        group: string,
        consumer: string,
        minIdleTime: number,
        start: string,
        count?: number,
    ): T {
        return this.addAndReturn(
            createXAutoClaim(
                key,
                group,
                consumer,
                minIdleTime,
                start,
                count,
                true,
            ),
        );
    }

    /**
     * Creates a new consumer group uniquely identified by `groupname` for the stream
     * stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-create/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param groupName - The newly created consumer group name.
     * @param id - Stream entry ID that specifies the last delivered entry in the stream from the new
     *     group’s perspective. The special ID `"$"` can be used to specify the last entry in the stream.
     *
     * Command Response - `"OK"`.
     */
    public xgroupCreate(
        key: string,
        groupName: string,
        id: string,
        options?: StreamGroupOptions,
    ): T {
        return this.addAndReturn(
            createXGroupCreate(key, groupName, id, options),
        );
    }

    /**
     * Destroys the consumer group `groupname` for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-destroy/|valkey.io} for details.
     *
     * @param key - The key of the stream.
     * @param groupname - The newly created consumer group name.
     *
     * Command Response - `true` if the consumer group is destroyed. Otherwise, `false`.
     */
    public xgroupDestroy(key: string, groupName: string): T {
        return this.addAndReturn(createXGroupDestroy(key, groupName));
    }

    /**
     * Creates a consumer named `consumerName` in the consumer group `groupName` for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-createconsumer/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The consumer group name.
     * @param consumerName - The newly created consumer.
     *
     * Command Response - `true` if the consumer is created. Otherwise, returns `false`.
     */
    public xgroupCreateConsumer(
        key: string,
        groupName: string,
        consumerName: string,
    ): T {
        return this.addAndReturn(
            createXGroupCreateConsumer(key, groupName, consumerName),
        );
    }

    /**
     * Deletes a consumer named `consumerName` in the consumer group `groupName` for the stream stored at `key`.
     *
     * @see {@link https://valkey.io/commands/xgroup-delconsumer/|valkey.io} for more details.
     *
     * @param key - The key of the stream.
     * @param groupName - The consumer group name.
     * @param consumerName - The consumer to delete.
     *
     * Command Response - The number of pending messages the `consumer` had before it was deleted.
     */
    public xgroupDelConsumer(
        key: string,
        groupName: string,
        consumerName: string,
    ): T {
        return this.addAndReturn(
            createXGroupDelConsumer(key, groupName, consumerName),
        );
    }

    /**
     * Renames `key` to `newkey`.
     * If `newkey` already exists it is overwritten.
     * In Cluster mode, both `key` and `newkey` must be in the same hash slot,
     * meaning that in practice only keys that have the same hash tag can be reliably renamed in cluster.
     *
     * @see {@link https://valkey.io/commands/rename/|valkey.io} for details.
     *
     * @param key - The key to rename.
     * @param newKey - The new name of the key.
     *
     * Command Response - If the `key` was successfully renamed, return "OK". If `key` does not exist, an error is thrown.
     */
    public rename(key: string, newKey: string): T {
        return this.addAndReturn(createRename(key, newKey));
    }

    /**
     * Renames `key` to `newkey` if `newkey` does not yet exist.
     * In Cluster mode, both `key` and `newkey` must be in the same hash slot,
     * meaning that in practice only keys that have the same hash tag can be reliably renamed in cluster.
     *
     * @see {@link https://valkey.io/commands/renamenx/|valkey.io} for details.
     *
     * @param key - The key to rename.
     * @param newKey - The new name of the key.
     * Command Response - If the `key` was successfully renamed, returns `true`. Otherwise, returns `false`.
     * If `key` does not exist, an error is thrown.
     */
    public renamenx(key: string, newKey: string): T {
        return this.addAndReturn(createRenameNX(key, newKey));
    }

    /** Blocking list pop primitive.
     * Pop an element from the tail of the first list that is non-empty,
     * with the given `keys` being checked in the order that they are given.
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see {@link https://valkey.io/commands/brpop/|valkey.io} for details.
     * @remarks `BRPOP` is a blocking command, see [Blocking Commands](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands) for more details and best practices.
     *
     * @param keys - The `keys` of the lists to pop from.
     * @param timeout - The `timeout` in seconds.
     *
     * Command Response - An `array` containing the `key` from which the element was popped and the value of the popped element,
     * formatted as [key, value]. If no element could be popped and the timeout expired, returns `null`.
     */
    public brpop(keys: string[], timeout: number): T {
        return this.addAndReturn(createBRPop(keys, timeout));
    }

    /** Blocking list pop primitive.
     * Pop an element from the head of the first list that is non-empty,
     * with the given `keys` being checked in the order that they are given.
     * Blocks the connection when there are no elements to pop from any of the given lists.
     *
     * @see {@link https://valkey.io/commands/blpop/|valkey.io} for details.
     * @remarks `BLPOP` is a blocking command, see [Blocking Commands](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands) for more details and best practices.
     *
     * @param keys - The `keys` of the lists to pop from.
     * @param timeout - The `timeout` in seconds.
     *
     * Command Response - An `array` containing the `key` from which the element was popped and the value of the popped element,
     * formatted as [key, value]. If no element could be popped and the timeout expired, returns `null`.
     */
    public blpop(keys: string[], timeout: number): T {
        return this.addAndReturn(createBLPop(keys, timeout));
    }

    /** Adds all elements to the HyperLogLog data structure stored at the specified `key`.
     * Creates a new structure if the `key` does not exist.
     * When no elements are provided, and `key` exists and is a HyperLogLog, then no operation is performed.
     *
     * @see {@link https://valkey.io/commands/pfadd/|valkey.io} for details.
     *
     * @param key - The key of the HyperLogLog data structure to add elements into.
     * @param elements - An array of members to add to the HyperLogLog stored at `key`.
     * Command Response - If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
     *     altered, then returns `1`. Otherwise, returns `0`.
     */
    public pfadd(key: string, elements: string[]): T {
        return this.addAndReturn(createPfAdd(key, elements));
    }

    /** Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
     * calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
     *
     * @see {@link https://valkey.io/commands/pfcount/|valkey.io} for details.
     *
     * @param keys - The keys of the HyperLogLog data structures to be analyzed.
     * Command Response - The approximated cardinality of given HyperLogLog data structures.
     *     The cardinality of a key that does not exist is `0`.
     */
    public pfcount(keys: string[]): T {
        return this.addAndReturn(createPfCount(keys));
    }

    /**
     * Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it is
     * treated as one of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.
     *
     * @see {@link https://valkey.io/commands/pfmerge/|valkey.io} for details.
     *
     * @param destination - The key of the destination HyperLogLog where the merged data sets will be stored.
     * @param sourceKeys - The keys of the HyperLogLog structures to be merged.
     * Command Response - A simple "OK" response.
     */
    public pfmerge(destination: string, sourceKeys: string[]): T {
        return this.addAndReturn(createPfMerge(destination, sourceKeys));
    }

    /** Returns the internal encoding for the Redis object stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-encoding/|valkey.io} for more details.
     *
     * @param key - The `key` of the object to get the internal encoding of.
     * Command Response - If `key` exists, returns the internal encoding of the object stored at `key` as a string.
     *     Otherwise, returns None.
     */
    public objectEncoding(key: string): T {
        return this.addAndReturn(createObjectEncoding(key));
    }

    /** Returns the logarithmic access frequency counter of a Redis object stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-freq/|valkey.io} for more details.
     *
     * @param key - The `key` of the object to get the logarithmic access frequency counter of.
     * Command Response - If `key` exists, returns the logarithmic access frequency counter of
     *     the object stored at `key` as a `number`. Otherwise, returns `null`.
     */
    public objectFreq(key: string): T {
        return this.addAndReturn(createObjectFreq(key));
    }

    /**
     * Returns the time in seconds since the last access to the value stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-idletime/|valkey.io} for details.
     *
     * @param key - The key of the object to get the idle time of.
     *
     * Command Response - If `key` exists, returns the idle time in seconds. Otherwise, returns `null`.
     */
    public objectIdletime(key: string): T {
        return this.addAndReturn(createObjectIdletime(key));
    }

    /**
     * Returns the reference count of the object stored at `key`.
     *
     * @see {@link https://valkey.io/commands/object-refcount/|valkey.io} for details.
     *
     * @param key - The `key` of the object to get the reference count of.
     *
     * Command Response - If `key` exists, returns the reference count of the object stored at `key` as a `number`.
     * Otherwise, returns `null`.
     */
    public objectRefcount(key: string): T {
        return this.addAndReturn(createObjectRefcount(key));
    }

    /**
     * Displays a piece of generative computer art and the server version.
     *
     * @see {@link https://valkey.io/commands/lolwut/|valkey.io} for details.
     *
     * @param options - The LOLWUT options.
     *
     * Command Response - A piece of generative computer art along with the current server version.
     */
    public lolwut(options?: LolwutOptions): T {
        return this.addAndReturn(createLolwut(options));
    }

    /**
     * Blocks the current client until all the previous write commands are successfully transferred and
     * acknowledged by at least `numreplicas` of replicas. If `timeout` is reached, the command returns
     * the number of replicas that were not yet reached.
     *
     * @see {@link https://valkey.io/commands/wait/|valkey.io} for more details.
     *
     * @param numreplicas - The number of replicas to reach.
     * @param timeout - The timeout value specified in milliseconds. A value of 0 will block indefinitely.
     *
     * Command Response - The number of replicas reached by all the writes performed in the context of the
     *     current connection.
     */
    public wait(numreplicas: number, timeout: number): T {
        return this.addAndReturn(createWait(numreplicas, timeout));
    }

    /**
     * Invokes a previously loaded function.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param keys - A list of `keys` accessed by the function. To ensure the correct execution of functions,
     *     all names of keys that a function accesses must be explicitly provided as `keys`.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     *
     * Command Response - The invoked function's return value.
     */
    public fcall(func: string, keys: string[], args: string[]): T {
        return this.addAndReturn(createFCall(func, keys, args));
    }

    /**
     * Invokes a previously loaded read-only function.
     *
     * @see {@link https://valkey.io/commands/fcall/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param func - The function name.
     * @param keys - A list of `keys` accessed by the function. To ensure the correct execution of functions,
     *     all names of keys that a function accesses must be explicitly provided as `keys`.
     * @param args - A list of `function` arguments and it should not represent names of keys.
     *
     * Command Response - The invoked function's return value.
     */
    public fcallReadonly(func: string, keys: string[], args: string[]): T {
        return this.addAndReturn(createFCallReadOnly(func, keys, args));
    }

    /**
     * Deletes a library and all its functions.
     *
     * @see {@link https://valkey.io/commands/function-delete/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The library name to delete.
     *
     * Command Response - `OK`.
     */
    public functionDelete(libraryCode: string): T {
        return this.addAndReturn(createFunctionDelete(libraryCode));
    }

    /**
     * Loads a library to Valkey.
     *
     * @see {@link https://valkey.io/commands/function-load/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param libraryCode - The source code that implements the library.
     * @param replace - Whether the given library should overwrite a library with the same name if it
     *     already exists.
     *
     * Command Response - The library name that was loaded.
     */
    public functionLoad(libraryCode: string, replace?: boolean): T {
        return this.addAndReturn(createFunctionLoad(libraryCode, replace));
    }

    /**
     * Deletes all function libraries.
     *
     * @see {@link https://valkey.io/commands/function-flush/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     * Command Response - `OK`.
     */
    public functionFlush(mode?: FlushMode): T {
        return this.addAndReturn(createFunctionFlush(mode));
    }

    /**
     * Returns information about the functions and libraries.
     *
     * @see {@link https://valkey.io/commands/function-list/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param options - Parameters to filter and request additional info.
     *
     * Command Response - Info about all or selected libraries and their functions in {@link FunctionListResponse} format.
     */
    public functionList(options?: FunctionListOptions): T {
        return this.addAndReturn(createFunctionList(options));
    }

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * @see {@link https://valkey.io/commands/function-stats/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * Command Response - A `Record` of type {@link FunctionStatsResponse} with two keys:
     *
     * - `"running_script"` with information about the running script.
     * - `"engines"` with information about available engines and their stats.
     */
    public functionStats(): T {
        return this.addAndReturn(createFunctionStats());
    }

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see {@link https://valkey.io/commands/flushall/|valkey.io} for details.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     *
     * Command Response - `OK`.
     */
    public flushall(mode?: FlushMode): T {
        return this.addAndReturn(createFlushAll(mode));
    }

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see {@link https://valkey.io/commands/flushdb/|valkey.io} for details.
     *
     * @param mode - The flushing mode, could be either {@link FlushMode.SYNC} or {@link FlushMode.ASYNC}.
     *
     * Command Response - `OK`.
     */
    public flushdb(mode?: FlushMode): T {
        return this.addAndReturn(createFlushDB(mode));
    }

    /**
     * Returns the index of the first occurrence of `element` inside the list specified by `key`. If no
     * match is found, `null` is returned. If the `count` option is specified, then the function returns
     * an `array` of indices of matching elements within the list.
     *
     * @see {@link https://valkey.io/commands/lpos/|valkey.io} for details.
     * @remarks Since Valkey version 6.0.6.
     *
     * @param key - The name of the list.
     * @param element - The value to search for within the list.
     * @param options - The LPOS options.
     *
     * Command Response - The index of `element`, or `null` if `element` is not in the list. If the `count`
     * option is specified, then the function returns an `array` of indices of matching elements within the list.
     */
    public lpos(key: string, element: string, options?: LPosOptions): T {
        return this.addAndReturn(createLPos(key, element, options));
    }

    /**
     * Returns the number of keys in the currently selected database.
     *
     * @see {@link https://valkey.io/commands/dbsize/|valkey.io} for details.
     *
     * Command Response - The number of keys in the currently selected database.
     */
    public dbsize(): T {
        return this.addAndReturn(createDBSize());
    }

    /**
     * Counts the number of set bits (population counting) in the string stored at `key`. The `options` argument can
     * optionally be provided to count the number of bits in a specific string interval.
     *
     * @see {@link https://valkey.io/commands/bitcount/|valkey.io} for more details.
     *
     * @param key - The key for the string to count the set bits of.
     * @param options - The offset options.
     *
     * Command Response - If `options` is provided, returns the number of set bits in the string interval specified by `options`.
     *     If `options` is not provided, returns the number of set bits in the string stored at `key`.
     *     Otherwise, if `key` is missing, returns `0` as it is treated as an empty string.
     */
    public bitcount(key: string, options?: BitOffsetOptions): T {
        return this.addAndReturn(createBitCount(key, options));
    }

    /**
     * Adds geospatial members with their positions to the specified sorted set stored at `key`.
     * If a member is already a part of the sorted set, its position is updated.
     *
     * @see {@link https://valkey.io/commands/geoadd/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param membersToGeospatialData - A mapping of member names to their corresponding positions - see
     *     {@link GeospatialData}. The command will report an error when the user attempts to index
     *     coordinates outside the specified ranges.
     * @param options - The GeoAdd options - see {@link GeoAddOptions}.
     *
     * Command Response - The number of elements added to the sorted set. If `changed` is set to
     *    `true` in the options, returns the number of elements updated in the sorted set.
     */
    public geoadd(
        key: string,
        membersToGeospatialData: Map<string, GeospatialData>,
        options?: GeoAddOptions,
    ): T {
        return this.addAndReturn(
            createGeoAdd(key, membersToGeospatialData, options),
        );
    }

    /**
     * Returns the members of a sorted set populated with geospatial information using {@link geoadd},
     * which are within the borders of the area specified by a given shape.
     *
     * @see {@link https://valkey.io/commands/geosearch/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param key - The key of the sorted set.
     * @param searchFrom - The query's center point options, could be one of:
     *
     * - {@link MemberOrigin} to use the position of the given existing member in the sorted set.
     *
     * - {@link CoordOrigin} to use the given longitude and latitude coordinates.
     *
     * @param searchBy - The query's shape options, could be one of:
     *
     * - {@link GeoCircleShape} to search inside circular area according to given radius.
     *
     * - {@link GeoBoxShape} to search inside an axis-aligned rectangle, determined by height and width.
     *
     * @param resultOptions - The optional inputs to request additional information and configure sorting/limiting the results, see {@link GeoSearchResultOptions}.
     *
     * Command Response - By default, returns an `Array` of members (locations) names.
     *     If any of `withCoord`, `withDist` or `withHash` are set to `true` in {@link GeoSearchResultOptions}, a 2D `Array` returned,
     *     where each sub-array represents a single item in the following order:
     *
     * - The member (location) name.
     * - The distance from the center as a floating point `number`, in the same unit specified for `searchBy`.
     * - The geohash of the location as a integer `number`.
     * - The coordinates as a two item `array` of floating point `number`s.
     */
    public geosearch(
        key: string,
        searchFrom: SearchOrigin,
        searchBy: GeoSearchShape,
        resultOptions?: GeoSearchResultOptions,
    ): T {
        return this.addAndReturn(
            createGeoSearch(key, searchFrom, searchBy, resultOptions),
        );
    }

    /**
     * Searches for members in a sorted set stored at `source` representing geospatial data
     * within a circular or rectangular area and stores the result in `destination`.
     *
     * If `destination` already exists, it is overwritten. Otherwise, a new sorted set will be created.
     *
     * To get the result directly, see {@link geosearch}.
     *
     * @see {@link https://valkey.io/commands/geosearchstore/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param destination - The key of the destination sorted set.
     * @param source - The key of the sorted set.
     * @param searchFrom - The query's center point options, could be one of:
     * - {@link MemberOrigin} to use the position of the given existing member in the sorted set.
     * - {@link CoordOrigin} to use the given longitude and latitude coordinates.
     * @param searchBy - The query's shape options, could be one of:
     * - {@link GeoCircleShape} to search inside circular area according to given radius.
     * - {@link GeoBoxShape} to search inside an axis-aligned rectangle, determined by height and width.
     * @param resultOptions - (Optional) Parameters to request additional information and configure sorting/limiting the results, see {@link GeoSearchStoreResultOptions}.
     *
     * Command Response - The number of elements in the resulting sorted set stored at `destination`.
     */
    public geosearchstore(
        destination: string,
        source: string,
        searchFrom: SearchOrigin,
        searchBy: GeoSearchShape,
        resultOptions?: GeoSearchStoreResultOptions,
    ): T {
        return this.addAndReturn(
            createGeoSearchStore(
                destination,
                source,
                searchFrom,
                searchBy,
                resultOptions,
            ),
        );
    }

    /**
     * Returns the positions (longitude, latitude) of all the specified `members` of the
     * geospatial index represented by the sorted set at `key`.
     *
     * @see {@link https://valkey.io/commands/geopos/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param members - The members for which to get the positions.
     *
     * Command Response - A 2D `Array` which represents positions (longitude and latitude) corresponding to the
     *     given members. The order of the returned positions matches the order of the input members.
     *     If a member does not exist, its position will be `null`.
     */
    public geopos(key: string, members: string[]): T {
        return this.addAndReturn(createGeoPos(key, members));
    }

    /**
     * Pops a member-score pair from the first non-empty sorted set, with the given `keys`
     * being checked in the order they are provided.
     *
     * @see {@link https://valkey.io/commands/zmpop/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sorted sets.
     * @param modifier - The element pop criteria - either {@link ScoreFilter.MIN} or
     *     {@link ScoreFilter.MAX} to pop the member with the lowest/highest score accordingly.
     * @param count - (Optional) The number of elements to pop. If not supplied, only one element will be popped.
     *
     * Command Response - A two-element `array` containing the key name of the set from which the
     *     element was popped, and a member-score `Record` of the popped element.
     *     If no member could be popped, returns `null`.
     */
    public zmpop(keys: string[], modifier: ScoreFilter, count?: number): T {
        return this.addAndReturn(createZMPop(keys, modifier, count));
    }

    /**
     * Pops a member-score pair from the first non-empty sorted set, with the given `keys` being
     * checked in the order they are provided. Blocks the connection when there are no members
     * to pop from any of the given sorted sets. `BZMPOP` is the blocking variant of {@link zmpop}.
     *
     * @see {@link https://valkey.io/commands/bzmpop/|valkey.io} for details.
     * @remarks `BZMPOP` is a client blocking command, see {@link https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#blocking-commands | Valkey Glide Wiki} for more details and best practices.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - The keys of the sorted sets.
     * @param modifier - The element pop criteria - either {@link ScoreFilter.MIN} or
     *     {@link ScoreFilter.MAX} to pop the member with the lowest/highest score accordingly.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of `0` will block indefinitely.
     * @param count - (Optional) The number of elements to pop. If not supplied, only one element will be popped.
     *
     * Command Response - A two-element `array` containing the key name of the set from which the element
     *     was popped, and a member-score `Record` of the popped element.
     *     If no member could be popped, returns `null`.
     */
    public bzmpop(
        keys: string[],
        modifier: ScoreFilter,
        timeout: number,
        count?: number,
    ): T {
        return this.addAndReturn(createBZMPop(keys, modifier, timeout, count));
    }

    /**
     * Increments the score of `member` in the sorted set stored at `key` by `increment`.
     * If `member` does not exist in the sorted set, it is added with `increment` as its score.
     * If `key` does not exist, a new sorted set is created with the specified member as its sole member.
     *
     * @see {@link https://valkey.io/commands/zincrby/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param increment - The score increment.
     * @param member - A member of the sorted set.
     *
     * Command Response - The new score of `member`.
     */
    public zincrby(key: string, increment: number, member: string): T {
        return this.addAndReturn(createZIncrBy(key, increment, member));
    }

    /**
     * Iterates incrementally over a sorted set.
     *
     * @see {@link https://valkey.io/commands/zscan/|valkey.io} for more details.
     *
     * @param key - The key of the sorted set.
     * @param cursor - The cursor that points to the next iteration of results. A value of `"0"` indicates the start of
     *      the search.
     * @param options - (Optional) The zscan options.
     *
     * Command Response - An `Array` of the `cursor` and the subset of the sorted set held by `key`.
     *      The first element is always the `cursor` for the next iteration of results. `0` will be the `cursor`
     *      returned on the last iteration of the sorted set. The second element is always an `Array` of the subset
     *      of the sorted set held in `key`. The `Array` in the second element is always a flattened series of
     *      `String` pairs, where the value is at even indices and the score is at odd indices.
     */
    public zscan(key: string, cursor: string, options?: BaseScanOptions): T {
        return this.addAndReturn(createZScan(key, cursor, options));
    }

    /**
     * Returns the distance between `member1` and `member2` saved in the geospatial index stored at `key`.
     *
     * @see {@link https://valkey.io/commands/geodist/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param member1 - The name of the first member.
     * @param member2 - The name of the second member.
     * @param geoUnit - The unit of distance measurement - see {@link GeoUnit}. If not specified, the default unit is {@link GeoUnit.METERS}.
     *
     * Command Response - The distance between `member1` and `member2`. Returns `null`, if one or both members do not exist,
     *     or if the key does not exist.
     */
    public geodist(
        key: string,
        member1: string,
        member2: string,
        geoUnit?: GeoUnit,
    ): T {
        return this.addAndReturn(createGeoDist(key, member1, member2, geoUnit));
    }

    /**
     * Returns the `GeoHash` strings representing the positions of all the specified `members` in the sorted set stored at `key`.
     *
     * @see {@link https://valkey.io/commands/geohash/|valkey.io} for details.
     *
     * @param key - The key of the sorted set.
     * @param members - The array of members whose `GeoHash` strings are to be retrieved.
     *
     * Command Response - An array of `GeoHash` strings representing the positions of the specified members stored at `key`.
     *   If a member does not exist in the sorted set, a `null` value is returned for that member.
     */
    public geohash(key: string, members: string[]): T {
        return this.addAndReturn(createGeoHash(key, members));
    }

    /**
     * Returns `UNIX TIME` of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see {@link https://valkey.io/commands/lastsave/|valkey.io} for details.
     *
     * Command Response - `UNIX TIME` of the last DB save executed with success.
     */
    public lastsave(): T {
        return this.addAndReturn(createLastSave());
    }

    /**
     * Returns all the longest common subsequences combined between strings stored at `key1` and `key2`.
     *
     * @see {@link https://valkey.io/commands/lcs/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     *
     * Command Response - A `String` containing all the longest common subsequence combined between the 2 strings.
     *     An empty `String` is returned if the keys do not exist or have no common subsequences.
     */
    public lcs(key1: string, key2: string): T {
        return this.addAndReturn(createLCS(key1, key2));
    }

    /**
     * Returns the total length of all the longest common subsequences between strings stored at `key1` and `key2`.
     *
     * @see {@link https://valkey.io/commands/lcs/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     *
     * Command Response - The total length of all the longest common subsequences between the 2 strings.
     */
    public lcsLen(key1: string, key2: string): T {
        return this.addAndReturn(createLCS(key1, key2, { len: true }));
    }

    /**
     * Returns the indices and lengths of the longest common subsequences between strings stored at
     * `key1` and `key2`.
     *
     * @see {@link https://valkey.io/commands/lcs/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key1 - The key that stores the first string.
     * @param key2 - The key that stores the second string.
     * @param withMatchLen - (Optional) If `true`, include the length of the substring matched for the each match.
     * @param minMatchLen - (Optional) The minimum length of matches to include in the result.
     *
     * Command Response - A `Record` containing the indices of the longest common subsequences between the
     *     2 strings and the lengths of the longest common subsequences. The resulting map contains two
     *     keys, "matches" and "len":
     *     - `"len"` is mapped to the total length of the all longest common subsequences between the 2 strings
     *           stored as an integer. This value doesn't count towards the `minMatchLen` filter.
     *     - `"matches"` is mapped to a three dimensional array of integers that stores pairs
     *           of indices that represent the location of the common subsequences in the strings held
     *           by `key1` and `key2`.
     *
     *     See example of {@link BaseClient.lcsIdx|lcsIdx} for more details.
     */
    public lcsIdx(
        key1: string,
        key2: string,
        options?: { withMatchLen?: boolean; minMatchLen?: number },
    ): T {
        return this.addAndReturn(createLCS(key1, key2, { idx: options ?? {} }));
    }

    /**
     * Updates the last access time of the specified keys.
     *
     * @see {@link https://valkey.io/commands/touch/|valkey.io} for details.
     *
     * @param keys - The keys to update the last access time of.
     *
     * Command Response - The number of keys that were updated. A key is ignored if it doesn't exist.
     */
    public touch(keys: string[]): T {
        return this.addAndReturn(createTouch(keys));
    }

    /**
     * Returns a random existing key name from the currently selected database.
     *
     * @see {@link https://valkey.io/commands/randomkey/|valkey.io} for details.
     *
     * Command Response - A random existing key name from the currently selected database.
     */
    public randomKey(): T {
        return this.addAndReturn(createRandomKey());
    }

    /**
     * Overwrites part of the string stored at `key`, starting at the specified `offset`,
     * for the entire length of `value`. If the `offset` is larger than the current length of the string at `key`,
     * the string is padded with zero bytes to make `offset` fit. Creates the `key` if it doesn't exist.
     *
     * @see {@link https://valkey.io/commands/setrange/|valkey.io} for details.
     *
     * @param key - The key of the string to update.
     * @param offset - The position in the string where `value` should be written.
     * @param value - The string written with `offset`.
     *
     * Command Response - The length of the string stored at `key` after it was modified.
     */
    public setrange(key: string, offset: number, value: string): T {
        return this.addAndReturn(createSetRange(key, offset, value));
    }

    /**
     * Appends a `value` to a `key`. If `key` does not exist it is created and set as an empty string,
     * so `APPEND` will be similar to {@link set} in this special case.
     *
     * @see {@link https://valkey.io/commands/append/|valkey.io} for details.
     *
     * @param key - The key of the string.
     * @param value - The key of the string.
     *
     * Command Response - The length of the string after appending the value.
     */
    public append(key: string, value: string): T {
        return this.addAndReturn(createAppend(key, value));
    }

    /**
     * Pops one or more elements from the first non-empty list from the provided `keys`.
     *
     * @see {@link https://valkey.io/commands/lmpop/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - An array of keys to lists.
     * @param direction - The direction based on which elements are popped from - see {@link ListDirection}.
     * @param count - (Optional) The maximum number of popped elements.
     *
     * Command Response - A `Record` of `key` name mapped array of popped elements.
     */
    public lmpop(keys: string[], direction: ListDirection, count?: number): T {
        return this.addAndReturn(createLMPop(keys, direction, count));
    }

    /**
     * Blocks the connection until it pops one or more elements from the first non-empty list from the
     * provided `key`. `BLMPOP` is the blocking variant of {@link lmpop}.
     *
     * @see {@link https://valkey.io/commands/blmpop/|valkey.io} for details.
     * @remarks Since Valkey version 7.0.0.
     *
     * @param keys - An array of keys to lists.
     * @param direction - The direction based on which elements are popped from - see {@link ListDirection}.
     * @param timeout - The number of seconds to wait for a blocking operation to complete. A value of
     *     `0` will block indefinitely.
     * @param count - (Optional) The maximum number of popped elements.
     *
     * Command Response - A `Record` of `key` name mapped array of popped elements.
     *     If no member could be popped and the timeout expired, returns `null`.
     */
    public blmpop(
        keys: string[],
        direction: ListDirection,
        timeout: number,
        count?: number,
    ): T {
        return this.addAndReturn(createBLMPop(timeout, keys, direction, count));
    }

    /**
     * Lists the currently active channels.
     * The command is routed to all nodes, and aggregates the response to a single array.
     *
     * @see {@link https://valkey.io/commands/pubsub-channels/|valkey.io} for more details.
     *
     * @param pattern - A glob-style pattern to match active channels.
     *                  If not provided, all active channels are returned.
     * Command Response - A list of currently active channels matching the given pattern.
     *          If no pattern is specified, all active channels are returned.
     */
    public pubsubChannels(pattern?: string): T {
        return this.addAndReturn(createPubSubChannels(pattern));
    }

    /**
     * Returns the number of unique patterns that are subscribed to by clients.
     *
     * Note: This is the total number of unique patterns all the clients are subscribed to,
     * not the count of clients subscribed to patterns.
     * The command is routed to all nodes, and aggregates the response to the sum of all pattern subscriptions.
     *
     * @see {@link https://valkey.io/commands/pubsub-numpat/|valkey.io} for more details.
     *
     * Command Response - The number of unique patterns.
     */
    public pubsubNumPat(): T {
        return this.addAndReturn(createPubSubNumPat());
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified channels.
     *
     * Note that it is valid to call this command without channels. In this case, it will just return an empty map.
     * The command is routed to all nodes, and aggregates the response to a single map of the channels and their number of subscriptions.
     *
     * @see {@link https://valkey.io/commands/pubsub-numsub/|valkey.io} for more details.
     *
     * @param channels - The list of channels to query for the number of subscribers.
     *                   If not provided, returns an empty map.
     * Command Response - A map where keys are the channel names and values are the number of subscribers.
     */
    public pubsubNumSub(channels?: string[]): T {
        return this.addAndReturn(createPubSubNumSub(channels));
    }
}

/**
 * Extends BaseTransaction class for Redis standalone commands.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the GlideClient.exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 * @example
 * ```typescript
 * const transaction = new Transaction()
 *    .set("key", "value")
 *    .select(1)  /// Standalone command
 *    .get("key");
 * const result = await GlideClient.exec(transaction);
 * console.log(result); // Output: ['OK', 'OK', null]
 * ```
 */
export class Transaction extends BaseTransaction<Transaction> {
    /// TODO: add MOVE, SLAVEOF and all SENTINEL commands

    /** Change the currently selected Redis database.
     * @see {@link https://valkey.io/commands/select/|valkey.io} for details.
     *
     * @param index - The index of the database to select.
     *
     * Command Response - A simple OK response.
     */
    public select(index: number): Transaction {
        return this.addAndReturn(createSelect(index));
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
     * @param options - (Optional) {@link SortOptions}.
     *
     * Command Response - An `Array` of sorted elements.
     */
    public sort(key: string, options?: SortOptions): Transaction {
        return this.addAndReturn(createSort(key, options));
    }

    /**
     * Sorts the elements in the list, set, or sorted set at `key` and returns the result.
     *
     * The `sortReadOnly` command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.
     *
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @remarks Since Valkey version 7.0.0.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param options - (Optional) {@link SortOptions}.
     *
     * Command Response - An `Array` of sorted elements
     */
    public sortReadOnly(key: string, options?: SortOptions): Transaction {
        return this.addAndReturn(createSortReadOnly(key, options));
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
     * @see {@link https://valkey.io/commands/sort/|valkey.io} for more details.
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param destination - The key where the sorted result will be stored.
     * @param options - (Optional) {@link SortOptions}.
     *
     * Command Response - The number of elements in the sorted key stored at `destination`.
     */
    public sortStore(
        key: string,
        destination: string,
        options?: SortOptions,
    ): Transaction {
        return this.addAndReturn(createSort(key, options, destination));
    }

    /**
     * Copies the value stored at the `source` to the `destination` key. If `destinationDB` is specified,
     * the value will be copied to the database specified, otherwise the current database will be used.
     * When `replace` is true, removes the `destination` key first if it already exists, otherwise performs
     * no action.
     *
     * @see {@link https://valkey.io/commands/copy/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source value.
     * @param destination - The key where the value should be copied to.
     * @param destinationDB - (Optional) The alternative logical database index for the destination key.
     *     If not provided, the current database will be used.
     * @param replace - (Optional) If `true`, the `destination` key should be removed before copying the
     *     value to it. If not provided, no action will be performed if the key already exists.
     *
     * Command Response - `true` if `source` was copied, `false` if the `source` was not copied.
     */
    public copy(
        source: string,
        destination: string,
        options?: { destinationDB?: number; replace?: boolean },
    ): Transaction {
        return this.addAndReturn(createCopy(source, destination, options));
    }

    /**
     * Move `key` from the currently selected database to the database specified by `dbIndex`.
     *
     * @see {@link https://valkey.io/commands/move/|valkey.io} for details.
     *
     * @param key - The key to move.
     * @param dbIndex - The index of the database to move `key` to.
     *
     * Command Response - `true` if `key` was moved, or `false` if the `key` already exists in the destination
     *     database or does not exist in the source database.
     */
    public move(key: string, dbIndex: number): Transaction {
        return this.addAndReturn(createMove(key, dbIndex));
    }

    /** Publish a message on pubsub channel.
     *
     * @see {@link https://valkey.io/commands/publish/|valkey.io} for more details.
     *
     * @param message - Message to publish.
     * @param channel - Channel to publish the message on.
     *
     * Command Response -  Number of subscriptions in primary node that received the message.
     * Note that this value does not include subscriptions that configured on replicas.
     */
    public publish(message: string, channel: string): Transaction {
        return this.addAndReturn(createPublish(message, channel));
    }
}

/**
 * Extends BaseTransaction class for cluster mode commands.
 * Transactions allow the execution of a group of commands in a single step.
 *
 * Command Response:
 *  An array of command responses is returned by the GlideClusterClient.exec command, in the order they were given.
 *  Each element in the array represents a command given to the transaction.
 *  The response for each command depends on the executed Redis command.
 *  Specific response types are documented alongside each method.
 *
 */
export class ClusterTransaction extends BaseTransaction<ClusterTransaction> {
    /// TODO: add all CLUSTER commands

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
     * @param options - (Optional) {@link SortClusterOptions}.
     *
     * Command Response - An `Array` of sorted elements.
     */
    public sort(key: string, options?: SortClusterOptions): ClusterTransaction {
        return this.addAndReturn(createSort(key, options));
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
     * @param options - (Optional) {@link SortClusterOptions}.
     *
     * Command Response - An `Array` of sorted elements
     */
    public sortReadOnly(
        key: string,
        options?: SortClusterOptions,
    ): ClusterTransaction {
        return this.addAndReturn(createSortReadOnly(key, options));
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
     *
     * @param key - The key of the list, set, or sorted set to be sorted.
     * @param destination - The key where the sorted result will be stored.
     * @param options - (Optional) {@link SortClusterOptions}.
     *
     * Command Response - The number of elements in the sorted key stored at `destination`.
     */
    public sortStore(
        key: string,
        destination: string,
        options?: SortClusterOptions,
    ): ClusterTransaction {
        return this.addAndReturn(createSort(key, options, destination));
    }

    /**
     * Copies the value stored at the `source` to the `destination` key. When `replace` is true,
     * removes the `destination` key first if it already exists, otherwise performs no action.
     *
     * @see {@link https://valkey.io/commands/copy/|valkey.io} for details.
     * @remarks Since Valkey version 6.2.0.
     *
     * @param source - The key to the source value.
     * @param destination - The key where the value should be copied to.
     * @param replace - (Optional) If `true`, the `destination` key should be removed before copying the
     *     value to it. If not provided, no action will be performed if the key already exists.
     *
     * Command Response - `true` if `source` was copied, `false` if the `source` was not copied.
     */
    public copy(
        source: string,
        destination: string,
        replace?: boolean,
    ): ClusterTransaction {
        return this.addAndReturn(
            createCopy(source, destination, { replace: replace }),
        );
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
     *
     * Command Response -  Number of subscriptions in primary node that received the message.
     */
    public publish(
        message: string,
        channel: string,
        sharded: boolean = false,
    ): ClusterTransaction {
        return this.addAndReturn(createPublish(message, channel, sharded));
    }

    /**
     * Lists the currently active shard channels.
     * The command is routed to all nodes, and aggregates the response to a single array.
     *
     * @see {@link https://valkey.io/commands/pubsub-shardchannels|valkey.io} for more details.
     *
     * @param pattern - A glob-style pattern to match active shard channels.
     *                  If not provided, all active shard channels are returned.
     * Command Response - A list of currently active shard channels matching the given pattern.
     *          If no pattern is specified, all active shard channels are returned.
     */
    public pubsubShardChannels(pattern?: string): ClusterTransaction {
        return this.addAndReturn(createPubsubShardChannels(pattern));
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the specified shard channels.
     *
     * Note that it is valid to call this command without channels. In this case, it will just return an empty map.
     * The command is routed to all nodes, and aggregates the response to a single map of the channels and their number of subscriptions.
     *
     * @see {@link https://valkey.io/commands/pubsub-shardnumsub|valkey.io} for more details.
     *
     * @param channels - The list of shard channels to query for the number of subscribers.
     *                   If not provided, returns an empty map.
     * @returns A map where keys are the shard channel names and values are the number of subscribers.
     */
    public pubsubShardNumSub(channels?: string[]): ClusterTransaction {
        return this.addAndReturn(createPubSubShardNumSub(channels));
    }
}
