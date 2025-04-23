/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideClient, GlideClusterClient } from "@valkey/valkey-glide";
import process from "node:process";
import {
    PORT,
    SIZE_SET_KEYSPACE,
    generateValue,
    receivedOptions,
} from "./utils.js";

async function fill_database(
    data_size: number,
    host: string,
    isCluster: boolean,
    tls: boolean,
    port: number = PORT,
) {
    const clientClass = isCluster ? GlideClusterClient : GlideClient;
    const client = await clientClass.createClient({
        addresses: [{ host, port: port }],
        useTLS: tls,
        requestTimeout: 1000,
    });
    const data = generateValue(data_size);
    const CONCURRENT_SETS = 1000;
    const BATCH_SIZE = 100;
    const KEYS_PER_WORKER = Math.floor(SIZE_SET_KEYSPACE / CONCURRENT_SETS);

    // Process each worker one at a time
    for (let index = 0; index < CONCURRENT_SETS; index++) {
        // Calculate start and end keys for this worker
        const startKey = index * KEYS_PER_WORKER;
        const endKey = startKey + KEYS_PER_WORKER;

        // Process in batches
        for (let i = startKey; i < endKey; i += BATCH_SIZE) {
            const keyValuePairs: Record<string, string> = {};

            // Calculate end of this batch
            const batchEnd = Math.min(i + BATCH_SIZE, endKey);

            // Create batch of key-value pairs
            for (let key = i; key < batchEnd; key++) {
                keyValuePairs[key.toString()] = data;
            }

            await client.mset(keyValuePairs);
        }
    }

    client.close();
}

Promise.resolve()
    .then(async () => {
        console.log(
            `Filling ${receivedOptions.host} with data size ${receivedOptions.dataSize}`,
        );
        await fill_database(
            receivedOptions.dataSize,
            receivedOptions.host,
            receivedOptions.clusterModeEnabled,
            receivedOptions.tls,
            receivedOptions.port,
        );
    })
    .then(() => {
        process.exit(0);
    });
