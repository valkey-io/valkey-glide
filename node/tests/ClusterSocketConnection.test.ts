import { describe, expect, it } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import { ClusterSocketConnection, ConnectionOptions } from "..";
import { runCommonTests } from "./TestUtilities";
/* eslint-disable @typescript-eslint/no-var-requires */
const FreePort = require("find-free-port");

type Context = {
    cluster: RedisCluster;
    client: ClusterSocketConnection;
};

const PORT_NUMBER = 5000;
const TIMEOUT = 10000;

async function OpenClusterAndExecute(
    action: (ports: number[]) => Promise<void>
) {
    const port = await FreePort(PORT_NUMBER).then(
        ([free_port]: number[]) => free_port
    );
    let cluster: RedisCluster | undefined = undefined;
    try {
        cluster = await RedisCluster.createCluster(port, 6);
        const ports = cluster.ports();
        await action(ports);
    } finally {
        await cluster?.dispose();
    }
}

class RedisCluster {
    private firstPort: number;
    private nodeCount: number;

    private constructor(firstPort: number, nodeCount: number) {
        this.firstPort = firstPort;
        this.nodeCount = nodeCount;
    }

    public static createCluster(
        firstPort: number,
        nodeCount: number
    ): Promise<RedisCluster> {
        return new Promise<RedisCluster>((resolve, reject) => {
            exec(
                `sudo START_PORT=${firstPort} NODE_COUNT=${nodeCount} ../create-cluster.sh start`,
                (error, _, stderr) => {
                    if (error) {
                        console.error(stderr);
                        reject(error);
                    } else {
                        resolve(new RedisCluster(firstPort, nodeCount));
                    }
                }
            );
        });
    }

    public ports(): number[] {
        return Array(this.nodeCount)
            .fill(0)
            .map((_, i) => this.firstPort + i);
    }

    public async dispose() {
        await new Promise<void>((resolve, reject) =>
            exec(
                `sudo START_PORT=${this.firstPort} NODE_COUNT=${this.nodeCount} ../create-cluster.sh stop`,
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

describe("ClusterSocketConnection", () => {
    const getOptions = (ports: number[]): ConnectionOptions => {
        return {
            addresses: ports.map((port) => ({
                host: "localhost",
                port,
            })),
        };
    };

    it(
        "set with return of old value works",
        async () => {
            await OpenClusterAndExecute(async (ports) => {
                const client = await ClusterSocketConnection.CreateConnection(
                    getOptions(ports)
                );

                const key = uuidv4();
                // Adding random repetition, to prevent the inputs from always having the same alignment.
                const value = uuidv4() + "0".repeat(Math.random() * 7);

                let result = await client.set(key, value);
                expect(result).toEqual("OK");

                result = await client.set(key, "", {
                    returnOldValue: true,
                });
                expect(result).toEqual(value);

                result = await client.get(key);
                expect(result).toEqual("");

                client.dispose();
            });
        },
        TIMEOUT
    );

    it(
        "conditional set works",
        async () => {
            await OpenClusterAndExecute(async (ports) => {
                const client = await ClusterSocketConnection.CreateConnection(
                    getOptions(ports)
                );

                const key = uuidv4();
                // Adding random repetition, to prevent the inputs from always having the same alignment.
                const value = uuidv4() + "0".repeat(Math.random() * 7);
                let result = await client.set(key, value, {
                    conditionalSet: "onlyIfExists",
                });
                expect(result).toEqual(null);

                result = await client.set(key, value, {
                    conditionalSet: "onlyIfDoesNotExist",
                });
                expect(result).toEqual("OK");
                expect(await client.get(key)).toEqual(value);

                result = await client.set(key, "foobar", {
                    conditionalSet: "onlyIfDoesNotExist",
                });
                expect(result).toEqual(null);

                result = await client.set(key, "foobar", {
                    conditionalSet: "onlyIfExists",
                });
                expect(result).toEqual("OK");

                expect(await client.get(key)).toEqual("foobar");

                client.dispose();
            });
        },
        TIMEOUT
    );

    runCommonTests<Context>({
        init: async () => {
            const port = await FreePort(PORT_NUMBER).then(
                ([free_port]: number[]) => free_port
            );
            const cluster = await RedisCluster.createCluster(port, 6);
            const client = await ClusterSocketConnection.CreateConnection(
                getOptions(cluster.ports())
            );
            return {
                context: {
                    cluster,
                    client,
                },
                client,
            };
        },
        close: async (context: Context) => {
            context.client.dispose();
            await context.cluster.dispose();
        },
        timeout: TIMEOUT,
    });
});
