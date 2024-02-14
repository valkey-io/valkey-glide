/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

import { beforeAll, expect } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import { ClusterTransaction, Logger, ReturnType, Transaction } from "..";

beforeAll(() => {
    Logger.init("info");
});

export type Client = {
    set: (key: string, value: string) => Promise<string | "OK" | null>;
    get: (key: string) => Promise<string | null>;
};

export async function GetAndSetRandomValue(client: Client) {
    const key = uuidv4();
    // Adding random repetition, to prevent the inputs from always having the same alignment.
    const value = uuidv4() + "0".repeat(Math.random() * 7);
    const setResult = await client.set(key, value);
    expect(setResult).toEqual("OK");
    const result = await client.get(key);
    expect(result).toEqual(value);
}

export function flushallOnPort(port: number): Promise<void> {
    return new Promise<void>((resolve, reject) =>
        exec(`redis-cli -p ${port} FLUSHALL`, (error, _, stderr) => {
            if (error) {
                console.error(stderr);
                reject(error);
            } else {
                resolve();
            }
        })
    );
}

/// This function takes the first result of the response if it got more than one response (like cluster responses).
export function getFirstResult(
    res: string | number | Record<string, string> | Record<string, number>
): string | number {
    if (typeof res == "string" || typeof res == "number") {
        return res;
    }

    return Object.values(res).at(0);
}

export function transactionTest(
    baseTransaction: Transaction | ClusterTransaction
): ReturnType[] {
    const key1 = "{key}" + uuidv4();
    const key2 = "{key}" + uuidv4();
    const key3 = "{key}" + uuidv4();
    const key4 = "{key}" + uuidv4();
    const key5 = "{key}" + uuidv4();
    const key6 = "{key}" + uuidv4();
    const key7 = "{key}" + uuidv4();
    const key8 = "{key}" + uuidv4();
    const field = uuidv4();
    const value = uuidv4();
    baseTransaction
        .set(key1, "bar")
        .set(key2, "baz", {
            returnOldValue: true,
        })
        .customCommand(["MGET", key1, key2])
        .mset({ [key3]: value })
        .mget([key1, key2])
        .del([key1])
        .hset(key4, { [field]: value })
        .hget(key4, field)
        .hgetall(key4)
        .hdel(key4, [field])
        .hmget(key4, [field])
        .hexists(key4, field)
        .lpush(key5, [field + "1", field + "2", field + "3", field + "4"])
        .lpop(key5)
        .llen(key5)
        .lrem(key5, 1, field + "1")
        .ltrim(key5, 0, 1)
        .lrange(key5, 0, -1)
        .lpopCount(key5, 2)
        .rpush(key6, [field + "1", field + "2", field + "3"])
        .rpop(key6)
        .rpopCount(key6, 2)
        .sadd(key7, ["bar", "foo"])
        .srem(key7, ["foo"])
        .scard(key7)
        .smembers(key7)
        .zadd(key8, { member1: 1, member2: 2 })
        .zaddIncr(key8, "member2", 1)
        .zrem(key8, ["member1"])
        .zcard(key8)
        .zscore(key8, "member2")
        .zcount(key8, { bound: 2 }, "positiveInfinity");
    return [
        "OK",
        null,
        ["bar", "baz"],
        "OK",
        ["bar", "baz"],
        1,
        1,
        value,
        { [field]: value },
        1,
        [null],
        false,
        4,
        field + "4",
        3,
        1,
        "OK",
        [field + "3", field + "2"],
        [field + "3", field + "2"],
        3,
        field + "3",
        [field + "2", field + "1"],
        2,
        1,
        1,
        ["bar"],
        2,
        3,
        1,
        1,
        3.0,
        1,
    ];
}

export class RedisCluster {
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
        replicaCount: number,
        loadModule?: string[]
    ): Promise<RedisCluster> {
        return new Promise<RedisCluster>((resolve, reject) => {
            let command = `python3 ../utils/cluster_manager.py start --cluster-mode -r  ${replicaCount} -n ${shardCount}`;

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

            console.log(command);
            exec(command, (error, stdout, stderr) => {
                if (error) {
                    console.error(stderr);
                    reject(error);
                } else {
                    const { clusterFolder, ports } = this.parseOutput(stdout);
                    resolve(new RedisCluster(ports, clusterFolder));
                }
            });
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
