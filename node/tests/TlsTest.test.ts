/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
} from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
} from "./TestUtilities";
const TIMEOUT = 50000;

// tls cluster tests
describe("tls GlideClusterClient", () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;

    beforeAll(async () => {

        const valkeyTlsCluster = await ValkeyCluster.createCluster(true, 1, 2, getServerVersion, true);
        const valkeyTlsStandalone = await ValkeyCluster.createCluster(false, 1, 2, getServerVersion, true);
    }, 40000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        } else {
            await cluster.close(true);
        }
    });


    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "should connect with insecure TLS (protocol: %p)",
        async (protocol) => {
            const config = getClientConfigurationOption(cluster.getAddresses(), protocol);

            const client = await GlideClusterClient.createClient({
                useTLS: true,
                advancedConfiguration: {
                    tlsConfig: {
                        insecure: true,
                    },
                },
                ...config,
            });

            const result = await client.ping();
            expect(result.toString()).toBe("PONG");
            await client.close();
        }, TIMEOUT,
    );

});
// tls standalone tests
describe(`tls GlideClient %p`, () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClient;

    beforeAll(async () => {

        const valkeyTlsStandalone = await ValkeyCluster.createCluster(false, 1, 2, getServerVersion, true);
    }, 40000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client);
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.close();
        } else {
            await cluster.close(true);
        }
    });


    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient test basic scan_%p`,
        async (protocol) => {
            client = await GlideClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

        }, TIMEOUT,
    );

});
