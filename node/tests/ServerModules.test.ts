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
import { DataType } from "build-ts/src/server-modules/GlideFtOptions";
import { GlideClusterClient, GlideFt, InfoOptions, ProtocolVersion } from "..";
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
        "ServerModules check JSON module is loaded",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
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

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "ServerModules check Vector Search module is loaded",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const info = await client.info({
                sections: [InfoOptions.Modules],
                route: "randomNode",
            });
            expect(info).toContain("# search_index_stats");
        },
    );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "",
        async (protocol) => {
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );
            const fields: Field[] = [];
            const index = 

            GlideFt.create(client, index, fields, {dataType: DataType.Hash, prefixes});
        },
    );
});
