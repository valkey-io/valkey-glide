/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { execFile } from "child_process";
import { lt } from "semver";

const PY_SCRIPT_PATH = __dirname + "/cluster_manager.py";

const isWindows = process.platform === "win32";

function toWslPath(p: string): string {
    return p
        .replace(/^([A-Za-z]):/, (_, d) => `/mnt/${d.toLowerCase()}`)
        .replace(/\\/g, "/");
}

const wslScriptPath = isWindows ? toWslPath(PY_SCRIPT_PATH) : PY_SCRIPT_PATH;

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
    ): Promise<ValkeyCluster> {
        return new Promise<ValkeyCluster>((resolve, reject) => {
            const effectiveReplicaCount =
                isWindows && replicaCount > 0 ? 0 : replicaCount;
            const commandArgs = [
                "start",
                "-r",
                `${effectiveReplicaCount}`,
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
