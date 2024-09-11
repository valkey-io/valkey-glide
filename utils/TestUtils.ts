/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { exec, execFile } from "child_process";
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

export class RedisCluster {
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

    private static async detectVersion(addresses: any): Promise<string> {
        return new Promise<string>((resolve, reject) => {
            const redisVersionKey = "redis_version:";
            const valkeyVersionKey = "valkey_version:";
            const extractVersion = (versionKey: string, stdout: string): string =>
                stdout.split(versionKey)[1].split("\n")[0];

            let redisInfoCommand = "redis-cli -h " + addresses[0][0] + " -p " + addresses[0][1] + " info";
            let valkeyInfoCommand = "valkey-cli -h " + addresses[0][0] + " -p " + addresses[0][1] + " info";

            //First, try with `valkey-server -v`
            exec(valkeyInfoCommand, (error, stdout) => {
                if (error) {
                    // If `valkey-server` fails, try `redis-server -v`
                    exec(redisInfoCommand, (error, stdout) => {
                        if (error) {
                            reject(error);
                        } else {
                            resolve(extractVersion(redisVersionKey, stdout));
                        }
                    });
                } else {
                    resolve(extractVersion(valkeyVersionKey, stdout));
                }
            });
        });
    }

    public static createCluster(
        cluster_mode: boolean,
        shardCount: number,
        replicaCount: number,
        loadModule?: string[]
    ): Promise<RedisCluster> {
        return new Promise<RedisCluster>((resolve, reject) => {
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
                            RedisCluster.detectVersion(ports).then(
                                (ver) =>
                                    new RedisCluster(ver, ports, clusterFolder)
                            )
                        );
                    }
                }
            );
        });
    }

    public static async initFromExistingCluster(
        addresses: [string, number][]
    ): Promise<RedisCluster> {
        return RedisCluster.detectVersion(addresses).then(
            (ver) => new RedisCluster(ver, addresses, "")
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

export default RedisCluster;
