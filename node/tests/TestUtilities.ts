/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { expect } from "@jest/globals";
import { exec } from "child_process";
import { Socket } from "net";
import { promisify } from "util";
import ValkeyCluster, { TestTLSConfig } from "../../utils/TestUtils";
import {
    BaseClient,
    BaseClientConfiguration,
    Batch,
    BitFieldGet,
    BitFieldSet,
    BitOffset,
    BitOffsetMultiplier,
    BitmapIndexType,
    BitwiseOperation,
    ClusterBatch,
    ConditionalChange,
    Decoder,
    FlushMode,
    FunctionListResponse,
    FunctionStatsSingleResponse,
    GeoUnit,
    GeospatialData,
    GlideClient,
    GlideClusterClient,
    GlideReturnType,
    GlideString,
    HashFieldConditionalChange,
    InfBoundary,
    InfoOptions,
    InsertPosition,
    JsonBatch,
    ListDirection,
    Logger,
    ProtocolVersion,
    ReturnTypeMap,
    ScoreFilter,
    SignedEncoding,
    SortOrder,
    TimeUnit,
    UnsignedEncoding,
    convertRecordToGlideRecord,
} from "../build-ts";
const execAsync = promisify(exec);

export function getRandomKey() {
    // generate key without using getRandomKey
    const randomKey = Math.random().toString(36).substring(2, 9);
    return randomKey; // return the generated random key
}

/* eslint-disable @typescript-eslint/no-explicit-any */
function intoArrayInternal(obj: any, builder: string[]) {
    if (obj == null) {
        builder.push("null");
    } else if (typeof obj === "string") {
        builder.push(obj);
    } else if (typeof obj === "number") {
        builder.push(obj.toPrecision(3));
    } else if (obj instanceof Uint8Array) {
        builder.push(obj.toString());
    } else if (obj instanceof Array) {
        for (const item of obj) {
            intoArrayInternal(item, builder);
        }
    } else if (obj instanceof Set) {
        const arr = Array.from(obj);
        arr.sort();

        for (const item of arr) {
            intoArrayInternal(item, builder);
        }
    } else if (obj instanceof Map) {
        const sortedArr = Array.from(obj.entries()).sort();

        for (const [key, value] of sortedArr) {
            intoArrayInternal(key, builder);
            intoArrayInternal(value, builder);
        }
    } else if (typeof obj[Symbol.iterator] === "function") {
        // iterable, recurse into children
        for (const item of obj) {
            intoArrayInternal(item, builder);
        }
    } else {
        const sortedArr = Array.from(Object.entries(obj)).sort();

        for (const [k, v] of sortedArr) {
            intoArrayInternal(k, builder);
            intoArrayInternal(v, builder);
        }
    }
}

// The function is used to check if the cluster is ready with the count nodes known command using the client supplied.
// The way it works is by parsing the response of the CLUSTER INFO command and checking if the cluster_state is ok and the cluster_known_nodes is equal to the count.
// If so, we know the cluster is ready, and it has the amount of nodes we expect.
export async function waitForClusterReady(
    client: GlideClusterClient,
    count: number,
): Promise<boolean> {
    const timeout = 20000; // 20 seconds timeout in milliseconds
    const startTime = Date.now();

    while (true) {
        if (Date.now() - startTime > timeout) {
            return false;
        }

        const clusterInfo = await client.customCommand(["CLUSTER", "INFO"]);
        // parse the response
        const clusterInfoMap = new Map<string, string>();

        if (clusterInfo) {
            const clusterInfoLines = clusterInfo
                .toString()
                .split("\n")
                .filter((line) => line.length > 0);

            for (const line of clusterInfoLines) {
                const [key, value] = line.split(":");

                clusterInfoMap.set(key.trim(), value.trim());
            }

            if (
                clusterInfoMap.get("cluster_state") == "ok" &&
                Number(clusterInfoMap.get("cluster_known_nodes")) == count
            ) {
                break;
            }
        }

        await new Promise((resolve) => setTimeout(resolve, 2000));
    }

    return true;
}

/**
 * accept any variable `v` and convert it into String, recursively
 */
export function intoString(v: any): string {
    const builder: string[] = [];
    intoArrayInternal(v, builder);
    return builder.join("");
}

/**
 * accept any variable `v` and convert it into array of string
 */
export function intoArray(v: any): string[] {
    const result: string[] = [];
    intoArrayInternal(v, result);
    return result;
}

/**
 * Convert array of strings into array of `Uint8Array`
 */
export function convertStringArrayToBuffer(value: string[]): Uint8Array[] {
    const bytesarr: Uint8Array[] = [];

    for (const str of value) {
        bytesarr.push(Buffer.from(str));
    }

    return bytesarr;
}

export class Checker {
    left: string;

    constructor(left: any) {
        this.left = intoString(left);
    }

    toEqual(right: any) {
        right = intoString(right);
        return expect(this.left).toEqual(right);
    }
}

export function checkSimple(left: any): Checker {
    return new Checker(left);
}

export interface Client {
    set: (key: string, value: string) => Promise<GlideString | "OK" | null>;
    get: (key: string) => Promise<GlideString | null>;
}

export async function checkWhichCommandAvailable(
    valkeyCommand: string,
    redisCommand: string,
): Promise<string> {
    try {
        if (await checkCommandAvailability(valkeyCommand)) {
            return valkeyCommand;
        }
    } catch {
        // ignore
    }

    if (await checkCommandAvailability(redisCommand)) {
        return redisCommand;
    }

    throw new Error("No available command found.");
}

export async function GetAndSetRandomValue(client: Client) {
    const key = getRandomKey();
    // Adding random repetition, to prevent the inputs from always having the same alignment.
    const value = getRandomKey() + "0".repeat(Math.random() * 7);
    const setResult = await client.set(key, value);
    expect(intoString(setResult)).toEqual("OK");
    const result = await client.get(key);
    expect(intoString(result)).toEqual(value);
}

/**
 * Parse CLIENT LIST output and count the number of client connections
 * @param output - The output from CLIENT LIST command
 * @returns The number of connected clients
 */
export async function getClientListOutputCount(
    output: GlideReturnType,
): Promise<number> {
    if (output === null) {
        return 0;
    }

    const text =
        output instanceof Buffer ? output.toString("utf8") : String(output);

    if (!text.trim()) {
        return 0;
    }

    return text.split("\n").filter((line) => line.trim().length > 0).length;
}

/**
 * Get the count of client connections for a client
 * @param client - GlideClient or GlideClusterClient instance
 * @returns The number of connected clients
 */
export async function getClientCount(
    client: GlideClient | GlideClusterClient,
): Promise<number> {
    if (client instanceof GlideClusterClient) {
        // For cluster client, execute CLIENT LIST on all nodes
        const result = await client.customCommand(["CLIENT", "LIST"], {
            route: "allNodes",
        });

        // Sum counts from all nodes
        let totalCount = 0;

        for (const nodeOutput of Object.values(
            result as Record<string, GlideReturnType>,
        )) {
            totalCount += await getClientListOutputCount(nodeOutput);
        }

        return totalCount;
    } else {
        // For standalone client
        const result = await client.customCommand(["CLIENT", "LIST"]);
        return await getClientListOutputCount(result);
    }
}

/**
 * Get the expected number of new connections when a lazy client is initialized
 * @param client - GlideClient or GlideClusterClient instance
 * @returns The number of expected new connections
 */
export async function getExpectedNewConnections(
    client: GlideClient | GlideClusterClient,
): Promise<number> {
    if (client instanceof GlideClusterClient) {
        // For cluster, get node count and multiply by 2 (2 connections per node)
        const result = await client.customCommand(["CLUSTER", "NODES"]);
        const nodesInfo = String(result).trim().split("\n");
        return nodesInfo.length * 2;
    } else {
        // For standalone, always expect 1 new connection
        return 1;
    }
}

export async function flushallOnPort(port: number): Promise<void> {
    try {
        const command = await checkWhichCommandAvailable(
            "valkey-cli",
            "redis-cli",
        );
        exec(`${command} -p ${port} flushall`, (error) => {
            if (error) {
                console.error(`exec error: ${error}`);
                return;
            }
        });
    } catch (error) {
        console.error(`Error flushing on port ${port}: ${error}`);
    }
}

/**
 * Parses a string of endpoints into an array of host-port pairs.
 * Each endpoint in the string should be in the format 'host:port', separated by commas.
 *
 * @param endpointsStr - The string containing endpoints in the format 'host:port', separated by commas.
 * @returns An array of host-port pairs parsed from the endpoints string.
 * @throws If the endpoints string or its format is invalid.
 * @example
 * ```typescript
 * const endpointsStr = 'localhost:8080,example.com:3000,192.168.1.100:5000';
 * const endpoints = parseEndpoints(endpointsStr);
 * console.log(endpoints);
 * // Output: [['localhost', 8080], ['example.com', 3000], ['192.168.1.100', 5000]]
 * ```
 */
export const parseEndpoints = (endpointsStr: string): [string, number][] => {
    try {
        const endpoints: string[][] = endpointsStr
            .split(",")
            .map((endpoint) => endpoint.split(":"));
        endpoints.forEach((endpoint) => {
            // Check if each endpoint has exactly two elements (host and port)
            if (endpoint.length !== 2) {
                throw new Error(
                    "Each endpoint should be in the format 'host:port'.\nEndpoints should be separated by commas.",
                );
            }

            // Extract host and port
            const [host, portStr] = endpoint;

            // Check if both host and port are specified and if port is a valid integer
            if (!host || !portStr || !/^\d+$/.test(portStr)) {
                throw new Error(
                    "Both host and port should be specified and port should be a valid integer.",
                );
            }
        });

        // Convert port strings to numbers and return the result
        return endpoints.map(([host, portStr]) => [host, Number(portStr)]);
    } catch (error) {
        throw new Error(
            "Invalid endpoints format: " + (error as Error).message,
        );
    }
};

/// This function takes the first result of the response if it got more than one response (like cluster responses).
export function getFirstResult(
    res: string | number | Record<string, string> | Record<string, number>,
): string | number {
    if (typeof res == "string" || typeof res == "number") {
        return res;
    }

    return Object.values(res).at(0);
}

// TODO use matcher instead of predicate
/** Check a multi-node response from a cluster. */
export function checkClusterMultiNodeResponse(
    res: object,
    predicate: (value: GlideReturnType) => void,
) {
    for (const nodeResponse of Object.values(res)) {
        predicate(nodeResponse);
    }
}

/** Check a response from a cluster. Response could be either single-node (value) or multi-node (string-value map). */
export function checkClusterResponse(
    res: object,
    singleNodeRoute: boolean,
    predicate: (value: GlideReturnType) => void,
) {
    if (singleNodeRoute) predicate(res as GlideReturnType);
    else checkClusterMultiNodeResponse(res, predicate);
}

/** Generate a String of LUA library code. */
export function generateLuaLibCode(
    libName: string,
    functions: Map<string, string>,
    readonly: boolean,
): string {
    let code = `#!lua name=${libName}\n`;

    for (const [functionName, functionBody] of functions) {
        code += `redis.register_function{ function_name = '${functionName}', callback = function(keys, args) ${functionBody} end`;
        if (readonly) code += ", flags = { 'no-writes' }";
        code += " }\n";
    }

    return code;
}

/**
 * Create a lua lib with a function which runs an endless loop up to timeout sec.
 * Execution takes at least 5 sec regardless of the timeout configured.
 */
export function createLuaLibWithLongRunningFunction(
    libName: string,
    funcName: string,
    timeout: number,
    readOnly: boolean,
): string {
    const code =
        "#!lua name=$libName\n" +
        "local function $libName_$funcName(keys, args)\n" +
        "  local started = tonumber(redis.pcall('time')[1])\n" +
        // fun fact - redis does no write if 'no-writes' flag is set
        "  redis.pcall('set', keys[1], 42)\n" +
        "  while (true) do\n" +
        "    local now = tonumber(redis.pcall('time')[1])\n" +
        "    if now > started + $timeout then\n" +
        "      return 'Timed out $timeout sec'\n" +
        "    end\n" +
        "  end\n" +
        "  return 'OK'\n" +
        "end\n" +
        "redis.register_function{\n" +
        "function_name='$funcName',\n" +
        "callback=$libName_$funcName,\n" +
        (readOnly ? "flags={ 'no-writes' }\n" : "") +
        "}";
    return code
        .replaceAll("$timeout", timeout.toString())
        .replaceAll("$funcName", funcName)
        .replaceAll("$libName", libName);
}

export async function waitForNotBusy(client: GlideClusterClient | GlideClient) {
    // If function wasn't killed, and it didn't time out - it blocks the server and cause rest test to fail.
    let isBusy = true;

    do {
        try {
            await client.functionKill();
        } catch (err) {
            // should throw `notbusy` error, because the function should be killed before
            if ((err as Error).message.toLowerCase().includes("notbusy")) {
                isBusy = false;
            }
        }
    } while (isBusy);
}

/**
 * Create a lua script which runs an endless loop up to timeout sec.
 * Execution takes at least 5 sec regardless of the timeout
 */
export function createLongRunningLuaScript(
    timeout: number,
    set: boolean,
): string {
    const script =
        (set ? "redis.call('SET', KEYS[1], 'value')\n" : "") +
        " local started = tonumber(redis.pcall('time')[1])\n" +
        " while (true) do\n" +
        "  local now = tonumber(redis.pcall('time')[1])\n" +
        "   if now > started + $timeout then\n" +
        "     return 'Timed out $timeout sec'\n" +
        "   end\n" +
        " end\n";

    return script.replaceAll("$timeout", timeout.toString());
}

export async function testTeardown(
    cluster_mode: boolean,
    option: BaseClientConfiguration,
    existingClient?: BaseClient,
) {
    let client: GlideClient | GlideClusterClient | undefined;
    let clientCreated = false;

    try {
        // Try to reuse existing client if available
        if (existingClient) {
            try {
                client = existingClient as GlideClient | GlideClusterClient;
                // Test if client is still usable by trying a quick operation
                await client.ping();
            } catch {
                // If existing client fails, create a new one
                client = cluster_mode
                    ? await GlideClusterClient.createClient(option)
                    : await GlideClient.createClient(option);
                clientCreated = true;
            }
        } else {
            // Create new client if existing one is not available
            client = cluster_mode
                ? await GlideClusterClient.createClient(option)
                : await GlideClient.createClient(option);
            clientCreated = true;
        }

        await client.flushall();
    } catch (error) {
        // If teardown fails, log the error but don't throw to avoid masking the original test failure
        Logger.log(
            "warn",
            "TestUtilities",
            "Test teardown failed",
            error as Error,
        );
    } finally {
        // Only close client if we created it (don't close existing client)
        if (client && clientCreated) {
            client.close();
        }
    }
}

export const getClientConfigurationOption = (
    addresses: [string, number][],
    protocol: ProtocolVersion,
    configOverrides?: Partial<BaseClientConfiguration>,
): BaseClientConfiguration => {
    return {
        addresses: addresses.map(([host, port]) => ({
            host,
            port,
        })),
        protocol,
        useTLS: global.TLS ?? false,
        requestTimeout: 1000,
        ...configOverrides,
    };
};

export async function flushAndCloseClient(
    cluster_mode: boolean,
    addresses: [string, number][] | undefined,
    client?: BaseClient,
    tlsConfig?: TestTLSConfig,
) {
    try {
        if (addresses) {
            await testTeardown(
                cluster_mode,
                getClientConfigurationOption(addresses, ProtocolVersion.RESP3, {
                    ...tlsConfig,
                    requestTimeout: 1500, // Reduced timeout to fail faster on socket exhaustion
                }),
                client, // Pass existing client to reuse if possible
            );
        }
    } finally {
        // Close the client
        client?.close();

        // Add a small delay to allow sockets to be properly released
        // This prevents socket exhaustion when running many tests sequentially
        await new Promise((resolve) => setTimeout(resolve, 10));
    }
}

/**
 * Compare two maps by converting them to JSON strings and checking for equality, including property order.
 *
 * @param map - The first map to compare.
 * @param map2 - The second map to compare.
 * @returns True if the maps are equal.
 * @remarks This function is used to compare maps, including their property order.
 * Direct comparison with `expect(map).toEqual(map2)` might ignore the order of properties,
 * whereas this function considers property order in the comparison by converting the maps to JSON strings.
 * This ensures a stricter comparison that takes property order into account.
 *
 * @example
 * ```typescript
 * const mapA = { name: 'John', age: 30 };
 * const mapB = { age: 30, name: 'John' };
 *
 * // Direct comparison will pass because it ignores property order
 * expect(mapA).toEqual(mapB); // This will pass
 *
 * // Correct comparison using compareMaps function
 * compareMaps(mapA, mapB); // This will return false due to different property order
 * ```
 */
export function compareMaps(
    map: Record<string, unknown>,
    map2: Record<string, unknown>,
): boolean {
    return JSON.stringify(map) == JSON.stringify(map2);
}

/**
 * Validate whether `FUNCTION LIST` response contains required info.
 *
 * @param response - The response from server.
 * @param libName - Expected library name.
 * @param functionDescriptions - Expected function descriptions. Key - function name, value - description.
 * @param functionFlags - Expected function flags. Key - function name, value - flags set.
 * @param libCode - Expected library to check if given.
 */
export function checkFunctionListResponse(
    response: FunctionListResponse,
    libName: GlideString,
    functionDescriptions: Map<string, GlideString | null>,
    functionFlags: Map<string, GlideString[]>,
    libCode?: GlideString,
) {
    expect(response.length).toBeGreaterThan(0);
    let hasLib = false;

    for (const lib of response) {
        hasLib =
            typeof libName === "string"
                ? libName === lib["library_name"]
                : (libName as Buffer).compare(lib["library_name"] as Buffer) ==
                0;

        if (hasLib) {
            const functions = lib["functions"];
            expect(functions.length).toEqual(functionDescriptions.size);

            for (const functionData of functions) {
                const functionInfo = functionData as Record<
                    string,
                    GlideString | GlideString[]
                >;
                const name = functionInfo["name"] as GlideString;
                const flags = functionInfo["flags"] as GlideString[];

                expect(functionInfo["description"]).toEqual(
                    functionDescriptions.get(name.toString()),
                );

                expect(flags).toEqual(functionFlags.get(name.toString()));
            }

            if (libCode) {
                expect(lib["library_code"]).toEqual(libCode);
            }

            break;
        }
    }

    expect(hasLib).toBeTruthy();
}

/**
 * Validate whether `FUNCTION STATS` response contains required info.
 *
 * @param response - The response from server.
 * @param runningFunction - Command line of running function expected. Empty, if nothing expected.
 * @param libCount - Expected libraries count.
 * @param functionCount - Expected functions count.
 */
export function checkFunctionStatsResponse(
    response: FunctionStatsSingleResponse,
    runningFunction: GlideString[],
    libCount: number,
    functionCount: number,
) {
    if (response.running_script === null && runningFunction.length > 0) {
        fail("No running function info");
    }

    if (response.running_script !== null && runningFunction.length == 0) {
        fail(
            "Unexpected running function info: " +
            (response.running_script.command as string[]).join(" "),
        );
    }

    if (response.running_script !== null) {
        expect(response.running_script.command).toEqual(runningFunction);
        // command line format is:
        // fcall|fcall_ro <function name> <num keys> <key>* <arg>*
        expect(response.running_script.name).toEqual(runningFunction[1]);
    }

    expect(response.engines).toEqual({
        LUA: { libraries_count: libCount, functions_count: functionCount },
    });
}

/**
 * Checks if the given test is a known flaky test. If it is, we test it accordingly.
 *
 * This function returns false in two cases:
 *  1. The test is not a known flaky test (i.e., we haven't created a case to specially test it).
 *  2. An error occurs during the processing of the responses. Then, we default back to regular testing instead.
 *
 * Otherwise, returns true to prevent redundant testing.
 *
 * @param testName - The name of the test.
 * @param response - One of the transaction results received from `exec` call.
 * @param expectedResponse - One of the expected result data from {@link batchTest}.
 */
export function checkAndHandleFlakyTests(
    testName: string,
    response: GlideReturnType | undefined,
    expectedResponse: GlideReturnType,
): boolean {
    switch (testName) {
        case "xpendingWithOptions(key9, groupName1, -, +, 10)": {
            // Response Type: [ [id: string, consumerName: string, idleTime: number, deliveryCount: number ] ]
            if (!Array.isArray(expectedResponse) || !Array.isArray(response)) {
                return false;
            }

            const [responseArray] = response as any[];
            const [expectedResponseArray] = expectedResponse as any[];

            for (let i = 0; i < responseArray.length; i++) {
                if (i == 2) {
                    // Since idleTime will vary, check that it does not exceed a threshold instead
                    expect(
                        Math.abs(expectedResponseArray[i] - responseArray[i]),
                    ).toBeLessThan(2);
                } else {
                    expect(responseArray[i]).toEqual(expectedResponseArray[i]);
                }
            }

            break;
        }

        case "httl(key4, [field, field2, field3])": {
            // Response Type: number[] - TTL values for hash fields
            if (expectedResponse === "TTL_ARRAY" && Array.isArray(response)) {
                const ttlArray = response as number[];
                expect(ttlArray.length).toEqual(3);
                // field and field2 should have positive TTL values (they have expiration)
                expect(ttlArray[0]).toBeGreaterThan(0);
                expect(ttlArray[1]).toBeGreaterThan(0);
                // field3 doesn't exist, should return -2
                expect(ttlArray[2]).toEqual(-2);
                return true;
            }
            return false;
        }

        default: {
            // All other tests
            return false;
        }
    }

    return true;
}

/**
 * Check batch response.
 * @param response - Batch result received from `exec` call.
 * @param expectedResponseData - Expected result data from {@link batchTest}.
 */
export function validateBatchResponse(
    response: GlideReturnType[] | null,
    expectedResponseData: [string, GlideReturnType][],
) {
    const failedChecks: string[] = [];

    for (let i = 0; i < expectedResponseData.length; i++) {
        const [testName, expectedResponse] = expectedResponseData[i];

        try {
            if (
                !checkAndHandleFlakyTests(
                    testName,
                    response?.[i],
                    expectedResponse,
                )
            ) {
                expect(response?.[i]).toEqual(expectedResponse);
            }
        } catch {
            const expected =
                expectedResponse instanceof Map
                    ? JSON.stringify(Array.from(expectedResponse.entries()))
                    : JSON.stringify(expectedResponse);
            const actual =
                response?.[i] instanceof Map
                    ? JSON.stringify(
                        Array.from(
                            (response?.[i] as ReturnTypeMap)?.entries(),
                        ),
                    )
                    : JSON.stringify(response?.[i]);
            failedChecks.push(
                `${testName} failed, expected <${expected}>, actual <${actual}>`,
            );
        }
    }

    if (failedChecks.length > 0) {
        throw new Error(
            "Checks failed in batch response:\n" + failedChecks.join("\n"),
        );
    }
}

/**
 * Populates a batch with commands to test the decodable commands with various default decoders.
 * @param baseBatch - A batch.
 * @param valueEncodedResponse - Represents the encoded response of "value" to compare
 * @returns Array of tuples, where first element is a test name/description, second - expected return value.
 */
export async function encodableBatchTest(
    baseBatch: Batch | ClusterBatch,
    valueEncodedResponse: GlideReturnType,
): Promise<[string, GlideReturnType][]> {
    const key = "{key}" + getRandomKey(); // string
    const value = "value";
    // array of tuples - first element is test name/description, second - expected return value
    const responseData: [string, GlideReturnType][] = [];

    baseBatch.set(key, value);
    responseData.push(["set(key, value)", "OK"]);
    baseBatch.get(key);
    responseData.push(["get(key)", valueEncodedResponse]);

    return responseData;
}

/**
 * Populates a batch with commands to test the decoded response.
 * @param baseBatch - A batch.
 * @returns Array of tuples, where first element is a test name/description, second - expected return value.
 */
export async function encodedBatchTest(
    baseBatch: Batch | ClusterBatch,
): Promise<[string, GlideReturnType][]> {
    const key1 = "{key}" + getRandomKey(); // string
    const key2 = "{key}" + getRandomKey(); // string
    const key = "dumpKey";
    const dumpResult = Buffer.from([
        0, 5, 118, 97, 108, 117, 101, 11, 0, 232, 41, 124, 75, 60, 53, 114, 231,
    ]);
    const value = "value";
    const valueEncoded = Buffer.from(value);
    // array of tuples - first element is test name/description, second - expected return value
    const responseData: [string, GlideReturnType][] = [];

    baseBatch.set(key1, value);
    responseData.push(["set(key1, value)", "OK"]);
    baseBatch.set(key2, value);
    responseData.push(["set(key2, value)", "OK"]);
    baseBatch.get(key1);
    responseData.push(["get(key1)", valueEncoded]);
    baseBatch.get(key2);
    responseData.push(["get(key2)", valueEncoded]);

    baseBatch.set(key, value);
    responseData.push(["set(key, value)", "OK"]);
    baseBatch.customCommand(["DUMP", key]);
    responseData.push(['customCommand(["DUMP", key])', dumpResult]);
    baseBatch.del([key]);
    responseData.push(["del(key)", 1]);
    baseBatch.get(key);
    responseData.push(["get(key)", null]);
    baseBatch.customCommand(["RESTORE", key, "0", dumpResult]);
    responseData.push([
        'customCommand(["RESTORE", key, "0", dumpResult])',
        "OK",
    ]);
    baseBatch.get(key);
    responseData.push(["get(key)", valueEncoded]);

    return responseData;
}

/**
 * @internal
 */
function decodeString(str: string, decoder: Decoder): GlideString {
    if (decoder == Decoder.Bytes) {
        return Buffer.from(str);
    }

    return str;
}

/**
 * Populates a batch with commands to test.
 * @param baseBatch - A batch.
 * @returns Array of tuples, where first element is a test name/description, second - expected return value.
 */
export async function batchTest(
    baseBatch: Batch | ClusterBatch,
    cluster: ValkeyCluster,
    decoder: Decoder,
): Promise<[string, GlideReturnType][]> {
    const clusterMode = baseBatch instanceof ClusterBatch;
    // initialize key values to work within the same hashslot
    const [
        key1, // string
        key2, // string
        key3, // string
        key4, // hash
        key5, // list
        key6, // list
        key7, // set
        key8, // sorted set
        key9, // stream
        key10, // string
        key11, // hyper log log
        key12, // sorted set
        key13, // sorted set
        key14, // sorted set
        key15, // list
        key16, // list
        key17, // bitmap
        key18, // Geospatial Data/ZSET
        key19, // bitmap
        key20, // list
        key21, // list for sort
        key22, // list for sort
        key23, // zset random
        key24, // list value
        key25, // Geospatial Data/ZSET
        key26, // sorted set
        key27, // sorted set
    ] = Array.from({ length: 27 }, () =>
        decodeString("{key}" + getRandomKey(), decoder),
    );

    // initialize non-key values
    const [value, groupName1, groupName2, consumer] = Array.from(
        { length: 4 },
        () => decodeString(getRandomKey(), decoder),
    );

    const fieldStr = getRandomKey();
    const [
        field,
        field1,
        field2,
        field3,
        field4,
        value1,
        value2,
        value3,
        foo,
        bar,
        baz,
        test,
        one,
        two,
        three,
        underscore,
        non_existing_member,
        member1,
        member2,
        member3,
        member4,
        member5,
        member6,
        member7,
        palermo,
        catania,
    ] = [
            decodeString(fieldStr, decoder),
            decodeString(fieldStr + 1, decoder),
            decodeString(fieldStr + 2, decoder),
            decodeString(fieldStr + 3, decoder),
            decodeString(fieldStr + 4, decoder),
            decodeString("value1", decoder),
            decodeString("value2", decoder),
            decodeString("value3", decoder),
            decodeString("foo", decoder),
            decodeString("bar", decoder),
            decodeString("baz", decoder),
            decodeString("test_message", decoder),
            decodeString("one", decoder),
            decodeString("two", decoder),
            decodeString("three", decoder),
            decodeString("_", decoder),
            decodeString("non_existing_member", decoder),
            decodeString("member1", decoder),
            decodeString("member2", decoder),
            decodeString("member3", decoder),
            decodeString("member4", decoder),
            decodeString("member5", decoder),
            decodeString("member6", decoder),
            decodeString("member7", decoder),
            decodeString("Palermo", decoder),
            decodeString("Catania", decoder),
        ];

    // array of tuples - first element is test name/description, second - expected return value
    const responseData: [string, GlideReturnType][] = [];

    baseBatch.publish(test, key1);
    responseData.push(['publish("test_message", key1)', 0]);
    baseBatch.pubsubChannels();
    responseData.push(["pubsubChannels()", []]);
    baseBatch.pubsubNumPat();
    responseData.push(["pubsubNumPat()", 0]);
    baseBatch.pubsubNumSub([]);
    responseData.push(["pubsubNumSub()", []]);

    baseBatch.flushall();
    responseData.push(["flushall()", "OK"]);
    baseBatch.flushall(FlushMode.SYNC);
    responseData.push(["flushall(FlushMode.SYNC)", "OK"]);
    baseBatch.flushdb();
    responseData.push(["flushdb()", "OK"]);
    baseBatch.flushdb(FlushMode.SYNC);
    responseData.push(["flushdb(FlushMode.SYNC)", "OK"]);
    baseBatch.dbsize();
    responseData.push(["dbsize()", 0]);
    baseBatch.set(key1, foo);
    responseData.push(['set(key1, "foo")', "OK"]);
    baseBatch.set(key1, bar, { returnOldValue: true });
    responseData.push(['set(key1, "bar", {returnOldValue: true})', "foo"]);

    if (!cluster.checkIfServerVersionLessThan("6.2.0")) {
        baseBatch.getex(key1);
        responseData.push(["getex(key1)", "bar"]);
        baseBatch.getex(key1, { type: TimeUnit.Seconds, duration: 1 });
        responseData.push([
            'getex(key1, {expiry: { type: "seconds", count: 1 }})',
            "bar",
        ]);
    }

    baseBatch.randomKey();
    responseData.push(["randomKey()", key1.toString()]);
    baseBatch.getrange(key1, 0, -1);
    responseData.push(["getrange(key1, 0, -1)", "bar"]);
    baseBatch.getdel(key1);
    responseData.push(["getdel(key1)", "bar"]);
    baseBatch.set(key1, bar);
    responseData.push(['set(key1, "bar")', "OK"]);
    baseBatch.objectEncoding(key1);
    responseData.push(["objectEncoding(key1)", "embstr"]);
    baseBatch.type(key1);
    responseData.push(["type(key1)", "string"]);
    baseBatch.echo(value);
    responseData.push(["echo(value)", value.toString()]);
    baseBatch.persist(key1);
    responseData.push(["persist(key1)", false]);

    if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
        baseBatch.expireTime(key1);
        responseData.push(["expiretime(key1)", -1]);

        baseBatch.pexpireTime(key1);
        responseData.push(["pexpiretime(key1)", -1]);
    }

    baseBatch.set(key2, baz, { returnOldValue: true });
    responseData.push(['set(key2, "baz", { returnOldValue: true })', null]);
    baseBatch.customCommand(["MGET", key1, key2]);
    responseData.push(['customCommand(["MGET", key1, key2])', ["bar", "baz"]]);
    baseBatch.mset([{ key: key3, value }]);
    responseData.push(["mset({ [key3]: value })", "OK"]);
    baseBatch.msetnx([{ key: key3, value }]);
    responseData.push(["msetnx({ [key3]: value })", false]);
    baseBatch.mget([key1, key2]);
    responseData.push(["mget([key1, key2])", ["bar", "baz"]]);
    baseBatch.strlen(key1);
    responseData.push(["strlen(key1)", 3]);
    baseBatch.setrange(key1, 0, "GLIDE");
    responseData.push(["setrange(key1, 0, 'GLIDE')", 5]);
    baseBatch.del([key1]);
    responseData.push(["del([key1])", 1]);
    baseBatch.append(key1, "bar");
    responseData.push(["append(key1, value)", 3]);
    baseBatch.del([key1]);
    responseData.push(["del([key1])", 1]);
    baseBatch.hset(key4, [{ field, value }]);
    responseData.push(["hset(key4, { [field]: value })", 1]);
    baseBatch.hscan(key4, "0");
    responseData.push([
        'hscan(key4, "0")',
        ["0", [field.toString(), value.toString()]],
    ]);

    if (!cluster.checkIfServerVersionLessThan("8.0.0")) {
        baseBatch.hscan(key4, "0", { noValues: false });
        responseData.push([
            'hscan(key4, "0", {noValues: false})',
            ["0", [field.toString(), value.toString()]],
        ]);
        baseBatch.hscan(key4, "0", {
            match: "*",
            count: 20,
            noValues: true,
        });
        responseData.push([
            'hscan(key4, "0", {match: "*", count: 20, noValues:true})',
            ["0", [field.toString()]],
        ]);
    }

    baseBatch.hscan(key4, "0", { match: "*", count: 20 });
    responseData.push([
        'hscan(key4, "0", {match: "*", count: 20})',
        ["0", [field.toString(), value.toString()]],
    ]);
    baseBatch.hstrlen(key4, field);
    responseData.push(["hstrlen(key4, field)", value.length]);
    baseBatch.hlen(key4);
    responseData.push(["hlen(key4)", 1]);
    baseBatch.hrandfield(key4);
    responseData.push(["hrandfield(key4)", field.toString()]);
    baseBatch.hrandfieldCount(key4, -2);
    responseData.push([
        "hrandfieldCount(key4, -2)",
        [field.toString(), field.toString()],
    ]);
    baseBatch.hrandfieldWithValues(key4, 2);
    responseData.push([
        "hrandfieldWithValues(key4, 2)",
        [[field.toString(), value.toString()]],
    ]);
    baseBatch.hsetnx(key4, field, value);
    responseData.push(["hsetnx(key4, field, value)", false]);
    baseBatch.hvals(key4);
    responseData.push(["hvals(key4)", [value.toString()]]);
    baseBatch.hkeys(key4);
    responseData.push(["hkeys(key4)", [field.toString()]]);
    baseBatch.hget(key4, field);
    responseData.push(["hget(key4, field)", value.toString()]);
    baseBatch.hgetall(key4);
    responseData.push([
        "hgetall(key4)",
        [{ key: field.toString(), value: value.toString() }],
    ]);
    baseBatch.hdel(key4, [field]);
    responseData.push(["hdel(key4, [field])", 1]);
    baseBatch.hmget(key4, [field]);
    responseData.push(["hmget(key4, [field])", [null]]);
    baseBatch.hexists(key4, field);
    responseData.push(["hexists(key4, field)", false]);
    baseBatch.hrandfield(key4);
    responseData.push(["hrandfield(key4)", null]);

    // HSETEX tests - only run if server version is 9.0.0 or higher
    if (!cluster.checkIfServerVersionLessThan("9.0.0")) {
        // Test basic HSETEX with expiry
        baseBatch.hsetex(key4, { [field.toString()]: value }, {
            expiry: { type: TimeUnit.Seconds, count: 60 },
        });
        responseData.push(["hsetex(key4, { [field]: value }, { expiry: { type: TimeUnit.Seconds, count: 60 } })", 1]);

        // Test HSETEX with KEEPTTL
        baseBatch.hsetex(key4, { [field2.toString()]: value }, {
            expiry: "KEEPTTL",
        });
        responseData.push(["hsetex(key4, { [field2]: value }, { expiry: 'KEEPTTL' })", 1]);

        // Test HSETEX with conditional changes
        baseBatch.hsetex(key4, { [field3.toString()]: value }, {
            conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
            expiry: { type: TimeUnit.Seconds, count: 60 },
        });
        responseData.push(["hsetex(key4, { [field3]: value }, { conditionalChange: ConditionalChange.ONLY_IF_EXISTS, expiry: { type: TimeUnit.Seconds, count: 60 } })", 1]);

        // Test HSETEX with field conditional changes
        baseBatch.hsetex(key4, { [field4.toString()]: value }, {
            fieldConditionalChange: HashFieldConditionalChange.ONLY_IF_NONE_EXIST,
            expiry: { type: TimeUnit.Seconds, count: 60 },
        });
        responseData.push(["hsetex(key4, { [field4]: value }, { fieldConditionalChange: HashFieldConditionalChange.ONLY_IF_NONE_EXIST, expiry: { type: TimeUnit.Seconds, count: 60 } })", 1]);

        // HGETEX tests - only run if server version is 9.0.0 or higher
        // Test basic HGETEX with expiry
        baseBatch.hgetex(key4, [field.toString(), field2.toString()], {
            expiry: { type: TimeUnit.Seconds, count: 60 },
        });
        responseData.push(["hgetex(key4, [field, field2], { expiry: { type: TimeUnit.Seconds, count: 60 } })", [value, value]]);

        // Test HGETEX with PERSIST
        baseBatch.hgetex(key4, [field.toString()], {
            expiry: "PERSIST",
        });
        responseData.push(["hgetex(key4, [field], { expiry: 'PERSIST' })", [value]]);

        // Test HGETEX with KEEPTTL
        baseBatch.hgetex(key4, [field2.toString()], {
            expiry: "KEEPTTL",
        });
        responseData.push(["hgetex(key4, [field2], { expiry: 'KEEPTTL' })", [value]]);

        // Test HTTL
        baseBatch.httl(key4, [field.toString(), field2.toString(), field3.toString()]);
        // Note: TTL values are dynamic, so we'll validate the structure rather than exact values
        responseData.push(["httl(key4, [field, field2, field3])", "TTL_ARRAY"]);
    }

    baseBatch.lpush(key5, [field1, field2, field3, field4]);
    responseData.push(["lpush(key5, [1, 2, 3, 4])", 4]);

    if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
        baseBatch.lpush(key24, [field1.toString(), field2.toString()]);
        responseData.push(["lpush(key22, [1, 2])", 2]);
        baseBatch.lmpop([key24], ListDirection.LEFT);
        responseData.push([
            "lmpop([key24], ListDirection.LEFT)",
            [{ key: key24.toString(), value: [field2.toString()] }],
        ]);
        baseBatch.lpush(key24, [field2]);
        responseData.push(["lpush(key24, [2])", 2]);
        baseBatch.blmpop([key24], ListDirection.LEFT, 0.1, 1);
        responseData.push([
            "blmpop([key24], ListDirection.LEFT, 0.1, 1)",
            [{ key: key24.toString(), value: [field2.toString()] }],
        ]);
    }

    baseBatch.lpop(key5);
    responseData.push(["lpop(key5)", field4.toString()]);
    baseBatch.llen(key5);
    responseData.push(["llen(key5)", 3]);
    baseBatch.lrem(key5, 1, field1);
    responseData.push(['lrem(key5, 1, field + "1")', 1]);
    baseBatch.ltrim(key5, 0, 1);
    responseData.push(["ltrim(key5, 0, 1)", "OK"]);
    baseBatch.lset(key5, 0, field3);
    responseData.push(['lset(key5, 0, field + "3")', "OK"]);
    baseBatch.lrange(key5, 0, -1);
    responseData.push([
        "lrange(key5, 0, -1)",
        [field3.toString(), field2.toString()],
    ]);

    if (!cluster.checkIfServerVersionLessThan("6.2.0")) {
        baseBatch.lmove(key5, key20, ListDirection.LEFT, ListDirection.LEFT);
        responseData.push([
            "lmove(key5, key20, ListDirection.LEFT, ListDirection.LEFT)",
            field3.toString(),
        ]);

        baseBatch.blmove(
            key20,
            key5,
            ListDirection.LEFT,
            ListDirection.LEFT,
            3,
        );
        responseData.push([
            "blmove(key20, key5, ListDirection.LEFT, ListDirection.LEFT, 3)",
            field3.toString(),
        ]);
    }

    baseBatch.lpopCount(key5, 2);
    responseData.push([
        "lpopCount(key5, 2)",
        [field3.toString(), field2.toString()],
    ]);

    baseBatch.linsert(
        key5,
        InsertPosition.Before,
        "nonExistingPivot",
        "element",
    );
    responseData.push(["linsert", 0]);
    baseBatch.rpush(key6, [field1, field2, field3]);
    responseData.push([
        'rpush(key6, [field + "1", field + "2", field + "3"])',
        3,
    ]);
    baseBatch.lindex(key6, 0);
    responseData.push(["lindex(key6, 0)", field1.toString()]);
    baseBatch.rpop(key6);
    responseData.push(["rpop(key6)", field3.toString()]);
    baseBatch.rpopCount(key6, 2);
    responseData.push([
        "rpopCount(key6, 2)",
        [field2.toString(), field1.toString()],
    ]);
    baseBatch.rpushx(key15, [underscore]); // key15 is empty
    responseData.push(['rpushx(key15, ["_"])', 0]);
    baseBatch.lpushx(key15, [underscore]);
    responseData.push(['lpushx(key15, ["_"])', 0]);
    baseBatch.rpush(key16, [field1, field1, field2, field3, field3]);
    responseData.push(["rpush(key16, [1, 1, 2, 3, 3,])", 5]);
    baseBatch.lpos(key16, field1, { rank: 2 });
    responseData.push(["lpos(key16, field1, { rank: 2 })", 1]);
    baseBatch.lpos(key16, field1, { rank: 2, count: 0 });
    responseData.push(["lpos(key16, field1, { rank: 2, count: 0 })", [1]]);
    baseBatch.sadd(key7, [bar, foo]);
    responseData.push(['sadd(key7, ["bar", "foo"])', 2]);
    baseBatch.sunionstore(key7, [key7, key7]);
    responseData.push(["sunionstore(key7, [key7, key7])", 2]);
    baseBatch.sunion([key7, key7]);
    responseData.push(["sunion([key7, key7])", new Set(["bar", "foo"])]);
    baseBatch.sinter([key7, key7]);
    responseData.push(["sinter([key7, key7])", new Set(["bar", "foo"])]);

    if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
        baseBatch.sintercard([key7, key7]);
        responseData.push(["sintercard([key7, key7])", 2]);
        baseBatch.sintercard([key7, key7], { limit: 1 });
        responseData.push(["sintercard([key7, key7], { limit: 1 })", 1]);
    }

    baseBatch.sinterstore(key7, [key7, key7]);
    responseData.push(["sinterstore(key7, [key7, key7])", 2]);
    baseBatch.sdiff([key7, key7]);
    responseData.push(["sdiff([key7, key7])", new Set()]);
    baseBatch.sdiffstore(key7, [key7]);
    responseData.push(["sdiffstore(key7, [key7])", 2]);
    baseBatch.srem(key7, [foo]);
    responseData.push(['srem(key7, ["foo"])', 1]);
    baseBatch.sscan(key7, "0");
    responseData.push(['sscan(key7, "0")', ["0", ["bar"]]]);
    baseBatch.sscan(key7, "0", { match: "*", count: 20 });
    responseData.push([
        'sscan(key7, "0", {match: "*", count: 20})',
        ["0", ["bar"]],
    ]);
    baseBatch.scard(key7);
    responseData.push(["scard(key7)", 1]);
    baseBatch.sismember(key7, bar);
    responseData.push(['sismember(key7, "bar")', true]);

    if (!cluster.checkIfServerVersionLessThan("6.2.0")) {
        baseBatch.smismember(key7, [bar, foo, baz]);
        responseData.push([
            'smismember(key7, ["bar", "foo", "baz"])',
            [true, false, false],
        ]);
    }

    baseBatch.smembers(key7);
    responseData.push(["smembers(key7)", new Set(["bar"])]);
    baseBatch.srandmember(key7);
    responseData.push(["srandmember(key7)", "bar"]);
    baseBatch.srandmemberCount(key7, 2);
    responseData.push(["srandmemberCount(key7, 2)", ["bar"]]);
    baseBatch.srandmemberCount(key7, -2);
    responseData.push(["srandmemberCount(key7, -2)", ["bar", "bar"]]);
    baseBatch.spop(key7);
    responseData.push(["spop(key7)", "bar"]);
    baseBatch.spopCount(key7, 2);
    responseData.push(["spopCount(key7, 2)", new Set()]);
    baseBatch.smove(key7, key7, non_existing_member);
    responseData.push(['smove(key7, key7, "non_existing_member")', false]);
    baseBatch.scard(key7);
    responseData.push(["scard(key7)", 0]);
    baseBatch.zadd(key8, [
        { element: member1, score: 1 },
        { element: member2, score: 2 },
        { element: member3, score: 3.5 },
        { element: member4, score: 4 },
        { element: member5, score: 5 },
        { element: "infMember", score: "+inf" },
        { element: "negInfMember", score: "-inf" },
    ]);
    responseData.push(["zadd(key8, { ... } ", 7]);
    baseBatch.zrank(key8, member1);
    responseData.push(['zrank(key8, "member1")', 1]);
    baseBatch.zrank(key8, "negInfMember");
    responseData.push(['zrank(key8, "negInfMember")', 0]);

    if (!cluster.checkIfServerVersionLessThan("7.2.0")) {
        baseBatch.zrankWithScore(key8, member1);
        responseData.push(['zrankWithScore(key8, "member1")', [1, 1]]);
        baseBatch.zrankWithScore(key8, "negInfMember");
        responseData.push([
            'zrankWithScore(key8, "negInfMember")',
            [0, -Infinity],
        ]);
    }

    baseBatch.zrevrank(key8, "member5");
    responseData.push(['zrevrank(key8, "member5")', 1]);
    baseBatch.zrevrank(key8, "infMember");
    responseData.push(['zrevrank(key8, "infMember")', 0]);

    if (!cluster.checkIfServerVersionLessThan("7.2.0")) {
        baseBatch.zrevrankWithScore(key8, "member5");
        responseData.push(['zrevrankWithScore(key8, "member5")', [1, 5]]);
        baseBatch.zrevrankWithScore(key8, "infMember");
        responseData.push([
            'zrevrankWithScore(key8, "infMember")',
            [0, Infinity],
        ]);
    }

    baseBatch.zaddIncr(key8, member2, 1);
    responseData.push(['zaddIncr(key8, "member2", 1)', 3]);
    baseBatch.zincrby(key8, 0.3, member1);
    responseData.push(['zincrby(key8, 0.3, "member1")', 1.3]);
    baseBatch.zrem(key8, [member1]);
    responseData.push(['zrem(key8, ["member1"])', 1]);
    baseBatch.zcard(key8);
    responseData.push(["zcard(key8)", 6]);

    baseBatch.zscore(key8, member2);
    responseData.push(['zscore(key8, "member2")', 3.0]);
    baseBatch.zscore(key8, "infMember");
    responseData.push(['zscore(key8, "infMember")', Infinity]);
    baseBatch.zrange(key8, { start: 0, end: -1 });
    responseData.push([
        "zrange(key8, { start: 0, end: -1 })",
        [
            "negInfMember",
            member2.toString(),
            member3.toString(),
            member4.toString(),
            member5.toString(),
            "infMember",
        ],
    ]);
    baseBatch.zrangeWithScores(key8, { start: 0, end: -1 });
    responseData.push([
        "zrangeWithScores(key8, { start: 0, end: -1 })",
        convertRecordToGlideRecord({
            negInfMember: -Infinity,
            member2: 3,
            member3: 3.5,
            member4: 4,
            member5: 5,
            infMember: Infinity,
        }),
    ]);
    baseBatch.zadd(key12, [
        { element: one, score: 1 },
        { element: two, score: 2 },
    ]);
    responseData.push(["zadd(key12, { one: 1, two: 2 })", 2]);
    baseBatch.zscan(key12, "0");
    responseData.push(['zscan(key12, "0")', ["0", ["one", "1", "two", "2"]]]);

    if (!cluster.checkIfServerVersionLessThan("8.0.0")) {
        baseBatch.zscan(key12, "0", { noScores: false });
        responseData.push([
            'zscan(key12, "0", {noScores: false})',
            ["0", [one.toString(), "1", two.toString(), "2"]],
        ]);

        baseBatch.zscan(key12, "0", {
            match: "*",
            count: 20,
            noScores: true,
        });
        responseData.push([
            'zscan(key12, "0", {match: "*", count: 20, noScores:true})',
            ["0", [one.toString(), two.toString()]],
        ]);
    }

    baseBatch.zscan(key12, "0", { match: "*", count: 20 });
    responseData.push([
        'zscan(key12, "0", {match: "*", count: 20})',
        ["0", ["one", "1", "two", "2"]],
    ]);
    baseBatch.zadd(key13, { one: 1, two: 2, three: 3.5 });
    responseData.push(["zadd(key13, { one: 1, two: 2, three: 3.5 })", 3]);

    if (!cluster.checkIfServerVersionLessThan("6.2.0")) {
        baseBatch.zrangeStore(key8, key8, { start: 0, end: -1 });
        responseData.push([
            "zrangeStore(key8, key8, { start: 0, end: -1 })",
            6,
        ]);
        baseBatch.zdiff([key13, key12]);
        responseData.push(["zdiff([key13, key12])", ["three"]]);
        baseBatch.zdiffWithScores([key13, key12]);
        responseData.push([
            "zdiffWithScores([key13, key12])",
            convertRecordToGlideRecord({ three: 3.5 }),
        ]);
        baseBatch.zdiffstore(key13, [key13, key13]);
        responseData.push(["zdiffstore(key13, [key13, key13])", 0]);
        baseBatch.zunionstore(key5, [key12, key13]);
        responseData.push(["zunionstore(key5, [key12, key13])", 2]);
        baseBatch.zmscore(key12, ["two", "one"]);
        responseData.push(['zmscore(key12, ["two", "one"]', [2.0, 1.0]]);
        baseBatch.zinterstore(key12, [key12, key13]);
        responseData.push(["zinterstore(key12, [key12, key13])", 0]);

        if (!cluster.checkIfServerVersionLessThan("6.2.0")) {
            baseBatch.zadd(key26, [
                { element: one, score: 1 },
                { element: two, score: 2 },
            ]);
            responseData.push(["zadd(key26, { one: 1, two: 2 })", 2]);
            baseBatch.zadd(key27, [
                { element: one, score: 1 },
                { element: two, score: 2 },
                { element: three, score: 3.5 },
            ]);
            responseData.push([
                "zadd(key27, { one: 1, two: 2, three: 3.5 })",
                3,
            ]);
            baseBatch.zinter([key27, key26]);
            responseData.push(["zinter([key27, key26])", ["one", "two"]]);
            baseBatch.zinterWithScores([key27, key26]);
            responseData.push([
                "zinterWithScores([key27, key26])",
                convertRecordToGlideRecord({ one: 2, two: 4 }),
            ]);
            baseBatch.zunionWithScores([key27, key26]);
            responseData.push([
                "zunionWithScores([key27, key26])",
                convertRecordToGlideRecord({ one: 2, two: 4, three: 3.5 }).sort(
                    (a, b) => a.value - b.value,
                ),
            ]);
        }
    } else {
        baseBatch.zinterstore(key12, [key12, key13]);
        responseData.push(["zinterstore(key12, [key12, key13])", 2]);
    }

    baseBatch.zcount(key8, { value: 2 }, InfBoundary.PositiveInfinity);
    responseData.push([
        "zcount(key8, { value: 2 }, InfBoundary.PositiveInfinity)",
        5,
    ]);
    baseBatch.zlexcount(key8, { value: "a" }, InfBoundary.PositiveInfinity);
    responseData.push([
        'zlexcount(key8, { value: "a" }, InfBoundary.PositiveInfinity)',
        6,
    ]);
    baseBatch.zpopmin(key8);
    responseData.push([
        "zpopmin(key8)",
        convertRecordToGlideRecord({ negInfMember: -Infinity }),
    ]);
    baseBatch.zpopmax(key8);
    responseData.push([
        "zpopmax(key8)",
        convertRecordToGlideRecord({ infMember: Infinity }),
    ]);
    baseBatch.zadd(key8, [{ element: member6, score: 6 }]);
    responseData.push(["zadd(key8, {member6: 6})", 1]);
    baseBatch.bzpopmax([key8], 0.5);
    responseData.push([
        "bzpopmax([key8], 0.5)",
        [key8.toString(), "member6", 6],
    ]);
    baseBatch.zadd(key8, [{ element: member7, score: 1 }]);
    responseData.push(["zadd(key8, {member7: 1})", 1]);
    baseBatch.bzpopmin([key8], 0.5);
    responseData.push([
        "bzpopmin([key8], 0.5)",
        [key8.toString(), "member7", 1],
    ]);
    baseBatch.zremRangeByRank(key8, 1, 1);
    responseData.push(["zremRangeByRank(key8, 1, 1)", 1]);
    baseBatch.zremRangeByScore(
        key8,
        InfBoundary.NegativeInfinity,
        InfBoundary.PositiveInfinity,
    );
    responseData.push(["zremRangeByScore(key8, -Inf, +Inf)", 3]); // key8 is now empty
    baseBatch.zremRangeByLex(
        key8,
        InfBoundary.NegativeInfinity,
        InfBoundary.PositiveInfinity,
    );
    responseData.push(["zremRangeByLex(key8, -Inf, +Inf)", 0]); // key8 is already empty

    if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
        baseBatch.zadd(key14, [
            { element: one, score: 1.0 },
            { element: two, score: 2.0 },
        ]);
        responseData.push(["zadd(key14, { one: 1.0, two: 2.0 })", 2]);
        baseBatch.zintercard([key8, key14]);
        responseData.push(["zintercard([key8, key14])", 0]);
        baseBatch.zintercard([key8, key14], { limit: 1 });
        responseData.push(["zintercard([key8, key14], { limit: 1 })", 0]);
        baseBatch.zmpop([key14], ScoreFilter.MAX);
        responseData.push([
            "zmpop([key14], MAX)",
            [key14.toString(), [{ key: "two", value: 2.0 }]],
        ]);
        baseBatch.zmpop([key14], ScoreFilter.MAX, 1);
        responseData.push([
            "zmpop([key14], MAX, 1)",
            [key14.toString(), [{ key: "one", value: 1.0 }]],
        ]);
        baseBatch.zadd(key14, [
            { element: one, score: 1.0 },
            { element: two, score: 2.0 },
        ]);
        responseData.push(["zadd(key14, { one: 1.0, two: 2.0 })", 2]);
        baseBatch.bzmpop([key14], ScoreFilter.MAX, 0.1);
        responseData.push([
            "bzmpop([key14], ScoreFilter.MAX, 0.1)",
            [key14.toString(), [{ key: "two", value: 2.0 }]],
        ]);
        baseBatch.bzmpop([key14], ScoreFilter.MAX, 0.1, 1);
        responseData.push([
            "bzmpop([key14], ScoreFilter.MAX, 0.1, 1)",
            [key14.toString(), [{ key: "one", value: 1.0 }]],
        ]);
    }

    baseBatch.xadd(key9, [[field, value1]], { id: "0-1" });
    responseData.push([
        'xadd(key9, [["field", "value1"]], { id: "0-1" })',
        "0-1",
    ]);
    baseBatch.xadd(key9, [[field, value2]], { id: "0-2" });
    responseData.push([
        'xadd(key9, [["field", "value2"]], { id: "0-2" })',
        "0-2",
    ]);
    baseBatch.xadd(key9, [[field, value3]], { id: "0-3" });
    responseData.push([
        'xadd(key9, [["field", "value3"]], { id: "0-3" })',
        "0-3",
    ]);
    baseBatch.xlen(key9);
    responseData.push(["xlen(key9)", 3]);
    baseBatch.xrange(key9, { value: "0-1" }, { value: "0-1" });
    responseData.push([
        "xrange(key9)",
        convertRecordToGlideRecord({
            "0-1": [[field.toString(), value1.toString()]],
        }),
    ]);
    baseBatch.xrevrange(key9, { value: "0-1" }, { value: "0-1" });
    responseData.push([
        "xrevrange(key9)",
        convertRecordToGlideRecord({
            "0-1": [[field.toString(), value1.toString()]],
        }),
    ]);
    baseBatch.xread([{ key: key9, value: "0-1" }]);
    responseData.push([
        'xread({ [key9]: "0-1" })',
        [
            {
                key: key9.toString(),
                value: [
                    {
                        key: "0-2",
                        value: [[field.toString(), value2.toString()]],
                    },
                    {
                        key: "0-3",
                        value: [[field.toString(), value3.toString()]],
                    },
                ],
            },
        ],
    ]);
    baseBatch.xtrim(key9, {
        method: "minid",
        threshold: "0-2",
        exact: true,
    });
    responseData.push([
        'xtrim(key9, { method: "minid", threshold: "0-2", exact: true }',
        1,
    ]);
    baseBatch.xinfoGroups(key9);
    responseData.push(["xinfoGroups(key9)", []]);
    baseBatch.xgroupCreate(key9, groupName1, "0-0");
    responseData.push(['xgroupCreate(key9, groupName1, "0-0")', "OK"]);
    baseBatch.xgroupCreate(key9, groupName2, "0-0", { mkStream: true });
    responseData.push([
        'xgroupCreate(key9, groupName2, "0-0", { mkStream: true })',
        "OK",
    ]);
    baseBatch.xinfoConsumers(key9, groupName1);
    responseData.push(["xinfoConsumers(key9, groupName1)", []]);
    baseBatch.xdel(key9, ["0-3", "0-5"]);
    responseData.push(["xdel(key9, [['0-3', '0-5']])", 1]);

    // key9 has one entry here: {"0-2":[["field","value2"]]}

    baseBatch.xgroupCreateConsumer(key9, groupName1, consumer);
    responseData.push([
        "xgroupCreateConsumer(key9, groupName1, consumer)",
        true,
    ]);
    baseBatch.xreadgroup(groupName1, consumer, [{ key: key9, value: ">" }]);
    responseData.push([
        'xreadgroup(groupName1, consumer, {[key9]: ">"})',
        [
            {
                key: key9.toString(),
                value: [
                    {
                        key: "0-2",
                        value: [[field.toString(), value2.toString()]],
                    },
                ],
            },
        ],
    ]);
    baseBatch.xpending(key9, groupName1);
    responseData.push([
        "xpending(key9, groupName1)",
        [1, "0-2", "0-2", [[consumer.toString(), "1"]]],
    ]);
    baseBatch.xpendingWithOptions(key9, groupName1, {
        start: InfBoundary.NegativeInfinity,
        end: InfBoundary.PositiveInfinity,
        count: 10,
    });
    responseData.push([
        "xpendingWithOptions(key9, groupName1, -, +, 10)",
        [["0-2", consumer.toString(), 0, 1]],
    ]);
    baseBatch.xclaim(key9, groupName1, consumer, 0, ["0-2"]);
    responseData.push([
        'xclaim(key9, groupName1, consumer, 0, ["0-2"])',
        convertRecordToGlideRecord({
            "0-2": [[field.toString(), value2.toString()]],
        }),
    ]);
    baseBatch.xclaim(key9, groupName1, consumer, 0, ["0-2"], {
        isForce: true,
        retryCount: 0,
        idle: 0,
    });
    responseData.push([
        'xclaim(key9, groupName1, consumer, 0, ["0-2"], { isForce: true, retryCount: 0, idle: 0})',
        convertRecordToGlideRecord({
            "0-2": [[field.toString(), value2.toString()]],
        }),
    ]);
    baseBatch.xclaimJustId(key9, groupName1, consumer, 0, ["0-2"]);
    responseData.push([
        'xclaimJustId(key9, groupName1, consumer, 0, ["0-2"])',
        ["0-2"],
    ]);
    baseBatch.xclaimJustId(key9, groupName1, consumer, 0, ["0-2"], {
        isForce: true,
        retryCount: 0,
        idle: 0,
    });
    responseData.push([
        'xclaimJustId(key9, groupName1, consumer, 0, ["0-2"], { isForce: true, retryCount: 0, idle: 0})',
        ["0-2"],
    ]);

    if (!cluster.checkIfServerVersionLessThan("6.2.0")) {
        baseBatch.xautoclaim(key9, groupName1, consumer, 0, "0-0", {
            count: 1,
        });
        responseData.push([
            'xautoclaim(key9, groupName1, consumer, 0, "0-0", { count: 1 })',
            !cluster.checkIfServerVersionLessThan("7.0.0")
                ? [
                    "0-0",
                    convertRecordToGlideRecord({
                        "0-2": [[field.toString(), value2.toString()]],
                    }),
                    [],
                ]
                : [
                    "0-0",
                    convertRecordToGlideRecord({
                        "0-2": [[field.toString(), value2.toString()]],
                    }),
                ],
        ]);
        baseBatch.xautoclaimJustId(key9, groupName1, consumer, 0, "0-0");
        responseData.push([
            'xautoclaimJustId(key9, groupName1, consumer, 0, "0-0")',
            !cluster.checkIfServerVersionLessThan("7.0.0")
                ? ["0-0", ["0-2"], []]
                : ["0-0", ["0-2"]],
        ]);
    }

    baseBatch.xack(key9, groupName1, ["0-3"]);
    responseData.push(["xack(key9, groupName1, ['0-3'])", 0]);
    baseBatch.xgroupSetId(key9, groupName1, "0-2");
    responseData.push(["xgroupSetId(key9, groupName1, '0-2')", "OK"]);
    baseBatch.xgroupDelConsumer(key9, groupName1, consumer);
    responseData.push(["xgroupDelConsumer(key9, groupName1, consumer)", 1]);
    baseBatch.xgroupDestroy(key9, groupName1);
    responseData.push(["xgroupDestroy(key9, groupName1)", true]);
    baseBatch.xgroupDestroy(key9, groupName2);
    responseData.push(["xgroupDestroy(key9, groupName2)", true]);
    baseBatch.rename(key9, key10);
    responseData.push(["rename(key9, key10)", "OK"]);
    baseBatch.exists([key10]);
    responseData.push(["exists([key10])", 1]);
    baseBatch.touch([key10]);
    responseData.push(["touch([key10])", 1]);
    baseBatch.renamenx(key10, key9);
    responseData.push(["renamenx(key10, key9)", true]);
    baseBatch.exists([key9, key10]);
    responseData.push(["exists([key9, key10])", 1]);
    baseBatch.rpush(key6, [field1, field2, field3]);
    responseData.push([
        'rpush(key6, [field + "1", field + "2", field + "3"])',
        3,
    ]);
    baseBatch.brpop([key6], 0.1);
    responseData.push([
        "brpop([key6], 0.1)",
        [key6.toString(), field3.toString()],
    ]);
    baseBatch.blpop([key6], 0.1);
    responseData.push([
        "blpop([key6], 0.1)",
        [key6.toString(), field1.toString()],
    ]);

    baseBatch.setbit(key17, 1, 1);
    responseData.push(["setbit(key17, 1, 1)", 0]);
    baseBatch.getbit(key17, 1);
    responseData.push(["getbit(key17, 1)", 1]);
    baseBatch.set(key17, "foobar");
    responseData.push(['set(key17, "foobar")', "OK"]);
    baseBatch.bitcount(key17);
    responseData.push(["bitcount(key17)", 26]);
    baseBatch.bitcount(key17, { start: 1, end: 1 });
    responseData.push(["bitcount(key17, { start: 1, end: 1 })", 6]);
    baseBatch.bitpos(key17, 1);
    responseData.push(["bitpos(key17, 1)", 1]);

    if (!cluster.checkIfServerVersionLessThan("6.0.0")) {
        baseBatch.bitfieldReadOnly(key17, [
            new BitFieldGet(new SignedEncoding(5), new BitOffset(3)),
        ]);
        responseData.push([
            "bitfieldReadOnly(key17, [new BitFieldGet(...)])",
            [6],
        ]);
    }

    baseBatch.set(key19, "abcdef");
    responseData.push(['set(key19, "abcdef")', "OK"]);
    baseBatch.bitop(BitwiseOperation.AND, key19, [key19, key17]);
    responseData.push([
        "bitop(BitwiseOperation.AND, key19, [key19, key17])",
        6,
    ]);
    baseBatch.get(key19);
    responseData.push(["get(key19)", "`bc`ab"]);

    if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
        baseBatch.bitcount(key17, {
            start: 5,
            end: 30,
            indexType: BitmapIndexType.BIT,
        });
        responseData.push([
            "bitcount(key17, new BitOffsetOptions(5, 30, BitmapIndexType.BIT))",
            17,
        ]);
        baseBatch.bitpos(key17, 1, {
            start: 44,
            end: 50,
            indexType: BitmapIndexType.BIT,
        });
        responseData.push([
            "bitpos(key17, 1, {start: 44, end: 50, indexType: BitmapIndexType.BIT})",
            46,
        ]);
    }

    if (!cluster.checkIfServerVersionLessThan("8.0.0")) {
        baseBatch.set(key17, "foobar");
        responseData.push(['set(key17, "foobar")', "OK"]);
        baseBatch.bitcount(key17, {
            start: 0,
        });
        responseData.push(["bitcount(key17, {start:0 }", 26]);
    }

    baseBatch.bitfield(key17, [
        new BitFieldSet(
            new UnsignedEncoding(10),
            new BitOffsetMultiplier(3),
            4,
        ),
    ]);
    responseData.push(["bitfield(key17, [new BitFieldSet(...)])", [609]]);

    baseBatch.pfadd(key11, [one, two, three]);
    responseData.push(["pfadd(key11, [one, two, three])", true]);
    baseBatch.pfmerge(key11, []);
    responseData.push(["pfmerge(key11, [])", "OK"]);
    baseBatch.pfcount([key11]);
    responseData.push(["pfcount([key11])", 3]);
    baseBatch.geoadd(
        key18,
        new Map<GlideString, GeospatialData>([
            [palermo, { longitude: 13.361389, latitude: 38.115556 }],
            [catania, { longitude: 15.087269, latitude: 37.502669 }],
        ]),
    );
    responseData.push(["geoadd(key18, { Palermo: ..., Catania: ... })", 2]);
    baseBatch.geopos(key18, [palermo, catania]);
    responseData.push([
        'geopos(key18, ["palermo", "catania"])',
        [
            [13.36138933897018433, 38.11555639549629859],
            [15.08726745843887329, 37.50266842333162032],
        ],
    ]);
    baseBatch.geodist(key18, palermo, catania);
    responseData.push(['geodist(key18, "Palermo", "Catania")', 166274.1516]);
    baseBatch.geodist(key18, palermo, catania, {
        unit: GeoUnit.KILOMETERS,
    });
    responseData.push([
        'geodist(key18, "palermo", "catania", { unit: GeoUnit.KILOMETERS })',
        166.2742,
    ]);
    baseBatch.geohash(key18, [palermo, catania, non_existing_member]);
    responseData.push([
        'geohash(key18, ["palermo", "catania", "NonExisting"])',
        ["sqc8b49rny0", "sqdtr74hyu0", null],
    ]);
    baseBatch.zadd(key23, { one: 1.0 });
    responseData.push(["zadd(key23, {one: 1.0}", 1]);
    baseBatch.zrandmember(key23);
    responseData.push(["zrandmember(key23)", "one"]);
    baseBatch.zrandmemberWithCount(key23, 1);
    responseData.push(["zrandmemberWithCount(key23, 1)", ["one"]]);
    baseBatch.zrandmemberWithCountWithScores(key23, 1);
    responseData.push([
        "zrandmemberWithCountWithScores(key23, 1)",
        [["one", 1.0]],
    ]);

    if (!cluster.checkIfServerVersionLessThan("6.2.0")) {
        baseBatch
            .geosearch(
                key18,
                { member: palermo },
                { radius: 200, unit: GeoUnit.KILOMETERS },
                { sortOrder: SortOrder.ASC },
            )
            .geosearch(
                key18,
                { position: { longitude: 15, latitude: 37 } },
                { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
            )
            .geosearch(
                key18,
                { member: palermo },
                { radius: 200, unit: GeoUnit.KILOMETERS },
                {
                    sortOrder: SortOrder.ASC,
                    count: 2,
                    withCoord: true,
                    withDist: true,
                    withHash: true,
                },
            )
            .geosearch(
                key18,
                { position: { longitude: 15, latitude: 37 } },
                { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
                {
                    sortOrder: SortOrder.ASC,
                    count: 2,
                    withCoord: true,
                    withDist: true,
                    withHash: true,
                },
            );
        responseData.push([
            'geosearch(key18, "Palermo", R200 KM, ASC)',
            [palermo.toString(), catania.toString()],
        ]);
        responseData.push([
            "geosearch(key18, (15, 37), 400x400 KM, ASC)",
            [palermo.toString(), catania.toString()],
        ]);
        responseData.push([
            'geosearch(key18, "palermo", R200 KM, ASC 2 3x true)',
            [
                [
                    palermo.toString(),
                    [
                        0.0,
                        3479099956230698,
                        [13.361389338970184, 38.1155563954963],
                    ],
                ],
                [
                    catania.toString(),
                    [
                        166.2742,
                        3479447370796909,
                        [15.087267458438873, 37.50266842333162],
                    ],
                ],
            ],
        ]);
        responseData.push([
            "geosearch(key18, (15, 37), 400x400 KM, ASC 2 3x true)",
            [
                [
                    catania.toString(),
                    [
                        56.4413,
                        3479447370796909,
                        [15.087267458438873, 37.50266842333162],
                    ],
                ],
                [
                    palermo.toString(),
                    [
                        190.4424,
                        3479099956230698,
                        [13.361389338970184, 38.1155563954963],
                    ],
                ],
            ],
        ]);

        baseBatch.geosearchstore(
            key25,
            key18,
            { position: { longitude: 15, latitude: 37 } },
            { width: 400, height: 400, unit: GeoUnit.KILOMETERS },
        );
        responseData.push([
            "geosearchstore(key25, key18, (15, 37), 400x400 KM)",
            2,
        ]);
    }

    const libName = "mylib1C" + getRandomKey().replaceAll("-", "");
    const funcName = "myfunc1c" + getRandomKey().replaceAll("-", "");
    const code = generateLuaLibCode(
        libName,
        new Map([[funcName, "return args[1]"]]),
        true,
    );

    if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
        baseBatch.functionFlush();
        responseData.push(["functionFlush()", "OK"]);
        baseBatch.functionLoad(code);
        responseData.push(["functionLoad(code)", libName]);
        baseBatch.functionLoad(code, true);
        responseData.push(["functionLoad(code, true)", libName]);

        if (baseBatch.isAtomic && !clusterMode) {
            baseBatch.functionList({ libNamePattern: "another" });
            responseData.push(['functionList("another")', []]);
            baseBatch.functionStats();
            responseData.push([
                "functionStats()",
                convertRecordToGlideRecord({
                    running_script: null,
                    engines: convertRecordToGlideRecord({
                        LUA: convertRecordToGlideRecord({
                            libraries_count: 1,
                            functions_count: 1,
                        }),
                    }),
                }),
            ]);
        }

        baseBatch.fcall(funcName, [key1], ["one", "two"]);
        responseData.push(['fcall(funcName, [key1], ["one", "two"])', "one"]);
        baseBatch.fcallReadonly(funcName, [key1], ["one", "two"]);
        responseData.push([
            'fcallReadonly(funcName, [key1], ["one", "two"]',
            "one",
        ]);

        baseBatch.functionDelete(libName);
        responseData.push(["functionDelete(libName)", "OK"]);
        baseBatch.functionFlush();
        responseData.push(["functionFlush()", "OK"]);
        baseBatch.functionFlush(FlushMode.ASYNC);
        responseData.push(["functionFlush(FlushMode.ASYNC)", "OK"]);
        baseBatch.functionFlush(FlushMode.SYNC);
        responseData.push(["functionFlush(FlushMode.SYNC)", "OK"]);

        if (baseBatch.isAtomic && clusterMode) {
            baseBatch.functionList({
                libNamePattern: libName,
                withCode: true,
            });
            responseData.push(["functionList({ libName, true})", []]);
        }

        baseBatch
            .mset([
                { key: key1, value: "abcd" },
                { key: key2, value: "bcde" },
                { key: key3, value: "wxyz" },
            ])
            .lcs(key1, key2)
            .lcs(key1, key3)
            .lcsLen(key1, key2)
            .lcsLen(key1, key3)
            .lcsIdx(key1, key2)
            .lcsIdx(key1, key2, { minMatchLen: 1 })
            .lcsIdx(key1, key2, { withMatchLen: true })
            .lcsIdx(key1, key2, { withMatchLen: true, minMatchLen: 1 })
            .del([key1, key2, key3]);

        responseData.push(
            ['mset({[key1]: "abcd", [key2]: "bcde", [key3]: "wxyz"})', "OK"],
            ["lcs(key1, key2)", "bcd"],
            ["lcs(key1, key3)", ""],
            ["lcsLen(key1, key2)", 3],
            ["lcsLen(key1, key3)", 0],
            [
                "lcsIdx(key1, key2)",
                convertRecordToGlideRecord({
                    matches: [
                        [
                            [1, 3],
                            [0, 2],
                        ],
                    ],
                    len: 3,
                }),
            ],
            [
                "lcsIdx(key1, key2, {minMatchLen: 1})",
                convertRecordToGlideRecord({
                    matches: [
                        [
                            [1, 3],
                            [0, 2],
                        ],
                    ],
                    len: 3,
                }),
            ],
            [
                "lcsIdx(key1, key2, {withMatchLen: true})",
                convertRecordToGlideRecord({
                    matches: [[[1, 3], [0, 2], 3]],
                    len: 3,
                }),
            ],
            [
                "lcsIdx(key1, key2, {withMatchLen: true, minMatchLen: 1})",
                convertRecordToGlideRecord({
                    matches: [[[1, 3], [0, 2], 3]],
                    len: 3,
                }),
            ],
            ["del([key1, key2, key3])", 3],
        );
    }

    baseBatch
        .lpush(key21, ["3", "1", "2"])
        .sort(key21)
        .sortStore(key21, key22)
        .lrange(key22, 0, -1);
    responseData.push(
        ['lpush(key21, ["3", "1", "2"])', 3],
        ["sort(key21)", ["1", "2", "3"]],
        ["sortStore(key21, key22)", 3],
        ["lrange(key22, 0, -1)", ["1", "2", "3"]],
    );

    if (!cluster.checkIfServerVersionLessThan("7.0.0")) {
        baseBatch.sortReadOnly(key21);
        responseData.push(["sortReadOnly(key21)", ["1", "2", "3"]]);
    }

    return responseData;
}

/**
 * Populates a batch with JSON commands to test.
 * @param baseBatch - A batch.
 * @returns Array of tuples, where first element is a test name/description, second - expected return value.
 */
export async function JsonBatchForArrCommands(
    baseBatch: ClusterBatch,
): Promise<[string, GlideReturnType][]> {
    const responseData: [string, GlideReturnType][] = [];
    const key = "{key}:1" + getRandomKey();
    const jsonValue = { a: 1.0, b: 2 };

    // JSON.SET
    JsonBatch.set(baseBatch, key, "$", JSON.stringify(jsonValue));
    responseData.push(['set(key, "{ a: 1.0, b: 2 }")', "OK"]);

    // JSON.CLEAR
    JsonBatch.clear(baseBatch, key, { path: "$" });
    responseData.push(['clear(key, "bar")', 1]);

    JsonBatch.set(baseBatch, key, "$", JSON.stringify(jsonValue));
    responseData.push(['set(key, "$", "{ "a": 1, b: ["one", "two"] }")', "OK"]);

    // JSON.GET
    JsonBatch.get(baseBatch, key, { path: "." });
    responseData.push(['get(key, {path: "."})', JSON.stringify(jsonValue)]);

    const jsonValue2 = { a: 1.0, b: [1, 2] };
    JsonBatch.set(baseBatch, key, "$", JSON.stringify(jsonValue2));
    responseData.push(['set(key, "$", "{ "a": 1, b: ["1", "2"] }")', "OK"]);

    // JSON.ARRAPPEND
    JsonBatch.arrappend(baseBatch, key, "$.b", ["3", "4"]);
    responseData.push(['arrappend(key, "$.b", [\'"3"\', \'"4"\'])', [4]]);

    // JSON.GET to check JSON.ARRAPPEND was successful.
    const jsonValueAfterAppend = { a: 1.0, b: [1, 2, 3, 4] };
    JsonBatch.get(baseBatch, key, { path: "." });
    responseData.push([
        'get(key, {path: "."})',
        JSON.stringify(jsonValueAfterAppend),
    ]);

    // JSON.ARRINDEX
    JsonBatch.arrindex(baseBatch, key, "$.b", "2");
    responseData.push(['arrindex(key, "$.b", "1")', [1]]);

    // JSON.ARRINSERT
    JsonBatch.arrinsert(baseBatch, key, "$.b", 2, ["5"]);
    responseData.push(['arrinsert(key, "$.b", 4, [\'"5"\'])', [5]]);

    // JSON.GET to check JSON.ARRINSERT was successful.
    const jsonValueAfterArrInsert = { a: 1.0, b: [1, 2, 5, 3, 4] };
    JsonBatch.get(baseBatch, key, { path: "." });
    responseData.push([
        'get(key, {path: "."})',
        JSON.stringify(jsonValueAfterArrInsert),
    ]);

    // JSON.ARRLEN
    JsonBatch.arrlen(baseBatch, key, { path: "$.b" });
    responseData.push(['arrlen(key, "$.b")', [5]]);

    // JSON.ARRPOP
    JsonBatch.arrpop(baseBatch, key, {
        path: "$.b",
        index: 2,
    });
    responseData.push(['arrpop(key, {path: "$.b", index: 4})', ["5"]]);

    // JSON.GET to check JSON.ARRPOP was successful.
    const jsonValueAfterArrpop = { a: 1.0, b: [1, 2, 3, 4] };
    JsonBatch.get(baseBatch, key, { path: "." });
    responseData.push([
        'get(key, {path: "."})',
        JSON.stringify(jsonValueAfterArrpop),
    ]);

    // JSON.ARRTRIM
    JsonBatch.arrtrim(baseBatch, key, "$.b", 1, 2);
    responseData.push(['arrtrim(key, "$.b", 2, 3)', [2]]);

    // JSON.GET to check JSON.ARRTRIM was successful.
    const jsonValueAfterArrTrim = { a: 1.0, b: [2, 3] };
    JsonBatch.get(baseBatch, key, { path: "." });
    responseData.push([
        'get(key, {path: "."})',
        JSON.stringify(jsonValueAfterArrTrim),
    ]);
    return responseData;
}

export async function CreateJsonBatchCommands(
    baseBatch: ClusterBatch,
): Promise<[string, GlideReturnType][]> {
    const responseData: [string, GlideReturnType][] = [];
    const key = "{key}:1" + getRandomKey();
    const jsonValue = { a: [1, 2], b: [3, 4], c: "c", d: true };

    // JSON.SET to create a key for testing commands.
    JsonBatch.set(baseBatch, key, "$", JSON.stringify(jsonValue));
    responseData.push(['set(key, "$")', "OK"]);

    // JSON.DEBUG MEMORY
    JsonBatch.debugMemory(baseBatch, key, { path: "$.a" });
    responseData.push(['debugMemory(key, "{ path: "$.a" }")', [48]]);

    // JSON.DEBUG FIELDS
    JsonBatch.debugFields(baseBatch, key, { path: "$.a" });
    responseData.push(['debugFields(key, "{ path: "$.a" }")', [2]]);

    // JSON.OBJLEN
    JsonBatch.objlen(baseBatch, key, { path: "." });
    responseData.push(["objlen(key)", 4]);

    // JSON.OBJKEY
    JsonBatch.objkeys(baseBatch, key, { path: "." });
    responseData.push(['objkeys(key, "$.")', ["a", "b", "c", "d"]]);

    // JSON.NUMINCRBY
    JsonBatch.numincrby(baseBatch, key, "$.a[*]", 10.0);
    responseData.push(['numincrby(key, "$.a[*]", 10.0)', "[11,12]"]);

    // JSON.NUMMULTBY
    JsonBatch.nummultby(baseBatch, key, "$.a[*]", 10.0);
    responseData.push(['nummultby(key, "$.a[*]", 10.0)', "[110,120]"]);

    // // JSON.STRAPPEND
    JsonBatch.strappend(baseBatch, key, '"-test"', { path: "$.c" });
    responseData.push(['strappend(key, \'"-test"\', "$.c")', [6]]);

    // // JSON.STRLEN
    JsonBatch.strlen(baseBatch, key, { path: "$.c" });
    responseData.push(['strlen(key, "$.c")', [6]]);

    // JSON.TYPE
    JsonBatch.type(baseBatch, key, { path: "$.a" });
    responseData.push(['type(key, "$.a")', ["array"]]);

    // JSON.MGET
    const key2 = "{key}:2" + getRandomKey();
    const key3 = "{key}:3" + getRandomKey();
    const jsonValue2 = { b: [3, 4], c: "c", d: true };
    JsonBatch.set(baseBatch, key2, "$", JSON.stringify(jsonValue2));
    responseData.push(['set(key2, "$")', "OK"]);

    JsonBatch.mget(baseBatch, [key, key2, key3], "$.a");
    responseData.push([
        'json.mget([key, key2, key3], "$.a")',
        ["[[110,120]]", "[]", null],
    ]);

    // JSON.TOGGLE
    JsonBatch.toggle(baseBatch, key, { path: "$.d" });
    responseData.push(['toggle(key2, "$.d")', [false]]);

    // JSON.RESP
    JsonBatch.resp(baseBatch, key, { path: "$" });
    responseData.push([
        'resp(key, "$")',
        [
            [
                "{",
                ["a", ["[", 110, 120]],
                ["b", ["[", 3, 4]],
                ["c", "c-test"],
                ["d", "false"],
            ],
        ],
    ]);

    // JSON.DEL
    JsonBatch.del(baseBatch, key, { path: "$.d" });
    responseData.push(['del(key, { path: "$.d" })', 1]);

    // JSON.FORGET
    JsonBatch.forget(baseBatch, key, { path: "$.c" });
    responseData.push(['forget(key, {path: "$.c" })', 1]);

    return responseData;
}

/**
 * This function gets server version using info command in glide client.
 *
 * @param addresses - Addresses containing host and port for the valkey server.
 * @returns Server version for valkey server
 */
export async function getServerVersion(
    addresses: [string, number][],
    clusterMode = false,
    tlsConfig?: TestTLSConfig,
): Promise<string> {
    let info = "";

    if (clusterMode) {
        const glideClusterClient = await GlideClusterClient.createClient({
            ...getClientConfigurationOption(addresses, ProtocolVersion.RESP2),
            ...tlsConfig,
        });
        info = getFirstResult(
            await glideClusterClient.info({ sections: [InfoOptions.Server] }),
        ).toString();
        await flushAndCloseClient(
            clusterMode,
            addresses,
            glideClusterClient,
            tlsConfig,
        );
    } else {
        const glideClient = await GlideClient.createClient({
            ...getClientConfigurationOption(addresses, ProtocolVersion.RESP2),
            ...tlsConfig,
        });
        info = await glideClient.info([InfoOptions.Server]);
        await flushAndCloseClient(
            clusterMode,
            addresses,
            glideClient,
            tlsConfig,
        );
    }

    let version = "";
    const redisVersionKey = "redis_version:";
    const valkeyVersionKey = "valkey_version:";

    if (info.includes(valkeyVersionKey)) {
        version = info.split(valkeyVersionKey)[1].split("\n")[0];
    } else if (info.includes(redisVersionKey)) {
        version = info.split(redisVersionKey)[1].split("\n")[0];
    }

    return version;
}

/**
 * Check if a command is available on the system
 */
export function checkCommandAvailability(command: string): Promise<boolean> {
    return new Promise((resolve) => {
        exec(`which ${command}`, (error) => {
            resolve(!error);
        });
    });
}

/**
 * Starts a Valkey/Redis-compatible server on the specified port
 */
export async function startServer(
    port: number,
): Promise<{ process: any; command: string }> {
    // check which command is available
    const serverCmd = await checkWhichCommandAvailable(
        "valkey-server",
        "redis-server",
    );
    // run server, and wait for it to start
    const serverProcess = await execAsync(`${serverCmd} --port ${port}`)
        .then(async (process) => {
            // wait for the server to start
            await new Promise((resolve) => setTimeout(resolve, 1000));
            // check if the server is running by connecting to it using a socket
            const client = new Socket();
            await new Promise<void>((resolve) => {
                client.connect(port, "localhost", () => {
                    client.end();
                    resolve();
                });
            });
            return process;
        })
        .catch((err) => {
            console.error("Failed to start the server:", err);
            process.exit(1);
        });

    return { process: serverProcess, command: serverCmd };
}
