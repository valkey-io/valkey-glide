/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import { GlideClusterClient, ProtocolVersion } from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
} from "./TestUtilities";
const TIMEOUT = 50000;

// tls cluster tests
describe("tls GlideClusterClient", () => {
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;

    beforeAll(async () => {
        cluster = await ValkeyCluster.createCluster(
            true,
            3,
            2,
            getServerVersion,
            true,
            {
                advancedConfiguration: {
                    tlsAdvancedConfiguration: {
                        insecure: true,
                    },
                },
                useTLS: true,
            },
        );
    }, 40000);

    afterEach(async () => {
        await flushAndCloseClient(true, cluster.getAddresses(), client, {
            advancedConfiguration: {
                tlsAdvancedConfiguration: {
                    insecure: true,
                },
            },
            useTLS: true,
        });
    });

    afterAll(async () => {
        if (cluster) {
            await cluster.close();
        }
    });

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        "clusterClient connect with insecure TLS (protocol: %p)",
        async (protocol) => {
            const config = getClientConfigurationOption(
                cluster.getAddresses(),
                protocol,
                { useTLS: true },
            );
            const c = {
                advancedConfiguration: {
                    tlsAdvancedConfiguration: {
                        insecure: true,
                    },
                },
                ...config,
            };
            client = await GlideClusterClient.createClient(c);

            const result = await client.ping();
            expect(result.toString()).toBe("PONG");
        },
        TIMEOUT,
    );
});

// // tls cluster tests
// describe("tls GlideClient", () => {
//     let cluster: ValkeyCluster;
//     let client: GlideClient;

//     beforeAll(async () => {
//         cluster = await ValkeyCluster.createCluster(
//             false,
//             1,
//             2,
//             getServerVersion,
//             true,
//             {
//                 advancedConfiguration: {
//                     tlsAdvancedConfiguration: {
//                         insecure: true,
//                     },
//                 },
//                 useTLS: true,
//             },
//         );
//     }, 40000);

//     afterEach(async () => {
//         await flushAndCloseClient(true, cluster.getAddresses(), client, {
//             advancedConfiguration: {
//                 tlsAdvancedConfiguration: {
//                     insecure: true,
//                 },
//             },
//             useTLS: true,
//         });
//     });

//     afterAll(async () => {
//         if (cluster) {
//             await cluster.close();
//         }
//     });

//     it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
//         "Standalone client connect with insecure TLS (protocol: %p)",
//         async (protocol) => {
//             const config = getClientConfigurationOption(
//                 cluster.getAddresses(),
//                 protocol,
//                 { useTLS: true },
//             );
//             const c = {
//                 advancedConfiguration: {
//                     tlsAdvancedConfiguration: {
//                         insecure: true,
//                     },
//                 },
//                 ...config,
//             };
//             client = await GlideClient.createClient(c);

//             const result = await client.ping();
//             expect(result.toString()).toBe("PONG");
//         },
//         TIMEOUT,
//     );
// });

// // tls standalone tests
// describe(`tls GlideClient %p`, () => {
//     const testsFailed = 0;
//     let cluster: ValkeyCluster;
//     let client: GlideClient;

//     beforeAll(async () => {

//         const valkeyTlsStandalone = await ValkeyCluster.createCluster(false, 1, 2, getServerVersion, true);
//     }, 40000);

//     afterEach(async () => {
//         await flushAndCloseClient(true, cluster.getAddresses(), client);
//     });

//     afterAll(async () => {
//         if (testsFailed === 0) {
//             await cluster.close();
//         } else {
//             await cluster.close(true);
//         }
//     });

//     it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
//         `GlideClient connect with insecure TLS (protocol: %p)"`,
//         async (protocol) => {
//             client = await GlideClient.createClient(
//                 getClientConfigurationOption(cluster.getAddresses(), protocol),
//             );

//         }, TIMEOUT,
//     );

// });
