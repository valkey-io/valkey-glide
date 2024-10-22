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
describe("GlideJson", () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;
    beforeAll(async () => {
        const clusterAddresses = parseCommandLineArgs()["cluster-endpoints"];
        cluster = await ValkeyCluster.initFromExistingCluster(
            true,
            parseEndpoints(clusterAddresses),
            getServerVersion,
        );
    }, 20000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    }, TIMEOUT);

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "check modules loaded",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const info = await client.info({
                sections: [InfoOptions.Modules],
                route: "randomNode",
            });
            expect(info).toContain("# json_core_metrics");
            expect(info).toContain("# search_index_stats");
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "json.set and json.get tests",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
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
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "json.set and json.get tests with multiple value",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const key = uuidv4();

            // JSON.set with complex object
            expect(
                await GlideJson.set(
                    client,
                    key,
                    "$",
                    JSON.stringify({ a: { c: 1, d: 4 }, b: { c: 2 }, c: true }),
                ),
            ).toBe("OK");

            // JSON.get with deep path
            let result = await GlideJson.get(client, key, { paths: ["$..c"] });
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
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "json.set conditional set",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
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
                    conditionalChange: ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                }),
            ).toBe("OK");

            expect(
                await GlideJson.set(client, key, "$.a", "4.5", {
                    conditionalChange: ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                }),
            ).toBeNull();
            let result = await GlideJson.get(client, key, { paths: [".a"] });
            expect(result).toEqual("1");

            expect(
                await GlideJson.set(client, key, "$.a", "4.5", {
                    conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                }),
            ).toBe("OK");
            result = await GlideJson.get(client, key, { paths: [".a"] });
            expect(result).toEqual("4.5");
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "json.get formatting",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
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
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "json.toggle tests",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
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
            expect(await GlideJson.toggle(client, key, { path: "bool" })).toBe(
                true,
            );
            expect(
                await GlideJson.toggle(client, key, { path: "$.non_existing" }),
            ).toEqual([]);
            expect(
                await GlideJson.toggle(client, key, { path: "$.nested" }),
            ).toEqual([null]);

            // testing behavior with default pathing
            expect(await GlideJson.set(client, key2, ".", "true")).toBe("OK");
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
        },
    );
});

describe("GlideFt", () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;
    beforeAll(async () => {
        const clusterAddresses = parseCommandLineArgs()["cluster-endpoints"];
        cluster = await ValkeyCluster.initFromExistingCluster(
            true,
            parseEndpoints(clusterAddresses),
            getServerVersion,
        );
    }, 20000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        }
    }, TIMEOUT);

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
                dimension: 2,
                distanceMetric: "L2",
            },
        };
        expect(await GlideFt.create(client, uuidv4(), [vectorField_1])).toEqual(
            "OK",
        );

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
                        dimension: 6,
                        distanceMetric: "L2",
                        numberOfEdges: 32,
                    },
                },
            ],
            {
                dataType: "JSON",
                prefixes: ["json:"],
            },
        );

        const vectorField_2: VectorField = {
            type: "VECTOR",
            name: "$.vec",
            alias: "VEC",
            attributes: {
                algorithm: "FLAT",
                type: "FLOAT32",
                dimension: 6,
                distanceMetric: "L2",
            },
        };
        expect(await GlideFt.create(client, uuidv4(), [vectorField_2])).toEqual(
            "OK",
        );

        // create an index with HNSW vector with additional parameters
        const vectorField_3: VectorField = {
            type: "VECTOR",
            name: "doc_embedding",
            attributes: {
                algorithm: "HNSW",
                type: "FLOAT32",
                dimension: 1536,
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
            expect((e as Error).message).toContain("wrong number of arguments");
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
});
