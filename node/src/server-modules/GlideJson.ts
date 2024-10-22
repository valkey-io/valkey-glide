/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { BaseClient, DecoderOption, GlideString } from "../BaseClient";
import { ConditionalChange } from "../Commands";
import { GlideClient } from "../GlideClient";
import { GlideClusterClient, RouteOption } from "../GlideClusterClient";

export type ReturnTypeJson<T> = T | (T | null)[];

/**
 * Represents options for formatting JSON data, to be used in the [JSON.GET](https://valkey.io/commands/json.get/) command.
 */
export interface JsonGetOptions {
    /** The path or list of paths within the JSON document. Default is root `$`. */
    paths?: GlideString[];
    /** Sets an indentation string for nested levels. */
    indent?: GlideString;
    /** Sets a string that's printed at the end of each line. */
    newline?: GlideString;
    /** Sets a string that's put between a key and a value. */
    space?: GlideString;
    /** Optional, allowed to be present for legacy compatibility and has no other effect */
    noescape?: boolean;
}

/**
 * @internal
 */
function _jsonGetOptionsToArgs(options: JsonGetOptions): GlideString[] {
    const result: GlideString[] = [];

    if (options.paths !== undefined) {
        result.push(...options.paths);
    }

    if (options.indent !== undefined) {
        result.push("INDENT", options.indent);
    }

    if (options.newline !== undefined) {
        result.push("NEWLINE", options.newline);
    }

    if (options.space !== undefined) {
        result.push("SPACE", options.space);
    }

    if (options.noescape !== undefined) {
        result.push("NOESCAPE");
    }

    return result;
}

/**
 * @internal
 */
function _executeCommand<T>(
    client: BaseClient,
    args: GlideString[],
    options?: RouteOption & DecoderOption,
): Promise<T> {
    if (client instanceof GlideClient) {
        return (client as GlideClient).customCommand(
            args,
            options,
        ) as Promise<T>;
    } else {
        return (client as GlideClusterClient).customCommand(
            args,
            options,
        ) as Promise<T>;
    }
}

/** Module for JSON commands. */
export class GlideJson {
    /**
     * Sets the JSON value at the specified `path` stored at `key`.
     *
     * @param client The client to execute the command.
     * @param key - The key of the JSON document.
     * @param path - Represents the path within the JSON document where the value will be set.
     *      The key will be modified only if `value` is added as the last child in the specified `path`, or if the specified `path` acts as the parent of a new child being added.
     * @param value - The value to set at the specific path, in JSON formatted bytes or str.
     * @param options - (Optional) Additional parameters:
     * - (Optional) `conditionalChange` - Set the value only if the given condition is met (within the key or path).
     *      Equivalent to [`XX` | `NX`] in the module API.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     *
     * @returns If the value is successfully set, returns `"OK"`.
     *       If `value` isn't set because of `conditionalChange`, returns `null`.
     *
     * @example
     * ```typescript
     * const value = {a: 1.0, b:2};
     * const jsonStr = JSON.stringify(value);
     * const result = await GlideJson.set("doc", "$", jsonStr);
     * console.log(result); // 'OK' - Indicates successful setting of the value at path '$' in the key stored at `doc`.
     *
     * const jsonGetStr = await GlideJson.get(client, "doc", "$"); // Returns the value at path '$' in the JSON document stored at `doc` as JSON string.
     * console.log(jsonGetStr); // '[{"a":1.0,"b":2}]'
     * console.log(JSON.stringify(jsonGetStr)); //  [{"a": 1.0, "b": 2}] # JSON object retrieved from the key `doc`
     * ```
     */
    static async set(
        client: BaseClient,
        key: GlideString,
        path: GlideString,
        value: GlideString,
        options?: { conditionalChange: ConditionalChange } & DecoderOption,
    ): Promise<"OK" | null> {
        const args: GlideString[] = ["JSON.SET", key, path, value];

        if (options?.conditionalChange !== undefined) {
            args.push(options.conditionalChange);
        }

        return _executeCommand<"OK" | null>(client, args, options);
    }

    /**
     *  Retrieves the JSON value at the specified `paths` stored at `key`.
     *
     * @param client The client to execute the command.
     * @param key - The key of the JSON document.
     * @param options - (Optional) Additional parameters:
     * - (Optional) Options for formatting the byte representation of the JSON data. See {@link JsonGetOptions}.
     * - (Optional) `decoder`: see {@link DecoderOption}.
     * @returns ReturnTypeJson:
     *   - If one path is given:
     *     - For JSONPath (path starts with `$`):
     *       - Returns a stringified JSON list of bytes replies for every possible path,
     *         or a byte string representation of an empty array, if path doesn't exist.
     *         If `key` doesn't exist, returns `null`.
     *     - For legacy path (path doesn't start with `$`):
     *         Returns a byte string representation of the value in `path`.
     *         If `path` doesn't exist, an error is raised.
     *         If `key` doesn't exist, returns `null`.
     *  - If multiple paths are given:
     *         Returns a stringified JSON object in bytes, in which each path is a key, and it's corresponding value, is the value as if the path was executed in the command as a single path.
     * In case of multiple paths, and `paths` are a mix of both JSONPath and legacy path, the command behaves as if all are JSONPath paths.
     *
     * @example
     * ```typescript
     * const jsonStr = await client.jsonGet('doc', '$');
     * console.log(JSON.parse(jsonStr as string));
     * // Output: [{"a": 1.0, "b" :2}] - JSON object retrieved from the key `doc`.
     *
     * const jsonData = await client.jsonGet('doc', '$');
     * console.log(jsonData);
     * // Output: '[{"a":1.0,"b":2}]' - Returns the value at path '$' in the JSON document stored at `doc`.
     *
     * const formattedJson = await client.jsonGet('doc', {
     *     ['$.a', '$.b']
     *     indent: "  ",
     *     newline: "\n",
     *     space: " "
     * });
     * console.log(formattedJson);
     * // Output: "{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}" - Returns values at paths '$.a' and '$.b' with custom format.
     *
     * const nonExistingPath = await client.jsonGet('doc', '$.non_existing_path');
     * console.log(nonExistingPath);
     * // Output: "[]" - Empty array since the path does not exist in the JSON document.
     * ```
     */
    static async get(
        client: BaseClient,
        key: GlideString,
        options?: JsonGetOptions & DecoderOption,
    ): Promise<ReturnTypeJson<GlideString>> {
        const args = ["JSON.GET", key];

        if (options) {
            const optionArgs = _jsonGetOptionsToArgs(options);
            args.push(...optionArgs);
        }

        return _executeCommand<ReturnTypeJson<GlideString>>(
            client,
            args,
            options,
        );
    }

    /**
     * Toggles a Boolean value stored at the specified `path` within the JSON document stored at `key`.
     *
     * @param client - The client to execute the command.
     * @param key - The key of the JSON document.
     * @param options - (Optional) Additional parameters:
     * - (Optional) The JSONPath to specify. Defaults to the root if not specified.
     * @returns - For JSONPath (`path` starts with `$`), returns a list of boolean replies for every possible path, with the toggled boolean value,
     * or `null` for JSON values matching the path that are not boolean.
     * - For legacy path (`path` doesn't starts with `$`), returns the value of the toggled boolean in `path`.
     * - Note that when sending legacy path syntax, If `path` doesn't exist or the value at `path` isn't a boolean, an error is raised.
     *
     * @example
     * ```typescript
     * const value = {bool: true, nested: {bool: false, nested: {bool: 10}}};
     * const jsonStr = JSON.stringify(value);
     * const resultSet = await GlideJson.set("doc", "$", jsonStr);
     * // Output: 'OK'
     *
     * const resultToggle = await.GlideJson.toggle(client, "doc", "$.bool")
     * // Output: [false, true, null] - Indicates successful toggling of the Boolean values at path '$.bool' in the key stored at `doc`.
     *
     * const resultToggle = await.GlideJson.toggle(client, "doc", "bool")
     * // Output: true - Indicates successful toggling of the Boolean value at path 'bool' in the key stored at `doc`.
     *
     * const resultToggle = await.GlideJson.toggle(client, "doc", "bool")
     * // Output: true - Indicates successful toggling of the Boolean value at path 'bool' in the key stored at `doc`.
     *
     * const jsonGetStr = await GlideJson.get(client, "doc", "$");
     * console.log(JSON.stringify(jsonGetStr));
     * // Output: [{bool: true, nested: {bool: true, nested: {bool: 10}}}] - The updated JSON value in the key stored at `doc`.
     *
     * // Without specifying a path, the path defaults to root.
     * console.log(await GlideJson.set(client, "doc2", ".", true)); // Output: "OK"
     * console.log(await GlideJson.toggle(client,"doc2")); // Output: "false"
     * console.log(await GlideJson.toggle(client, "doc2")); // Output: "true"
     * ```
     */
    static async toggle(
        client: BaseClient,
        key: GlideString,
        options?: { path: GlideString },
    ): Promise<ReturnTypeJson<boolean>> {
        const args = ["JSON.TOGGLE", key];

        if (options !== undefined) {
            args.push(options.path);
        }

        return _executeCommand<ReturnTypeJson<boolean>>(client, args);
    }

    /**
     * Deletes the JSON value at the specified `path` within the JSON document stored at `key`.
     *
     * @param client - The client to execute the command.
     * @param key - The key of the JSON document.
     * @param options - (Optional) Additional parameters:
     * - (Optional) If `null`, deletes the entire JSON document at `key`.
     * @returns - The number of elements removed. If `key` or `path` doesn't exist, returns 0.
     *
     * @example
     * ```typescript
     * console.log(await GlideJson.set(client, "doc", "$", '{a: 1, nested: {a:2, b:3}}'));
     * // Output: "OK" - Indicates successful setting of the value at path '$' in the key stored at `doc`.
     * console.log(await GlideJson.del(client, "doc", "$..a"));
     * // Output: 2 - Indicates successful deletion of the specific values in the key stored at `doc`.
     * console.log(await GlideJson.get(client, "doc", "$"));
     * // Output: "[{nested: {b: 3}}]" - Returns the value at path '$' in the JSON document stored at `doc`.
     * console.log(await GlideJson.del(client, "doc"));
     * // Output: 1 - Deletes the entire JSON document stored at `doc`.
     * ```
     */
    static async del(
        client: BaseClient,
        key: GlideString,
        options?: { path: GlideString },
    ): Promise<number> {
        const args = ["JSON.DEL", key];

        if (options !== undefined) {
            args.push(options.path);
        }

        return _executeCommand<number>(client, args);
    }

    /**
     * Deletes the JSON value at the specified `path` within the JSON document stored at `key`. This command is
     * an alias of {@link del}.
     *
     * @param client - The client to execute the command.
     * @param key - The key of the JSON document.
     * @param options - (Optional) Additional parameters:
     * - (Optional) If `null`, deletes the entire JSON document at `key`.
     * @returns - The number of elements removed. If `key` or `path` doesn't exist, returns 0.
     *
     * @example
     * ```typescript
     * console.log(await GlideJson.set(client, "doc", "$", '{a: 1, nested: {a:2, b:3}}'));
     * // Output: "OK" - Indicates successful setting of the value at path '$' in the key stored at `doc`.
     * console.log(await GlideJson.forget(client, "doc", "$..a"));
     * // Output: 2 - Indicates successful deletion of the specific values in the key stored at `doc`.
     * console.log(await GlideJson.get(client, "doc", "$"));
     * // Output: "[{nested: {b: 3}}]" - Returns the value at path '$' in the JSON document stored at `doc`.
     * console.log(await GlideJson.forget(client, "doc"));
     * // Output: 1 - Deletes the entire JSON document stored at `doc`.
     * ```
     */
    static async forget(
        client: BaseClient,
        key: GlideString,
        options?: { path: GlideString },
    ): Promise<number> {
        const args = ["JSON.FORGET", key];

        if (options !== undefined) {
            args.push(options.path);
        }

        return _executeCommand<number>(client, args);
    }
}
