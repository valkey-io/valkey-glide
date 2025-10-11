/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideClient, GlideClusterClient } from "@valkey/valkey-glide";
import { createGlideClient, receivedOptions } from "./utils";

async function flush_database(
    host: string,
    isCluster: boolean,
    tls: boolean,
    port: number,
) {
    const client = await createGlideClient(host, isCluster, tls, port);

    if (isCluster) {
        // For cluster mode, use FLUSHALL on all nodes
        await (client as GlideClusterClient).flushall();
    } else {
        // For standalone mode, use FLUSHALL
        await (client as GlideClient).flushall();
    }

    client.close();
}

Promise.resolve()
    .then(async () => {
        console.log("Flushing " + receivedOptions.host);
        await flush_database(
            receivedOptions.host,
            receivedOptions.clusterModeEnabled,
            receivedOptions.tls,
            receivedOptions.port,
        );
    })
    .then(() => {
        process.exit(0);
    });
