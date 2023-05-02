import { describe } from "@jest/globals";
import { exec } from "child_process";
import { ClusterSocketConnection, ConnectionOptions } from "..";
import { runBaseTests } from "./TestUtilities";
/* eslint-disable @typescript-eslint/no-var-requires */
const FreePort = require("find-free-port");

type Context = {
    cluster: RedisCluster;
    client: ClusterSocketConnection;
};

const PORT_NUMBER = 5000;
const TIMEOUT = 10000;

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

    runBaseTests<Context>({
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
