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
import { DataType, Field } from "build-ts/src/server-modules/GlideFtOptions";
import {
    ConditionalChange,
    GlideClusterClient,
    GlideFt,
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

    it(
        "ServerModules check JSON module is loaded",
        async () => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), ProtocolVersion.RESP3),
            );
            const info = await client.info({
                sections: [InfoOptions.Modules],
                route: "randomNode",
            });
            expect(info).toContain("# search_index_stats");
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

    it(
        "ServerModules check Vector Search module is loaded",
        async () => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), ProtocolVersion.RESP3),
            );
            const info = await client.info({
                sections: [InfoOptions.Modules],
                route: "randomNode",
            });
            expect(info).toContain("# search_index_stats");
        },
    );

    it(
        "Ft.Create test",
        async () => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), ProtocolVersion.RESP3),
            );

            // Create a few simple indices:
            const vectorField_1: VectorField = {type: "VECTOR", name: "vec", alias: "VEC", attributes: {algorithm: "HNSW", type: "FLOAT32", dim: 2, distanceMetric: "L2"} };
            expect(
                await GlideFt.create(client, uuidv4(), [vectorField_1])
            ).toEqual("OK");

            const vectorField_2: VectorField = {type: "VECTOR", name: "$.vec", alias: "VEC", attributes: {algorithm: "FLAT", type: "FLOAT32", dim: 6, distanceMetric: "L2"} };
            expect(
                await GlideFt.create(client, uuidv4(), [vectorField_2])
            ).toEqual("OK");

            // create an index with HNSW vector with additional parameters
            const vectorField_3: VectorField = {
                type: "VECTOR",
                name: "doc_embedding",
                attributes: {
                    algorithm: "HNSW",
                    type: "FLOAT32",
                    dim: 1536,
                    distanceMetric: "COSINE",
                    numberOfEdges: 40,
                    vectorsExaminedOnConstruction: 250,
                    vectorsExaminedOnRuntime: 40
                }
            };
            expect(
                await GlideFt.create(client, uuidv4(), [vectorField_3], {dataType: "HASH", prefixes: ["docs:"]})
            ).toEqual("OK");

            // create an index with multiple fields
            expect(
                await GlideFt.create(client, uuidv4(), [
                    {type: "TEXT", name: "title"},
                    {type: "NUMERIC", name: "published_at"},
                    {type: "TAG", name: "category"},
                ], {dataType: "HASH", prefixes: ["blog:post:"]})
            ).toEqual("OK");

            // create an index with multiple prefixes
            const name = uuidv4();
            expect(
                await GlideFt.create(client, name, [
                    {type: "TAG", name: "author_id"},
                    {type: "TAG", name: "author_ids"},
                    {type: "TEXT", name: "title"},
                    {type: "TEXT", name: "name"},
                ], {dataType: "HASH", prefixes: ["author:details:", "book:details:"]})
            ).toEqual("OK");

            // create a duplicating index
            expect(
                await GlideFt.create(client, name, [
                    {type: "TEXT", name: "title"},
                    {type: "TEXT", name: "name"},
                ])
            ).toEqual("OK");
        },
    );
});
