/**
 * Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
 *
 * Jest global teardown:
 * - USE_ELASTICACHE=true: deletes ElastiCache clusters via elasticache_manager.py
 */

import { spawnSync } from "child_process";
import * as fs from "fs";
import * as path from "path";
import { ELASTICACHE_ENDPOINTS_FILE, EndpointsFile } from "./jest.globalSetup";

export default async function globalTeardown(): Promise<void> {
    // -------------------------------------------------------------------------
    // GLIDE_REMOTE teardown: stop pre-started Valkey on Linux EC2
    // -------------------------------------------------------------------------
    if (
        process.env.GLIDE_REMOTE_INSTANCE_ID &&
        fs.existsSync(ELASTICACHE_ENDPOINTS_FILE)
    ) {
        const pythonCmd =
            process.platform === "win32" ? "python" : "python3";
        const repoRoot = path.resolve(__dirname, "..", "..");
        const clusterManagerScript = path.join(
            repoRoot,
            "utils",
            "cluster_manager.py",
        );
        const instanceId = process.env.GLIDE_REMOTE_INSTANCE_ID;
        const region = process.env.GLIDE_REMOTE_REGION ?? "us-east-1";

        let data: EndpointsFile;

        try {
            data = JSON.parse(
                fs.readFileSync(ELASTICACHE_ENDPOINTS_FILE, "utf-8"),
            ) as EndpointsFile;
        } catch {
            data = {
                cmdClusterName: "",
                cmeClusterName: "",
                standaloneEndpoint: "",
                clusterEndpoint: "",
            };
        }

        for (const [label, folder] of ([
            ["standalone", data.standaloneClusterFolder],
            ["cluster", data.clusterClusterFolder],
        ] as [string, string | undefined][]).filter(([, f]) => f)) {
            console.log(
                `[globalTeardown] Stopping ${label} Valkey on Linux EC2 (folder: ${folder})`,
            );
            spawnSync(
                pythonCmd,
                [
                    clusterManagerScript,
                    "stop",
                    "--cluster-folder",
                    folder!,
                    "--remote",
                    instanceId!,
                    "--remote-region",
                    region,
                ],
                { stdio: "inherit", env: process.env, timeout: 60 * 1000 },
            );
        }

        try {
            fs.unlinkSync(ELASTICACHE_ENDPOINTS_FILE);
        } catch {
            // best effort
        }

        console.log("[globalTeardown] GLIDE_REMOTE teardown done.");
        return;
    }

    const useElastiCache = process.env.USE_ELASTICACHE === "true";

    if (!useElastiCache) {
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

    try {
        fs.unlinkSync(ELASTICACHE_ENDPOINTS_FILE);
    } catch {
        // best effort
    }

    console.log("[globalTeardown] Done.");
}
