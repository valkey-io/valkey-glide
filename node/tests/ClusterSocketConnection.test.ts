import { afterAll, afterEach, beforeAll, describe } from "@jest/globals";
import { exec } from "child_process";
import { ClusterSocketConnection, ConnectionOptions } from "..";
import { flushallOnPort } from "./TestUtilities";
import { runBaseTests } from "./SharedTests";
/* eslint-disable @typescript-eslint/no-var-requires */
const FreePort = require("find-free-port");

type Context = {
    client: ClusterSocketConnection;
};

const PORT_NUMBER = 5000;
const TIMEOUT = 10000;

class RedisCluster {
    private firstPort: number;
    private nodeCount: number;
    private replicaCount: number;

    private constructor(
        firstPort: number,
        nodeCount: number,
        replicaCount: number
    ) {
        this.firstPort = firstPort;
        this.nodeCount = nodeCount;
        this.replicaCount = replicaCount;
    }

    public static createCluster(
        firstPort: number,
        nodeCount: number,
        replicaCount: number
    ): Promise<RedisCluster> {
        return new Promise<RedisCluster>((resolve, reject) => {
            exec(
                `sudo REPLICA_COUNT=${replicaCount} START_PORT=${firstPort} NODE_COUNT=${nodeCount} ../create-cluster.sh start`,
                (error, _, stderr) => {
                    if (error) {
                        console.error(stderr);
                        reject(error);
                    } else {
                        resolve(
                            new RedisCluster(firstPort, nodeCount, replicaCount)
                        );
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
    let cluster: RedisCluster;
    beforeAll(async () => {
        const port = await FreePort(PORT_NUMBER).then(
            ([free_port]: number[]) => free_port
        );
        cluster = await RedisCluster.createCluster(port, 3, 0);
    });

    afterEach(async () => {
        await Promise.all(cluster.ports().map((port) => flushallOnPort(port)));
    });

    afterAll(async () => {
        await cluster.dispose();
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
            const client = await ClusterSocketConnection.CreateConnection(
                getOptions(cluster.ports())
            );
            return {
                context: {
                    client,
                },
                client,
            };
        },
        close: (context: Context) => {
            context.client.dispose();
        },
        timeout: TIMEOUT,
    });
});
