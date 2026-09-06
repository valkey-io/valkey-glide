/**
 * Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
 *
 * Jest global setup:
 * - When USE_ELASTICACHE=true: provisions ElastiCache clusters via elasticache_manager.py
 *
 * Only active when USE_ELASTICACHE=true.
 */

import * as fs from "fs";
import * as os from "os";
import * as path from "path";

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

function parseElastiCacheOutput(output: string): {
    name: string;
    endpoint: string;
} {
    const nameLine = output
        .split("\n")
        .find((l) => l.startsWith("CLUSTER_NAME="));
    const endpointLine = output
        .split("\n")
        .find((l) => l.startsWith("CLUSTER_ENDPOINT="));

    if (!nameLine || !endpointLine) {
        throw new Error(
            `[globalSetup] Could not parse elasticache_manager.py output:\n${output}`,
        );
    }

    return {
        name: nameLine.split("=").slice(1).join("=").trim(),
        endpoint: endpointLine.split("=").slice(1).join("=").trim(),
    };
}

function spawnAsync(
    cmd: string,
    args: string[],
    label: string,
    timeoutMs = 10 * 60 * 1000,
): Promise<string> {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { spawn } = require("child_process") as typeof import("child_process");
    return new Promise((resolve, reject) => {
        const proc = spawn(cmd, args, { env: process.env });
        const timer = setTimeout(() => {
            proc.kill();
            reject(new Error(`[globalSetup] ${label} timed out after ${timeoutMs / 60000} min`));
        }, timeoutMs);
        let stdout = "";
        let stderr = "";
        proc.stdout.on("data", (d: Buffer) => { stdout += d.toString(); process.stdout.write(d); });
        proc.stderr.on("data", (d: Buffer) => { stderr += d.toString(); process.stderr.write(d); });
        proc.on("close", (code: number | null) => {
            clearTimeout(timer);
            if (code !== 0) reject(new Error(`[globalSetup] ${label} exited ${code}\n${stderr}`));
            else resolve(stdout);
        });
        proc.on("error", (err: Error) => { clearTimeout(timer); reject(err); });
    });
}

export default async function globalSetup(): Promise<void> {
    // -------------------------------------------------------------------------
    // GLIDE_REMOTE path: start Valkey on Linux EC2, set endpoints for all tests
    // The orchestrator writes C:\glide-remote.json with instanceId/privateIp.
    // -------------------------------------------------------------------------
    const remoteConfigFile = "C:\\glide-remote.json";
    if (fs.existsSync(remoteConfigFile)) {
        let remoteConfig: { instanceId: string; privateIp: string; region: string };
        try {
            remoteConfig = JSON.parse(fs.readFileSync(remoteConfigFile, "utf-8"));
        } catch (e) {
            throw new Error(`[globalSetup] Failed to read remote config: ${e}`);
        }
        const { instanceId, privateIp, region } = remoteConfig;
        console.log(`[globalSetup] GLIDE_REMOTE: instance=${instanceId} ip=${privateIp}`);

        const pythonCmd = process.platform === "win32" ? "python" : "python3";
        const repoRoot = path.resolve(__dirname, "..", "..");
        const clusterManagerScript = path.join(repoRoot, "utils", "cluster_manager.py");
        const baseRemoteArgs = ["--remote", instanceId, "--remote-ip", privateIp, "--remote-region", region];

        console.log("[globalSetup] Killing any leftover Valkey servers...");
        await spawnAsync(pythonCmd, [
            clusterManagerScript, "stop", "--prefix", "cluster",
            "--remote", instanceId, "--remote-region", region,
        ], "stop leftover clusters", 30 * 1000).catch(() => { /* ignore if nothing to stop */ });

        console.log("[globalSetup] Starting standalone + cluster Valkey in parallel...");
        const [standaloneOut, clusterOut] = await Promise.all([
            spawnAsync(pythonCmd, [clusterManagerScript, "start", ...baseRemoteArgs], "start standalone", 5 * 60 * 1000),
            spawnAsync(pythonCmd, [clusterManagerScript, "start", "--cluster-mode", ...baseRemoteArgs], "start cluster", 5 * 60 * 1000),
        ]);

        const parseNodes = (out: string) => out.split("\n").find(l => l.startsWith("CLUSTER_NODES="))?.split("=").slice(1).join("=").trim().split(",")[0] ?? "";
        const standaloneEndpoint = parseNodes(standaloneOut);
        const clusterEndpoint = parseNodes(clusterOut);

        process.env.STANDALONE_ENDPOINT = standaloneEndpoint;
        process.env.CLUSTER_ENDPOINT = clusterEndpoint;

        const data: EndpointsFile = {
            cmdClusterName: "",
            cmeClusterName: "",
            standaloneEndpoint,
            clusterEndpoint,
        };
        fs.writeFileSync(ELASTICACHE_ENDPOINTS_FILE, JSON.stringify(data, null, 2));
        console.log(`[globalSetup] Standalone: ${standaloneEndpoint}`);
        console.log(`[globalSetup] Cluster: ${clusterEndpoint}`);
        return;
    }

    if (process.env.USE_ELASTICACHE !== "true") {
        console.log(
            "[globalSetup] USE_ELASTICACHE is not set - skipping cloud cluster creation.",
        );
        return;
    }

    const { spawn } = await import("child_process");
    const repoRoot = path.resolve(__dirname, "..", "..");
    const pythonCmd = process.platform === "win32" ? "python" : "python3";

    const managerScript = path.join(
        repoRoot,
        "utils",
        "elasticache_manager.py",
    );

    const commonArgs: string[] = [];
    if (process.env.AWS_REGION)
        commonArgs.push("--region", process.env.AWS_REGION);

    const startArgs = (extra: string[]): string[] => [
        "start",
        ...commonArgs,
        ...(process.env.NAME ? ["--name", process.env.NAME] : []),
        ...(process.env.EC_SUBNET_GROUP
            ? ["--subnet-group", process.env.EC_SUBNET_GROUP]
            : []),
        ...(process.env.EC_SECURITY_GROUP
            ? ["--security-group", process.env.EC_SECURITY_GROUP]
            : []),
        ...extra,
    ];

    console.log("[globalSetup] Launching CMD and CME clusters in parallel...");

    const createdClusters: { name: string; endpoint?: string; role: string }[] =
        [];

    function spawnElastiCache(args: string[], role: string): Promise<string> {
        return new Promise((resolve, reject) => {
            const proc = spawn(pythonCmd, [managerScript, ...args], {
                env: process.env,
            });
            const timeoutMs = 40 * 60 * 1000;
            const timer = setTimeout(() => {
                proc.kill();
                reject(
                    new Error(
                        `[globalSetup] elasticache_manager.py timed out after ${timeoutMs / 60000} minutes`,
                    ),
                );
            }, timeoutMs);
            let stdout = "";
            let stderr = "";
            proc.stdout.on("data", (d: Buffer) => {
                const s = d.toString();
                stdout += s;
                process.stdout.write(s);
                const nameMatch = s.match(/^CLUSTER_NAME=(.+)$/m);

                if (nameMatch) {
                    createdClusters.push({ name: nameMatch[1].trim(), role });
                }
            });
            proc.stderr.on("data", (d: Buffer) => {
                const s = d.toString();
                stderr += s;
                process.stderr.write(s);
            });
            proc.on("close", (code) => {
                clearTimeout(timer);

                if (code !== 0) {
                    reject(
                        new Error(
                            `[globalSetup] elasticache_manager.py exited with code ${code}\n${stderr}`,
                        ),
                    );
                } else {
                    resolve(stdout);
                }
            });
            proc.on("error", (err) => {
                clearTimeout(timer);
                reject(err);
            });
        });
    }

    let cmdOutput: string;
    let cmeOutput: string;

    try {
        [cmdOutput, cmeOutput] = await Promise.all([
            spawnElastiCache(startArgs([]), "cmd"),
            spawnElastiCache(startArgs(["--cluster-mode"]), "cme"),
        ]);
    } catch (err) {
        if (createdClusters.length > 0) {
            const partialData = {
                cmdClusterName:
                    createdClusters.find((c) => c.role === "cmd")?.name ?? "",
                cmeClusterName:
                    createdClusters.find((c) => c.role === "cme")?.name ?? "",
                standaloneEndpoint: "",
                clusterEndpoint: "",
            };
            fs.writeFileSync(
                ELASTICACHE_ENDPOINTS_FILE,
                JSON.stringify(partialData, null, 2),
            );
            console.error(
                `[globalSetup] Wrote partial cleanup file for ${createdClusters.length} cluster(s): ${createdClusters.map((c) => c.name).join(", ")}`,
            );
        }

        throw err;
    }

    const cmd = parseElastiCacheOutput(cmdOutput);
    const cme = parseElastiCacheOutput(cmeOutput);

    const standaloneEndpoint = cmd.endpoint.includes(":")
        ? cmd.endpoint
        : `${cmd.endpoint}:6379`;
    const clusterEndpoint = cme.endpoint.includes(":")
        ? cme.endpoint
        : `${cme.endpoint}:6379`;

    const data: EndpointsFile = {
        cmdClusterName: cmd.name,
        cmeClusterName: cme.name,
        standaloneEndpoint,
        clusterEndpoint,
    };

    fs.writeFileSync(ELASTICACHE_ENDPOINTS_FILE, JSON.stringify(data, null, 2));

    process.env.STANDALONE_ENDPOINT = standaloneEndpoint;
    process.env.CLUSTER_ENDPOINT = clusterEndpoint;

    console.log(
        `[globalSetup] CMD standalone: ${cmd.name} -> ${standaloneEndpoint}`,
    );
    console.log(
        `[globalSetup] CME cluster:    ${cme.name} -> ${clusterEndpoint}`,
    );
    console.log(
        `[globalSetup] Endpoints written to ${ELASTICACHE_ENDPOINTS_FILE}`,
    );
}
