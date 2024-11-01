/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import {
    ConditionalChange,
    Decoder,
    GlideClusterClient,
    GlideFt,
    GlideJson,
    InfoOptions,
    JsonGetOptions,
    ProtocolVersion,
    RequestError,
    VectorField,
} from "..";
import { ValkeyCluster } from "../../utils/TestUtils";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
    parseCommandLineArgs,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 50000;

describe("Server Module Tests", () => {
    let cluster: ValkeyCluster;

    beforeAll(async () => {
        const clusterAddresses = parseCommandLineArgs()["cluster-endpoints"];
        cluster = await ValkeyCluster.initFromExistingCluster(
            true,
            parseEndpoints(clusterAddresses),
            getServerVersion,
        );
    }, 20000);

    afterAll(async () => {
        await cluster.close();
    }, TIMEOUT);

    describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "GlideJson",
        (protocol) => {
            let client: GlideClusterClient;

            afterEach(async () => {
                await flushAndCloseClient(true, cluster.getAddresses(), client);
            });

            it("check modules loaded", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const info = await client.info({
                    sections: [InfoOptions.Modules],
                    route: "randomNode",
                });
                expect(info).toContain("# json_core_metrics");
                expect(info).toContain("# search_index_stats");
            });

            it("json.set and json.get tests", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                const jsonValue = { a: 1.0, b: 2 };

                // JSON.set
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");

                // JSON.get
                let result = await GlideJson.get(client, key, { path: "." });
                expect(JSON.parse(result.toString())).toEqual(jsonValue);

                // JSON.get with array of paths
                result = await GlideJson.get(client, key, {
                    path: ["$.a", "$.b"],
                });
                expect(JSON.parse(result.toString())).toEqual({
                    "$.a": [1.0],
                    "$.b": [2],
                });

                // JSON.get with non-existing key
                expect(
                    await GlideJson.get(client, "non_existing_key", {
                        path: ["$"],
                    }),
                );

                // JSON.get with non-existing path
                result = await GlideJson.get(client, key, { path: "$.d" });
                expect(result).toEqual("[]");
            });

            it("json.set and json.get tests with multiple value", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();

                // JSON.set with complex object
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify({
                            a: { c: 1, d: 4 },
                            b: { c: 2 },
                            c: true,
                        }),
                    ),
                ).toBe("OK");

                // JSON.get with deep path
                let result = await GlideJson.get(client, key, {
                    path: "$..c",
                });
                expect(JSON.parse(result.toString())).toEqual([true, 1, 2]);

                // JSON.set with deep path
                expect(
                    await GlideJson.set(client, key, "$..c", '"new_value"'),
                ).toBe("OK");

                // verify JSON.set result
                result = await GlideJson.get(client, key, { path: "$..c" });
                expect(JSON.parse(result.toString())).toEqual([
                    "new_value",
                    "new_value",
                    "new_value",
                ]);
            });

            it("json.set conditional set", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                const value = JSON.stringify({ a: 1.0, b: 2 });

                expect(
                    await GlideJson.set(client, key, "$", value, {
                        conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                    }),
                ).toBeNull();

                expect(
                    await GlideJson.set(client, key, "$", value, {
                        conditionalChange:
                            ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    }),
                ).toBe("OK");

                expect(
                    await GlideJson.set(client, key, "$.a", "4.5", {
                        conditionalChange:
                            ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    }),
                ).toBeNull();
                let result = await GlideJson.get(client, key, {
                    path: ".a",
                });
                expect(result).toEqual("1");

                expect(
                    await GlideJson.set(client, key, "$.a", "4.5", {
                        conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                    }),
                ).toBe("OK");
                result = await GlideJson.get(client, key, { path: ".a" });
                expect(result).toEqual("4.5");
            });

            it("json.get formatting", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                // Set initial JSON value
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify({ a: 1.0, b: 2, c: { d: 3, e: 4 } }),
                    ),
                ).toBe("OK");
                // JSON.get with formatting options
                let result = await GlideJson.get(client, key, {
                    path: "$",
                    indent: "  ",
                    newline: "\n",
                    space: " ",
                } as JsonGetOptions);

                const expectedResult1 =
                    '[\n  {\n    "a": 1,\n    "b": 2,\n    "c": {\n      "d": 3,\n      "e": 4\n    }\n  }\n]';
                expect(result).toEqual(expectedResult1);
                // JSON.get with different formatting options
                result = await GlideJson.get(client, key, {
                    path: "$",
                    indent: "~",
                    newline: "\n",
                    space: "*",
                } as JsonGetOptions);

                const expectedResult2 =
                    '[\n~{\n~~"a":*1,\n~~"b":*2,\n~~"c":*{\n~~~"d":*3,\n~~~"e":*4\n~~}\n~}\n]';
                expect(result).toEqual(expectedResult2);
            });

            it("json.arrinsert", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );

                const key = uuidv4();
                const doc = {
                    a: [],
                    b: { a: [1, 2, 3, 4] },
                    c: { a: "not an array" },
                    d: [{ a: ["x", "y"] }, { a: [["foo"]] }],
                    e: [{ a: 42 }, { a: {} }],
                    f: { a: [true, false, null] },
                };
                expect(
                    await GlideJson.set(client, key, "$", JSON.stringify(doc)),
                ).toBe("OK");

                const result = await GlideJson.arrinsert(
                    client,
                    key,
                    "$..a",
                    0,
                    [
                        '"string_value"',
                        "123",
                        '{"key": "value"}',
                        "true",
                        "null",
                        '["bar"]',
                    ],
                );
                expect(result).toEqual([6, 10, null, 8, 7, null, null, 9]);

                const expected = {
                    a: [
                        "string_value",
                        123,
                        { key: "value" },
                        true,
                        null,
                        ["bar"],
                    ],
                    b: {
                        a: [
                            "string_value",
                            123,
                            { key: "value" },
                            true,
                            null,
                            ["bar"],
                            1,
                            2,
                            3,
                            4,
                        ],
                    },
                    c: { a: "not an array" },
                    d: [
                        {
                            a: [
                                "string_value",
                                123,
                                { key: "value" },
                                true,
                                null,
                                ["bar"],
                                "x",
                                "y",
                            ],
                        },
                        {
                            a: [
                                "string_value",
                                123,
                                { key: "value" },
                                true,
                                null,
                                ["bar"],
                                ["foo"],
                            ],
                        },
                    ],
                    e: [{ a: 42 }, { a: {} }],
                    f: {
                        a: [
                            "string_value",
                            123,
                            { key: "value" },
                            true,
                            null,
                            ["bar"],
                            true,
                            false,
                            null,
                        ],
                    },
                };
                expect(
                    JSON.parse((await GlideJson.get(client, key)) as string),
                ).toEqual(expected);
            });

            it("json.arrpop", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );

                const key = uuidv4();
                let doc =
                    '{"a": [1, 2, true], "b": {"a": [3, 4, ["value", 3, false], 5], "c": {"a": 42}}}';
                expect(await GlideJson.set(client, key, "$", doc)).toBe("OK");

                let res = await GlideJson.arrpop(client, key, {
                    path: "$.a",
                    index: 1,
                });
                expect(res).toEqual(["2"]);

                res = await GlideJson.arrpop(client, Buffer.from(key), {
                    path: "$..a",
                });
                expect(res).toEqual(["true", "5", null]);

                res = await GlideJson.arrpop(client, key, {
                    path: "..a",
                    decoder: Decoder.Bytes,
                });
                expect(res).toEqual(Buffer.from("1"));

                // Even if only one array element was returned, ensure second array at `..a` was popped
                doc = (await GlideJson.get(client, key, {
                    path: ["$..a"],
                })) as string;
                expect(doc).toEqual("[[],[3,4],42]");

                // Out of index
                res = await GlideJson.arrpop(client, key, {
                    path: Buffer.from("$..a"),
                    index: 10,
                });
                expect(res).toEqual([null, "4", null]);

                // pop without options
                expect(await GlideJson.set(client, key, "$", doc)).toEqual(
                    "OK",
                );
                expect(await GlideJson.arrpop(client, key)).toEqual("42");
            });

            it("json.arrlen", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );

                const key = uuidv4();
                const doc =
                    '{"a": [1, 2, 3], "b": {"a": [1, 2], "c": {"a": 42}}}';
                expect(await GlideJson.set(client, key, "$", doc)).toBe("OK");

                expect(
                    await GlideJson.arrlen(client, key, { path: "$.a" }),
                ).toEqual([3]);
                expect(
                    await GlideJson.arrlen(client, key, { path: "$..a" }),
                ).toEqual([3, 2, null]);
                // Legacy path retrieves the first array match at ..a
                expect(
                    await GlideJson.arrlen(client, key, { path: "..a" }),
                ).toEqual(3);
                // Value at path is not an array
                expect(
                    await GlideJson.arrlen(client, key, { path: "$" }),
                ).toEqual([null]);

                await expect(
                    GlideJson.arrlen(client, key, { path: "." }),
                ).rejects.toThrow();

                expect(
                    await GlideJson.set(client, key, "$", "[1, 2, 3, 4]"),
                ).toBe("OK");
                expect(await GlideJson.arrlen(client, key)).toEqual(4);
            });

            it("json.arrindex", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );

                const key1 = uuidv4();
                const key2 = uuidv4();
                const doc1 =
                    '{"a": [1, 3, true, "hello"], "b": {"a": [3, 4, [3, false], 5], "c": {"a": 42}}}';

                expect(await GlideJson.set(client, key1, "$", doc1)).toBe("OK");

                // Verify scalar type
                expect(
                    await GlideJson.arrindex(client, key1, "$..a", true),
                ).toEqual([2, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key1, "..a", true),
                ).toEqual(2);

                expect(
                    await GlideJson.arrindex(client, key1, "$..a", 3),
                ).toEqual([1, 0, null]);
                expect(
                    await GlideJson.arrindex(client, key1, "..a", 3),
                ).toEqual(1);

                expect(
                    await GlideJson.arrindex(client, key1, "$..a", '"hello"'),
                ).toEqual([3, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key1, "..a", '"hello"'),
                ).toEqual(3);

                expect(
                    await GlideJson.arrindex(client, key1, "$..a", null),
                ).toEqual([-1, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key1, "..a", null),
                ).toEqual(-1);

                // Value at the path is not an array
                expect(
                    await GlideJson.arrindex(client, key1, "$..c", 42),
                ).toEqual([null]);
                await expect(
                    GlideJson.arrindex(client, key1, "..c", 42),
                ).rejects.toThrow(RequestError);

                const doc2 =
                    '{"a": [1, 3, true, "foo", "meow", "m", "foo", "lol", false],' +
                    ' "b": {"a": [3, 4, ["value", 3, false], 5], "c": {"a": 42}}}';

                expect(await GlideJson.set(client, key2, "$", doc2)).toBe("OK");

                // Verify optional `start` and `end`
                expect(
                    await GlideJson.arrindex(client, key2, "$..a", '"foo"', {
                        start: 6,
                        end: 8,
                    }),
                ).toEqual([6, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key2, "$..a", '"foo"', {
                        start: 2,
                        end: 8,
                    }),
                ).toEqual([3, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key2, "..a", '"meow"', {
                        start: 2,
                        end: 8,
                    }),
                ).toEqual(4);

                // Verify without optional `end`
                expect(
                    await GlideJson.arrindex(client, key2, "$..a", '"foo"', {
                        start: 6,
                    }),
                ).toEqual([6, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key2, "..a", '"foo"', {
                        start: 6,
                    }),
                ).toEqual(6);

                // Verify optional `end` with 0 or -1 (means the last element is included)
                expect(
                    await GlideJson.arrindex(client, key2, "$..a", '"foo"', {
                        start: 6,
                        end: 0,
                    }),
                ).toEqual([6, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key2, "..a", '"foo"', {
                        start: 6,
                        end: 0,
                    }),
                ).toEqual(6);
                expect(
                    await GlideJson.arrindex(client, key2, "$..a", '"foo"', {
                        start: 6,
                        end: -1,
                    }),
                ).toEqual([6, -1, null]);
                expect(
                    await GlideJson.arrindex(client, key2, "..a", '"foo"', {
                        start: 6,
                        end: -1,
                    }),
                ).toEqual(6);

                // Test with non-existent path
                expect(
                    await GlideJson.arrindex(
                        client,
                        key2,
                        "$.nonexistent",
                        true,
                    ),
                ).toEqual([]);
                await expect(
                    GlideJson.arrindex(client, key2, "nonexistent", true),
                ).rejects.toThrow(RequestError);

                // Test with non-existent key
                await expect(
                    GlideJson.arrindex(client, "non_existing_key", "$", true),
                ).rejects.toThrow(RequestError);
                await expect(
                    GlideJson.arrindex(client, "non_existing_key", ".", true),
                ).rejects.toThrow(RequestError);
            });

            it("json.toggle tests", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                const key2 = uuidv4();
                const jsonValue = {
                    bool: true,
                    nested: { bool: false, nested: { bool: 10 } },
                };
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.toggle(client, key, { path: "$..bool" }),
                ).toEqual([false, true, null]);
                expect(
                    await GlideJson.toggle(client, key, { path: "bool" }),
                ).toBe(true);
                expect(
                    await GlideJson.toggle(client, key, {
                        path: "$.non_existing",
                    }),
                ).toEqual([]);
                expect(
                    await GlideJson.toggle(client, key, { path: "$.nested" }),
                ).toEqual([null]);

                // testing behavior with default pathing
                expect(await GlideJson.set(client, key2, ".", "true")).toBe(
                    "OK",
                );
                expect(await GlideJson.toggle(client, key2)).toBe(false);
                expect(await GlideJson.toggle(client, key2)).toBe(true);

                // expect request errors
                await expect(
                    GlideJson.toggle(client, key, { path: "nested" }),
                ).rejects.toThrow(RequestError);
                await expect(
                    GlideJson.toggle(client, key, { path: ".non_existing" }),
                ).rejects.toThrow(RequestError);
                await expect(
                    GlideJson.toggle(client, "non_existing_key", { path: "$" }),
                ).rejects.toThrow(RequestError);
            });

            it("json.del tests", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                const jsonValue = { a: 1.0, b: { a: 1, b: 2.5, c: true } };
                // setup
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");

                // non-existing paths
                expect(
                    await GlideJson.del(client, key, { path: "$..path" }),
                ).toBe(0);
                expect(
                    await GlideJson.del(client, key, { path: "..path" }),
                ).toBe(0);

                // deleting existing path
                expect(await GlideJson.del(client, key, { path: "$..a" })).toBe(
                    2,
                );
                expect(await GlideJson.get(client, key, { path: "$..a" })).toBe(
                    "[]",
                );
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(await GlideJson.del(client, key, { path: "..a" })).toBe(
                    2,
                );
                await expect(
                    GlideJson.get(client, key, { path: "..a" }),
                ).rejects.toThrow(RequestError);

                // verify result
                const result = await GlideJson.get(client, key, {
                    path: "$",
                });
                expect(JSON.parse(result as string)).toEqual([
                    { b: { b: 2.5, c: true } },
                ]);

                // test root deletion operations
                expect(await GlideJson.del(client, key, { path: "$" })).toBe(1);

                // reset and test dot deletion
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(await GlideJson.del(client, key, { path: "." })).toBe(1);

                // reset and test key deletion
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(await GlideJson.del(client, key)).toBe(1);
                expect(await GlideJson.del(client, key)).toBe(0);
                expect(
                    await GlideJson.get(client, key, { path: "$" }),
                ).toBeNull();

                // non-existing keys
                expect(
                    await GlideJson.del(client, "non_existing_key", {
                        path: "$",
                    }),
                ).toBe(0);
                expect(
                    await GlideJson.del(client, "non_existing_key", {
                        path: ".",
                    }),
                ).toBe(0);
            });

            it("json.forget tests", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                const jsonValue = { a: 1.0, b: { a: 1, b: 2.5, c: true } };
                // setup
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");

                // non-existing paths
                expect(
                    await GlideJson.forget(client, key, { path: "$..path" }),
                ).toBe(0);
                expect(
                    await GlideJson.forget(client, key, { path: "..path" }),
                ).toBe(0);

                // deleting existing paths
                expect(
                    await GlideJson.forget(client, key, { path: "$..a" }),
                ).toBe(2);
                expect(await GlideJson.get(client, key, { path: "$..a" })).toBe(
                    "[]",
                );
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.forget(client, key, { path: "..a" }),
                ).toBe(2);
                await expect(
                    GlideJson.get(client, key, { path: "..a" }),
                ).rejects.toThrow(RequestError);

                // verify result
                const result = await GlideJson.get(client, key, {
                    path: "$",
                });
                expect(JSON.parse(result as string)).toEqual([
                    { b: { b: 2.5, c: true } },
                ]);

                // test root deletion operations
                expect(await GlideJson.forget(client, key, { path: "$" })).toBe(
                    1,
                );

                // reset and test dot deletion
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(await GlideJson.forget(client, key, { path: "." })).toBe(
                    1,
                );

                // reset and test key deletion
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(await GlideJson.forget(client, key)).toBe(1);
                expect(await GlideJson.forget(client, key)).toBe(0);
                expect(
                    await GlideJson.get(client, key, { path: "$" }),
                ).toBeNull();

                // non-existing keys
                expect(
                    await GlideJson.forget(client, "non_existing_key", {
                        path: "$",
                    }),
                ).toBe(0);
                expect(
                    await GlideJson.forget(client, "non_existing_key", {
                        path: ".",
                    }),
                ).toBe(0);
            });

            it("json.type tests", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                const jsonValue = [1, 2.3, "foo", true, null, {}, []];
                // setup
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.type(client, key, { path: "$[*]" }),
                ).toEqual([
                    "integer",
                    "number",
                    "string",
                    "boolean",
                    "null",
                    "object",
                    "array",
                ]);
                expect(
                    await GlideJson.type(client, "non_existing", {
                        path: "$[*]",
                    }),
                ).toBeNull();
                expect(
                    await GlideJson.type(client, key, {
                        path: "$non_existing",
                    }),
                ).toEqual([]);

                const key2 = uuidv4();
                const jsonValue2 = { Name: "John", Age: 27 };
                // setup
                expect(
                    await GlideJson.set(
                        client,
                        key2,
                        "$",
                        JSON.stringify(jsonValue2),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.type(client, key2, { path: "." }),
                ).toEqual("object");
                expect(
                    await GlideJson.type(client, key2, { path: ".Age" }),
                ).toEqual("integer");
                expect(
                    await GlideJson.type(client, key2, { path: ".Job" }),
                ).toBeNull();
                expect(
                    await GlideJson.type(client, "non_existing", { path: "." }),
                ).toBeNull();
            });

            it("json.resp tests", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );
                const key = uuidv4();
                const jsonValue = {
                    obj: { a: 1, b: 2 },
                    arr: [1, 2, 3],
                    str: "foo",
                    bool: true,
                    int: 42,
                    float: 3.14,
                    nullVal: null,
                };
                // setup
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.resp(client, key, { path: "$.*" }),
                ).toEqual([
                    ["{", ["a", 1], ["b", 2]],
                    ["[", 1, 2, 3],
                    "foo",
                    "true",
                    42,
                    "3.14",
                    null,
                ]); // leading "{" - JSON objects, leading "[" - JSON arrays

                // multiple path match, the first will be returned
                expect(
                    await GlideJson.resp(client, key, { path: "*" }),
                ).toEqual(["{", ["a", 1], ["b", 2]]);

                // testing $ path
                expect(
                    await GlideJson.resp(client, key, { path: "$" }),
                ).toEqual([
                    [
                        "{",
                        ["obj", ["{", ["a", 1], ["b", 2]]],
                        ["arr", ["[", 1, 2, 3]],
                        ["str", "foo"],
                        ["bool", "true"],
                        ["int", 42],
                        ["float", "3.14"],
                        ["nullVal", null],
                    ],
                ]);

                // testing . path
                expect(
                    await GlideJson.resp(client, key, { path: "." }),
                ).toEqual([
                    "{",
                    ["obj", ["{", ["a", 1], ["b", 2]]],
                    ["arr", ["[", 1, 2, 3]],
                    ["str", "foo"],
                    ["bool", "true"],
                    ["int", 42],
                    ["float", "3.14"],
                    ["nullVal", null],
                ]);

                // $.str and .str
                expect(
                    await GlideJson.resp(client, key, { path: "$.str" }),
                ).toEqual(["foo"]);
                expect(
                    await GlideJson.resp(client, key, { path: ".str" }),
                ).toEqual("foo");

                // setup new json value
                const jsonValue2 = {
                    a: [1, 2, 3],
                    b: { a: [1, 2], c: { a: 42 } },
                };
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue2),
                    ),
                ).toBe("OK");

                expect(
                    await GlideJson.resp(client, key, { path: "..a" }),
                ).toEqual(["[", 1, 2, 3]);

                expect(
                    await GlideJson.resp(client, key, {
                        path: "$.nonexistent",
                    }),
                ).toEqual([]);

                // error case
                await expect(
                    GlideJson.resp(client, key, { path: "nonexistent" }),
                ).rejects.toThrow(RequestError);

                // non-existent key
                expect(
                    await GlideJson.resp(client, "nonexistent_key", {
                        path: "$",
                    }),
                ).toBeNull();
                expect(
                    await GlideJson.resp(client, "nonexistent_key", {
                        path: ".",
                    }),
                ).toBeNull();
                expect(
                    await GlideJson.resp(client, "nonexistent_key"),
                ).toBeNull();
            });

            it("json.arrtrim tests", async () => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );

                const key = uuidv4();
                const jsonValue = {
                    a: [0, 1, 2, 3, 4, 5, 6, 7, 8],
                    b: { a: [0, 9, 10, 11, 12, 13], c: { a: 42 } },
                };

                // setup
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");

                // Basic trim
                expect(
                    await GlideJson.arrtrim(client, key, "$..a", 1, 7),
                ).toEqual([7, 5, null]);

                // Test end >= size (should be treated as size-1)
                expect(
                    await GlideJson.arrtrim(client, key, "$.a", 0, 10),
                ).toEqual([7]);
                expect(
                    await GlideJson.arrtrim(client, key, ".a", 0, 10),
                ).toEqual(7);

                // Test negative start (should be treated as 0)
                expect(
                    await GlideJson.arrtrim(client, key, "$.a", -1, 5),
                ).toEqual([6]);
                expect(
                    await GlideJson.arrtrim(client, key, ".a", -1, 5),
                ).toEqual(6);

                // Test start >= size (should empty the array)
                expect(
                    await GlideJson.arrtrim(client, key, "$.a", 7, 10),
                ).toEqual([0]);
                const jsonValue2 = ["a", "b", "c"];
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        ".a",
                        JSON.stringify(jsonValue2),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.arrtrim(client, key, ".a", 7, 10),
                ).toEqual(0);

                // Test start > end (should empty the array)
                expect(
                    await GlideJson.arrtrim(client, key, "$..a", 2, 1),
                ).toEqual([0, 0, null]);
                const jsonValue3 = ["a", "b", "c", "d"];
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "..a",
                        JSON.stringify(jsonValue3),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.arrtrim(client, key, "..a", 2, 1),
                ).toEqual(0);

                // Multiple path match
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$",
                        JSON.stringify(jsonValue),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.arrtrim(client, key, "..a", 1, 10),
                ).toEqual(8);

                // Test with non-existent path
                await expect(
                    GlideJson.arrtrim(client, key, "nonexistent", 0, 1),
                ).rejects.toThrow(RequestError);
                expect(
                    await GlideJson.arrtrim(client, key, "$.nonexistent", 0, 1),
                ).toEqual([]);

                // Test with non-array path
                expect(await GlideJson.arrtrim(client, key, "$", 0, 1)).toEqual(
                    [null],
                );
                await expect(
                    GlideJson.arrtrim(client, key, ".", 0, 1),
                ).rejects.toThrow(RequestError);

                // Test with non-existent key
                await expect(
                    GlideJson.arrtrim(client, "non_existing_key", "$", 0, 1),
                ).rejects.toThrow(RequestError);
                await expect(
                    GlideJson.arrtrim(client, "non_existing_key", ".", 0, 1),
                ).rejects.toThrow(RequestError);

                // Test empty array
                expect(
                    await GlideJson.set(
                        client,
                        key,
                        "$.empty",
                        JSON.stringify([]),
                    ),
                ).toBe("OK");
                expect(
                    await GlideJson.arrtrim(client, key, "$.empty", 0, 1),
                ).toEqual([0]);
                expect(
                    await GlideJson.arrtrim(client, key, ".empty", 0, 1),
                ).toEqual(0);
            });

            it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
                "json.strlen tests",
                async (protocol) => {
                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const key = uuidv4();
                    const jsonValue = {
                        a: "foo",
                        nested: { a: "hello" },
                        nested2: { a: 31 },
                    };
                    // setup
                    expect(
                        await GlideJson.set(
                            client,
                            key,
                            "$",
                            JSON.stringify(jsonValue),
                        ),
                    ).toBe("OK");

                    expect(
                        await GlideJson.strlen(client, key, { path: "$..a" }),
                    ).toEqual([3, 5, null]);
                    expect(
                        await GlideJson.strlen(client, key, { path: "a" }),
                    ).toBe(3);

                    expect(
                        await GlideJson.strlen(client, key, {
                            path: "$.nested",
                        }),
                    ).toEqual([null]);
                    expect(
                        await GlideJson.strlen(client, key, { path: "$..a" }),
                    ).toEqual([3, 5, null]);

                    expect(
                        await GlideJson.strlen(client, "non_existing_key", {
                            path: ".",
                        }),
                    ).toBeNull();
                    expect(
                        await GlideJson.strlen(client, "non_existing_key", {
                            path: "$",
                        }),
                    ).toBeNull();
                    expect(
                        await GlideJson.strlen(client, key, {
                            path: "$.non_existing_path",
                        }),
                    ).toEqual([]);

                    // error case
                    await expect(
                        GlideJson.strlen(client, key, { path: "nested" }),
                    ).rejects.toThrow(RequestError);
                    await expect(GlideJson.strlen(client, key)).rejects.toThrow(
                        RequestError,
                    );
                },
            );

            it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
                "json.strappend tests",
                async (protocol) => {
                    client = await GlideClusterClient.createClient(
                        getClientConfigurationOption(
                            cluster.getAddresses(),
                            protocol,
                        ),
                    );
                    const key = uuidv4();
                    const jsonValue = {
                        a: "foo",
                        nested: { a: "hello" },
                        nested2: { a: 31 },
                    };
                    // setup
                    expect(
                        await GlideJson.set(
                            client,
                            key,
                            "$",
                            JSON.stringify(jsonValue),
                        ),
                    ).toBe("OK");

                    expect(
                        await GlideJson.strappend(client, key, '"bar"', {
                            path: "$..a",
                        }),
                    ).toEqual([6, 8, null]);
                    expect(
                        await GlideJson.strappend(
                            client,
                            key,
                            JSON.stringify("foo"),
                            {
                                path: "a",
                            },
                        ),
                    ).toBe(9);

                    expect(
                        await GlideJson.get(client, key, { path: "." }),
                    ).toEqual(
                        JSON.stringify({
                            a: "foobarfoo",
                            nested: { a: "hellobar" },
                            nested2: { a: 31 },
                        }),
                    );

                    expect(
                        await GlideJson.strappend(
                            client,
                            key,
                            JSON.stringify("bar"),
                            {
                                path: "$.nested",
                            },
                        ),
                    ).toEqual([null]);

                    await expect(
                        GlideJson.strappend(
                            client,
                            key,
                            JSON.stringify("bar"),
                            {
                                path: ".nested",
                            },
                        ),
                    ).rejects.toThrow(RequestError);
                    await expect(
                        GlideJson.strappend(client, key, JSON.stringify("bar")),
                    ).rejects.toThrow(RequestError);

                    expect(
                        await GlideJson.strappend(
                            client,
                            key,
                            JSON.stringify("try"),
                            {
                                path: "$.non_existing_path",
                            },
                        ),
                    ).toEqual([]);

                    await expect(
                        GlideJson.strappend(
                            client,
                            key,
                            JSON.stringify("try"),
                            {
                                path: ".non_existing_path",
                            },
                        ),
                    ).rejects.toThrow(RequestError);
                    await expect(
                        GlideJson.strappend(
                            client,
                            "non_existing_key",
                            JSON.stringify("try"),
                        ),
                    ).rejects.toThrow(RequestError);
                },
            );
        },
    );

    describe("GlideFt", () => {
        let client: GlideClusterClient;

        afterEach(async () => {
            await flushAndCloseClient(true, cluster.getAddresses(), client);
        });

        it("ServerModules check Vector Search module is loaded", async () => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(
                    cluster.getAddresses(),
                    ProtocolVersion.RESP3,
                ),
            );
            const info = await client.info({
                sections: [InfoOptions.Modules],
                route: "randomNode",
            });
            expect(info).toContain("# search_index_stats");
        });

        it("Ft.Create test", async () => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(
                    cluster.getAddresses(),
                    ProtocolVersion.RESP3,
                ),
            );

            // Create a few simple indices:
            const vectorField_1: VectorField = {
                type: "VECTOR",
                name: "vec",
                alias: "VEC",
                attributes: {
                    algorithm: "HNSW",
                    type: "FLOAT32",
                    dimensions: 2,
                    distanceMetric: "L2",
                },
            };
            expect(
                await GlideFt.create(client, uuidv4(), [vectorField_1]),
            ).toEqual("OK");

            expect(
                await GlideFt.create(
                    client,
                    "json_idx1",
                    [
                        {
                            type: "VECTOR",
                            name: "$.vec",
                            alias: "VEC",
                            attributes: {
                                algorithm: "HNSW",
                                type: "FLOAT32",
                                dimensions: 6,
                                distanceMetric: "L2",
                                numberOfEdges: 32,
                            },
                        },
                    ],
                    {
                        dataType: "JSON",
                        prefixes: ["json:"],
                    },
                ),
            ).toEqual("OK");

            const vectorField_2: VectorField = {
                type: "VECTOR",
                name: "$.vec",
                alias: "VEC",
                attributes: {
                    algorithm: "FLAT",
                    type: "FLOAT32",
                    dimensions: 6,
                    distanceMetric: "L2",
                },
            };
            expect(
                await GlideFt.create(client, uuidv4(), [vectorField_2]),
            ).toEqual("OK");

            // create an index with HNSW vector with additional parameters
            const vectorField_3: VectorField = {
                type: "VECTOR",
                name: "doc_embedding",
                attributes: {
                    algorithm: "HNSW",
                    type: "FLOAT32",
                    dimensions: 1536,
                    distanceMetric: "COSINE",
                    numberOfEdges: 40,
                    vectorsExaminedOnConstruction: 250,
                    vectorsExaminedOnRuntime: 40,
                },
            };
            expect(
                await GlideFt.create(client, uuidv4(), [vectorField_3], {
                    dataType: "HASH",
                    prefixes: ["docs:"],
                }),
            ).toEqual("OK");

            // create an index with multiple fields
            expect(
                await GlideFt.create(
                    client,
                    uuidv4(),
                    [
                        { type: "TEXT", name: "title" },
                        { type: "NUMERIC", name: "published_at" },
                        { type: "TAG", name: "category" },
                    ],
                    { dataType: "HASH", prefixes: ["blog:post:"] },
                ),
            ).toEqual("OK");

            // create an index with multiple prefixes
            const name = uuidv4();
            expect(
                await GlideFt.create(
                    client,
                    name,
                    [
                        { type: "TAG", name: "author_id" },
                        { type: "TAG", name: "author_ids" },
                        { type: "TEXT", name: "title" },
                        { type: "TEXT", name: "name" },
                    ],
                    {
                        dataType: "HASH",
                        prefixes: ["author:details:", "book:details:"],
                    },
                ),
            ).toEqual("OK");

            // create a duplicating index - expect a RequestError
            try {
                expect(
                    await GlideFt.create(client, name, [
                        { type: "TEXT", name: "title" },
                        { type: "TEXT", name: "name" },
                    ]),
                ).rejects.toThrow();
            } catch (e) {
                expect((e as Error).message).toContain("already exists");
            }

            // create an index without fields - expect a RequestError
            try {
                expect(
                    await GlideFt.create(client, uuidv4(), []),
                ).rejects.toThrow();
            } catch (e) {
                expect((e as Error).message).toContain(
                    "wrong number of arguments",
                );
            }

            // duplicated field name - expect a RequestError
            try {
                expect(
                    await GlideFt.create(client, uuidv4(), [
                        { type: "TEXT", name: "name" },
                        { type: "TEXT", name: "name" },
                    ]),
                ).rejects.toThrow();
            } catch (e) {
                expect((e as Error).message).toContain("already exists");
            }
        });

        it("Ft.DROPINDEX test", async () => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(
                    cluster.getAddresses(),
                    ProtocolVersion.RESP3,
                ),
            );

            // create an index
            const index = uuidv4();
            expect(
                await GlideFt.create(client, index, [
                    {
                        type: "VECTOR",
                        name: "vec",
                        attributes: {
                            algorithm: "HNSW",
                            distanceMetric: "L2",
                            dimensions: 2,
                        },
                    },
                    { type: "NUMERIC", name: "published_at" },
                    { type: "TAG", name: "category" },
                ]),
            ).toEqual("OK");

            const before = await client.customCommand(["FT._LIST"]);
            expect(before).toContain(index);

            // DROP it
            expect(await GlideFt.dropindex(client, index)).toEqual("OK");

            const after = await client.customCommand(["FT._LIST"]);
            expect(after).not.toContain(index);

            // dropping the index again results in an error
            try {
                expect(
                    await GlideFt.dropindex(client, index),
                ).rejects.toThrow();
            } catch (e) {
                expect((e as Error).message).toContain("Index does not exist");
            }
        });

        it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "FT.INFO ft.info",
            async (protocol) => {
                client = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        cluster.getAddresses(),
                        protocol,
                    ),
                );

                const index = uuidv4();
                expect(
                    await GlideFt.create(
                        client,
                        Buffer.from(index),
                        [
                            {
                                type: "VECTOR",
                                name: "$.vec",
                                alias: "VEC",
                                attributes: {
                                    algorithm: "HNSW",
                                    distanceMetric: "COSINE",
                                    dimensions: 42,
                                },
                            },
                            { type: "TEXT", name: "$.name" },
                        ],
                        { dataType: "JSON", prefixes: ["123"] },
                    ),
                ).toEqual("OK");

                let response = await GlideFt.info(client, Buffer.from(index));

                expect(response).toMatchObject({
                    index_name: index,
                    key_type: "JSON",
                    key_prefixes: ["123"],
                    fields: [
                        {
                            identifier: "$.name",
                            type: "TEXT",
                            field_name: "$.name",
                            option: "",
                        },
                        {
                            identifier: "$.vec",
                            type: "VECTOR",
                            field_name: "VEC",
                            option: "",
                            vector_params: {
                                distance_metric: "COSINE",
                                dimension: 42,
                            },
                        },
                    ],
                });

                response = await GlideFt.info(client, index, {
                    decoder: Decoder.Bytes,
                });
                expect(response).toMatchObject({
                    index_name: Buffer.from(index),
                });

                expect(await GlideFt.dropindex(client, index)).toEqual("OK");
                // querying a missing index
                await expect(GlideFt.info(client, index)).rejects.toThrow(
                    "Index not found",
                );
            },
        );
    });
});
