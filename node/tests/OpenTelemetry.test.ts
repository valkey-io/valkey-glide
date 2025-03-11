/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { createLeakedOtelSpan } from "glide-rs";
import Long from "long";
import { GlideClusterClient, ProtocolVersion } from "..";
import ValkeyCluster from "../../utils/TestUtils";
import { flushAndCloseClient, getClientConfigurationOption, getServerVersion, parseEndpoints } from "./TestUtilities";

const TIMEOUT = 50000;

//cluster tests
describe("OpenTelemetry GlideClusterClient", () => {
    const testsFailed = 0;
    let cluster: ValkeyCluster;
    let client: GlideClusterClient;
    beforeAll(async () => {
        const clusterAddresses = global.CLUSTER_ENDPOINTS;
        // Connect to cluster or create a new one based on the parsed addresses
        cluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : // setting replicaCount to 1 to facilitate tests routed to replicas
              await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
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

    it.each([ProtocolVersion.RESP2])(
        `GlideClusterClient test basic openTelemetry_%p`,
        async (protocol) => {
            if (global.gc) {
                global.gc(); // Run garbage collection
            }
            const startMemory = process.memoryUsage().heapUsed;
            client = await GlideClusterClient.createClient(
                getClientConfigurationOption(cluster.getAddresses(), protocol),
            );

            if (global.gc) {
                global.gc(); // Run GC again to clean up
            }
            const endMemory = process.memoryUsage().heapUsed;

            console.log(`Memory before: ${startMemory}, after: ${endMemory}`);
            expect(endMemory).toBeLessThan(startMemory * 1.1); // Allow small fluctuations
        },
        TIMEOUT,
    );
});


//standalone tests
describe("OpenTelemetry GlideClient", () => {
    // const testsFailed = 0;
    // let cluster: ValkeyCluster;
    // let client: GlideClient;
    // beforeAll(async () => {
    //     const standaloneAddresses = global.STAND_ALONE_ENDPOINT;
    //     cluster = standaloneAddresses
    //         ? await ValkeyCluster.initFromExistingCluster(
    //               false,
    //               parseEndpoints(standaloneAddresses),
    //               getServerVersion,
    //           )
    //         : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);
    // }, 20000);

    // afterEach(async () => {
    //     await flushAndCloseClient(false, cluster.getAddresses(), client);
    // });

    // afterAll(async () => {
    //     if (testsFailed === 0) {
    //         await cluster.close();
    //     } else {
    //         await cluster.close(true);
    //     }
    // });

    // it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
    //     `GlideClient test basic openTelemetry_%p`,
    //     async (protocol) => {
    //         client = await GlideClient.createClient(
    //             getClientConfigurationOption(cluster.getAddresses(), protocol),
    //         );
    //     },
    //     TIMEOUT,
    // );

    it.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
        `GlideClient test basic openTelemetry_%p`,
        async (protocol) => {
            let span_ptr = createLeakedOtelSpan("testSpanFFI");
            console.log(span_ptr);
            let span_long = new Long(span_ptr[0], span_ptr[1]);
            console.log(span_long.toString());
            
        },
        TIMEOUT,
    );
});
