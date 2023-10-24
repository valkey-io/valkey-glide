import {
    MAX_REQUEST_ARGS_LEN,
    createLeakedStringVec,
} from "babushka-rs-internal";
import Long from "long";
import { redis_request } from "./ProtobufMessage";
import RequestType = redis_request.RequestType;

function isLargeCommand(args: string[]) {
    let lenSum = 0;
    for (const arg of args) {
        lenSum += arg.length;
        if (lenSum >= MAX_REQUEST_ARGS_LEN) {
            return true;
        }
    }
    return false;
}

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
    requestType: redis_request.RequestType,
    args: string[]
): redis_request.Command {
    const singleCommand = redis_request.Command.create({
        requestType,
    });

    if (isLargeCommand(args)) {
        // pass as a pointer
        const pointerArr = createLeakedStringVec(args);
        const pointer = new Long(pointerArr[0], pointerArr[1]);
        singleCommand.argsVecPointer = pointer;
    } else {
        singleCommand.argsArray = redis_request.Command.ArgsArray.create({
            args: args,
        });
    }
    return singleCommand;
}

export function createGet(key: string): redis_request.Command {
    return createCommand(RequestType.GetString, [key]);
}

export type SetOptions = {
    /**
     *  `onlyIfDoesNotExist` - Only set the key if it does not already exist. Equivalent to `NX` in the Redis API.
     * `onlyIfExists` - Only set the key if it already exist. Equivalent to `EX` in the Redis API.
     * if `conditional` is not set the value will be set regardless of prior value existence.
     * If value isn't set because of the condition, return null.
     */
    conditionalSet?: "onlyIfExists" | "onlyIfDoesNotExist";
    /**
     * Return the old string stored at key, or nil if key did not exist. An error is returned and SET aborted if the value stored at key is not a string.
     * Equivalent to `GET` in the Redis API.
     */
    returnOldValue?: boolean;
    /**
     * If not set, no expiry time will be set for the value.
     */
    expiry?: /**
     * Retain the time to live associated with the key. Equivalent to `KEEPTTL` in the Redis API.
     */
    | "keepExisting"
        | {
              type: /**
               * Set the specified expire time, in seconds. Equivalent to `EX` in the Redis API.
               */
              | "seconds"
                  /**
                   * Set the specified expire time, in milliseconds. Equivalent to `PX` in the Redis API.
                   */
                  | "milliseconds"
                  /**
                   * Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Redis API.
                   */
                  | "unixSeconds"
                  /**
                   * Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the Redis API.
                   */
                  | "unixMilliseconds";
              count: number;
          };
};

export function createSet(
    key: string,
    value: string,
    options?: SetOptions
): redis_request.Command {
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
    return createCommand(RequestType.SetString, args);
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

export function createPing(str?: string): redis_request.Command {
    const args: string[] = str == undefined ? [] : [str];
    return createCommand(RequestType.Ping, args);
}

export function createInfo(options?: InfoOptions[]): redis_request.Command {
    const args: string[] = options == undefined ? [] : options;
    return createCommand(RequestType.Info, args);
}

export function createDel(keys: string[]): redis_request.Command {
    return createCommand(RequestType.Del, keys);
}

export function createSelect(index: number): redis_request.Command {
    return createCommand(RequestType.Select, [index.toString()]);
}

export function createClientGetName(): redis_request.Command {
    return createCommand(RequestType.ClientGetName, []);
}

export function createConfigRewrite(): redis_request.Command {
    return createCommand(RequestType.ConfigRewrite, []);
}

export function createConfigResetStat(): redis_request.Command {
    return createCommand(RequestType.ConfigResetStat, []);
}

export function createMGet(keys: string[]): redis_request.Command {
    return createCommand(RequestType.MGet, keys);
}

export function createMSet(
    keyValueMap: Record<string, string>
): redis_request.Command {
    return createCommand(RequestType.MSet, Object.entries(keyValueMap).flat());
}

export function createIncr(key: string): redis_request.Command {
    return createCommand(RequestType.Incr, [key]);
}

export function createIncrBy(
    key: string,
    amount: number
): redis_request.Command {
    return createCommand(RequestType.IncrBy, [key, amount.toString()]);
}

export function createIncrByFloat(
    key: string,
    amount: number
): redis_request.Command {
    return createCommand(RequestType.IncrByFloat, [key, amount.toString()]);
}

export function createClientId(): redis_request.Command {
    return createCommand(RequestType.ClientId, []);
}

export function createConfigGet(parameters: string[]): redis_request.Command {
    return createCommand(RequestType.ConfigGet, parameters);
}

export function createConfigSet(
    parameters: Record<string, string>
): redis_request.Command {
    return createCommand(
        RequestType.ConfigSet,
        Object.entries(parameters).flat()
    );
}

export function createHGet(key: string, field: string): redis_request.Command {
    return createCommand(RequestType.HashGet, [key, field]);
}

export function createHSet(
    key: string,
    fieldValueMap: Record<string, string>
): redis_request.Command {
    return createCommand(
        RequestType.HashSet,
        [key].concat(Object.entries(fieldValueMap).flat())
    );
}

export function createDecr(key: string): redis_request.Command {
    return createCommand(RequestType.Decr, [key]);
}

export function createDecrBy(
    key: string,
    amount: number
): redis_request.Command {
    return createCommand(RequestType.DecrBy, [key, amount.toString()]);
}

export function createHDel(
    key: string,
    fields: string[]
): redis_request.Command {
    return createCommand(RequestType.HashDel, [key].concat(fields));
}

export function createHMGet(
    key: string,
    fields: string[]
): redis_request.Command {
    return createCommand(RequestType.HashMGet, [key].concat(fields));
}

export function createHExists(
    key: string,
    field: string
): redis_request.Command {
    return createCommand(RequestType.HashExists, [key, field]);
}

export function createHGetAll(key: string): redis_request.Command {
    return createCommand(RequestType.HashGetAll, [key]);
}

export function createLPush(
    key: string,
    elements: string[]
): redis_request.Command {
    return createCommand(RequestType.LPush, [key].concat(elements));
}

export function createLPop(key: string, count?: number): redis_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.LPop, args);
}

export function createLRange(
    key: string,
    start: number,
    end: number
): redis_request.Command {
    return createCommand(RequestType.LRange, [
        key,
        start.toString(),
        end.toString(),
    ]);
}

export function createRPush(
    key: string,
    elements: string[]
): redis_request.Command {
    return createCommand(RequestType.RPush, [key].concat(elements));
}

export function createRPop(key: string, count?: number): redis_request.Command {
    const args: string[] = count == undefined ? [key] : [key, count.toString()];
    return createCommand(RequestType.RPop, args);
}

export function createSAdd(
    key: string,
    members: string[]
): redis_request.Command {
    return createCommand(RequestType.SAdd, [key].concat(members));
}

export function createSRem(
    key: string,
    members: string[]
): redis_request.Command {
    return createCommand(RequestType.SRem, [key].concat(members));
}

export function createSMembers(key: string): redis_request.Command {
    return createCommand(RequestType.SMembers, [key]);
}

export function createSCard(key: string): redis_request.Command {
    return createCommand(RequestType.SCard, [key]);
}

export function createCustomCommand(commandName: string, args: string[]) {
    return createCommand(RequestType.CustomCommand, [commandName, ...args]);
}

export function createHIncrBy(
    key: string,
    field: string,
    amount: number
): redis_request.Command {
    return createCommand(RequestType.HashIncrBy, [
        key,
        field,
        amount.toString(),
    ]);
}

export function createHIncrByFloat(
    key: string,
    field: string,
    amount: number
): redis_request.Command {
    return createCommand(RequestType.HashIncrByFloat, [
        key,
        field,
        amount.toString(),
    ]);
}
