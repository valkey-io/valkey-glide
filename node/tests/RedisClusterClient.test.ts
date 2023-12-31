import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { exec } from "child_process";
import {
    BaseClientConfiguration,
    ClusterTransaction,
    InfoOptions,
    RedisClusterClient,
} from "../";
import { runBaseTests } from "./SharedTests";
import { flushallOnPort, getFirstResult, transactionTest } from "./TestUtilities";

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
                `python3 ../utils/cluster_manager.py start --cluster-mode -r  ${replicaCount} -n ${shardCount}`,
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

    public async close() {
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
            await cluster.close();
        }
    });

    const getOptions = (ports: number[]): BaseClientConfiguration => {
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
            context.client.close();
        },
        timeout: TIMEOUT,
    });

    it(
        "info with server and replication",
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const info_server = getFirstResult(await client.info([
                InfoOptions.Server,
            ]));
            expect(info_server).toEqual(expect.stringContaining("# Server"));

            const result = (await client.info([
                InfoOptions.Replication,
            ])) as Record<string, string>;
            const clusterNodes = await client.customCommand([
                "CLUSTER",
                "NODES",
            ]);
            expect(
                (clusterNodes as string)?.split("master").length - 1
            ).toEqual(Object.keys(result).length);
            Object.values(result).every((item) => {
                expect(item).toEqual(expect.stringContaining("# Replication"));
                expect(item).toEqual(
                    expect.not.stringContaining("# Errorstats")
                );
            });
            client.close();
        },
        TIMEOUT
    );

    it(
        "info with server and randomNode route",
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const result = await client.info(
                [InfoOptions.Server],
                "randomNode"
            );
            expect(typeof result).toEqual("string");
            expect(result).toEqual(expect.stringContaining("# Server"));
            expect(result).toEqual(expect.not.stringContaining("# Errorstats"));
            client.close();
        },
        TIMEOUT
    );

    it(
        "config get and config set transactions test",
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const transaction = new ClusterTransaction();
            transaction.configSet({ timeout: "1000" });
            transaction.configGet(["timeout"]);
            const result = await client.exec(transaction);
            expect(result).toEqual(["OK", { timeout: "1000" }]);
            client.close();
        },
        TIMEOUT
    );

    it(
        "can send transactions",
        async () => {
            const client = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const transaction = new ClusterTransaction();
            const expectedRes = transactionTest(transaction);
            const result = await client.exec(transaction)
            expect(result).toEqual(expectedRes);
            client.close();
        },
        TIMEOUT
    );

    it(
        "can return null on WATCH transaction failures",
        async () => {
            const client1 = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const client2 = await RedisClusterClient.createClient(
                getOptions(cluster.ports())
            );
            const transaction = new ClusterTransaction();
            transaction.get("key");
            const result1 = await client1.customCommand(["WATCH", "key"]);
            expect(result1).toEqual("OK");

            const result2 = await client2.set("key", "foo");
            expect(result2).toEqual("OK");

            const result3 = await client1.exec(transaction);
            expect(result3).toBeNull();

            client1.close();
            client2.close();
        },
        TIMEOUT
    );
});
