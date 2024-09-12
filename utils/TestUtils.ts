/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { execFile } from "child_process";
import { lt } from "semver";

const PY_SCRIPT_PATH = __dirname + "/cluster_manager.py";

function parseOutput(input: string): {
    clusterFolder: string;
    addresses: [string, number][];
} {
    const lines = input.split(/\r\n|\r|\n/);
    const clusterFolder = lines
        .find((line) => line.startsWith("CLUSTER_FOLDER"))
        ?.split("=")[1];
    const ports = lines
        .find((line) => line.startsWith("CLUSTER_NODES"))
        ?.split("=")[1]
        .split(",")
        .map((address) => address.split(":"))
        .map((address) => [address[0], Number(address[1])]) as [
            string,
            number
        ][];

    if (clusterFolder === undefined || ports === undefined) {
        throw new Error(`Insufficient data in input: ${input}`);
    }

    return {
        clusterFolder,
        addresses: ports,
    };
}

export class ValkeyCluster {
    private addresses: [string, number][];
    private clusterFolder: string | undefined;
    private version: string;

    private constructor(
        version: string,
        addresses: [string, number][],
        clusterFolder?: string
    ) {
        this.addresses = addresses;
        this.clusterFolder = clusterFolder;
        this.version = version;
    }

    private static extractVersion(stdout: string): string {
        let version = "";
        const redisVersionKey = "redis_version:";
        const valkeyVersionKey = "valkey_version:";
        if (stdout.includes(valkeyVersionKey)) {
            version = stdout.split(valkeyVersionKey)[1].split("\n")[0];
        } else if (stdout.includes(redisVersionKey)) {
            version = stdout.split(redisVersionKey)[1].split("\n")[0];
        }
        return version;
    }

    public static createCluster(
        cluster_mode: boolean,
        shardCount: number,
        replicaCount: number,
        getVersionCallback: (addresses: [string, number][]) => Promise<string>,
        loadModule?: string[]
    ): Promise<ValkeyCluster> {
        return new Promise<ValkeyCluster>((resolve, reject) => {
            let command = `start -r ${replicaCount} -n ${shardCount}`;

            if (cluster_mode) {
                command += " --cluster-mode";
            }

            if (loadModule) {
                if (loadModule.length === 0) {
                    throw new Error(
                        "Please provide the path(s) to the module(s) you want to load."
                    );
                }

                for (const module of loadModule) {
                    command += ` --load-module ${module}`;
                }
            }

            execFile(
                "python3",
                [PY_SCRIPT_PATH, ...command.split(" ")],
                (error, stdout, stderr) => {
                    if (error) {
                        reject(error);
                    } else {
                        const { clusterFolder, addresses: ports } =
                            parseOutput(stdout);
                        resolve(
                            getVersionCallback(ports).then((info) => {
                                return this.extractVersion(info);
                            }).then(
                                (ver) =>
                                    new ValkeyCluster(ver, ports, clusterFolder)
                            )
                        );
                    }
                }
            );
        });
    }

    public static async initFromExistingCluster(
        addresses: [string, number][],
        getVersionCallback: (addresses: [string, number][]) => Promise<string>
    ): Promise<ValkeyCluster> {
        return getVersionCallback(addresses).then(info => {
            return this.extractVersion(info);
        }).then(
            (ver) => new ValkeyCluster(ver, addresses, "")
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

    public checkIfServerVersionLessThan(minVersion: string): boolean {
        return lt(this.version, minVersion);
    }

    public async close() {
        if (this.clusterFolder) {
            await new Promise<void>((resolve, reject) => {
                execFile(
                    "python3",
                    [
                        PY_SCRIPT_PATH,
                        `stop`,
                        `--cluster-folder`,
                        `${this.clusterFolder}`,
                    ],
                    (error, _, stderr) => {
                        if (error) {
                            console.error(stderr);
                            reject(error);
                        } else {
                            resolve();
                        }
                    }
                );
            });
        }
    }
}

export default ValkeyCluster;
