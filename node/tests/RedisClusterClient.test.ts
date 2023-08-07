import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { exec } from "child_process";
import { ConnectionOptions, RedisClusterClient } from "../build-ts";
import { runBaseTests } from "./SharedTests";
import { flushallOnPort } from "./TestUtilities";

type Context = {
    client: RedisClusterClient;
};

const TIMEOUT = 10000;

class RedisCluster {
    private usedPorts: number[];
    private clusterFolder: string;

    private constructor(ports: number[], clusterFolder: string) {
        this.usedPorts = ports;
        this.clusterFolder = clusterFolder;
    }

    private static parseOutput(input: string): {
        clusterFolder: string;
        ports: number[];
    } {
        const lines = input.split(/\r\n|\r|\n/);
        const clusterFolder = lines
            .find((line) => line.startsWith("CLUSTER_FOLDER"))
            ?.split("=")[1];
        const ports = lines
            .find((line) => line.startsWith("CLUSTER_NODES"))
            ?.split("=")[1]
            .split(",")
            .map((address) => address.split(":")[1])
            .map((port) => Number(port));
        if (clusterFolder === undefined || ports === undefined) {
            throw new Error(`Insufficient data in input: ${input}`);
        }

        return {
            clusterFolder,
            ports,
        };
    }

    public static createCluster(
        shardCount: number,
        replicaCount: number
    ): Promise<RedisCluster> {
        return new Promise<RedisCluster>((resolve, reject) => {
            exec(
                `python3 ../utils/cluster_manager.py start -r  ${replicaCount} -n ${shardCount}`,
                (error, stdout, stderr) => {
                    if (error) {
                        console.error(stderr);
                        reject(error);
                    } else {
                        const { clusterFolder, ports } =
                            this.parseOutput(stdout);
                        resolve(new RedisCluster(ports, clusterFolder));
                    }
                }
            );
        });
    }

    public ports(): number[] {
        return [...this.usedPorts];
    }

    public async dispose() {
        await new Promise<void>((resolve, reject) =>
            exec(
                `python3 ../utils/cluster_manager.py stop --cluster-folder ${this.clusterFolder}`,
                (error, _, stderr) => {
                    if (error) {
                        console.error(stderr);
                        reject(error);
                    } else {
                        resolve();
                    }
                }
            )
        );
    }
}

describe("RedisClusterClient", () => {
    let testsFailed = 0;
    let cluster: RedisCluster;
    beforeAll(async () => {
        cluster = await RedisCluster.createCluster(3, 0);
    }, 20000);

    afterEach(async () => {
        await Promise.all(cluster.ports().map((port) => flushallOnPort(port)));
    });

    afterAll(async () => {
        if (testsFailed === 0) {
            await cluster.dispose();
        }
    });

    const getOptions = (ports: number[]): ConnectionOptions => {
        return {
            addresses: ports.map((port) => ({
                host: "localhost",
                port,
            })),
        };
    };

    runBaseTests<Context>({
        init: async () => {
            testsFailed += 1;
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            return {
                context: {
                    client,
                },
                client,
            };
        },
        close: (context: Context, testSucceeded: boolean) => {
            if (testSucceeded) {
                testsFailed -= 1;
            }
            context.client.dispose();
        },
        timeout: TIMEOUT,
    });
});
