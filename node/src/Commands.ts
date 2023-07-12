import {
    MAX_REQUEST_ARGS_LEN,
    createLeakedStringVec,
} from "babushka-rs-internal";
import Long from "long";
import { redis_request } from "./ProtobufMessage";
import RequestType = redis_request.RequestType;

type slotIdTypes = {
    type: "master_slot_id" | "replica_slot_id";
    id: number;
};

type slotKeyTypes = {
    type: "master_slot_key" | "replica_slot_key";
    key: string;
};

export type Routes =
    | "all_primaries"
    | "all_nodes"
    | "multi_shard"
    | "random"
    | slotIdTypes
    | slotKeyTypes;

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
// TODO - implement route handdeling

function createCommand(
    requestType: redis_request.RequestType,
    args: string[],
    route?: Routes
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

export function createPing(
    str?: string,
    route?: Routes
): redis_request.Command {
    const args = str !== undefined ? [str] : [];
    if (route !== undefined)
        return createCommand(RequestType.Ping, args, route);
    return createCommand(RequestType.Ping, args);
}

export type SetOptions = {
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

export function createCustomCommand(commandName: string, args: string[]) {
    return createCommand(RequestType.CustomCommand, [commandName, ...args]);
}
