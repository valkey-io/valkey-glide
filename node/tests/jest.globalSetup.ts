/**
 * Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
 *
 * Jest global setup: creates ElastiCache clusters (CMD standalone and CME cluster)
 * and writes their endpoints to a shared temp file. setup.ts reads that file
 * to expose endpoints as globals to test workers.
 *
 * Only active when USE_ELASTICACHE=true.
 */

import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import { ElastiCacheClusterCMD } from "../src/elasticache/ClusterCMD";
import { ElastiCacheClusterCME } from "../src/elasticache/ClusterCME";
import { ConfigCMD } from "../src/elasticache/ConfigCMD";
import { ConfigCME } from "../src/elasticache/ConfigCME";

export const ELASTICACHE_ENDPOINTS_FILE = path.join(
    os.tmpdir(),
    "glide-windows-test-ec-endpoints.json",
);

export interface EndpointsFile {
    cmdClusterName: string;
    cmeClusterName: string;
    standaloneEndpoint: string;
    clusterEndpoint: string;
}

export default async function globalSetup(): Promise<void> {
    if (process.env.USE_ELASTICACHE !== "true") {
        console.log(
            "[globalSetup] USE_ELASTICACHE is not set - skipping ElastiCache cluster creation.",
        );
        return;
    }

    const cmdConfig = new ConfigCMD();
    const cmeConfig = new ConfigCME();

    console.log(`[globalSetup] CMD config: ${cmdConfig.toString()}`);
    console.log(`[globalSetup] CME config: ${cmeConfig.toString()}`);
    console.log("[globalSetup] Launching CMD and CME clusters in parallel...");

    const [cmdCluster, cmeCluster] = await Promise.all([
        ElastiCacheClusterCMD.launch(cmdConfig),
        ElastiCacheClusterCME.launch(cmeConfig),
    ]);

    const [standaloneEndpoint, clusterEndpoint] = await Promise.all([
        cmdCluster.getPrimaryEndpoint(),
        cmeCluster.getConfigurationEndpoint(),
    ]);

    if (!standaloneEndpoint) {
        throw new Error(
            `[globalSetup] CMD cluster ${cmdCluster.getName()} has no primary endpoint`,
        );
    }

    if (!clusterEndpoint) {
        throw new Error(
            `[globalSetup] CME cluster ${cmeCluster.getName()} has no configuration endpoint`,
        );
    }

    const standaloneWithPort = standaloneEndpoint.includes(":")
        ? standaloneEndpoint
        : `${standaloneEndpoint}:6379`;
    const clusterWithPort = clusterEndpoint.includes(":")
        ? clusterEndpoint
        : `${clusterEndpoint}:6379`;

    const data: EndpointsFile = {
        cmdClusterName: cmdCluster.getName(),
        cmeClusterName: cmeCluster.getName(),
        standaloneEndpoint: standaloneWithPort,
        clusterEndpoint: clusterWithPort,
    };

    // Write to temp file - setup.ts reads this to expose endpoints to test workers
    fs.writeFileSync(ELASTICACHE_ENDPOINTS_FILE, JSON.stringify(data, null, 2));

    // Also set on this process for any code running in globalSetup context
    process.env.STANDALONE_ENDPOINT = standaloneWithPort;
    process.env.CLUSTER_ENDPOINT = clusterWithPort;

    console.log(
        `[globalSetup] CMD standalone: ${cmdCluster.getName()} -> ${standaloneWithPort}`,
    );
    console.log(
        `[globalSetup] CME cluster:    ${cmeCluster.getName()} -> ${clusterWithPort}`,
    );
    console.log(
        `[globalSetup] Endpoints written to ${ELASTICACHE_ENDPOINTS_FILE}`,
    );
}
