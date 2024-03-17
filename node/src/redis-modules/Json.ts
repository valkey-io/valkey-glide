/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import { RedisClient, RedisClusterClient } from "../../";
import {
    ConditionalChange,
    JsonGetOptions,
    createJsonGet,
    createJsonSet,
} from "../Commands";
export type TRedisClient = RedisClient | RedisClusterClient;

/**
 * Sets the JSON value at the specified `path` stored at `key`.
 * @param client - The Redis client to execute the command.
 * @param key - The key of the JSON document.
 * @param path - Represents the path within the JSON document where the value will be set.
 * @param value - The value to set at the specific path, in JSON formatted string.
 * @param setCondition - Set the value only if the given condition is met (within the key or path).
 * Equivalent to ['XX' | 'NX'] in the Redis API.
 * @returns If the value is successfully set, returns OK.
 * If value isn't set because of `setCondition`, returns null.
 */
export async function set(
    client: TRedisClient,
    key: string,
    path: string,
    value: string,
    setCondition?: ConditionalChange,
): Promise<"OK" | null> {
    const result = client.customCommand(
        createJsonSet(key, path, value, setCondition),
    );
    return result as unknown as "OK" | null;
}

/**
 * Retrieves the JSON value at the specified `paths` stored at `key`.
 * See https://redis.io/commands/json.get/ for more details.
 *
 * @param client - The Redis client to execute the command.
 * @param key - The key of the JSON document.
 * @param paths - The path or list of paths within the JSON document. Default is root `$`.
 * @param options - Options for formatting the string representation of the JSON data. See `JsonGetOptions`.
 * @returns A bulk string representation of the returned value.
 * If `key` doesn't exist, returns null.
 */
export async function get(
    client: TRedisClient,
    key: string,
    paths?: string | string[],
    options?: JsonGetOptions,
): Promise<string | null> {
    const result = client.customCommand(createJsonGet(key, paths, options));
    return result as unknown as string | null;
}
