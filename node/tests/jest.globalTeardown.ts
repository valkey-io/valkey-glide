/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Jest global teardown: reads cluster names from the temp file written by
 * globalSetup and deletes the ElastiCache clusters.
 *
 * Only runs when USE_ELASTICACHE=true is set in the environment.
 */

import * as fs from "fs";
import { ElastiCacheClusterBase } from "../src/elasticache/ClusterBase";
import { ELASTICACHE_ENDPOINTS_FILE, EndpointsFile } from "./jest.globalSetup";

export default async function globalTeardown(): Promise<void> {
    if (process.env.USE_ELASTICACHE !== "true") {
        return;
    }

    if (!fs.existsSync(ELASTICACHE_ENDPOINTS_FILE)) {
        console.log(
            `[globalTeardown] Endpoints file not found at ${ELASTICACHE_ENDPOINTS_FILE} - nothing to delete.`,
        );
        return;
    }

    let data: EndpointsFile;

    try {
        data = JSON.parse(fs.readFileSync(ELASTICACHE_ENDPOINTS_FILE, "utf-8"));
    } catch (err) {
        console.error(`[globalTeardown] Failed to read endpoints file: ${err}`);
        return;
    }

    const clusterNames = [data.cmdClusterName, data.cmeClusterName];

    console.log(
        `[globalTeardown] Deleting clusters: ${clusterNames.join(", ")}`,
    );

    const allClusters = [
        new ElastiCacheClusterBase(data.cmdClusterName),
        new ElastiCacheClusterBase(data.cmeClusterName),
    ];

    const results = await Promise.allSettled(
        allClusters.map((c) => c.deleteCluster()),
    );

    results.forEach((result, i) => {
        const name = clusterNames[i];

        if (result.status === "rejected") {
            console.error(
                `[globalTeardown] Failed to delete ${name}: ${result.reason}`,
            );
        } else {
            console.log(`[globalTeardown] Delete request sent for ${name}.`);
        }
    });

    // Clean up temp file
    try {
        fs.unlinkSync(ELASTICACHE_ENDPOINTS_FILE);
    } catch {
        // best effort
    }

    console.log("[globalTeardown] Done.");
}
