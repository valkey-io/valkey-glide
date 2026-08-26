/**
 * Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
 *
 * Jest global teardown:
 * - USE_ELASTICACHE=true: deletes ElastiCache clusters via elasticache_manager.py
 * - USE_EC2=true: stops Valkey servers via cluster_manager.py stop --remote,
 *   then terminates the EC2 instance via cluster_manager.py teardown-ec2
 */

import { spawnSync } from "child_process";
import * as fs from "fs";
import * as path from "path";
import { ELASTICACHE_ENDPOINTS_FILE, EndpointsFile } from "./jest.globalSetup";

export default async function globalTeardown(): Promise<void> {
    const useElastiCache = process.env.USE_ELASTICACHE === "true";
    const useEc2 = process.env.USE_EC2 === "true";

    if (!useElastiCache && !useEc2) {
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

    const repoRoot = path.resolve(__dirname, "..", "..");
    const pythonCmd = process.platform === "win32" ? "python" : "python3";
    const region = process.env.AWS_REGION ?? "us-east-1";

    // -------------------------------------------------------------------------
    // USE_EC2 teardown
    // -------------------------------------------------------------------------
    if (useEc2) {
        const clusterManagerScript = path.join(
            repoRoot,
            "utils",
            "cluster_manager.py",
        );
        const instanceId = data.ec2InstanceId;

        if (!instanceId) {
            console.log(
                "[globalTeardown] No EC2 instance ID in endpoints file - nothing to terminate.",
            );
        } else {
            // Stop Valkey servers remotely (best-effort)
            for (const [label, folder] of [
                ["standalone", data.standaloneClusterFolder],
                ["cluster", data.clusterClusterFolder],
            ] as [string, string | undefined][]) {
                if (!folder) continue;
                console.log(
                    `[globalTeardown] Stopping ${label} Valkey on EC2 (folder: ${folder})`,
                );
                spawnSync(
                    pythonCmd,
                    [
                        clusterManagerScript,
                        "stop",
                        "--cluster-folder",
                        folder,
                        "--remote",
                        instanceId,
                        "--remote-region",
                        region,
                    ],
                    { stdio: "inherit", env: process.env, timeout: 60 * 1000 },
                );
            }

            // Terminate EC2 instance
            console.log(
                `[globalTeardown] Terminating EC2 instance ${instanceId}`,
            );
            const result = spawnSync(
                pythonCmd,
                [
                    clusterManagerScript,
                    "teardown-ec2",
                    "--instance-id",
                    instanceId,
                    "--region",
                    region,
                ],
                { stdio: "inherit", env: process.env, timeout: 2 * 60 * 1000 },
            );

            if (result.status !== 0) {
                console.error(
                    `[globalTeardown] Failed to terminate EC2 instance ${instanceId}`,
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // USE_ELASTICACHE teardown
    // -------------------------------------------------------------------------
    if (useElastiCache) {
        const managerScript = path.join(
            repoRoot,
            "utils",
            "elasticache_manager.py",
        );

        for (const clusterName of [data.cmdClusterName, data.cmeClusterName]) {
            if (!clusterName) continue;
            console.log(
                `[globalTeardown] Deleting ElastiCache cluster: ${clusterName}`,
            );
            const regionArgs = process.env.AWS_REGION
                ? ["--region", process.env.AWS_REGION]
                : [];
            const result = spawnSync(
                pythonCmd,
                [
                    managerScript,
                    "stop",
                    "--cluster-name",
                    clusterName,
                    ...regionArgs,
                ],
                {
                    stdio: "inherit",
                    env: process.env,
                    timeout: 10 * 60 * 1000,
                    shell: process.platform === "win32",
                },
            );

            if (result.error) {
                console.error(
                    `[globalTeardown] Failed to spawn for ${clusterName}: ${result.error.message}`,
                );
            } else if (result.status !== 0) {
                console.error(
                    `[globalTeardown] elasticache_manager.py exited with code ${result.status} for ${clusterName}`,
                );
            }
        }
    }

    try {
        fs.unlinkSync(ELASTICACHE_ENDPOINTS_FILE);
    } catch {
        // best effort
    }

    console.log("[globalTeardown] Done.");
}
