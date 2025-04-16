/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideClient, GlideClusterClient } from "@valkey/valkey-glide";
import process from "node:process";
import { PORT, receivedOptions } from "./utils.js";

interface ReceivedOptions {
    host: string;
    clusterModeEnabled: boolean;
    tls: boolean;
    port: number;
}

async function flush_database(
    host: string,
    isCluster: boolean,
    tls: boolean,
    port: number = PORT,
): Promise<void> {
    const clientClass = isCluster ? GlideClusterClient : GlideClient;
    const client = await clientClass.createClient({
        addresses: [{ host, port: port }],
        useTLS: tls,
    });
    await client.flushall();
    client.close();
}

Promise.resolve()
    .then(async (): Promise<void> => {
        console.log("Flushing " + (receivedOptions as ReceivedOptions).host);
        await flush_database(
            (receivedOptions as ReceivedOptions).host,
            (receivedOptions as ReceivedOptions).clusterModeEnabled,
            (receivedOptions as ReceivedOptions).tls,
            (receivedOptions as ReceivedOptions).port,
        );
    })
    .then(() => {
        process.exit(0);
    });
