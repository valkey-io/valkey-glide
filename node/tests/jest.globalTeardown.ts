/**
 * Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
 *
 * Jest global teardown: calls utils/elasticache_manager.py stop for each
 * cluster created by globalSetup.
 *
 * Only runs when USE_ELASTICACHE=true is set in the environment.
 */

import { spawnSync } from "child_process";
import * as fs from "fs";
import * as path from "path";
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

    const repoRoot = path.resolve(__dirname, "..", "..");
    const managerScript = path.join(
        repoRoot,
        "utils",
        "elasticache_manager.py",
    );

    for (const clusterName of [data.cmdClusterName, data.cmeClusterName]) {
        console.log(`[globalTeardown] Deleting cluster: ${clusterName}`);
        const pythonCmd = process.platform === "win32" ? "python" : "python3";
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
