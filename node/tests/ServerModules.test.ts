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
    GlideJson,
    InfoOptions,
    JsonGetOptions,
    ProtocolVersion,
    RequestError,
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
            expect(await GlideJson.toggle(client, key, "$..bool")).toEqual([
                false,
                true,
                null,
            ]);
            expect(await GlideJson.toggle(client, key, "bool")).toBe(true);
            expect(
                await GlideJson.toggle(client, key, "$.non_existing"),
            ).toEqual([]);
            expect(await GlideJson.toggle(client, key, "$.nested")).toEqual([
                null,
            ]);

            // expect request errors
            await expect(
                GlideJson.toggle(client, key, "nested"),
            ).rejects.toThrow(RequestError);
            await expect(
                GlideJson.toggle(client, key, ".non_existing"),
            ).rejects.toThrow(RequestError);
            await expect(
                GlideJson.toggle(client, "non_existing_key", "$"),
            ).rejects.toThrow(RequestError);
        },
    );
});
