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
    GlideString,
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
                let result = await GlideJson.get(client, key, { paths: ["."] });
                expect(JSON.parse(result.toString())).toEqual(jsonValue);

                // JSON.get with array of paths
                result = await GlideJson.get(client, key, {
                    paths: ["$.a", "$.b"],
                });
                expect(JSON.parse(result.toString())).toEqual({
                    "$.a": [1.0],
                    "$.b": [2],
                });

                // JSON.get with non-existing key
                expect(
                    await GlideJson.get(client, "non_existing_key", {
                        paths: ["$"],
                    }),
                );

                // JSON.get with non-existing path
                result = await GlideJson.get(client, key, { paths: ["$.d"] });
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
                    paths: ["$..c"],
                });
                expect(JSON.parse(result.toString())).toEqual([true, 1, 2]);

                // JSON.set with deep path
                expect(
                    await GlideJson.set(client, key, "$..c", '"new_value"'),
                ).toBe("OK");

                // verify JSON.set result
                result = await GlideJson.get(client, key, { paths: ["$..c"] });
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
                    paths: [".a"],
                });
                expect(result).toEqual("1");

                expect(
                    await GlideJson.set(client, key, "$.a", "4.5", {
                        conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                    }),
                ).toBe("OK");
                result = await GlideJson.get(client, key, { paths: [".a"] });
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
                    paths: ["$"],
                    indent: "  ",
                    newline: "\n",
                    space: " ",
                } as JsonGetOptions);

                const expectedResult1 =
                    '[\n  {\n    "a": 1,\n    "b": 2,\n    "c": {\n      "d": 3,\n      "e": 4\n    }\n  }\n]';
                expect(result).toEqual(expectedResult1);
                // JSON.get with different formatting options
                result = await GlideJson.get(client, key, {
                    paths: ["$"],
                    indent: "~",
                    newline: "\n",
                    space: "*",
                } as JsonGetOptions);

                const expectedResult2 =
                    '[\n~{\n~~"a":*1,\n~~"b":*2,\n~~"c":*{\n~~~"d":*3,\n~~~"e":*4\n~~}\n~}\n]';
                expect(result).toEqual(expectedResult2);
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

                // deleting existing paths
                expect(await GlideJson.del(client, key, { path: "$..a" })).toBe(
                    2,
                );
                expect(
                    await GlideJson.get(client, key, { paths: ["$..a"] }),
                ).toBe("[]");
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
                    GlideJson.get(client, key, { paths: ["..a"] }),
                ).rejects.toThrow(RequestError);

                // verify result
                const result = await GlideJson.get(client, key, {
                    paths: ["$"],
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
                    await GlideJson.get(client, key, { paths: ["$"] }),
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
                expect(
                    await GlideJson.get(client, key, { paths: ["$..a"] }),
                ).toBe("[]");
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
                    GlideJson.get(client, key, { paths: ["..a"] }),
                ).rejects.toThrow(RequestError);

                // verify result
                const result = await GlideJson.get(client, key, {
                    paths: ["$"],
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
                    await GlideJson.get(client, key, { paths: ["$"] }),
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
