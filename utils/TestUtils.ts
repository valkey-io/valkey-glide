/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { execFile } from "child_process";
import { createConnection } from "net";
import { lt } from "semver";

const PY_SCRIPT_PATH = __dirname + "/cluster_manager.py";

const isWindows = process.platform === "win32";

function toWslPath(p: string): string {
    return p
        .replace(/^([A-Za-z]):/, (_, d) => `/mnt/${d.toLowerCase()}`)
        .replace(/\\/g, "/");
}

const wslScriptPath = isWindows ? toWslPath(PY_SCRIPT_PATH) : PY_SCRIPT_PATH;

/**
 * On Windows/WSL, replica sync can lag after cluster_manager.py exits.
 * Poll each address: if it reports role:slave, wait until master_link_status:up
 * and master_sync_in_progress:0 before returning.
 */
async function waitForReplicasReady(
    addresses: [string, number][],
    timeoutMs = 15000,
): Promise<void> {
    const deadline = Date.now() + timeoutMs;

    async function getInfo(host: string, port: number): Promise<string> {
        return new Promise<string>((resolve, reject) => {
            const sock = createConnection({ host, port }, () => {
                sock.write("INFO replication\r\n");
            });
            let buf = "";
            const timer = setTimeout(() => {
                sock.destroy();
                resolve(buf); // return whatever we got
            }, 1000);
            sock.on("data", (d: Buffer) => {
                buf += d.toString();
                // Resolve as soon as we have enough to determine sync state
                if (buf.includes("master_link_status") || buf.includes("role:master")) {
                    clearTimeout(timer);
                    sock.destroy();
                    resolve(buf);
                }
            });
            sock.on("error", (e) => { clearTimeout(timer); reject(e); });
            sock.on("close", () => { clearTimeout(timer); resolve(buf); });
        });
    }

    await Promise.all(
        addresses.map(async ([host, port]) => {
            while (Date.now() < deadline) {
                try {
                    const info = await getInfo(host, port);
                    if (!info.includes("role:slave")) return;
                    if (
                        info.includes("master_link_status:up") &&
                        info.includes("master_sync_in_progress:0")
                    ) {
                        return;
                    }
                } catch {
                    // node not reachable yet
                }
                await new Promise((r) => setTimeout(r, 200));
            }
        }),
    );
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

    private static startCluster(
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

            const [cmd, cmdArgs] = isWindows
                ? ["wsl", ["python3", wslScriptPath, ...commandArgs]]
                : ["python3", [PY_SCRIPT_PATH, ...commandArgs]];

            execFile(
                cmd,
                cmdArgs,
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
                            )
                                .then(
                                    (ver) =>
                                        new ValkeyCluster(
                                            ver,
                                            addresses,
                                            tls,
                                            clusterFolder,
                                        ),
                                )
                                .then(async (cluster) => {
                                    if (isWindows && replicaCount > 0) {
                                        await waitForReplicasReady(
                                            cluster.getAddresses(),
                                        );
                                    }
                                    return cluster;
                                }),
                        );
                    }
                },
            );
        });
    }

    public static async createCluster(
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
        maxRetries = 3,
    ): Promise<ValkeyCluster> {
        let lastError: Error | unknown;
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            let cluster: ValkeyCluster | undefined;
            try {
                cluster = await ValkeyCluster.startCluster(
                    cluster_mode,
                    shardCount,
                    replicaCount,
                    getVersionCallback,
                    tls,
                    tlsConfig,
                    loadModule,
                );
                // startCluster already verified connectivity via getVersionCallback
                // Brief stabilization delay before returning
                await new Promise((r) => setTimeout(r, 500));
                return cluster;
            } catch (e) {
                lastError = e;
                if (cluster) await cluster.close().catch(() => void 0);
                console.warn(
                    `[createCluster] attempt ${attempt}/${maxRetries} failed: ${e}`,
                );
            }
            if (attempt < maxRetries) {
                // Fixed 1s delay between retries
                await new Promise((r) => setTimeout(r, 1000));
            }
        }
        throw lastError ?? new Error(`Failed to create cluster after ${maxRetries} attempts`);
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

                const [cmd, cmdArgs] = isWindows
                    ? [
                          "wsl",
                          [
                              "python3",
                              wslScriptPath,
                              ...commandArgs.slice(1),
                          ],
                      ]
                    : ["python3", commandArgs];

                execFile(cmd, cmdArgs, (error, _, stderr) => {
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
