/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { execFile } from "child_process";
import { lt } from "semver";

const PYTHON_CMD = process.platform === "win32" ? "python" : "python3";
const PY_SCRIPT_PATH = __dirname + "/cluster_manager.py";

// Read remote config from C:\glide-remote.json if present (Windows EC2 CI).
// This is done at module load time so appendRemoteArgs works correctly.
// Env vars are also checked as fallback for non-Windows environments.
function _loadRemoteConfig(): { instanceId?: string; privateIp?: string; region: string } {
    // Check env vars first
    if (process.env.GLIDE_REMOTE_INSTANCE_ID) {
        return {
            instanceId: process.env.GLIDE_REMOTE_INSTANCE_ID,
            privateIp: process.env.GLIDE_REMOTE_IP,
            region: process.env.GLIDE_REMOTE_REGION ?? "us-east-1",
        };
    }
    // Fall back to file (Windows EC2 CI)
    try {
        const configPath = "C:\\glide-remote.json";
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const fs = require("fs") as typeof import("fs");
        if (fs.existsSync(configPath)) {
            const cfg = JSON.parse(fs.readFileSync(configPath, "utf-8"));
            return { instanceId: cfg.instanceId, privateIp: cfg.privateIp, region: cfg.region ?? "us-east-1" };
        }
    } catch { /* ignore */ }
    return { region: "us-east-1" };
}
const _remoteConfig = _loadRemoteConfig();
const REMOTE_INSTANCE_ID = _remoteConfig.instanceId;
const REMOTE_IP = _remoteConfig.privateIp;
const REMOTE_REGION = _remoteConfig.region;

/**
 * Appends --remote flags to cluster_manager.py args when running on Windows CI.
 * This transparently redirects server start/stop to the Linux EC2.
 */
function appendRemoteArgs(args: string[]): string[] {
    if (REMOTE_INSTANCE_ID && REMOTE_IP) {
        return [
            ...args,
            "--remote",
            REMOTE_INSTANCE_ID,
            "--remote-ip",
            REMOTE_IP,
            "--remote-region",
            REMOTE_REGION,
        ];
    }
    return args;
}

function parseOutput(input: string): {
    clusterFolder: string;
    addresses: [string, number][];
} {
    const lines = input.split(/\r\n|\r|\n/);
    const clusterFolderLine = lines.find((line) =>
        line.startsWith("CLUSTER_FOLDER="),
    );
    const clusterNodesLine = lines.find((line) =>
        line.startsWith("CLUSTER_NODES="),
    );

    if (!clusterFolderLine || !clusterNodesLine) {
        throw new Error(`Insufficient data in input: ${input}`);
    }

    const clusterFolder = clusterFolderLine.substring("CLUSTER_FOLDER=".length);
    const nodes = clusterNodesLine.substring("CLUSTER_NODES=".length);

    if (!clusterFolder || !nodes) {
        throw new Error(`Insufficient data in input: ${input}`);
    }

    const ports = nodes
        .split(",")
        .map((address) => address.split(":"))
        .map((address) => [address[0], Number(address[1])]) as [
        string,
        number,
    ][];

    return {
        clusterFolder,
        addresses: ports,
    };
}

export type TestTLSConfig = {
    useTLS: boolean;
    requestTimeout?: number;
    advancedConfiguration?: {
        tlsAdvancedConfiguration?: {
            insecure?: boolean;
            rootCertificates?: Buffer<ArrayBufferLike>;
        };
    };
};

export class ValkeyCluster {
    private addresses: [string, number][];
    private clusterFolder: string | undefined;
    private version: string;
    private tls: boolean;

    private constructor(
        version: string,
        addresses: [string, number][],
        tls: boolean,
        clusterFolder?: string,
    ) {
        this.addresses = addresses;
        this.clusterFolder = clusterFolder;
        this.version = version;
        this.tls = tls;
    }

    public static createCluster(
        cluster_mode: boolean,
        shardCount: number,
        replicaCount: number,
        getVersionCallback: (
            addresses: [string, number][],
            clusterMode: boolean,
            tlsConfig?: TestTLSConfig,
        ) => Promise<string>,
        tls: boolean = false,
        tlsConfig?: TestTLSConfig,
        loadModule?: string[],
        tlsAuthClients: boolean = false,
    ): Promise<ValkeyCluster> {
        return new Promise<ValkeyCluster>((resolve, reject) => {
            const commandArgs = [
                "start",
                "-r",
                `${replicaCount}`,
                "-n",
                `${shardCount}`,
            ];

            if (tls) {
                commandArgs.unshift("--tls");
            }

            if (cluster_mode) {
                commandArgs.push("--cluster-mode");
            }

            if (tlsAuthClients) {
                commandArgs.push("--tls-auth-clients");
            }

            if (loadModule) {
                if (loadModule.length === 0) {
                    throw new Error(
                        "Please provide the path(s) to the module(s) you want to load.",
                    );
                }

                for (const module of loadModule) {
                    commandArgs.push("--load-module", module);
                }
            }

            execFile(
                PYTHON_CMD,
                appendRemoteArgs([PY_SCRIPT_PATH, ...commandArgs]),
                (error, stdout) => {
                    if (error) {
                        reject(error);
                    } else {
                        const { clusterFolder, addresses } =
                            parseOutput(stdout);
                        resolve(
                            getVersionCallback(
                                addresses,
                                cluster_mode,
                                tlsConfig,
                            ).then(
                                (ver) =>
                                    new ValkeyCluster(
                                        ver,
                                        addresses,
                                        tls,
                                        clusterFolder,
                                    ),
                            ),
                        );
                    }
                },
            );
        });
    }

    public static async initFromExistingCluster(
        cluster_mode: boolean,
        addresses: [string, number][],
        getVersionCallback: (
            addresses: [string, number][],
            clusterMode: boolean,
        ) => Promise<string>,
        tls: boolean = false,
    ): Promise<ValkeyCluster> {
        return getVersionCallback(addresses, cluster_mode).then(
            (ver) => new ValkeyCluster(ver, addresses, tls, ""),
        );
    }

    public ports(): number[] {
        return this.addresses.map((address) => address[1]);
    }

    public getAddresses(): [string, number][] {
        return this.addresses;
    }

    public getVersion(): string {
        return this.version;
    }

    public isTls(): boolean {
        return this.tls;
    }

    public checkIfServerVersionLessThan(minVersion: string): boolean {
        return lt(this.version, minVersion);
    }

    public async close(keepFolder = false): Promise<void> {
        if (this.clusterFolder) {
            await new Promise<void>((resolve, reject) => {
                const commandArgs = [PY_SCRIPT_PATH];

                if (this.tls) {
                    commandArgs.push(`--tls`);
                }

                commandArgs.push(
                    `stop`,
                    `--cluster-folder`,
                    `${this.clusterFolder}`,
                );

                if (keepFolder) {
                    commandArgs.push(`--keep-folder`);
                }

                execFile(PYTHON_CMD, appendRemoteArgs(commandArgs), (error, _, stderr) => {
                    if (error) {
                        console.error(stderr);
                        reject(error);
                    } else {
                        resolve();
                    }
                });
            });
        }
    }
}

export default ValkeyCluster;
