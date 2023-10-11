import { expect, it } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import { InfoOptions, ReturnType, SetOptions, parseInfoResponse } from "../";
import { ClusterResponse } from "../src/RedisClusterClient";
import { Client, GetAndSetRandomValue, getFirstResult } from "./TestUtilities";

type BaseClient = {
    set: (
        key: string,
        value: string,
        options?: SetOptions
    ) => Promise<string | "OK" | null>;
    ping: (str?: string) => Promise<string>;
    get: (key: string) => Promise<string | null>;
    del: (keys: string[]) => Promise<number>;
    clientGetName: () => Promise<ClusterResponse<string | null>>;
    configRewrite: () => Promise<"OK">;
    info(options?: InfoOptions[]): Promise<ClusterResponse<string>>;
    configResetStat: () => Promise<"OK">;
    mset: (keyValueMap: Record<string, string>) => Promise<"OK">;
    mget: (keys: string[]) => Promise<(string | null)[]>;
    incr: (key: string) => Promise<number>;
    incrBy: (key: string, amount: number) => Promise<number>;
    clientId: () => Promise<ClusterResponse<number>>;
    decr: (key: string) => Promise<number>;
    decrBy: (key: string, amount: number) => Promise<number>;
    incrByFloat: (key: string, amount: number) => Promise<string>;
    configGet: (parameters: string[]) => Promise<ClusterResponse<string[]>>;
    configSet: (parameters: Record<string, string>) => Promise<"OK">;
    hset: (
        key: string,
        fieldValueMap: Record<string, string>
    ) => Promise<number>;
    hget: (key: string, field: string) => Promise<string | null>;
    hdel: (key: string, fields: string[]) => Promise<number>;
    hmget: (key: string, fields: string[]) => Promise<(string | null)[]>;
    hexists: (key: string, field: string) => Promise<number>;
    hgetall: (key: string) => Promise<string[]>;
    customCommand: (commandName: string, args: string[]) => Promise<ReturnType>;
};

async function getVersion(): Promise<[number, number, number]> {
    const versioString = await new Promise<string>((resolve, reject) => {
        exec(`redis-server -v`, (error, stdout) => {
            if (error) {
                reject(error);
            } else {
                resolve(stdout);
            }
        });
    });
    const version = versioString.split("v=")[1].split(" ")[0];
    const numbers = version?.split(".");
    if (numbers.length != 3) {
        return [0, 0, 0];
    }
    return [parseInt(numbers[0]), parseInt(numbers[1]), parseInt(numbers[2])];
}

export function runBaseTests<Context>(config: {
    init: () => Promise<{ context: Context; client: BaseClient }>;
    close: (context: Context, testSucceeded: boolean) => void;
    timeout?: number;
}) {
    runCommonTests(config);

    const runTest = async (test: (client: BaseClient) => Promise<void>) => {
        const { context, client } = await config.init();
        let testSucceeded = false;
        try {
            await test(client);
            testSucceeded = true;
        } finally {
            config.close(context, testSucceeded);
        }
    };

    it(
        "should register client name and version",
        async () => {
            await runTest(async (client: BaseClient) => {
                const version = await getVersion();
                if (version[0] < 7 || (version[0] === 7 && version[1] < 2)) {
                    return;
                }

                const result = await client.customCommand("CLIENT", ["INFO"]);

                expect(result).toContain("lib-name=BabushkaJS");
                expect(result).toContain("lib-ver=0.1.0");
            });
        },
        config.timeout
    );

    it(
        "set with return of old value works",
        async () => {
            await runTest(async (client: BaseClient) => {
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
            });
        },
        config.timeout
    );

    it(
        "conditional set works",
        async () => {
            await runTest(async (client: BaseClient) => {
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
            });
        },
        config.timeout
    );

    it(
        "custom command works",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                // Adding random repetition, to prevent the inputs from always having the same alignment.
                const value = uuidv4() + "0".repeat(Math.random() * 7);
                const setResult = await client.customCommand("SET", [
                    key,
                    value,
                ]);
                expect(setResult).toEqual("OK");
                const result = await client.customCommand("GET", [key]);
                expect(result).toEqual(value);
            });
        },
        config.timeout
    );

    it(
        "getting array return value works",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const key3 = "{key}" + uuidv4();
                // Adding random repetition, to prevent the inputs from always having the same alignment.
                const value1 = uuidv4() + "0".repeat(Math.random() * 7);
                const value2 = uuidv4() + "0".repeat(Math.random() * 7);
                const setResult1 = await client.customCommand("SET", [
                    key1,
                    value1,
                ]);
                expect(setResult1).toEqual("OK");
                const setResult2 = await client.customCommand("SET", [
                    key2,
                    value2,
                ]);
                expect(setResult2).toEqual("OK");
                const mget_result = await client.customCommand("MGET", [
                    key1,
                    key2,
                    key3,
                ]);
                expect(mget_result).toEqual([value1, value2, null]);
            });
        },
        config.timeout
    );

    it(
        "delete multiple existing keys and an non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key1 = "{key}" + uuidv4();
                const key2 = "{key}" + uuidv4();
                const key3 = "{key}" + uuidv4();
                const value = uuidv4();
                let result = await client.set(key1, value);
                expect(result).toEqual("OK");
                result = await client.set(key2, value);
                expect(result).toEqual("OK");
                result = await client.set(key3, value);
                expect(result).toEqual("OK");
                let deletedKeysNum = await client.del([key1, key2, key3]);
                expect(deletedKeysNum).toEqual(3);
                deletedKeysNum = await client.del([uuidv4()]);
                expect(deletedKeysNum).toEqual(0);
            });
        },
        config.timeout
    );

    it(
        "testing clientGetName",
        async () => {
            await runTest(async (client: BaseClient) => {
                expect(await client.clientGetName()).toBeNull();
            });
        },
        config.timeout
    );

    it(
        "test config rewrite",
        async () => {
            await runTest(async (client: BaseClient) => {
                const serverInfo = await client.info([InfoOptions.Server]);
                const conf_file = parseInfoResponse(
                    getFirstResult(serverInfo).toString()
                )["config_file"];
                if (conf_file.length > 0) {
                    expect(await client.configRewrite()).toEqual("OK");
                } else {
                    try {
                        /// We expect Redis to return an error since the test cluster doesn't use redis.conf file
                        expect(await client.configRewrite()).toThrow();
                    } catch (e) {
                        expect((e as Error).message).toMatch(
                            "The server is running without a config file"
                        );
                    }
                }
            });
        },
        config.timeout
    );

    it(
        "info stats before and after Config ResetStat is different",
        async () => {
            await runTest(async (client: BaseClient) => {
                /// we execute set and info so the total_commands_processed will be greater than 1
                /// after the configResetStat call we initiate an info command and the the total_commands_processed will be 1.
                await client.set("foo", "bar");
                const OldResult = await client.info([InfoOptions.Stats]);
                expect(
                    Number(
                        parseInfoResponse(getFirstResult(OldResult).toString())[
                            "total_commands_processed"
                        ]
                    )
                ).toBeGreaterThan(1);
                expect(await client.configResetStat()).toEqual("OK");
                const result = await client.info([InfoOptions.Stats]);
                expect(
                    parseInfoResponse(getFirstResult(result).toString())[
                        "total_commands_processed"
                    ]
                ).toEqual("1");
            });
        },
        config.timeout
    );

    it(
        "testing mset and mget with multiple existing keys and one non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const key3 = uuidv4();
                const value = uuidv4();
                const keyValueList = {
                    [key1]: value,
                    [key2]: value,
                    [key3]: value,
                };
                expect(await client.mset(keyValueList)).toEqual("OK");
                expect(
                    await client.mget([key1, key2, "nonExistingKey", key3])
                ).toEqual([value, value, null, value]);
            });
        },
        config.timeout
    );

    it(
        "incr, incrBy and incrByFloat with existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "10")).toEqual("OK");
                expect(await client.incr(key)).toEqual(11);
                expect(await client.get(key)).toEqual("11");
                expect(await client.incrBy(key, 4)).toEqual(15);
                expect(await client.get(key)).toEqual("15");
                expect(await client.incrByFloat(key, 1.5)).toEqual("16.5");
                expect(await client.get(key)).toEqual("16.5");
            });
        },
        config.timeout
    );

    it(
        "incr, incrBy and incrByFloat with non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                const key3 = uuidv4();
                /// key1 and key2 does not exist, so it set to 0 before performing the operation.
                expect(await client.incr(key1)).toEqual(1);
                expect(await client.get(key1)).toEqual("1");
                expect(await client.incrBy(key2, 2)).toEqual(2);
                expect(await client.get(key2)).toEqual("2");
                expect(await client.incrByFloat(key3, -0.5)).toEqual("-0.5");
                expect(await client.get(key3)).toEqual("-0.5");
            });
        },
        config.timeout
    );

    it(
        "incr, incrBy and incrByFloat with a key that contains a value of string that can not be represented as integer",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");
                try {
                    expect(await client.incr(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer"
                    );
                }

                try {
                    expect(await client.incrBy(key, 1)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer"
                    );
                }

                try {
                    expect(await client.incrByFloat(key, 1.5)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not a valid float"
                    );
                }
            });
        },
        config.timeout
    );

    it(
        "ping test",
        async () => {
            await runTest(async (client: BaseClient) => {
                expect(await client.ping()).toEqual("PONG");
                expect(await client.ping("Hello")).toEqual("Hello");
            });
        },
        config.timeout
    );

    it(
        "clientId test",
        async () => {
            await runTest(async (client: BaseClient) => {
                expect(getFirstResult(await client.clientId())).toBeGreaterThan(
                    0
                );
            });
        },
        config.timeout
    );

    it(
        "decr and decrBy existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "10")).toEqual("OK");
                expect(await client.decr(key)).toEqual(9);
                expect(await client.get(key)).toEqual("9");
                expect(await client.decrBy(key, 4)).toEqual(5);
                expect(await client.get(key)).toEqual("5");
            });
        },
        config.timeout
    );

    it(
        "decr and decrBy with non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                /// key1 and key2 does not exist, so it set to 0 before performing the operation.
                expect(await client.get(key1)).toBeNull();
                expect(await client.decr(key1)).toEqual(-1);
                expect(await client.get(key1)).toEqual("-1");
                expect(await client.get(key2)).toBeNull();
                expect(await client.decrBy(key2, 3)).toEqual(-3);
                expect(await client.get(key2)).toEqual("-3");
            });
        },
        config.timeout
    );

    it(
        "decr and decrBy with a key that contains a value of string that can not be represented as integer",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "foo")).toEqual("OK");
                try {
                    expect(await client.decr(key)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer"
                    );
                }

                try {
                    expect(await client.decrBy(key, 3)).toThrow();
                } catch (e) {
                    expect((e as Error).message).toMatch(
                        "value is not an integer"
                    );
                }
            });
        },
        config.timeout
    );

    it(
        "config get and config set with timeout parameter",
        async () => {
            await runTest(async (client: BaseClient) => {
                const prevTimeout = (await client.configGet([
                    "timeout",
                ])) as string[];
                expect(await client.configSet({ timeout: "1000" })).toEqual(
                    "OK"
                );
                const currTimeout = (await client.configGet([
                    "timeout",
                ])) as string[];
                expect(currTimeout).toEqual(["timeout", "1000"]);
                /// Revert to the pervious configuration
                expect(
                    await client.configSet({ [prevTimeout[0]]: prevTimeout[1] })
                ).toEqual("OK");
            });
        },
        config.timeout
    );

    it(
        "testing hset and hget with multiple existing fields and one non existing field",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);
                expect(await client.hget(key, field1)).toEqual(value);
                expect(await client.hget(key, field2)).toEqual(value);
                expect(await client.hget(key, "nonExistingField")).toEqual(
                    null
                );
            });
        },
        config.timeout
    );

    it(
        "hdel multiple existing fields, an non existing field and an non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const field3 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                    [field3]: value,
                };

                expect(await client.hset(key, fieldValueMap)).toEqual(3);
                expect(await client.hdel(key, [field1, field2])).toEqual(2);
                expect(await client.hdel(key, ["nonExistingField"])).toEqual(0);
                expect(await client.hdel("nonExistingKey", [field3])).toEqual(
                    0
                );
            });
        },
        config.timeout
    );

    it(
        "testing hmget with multiple existing fields, an non existing field and an non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);
                expect(
                    await client.hmget(key, [
                        field1,
                        "nonExistingField",
                        field2,
                    ])
                ).toEqual([value, null, value]);
                expect(
                    await client.hmget("nonExistingKey", [field1, field2])
                ).toEqual([null, null]);
            });
        },
        config.timeout
    );

    it(
        "hexists existing field, an non existing field and an non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const fieldValueMap = {
                    [field1]: "value1",
                    [field2]: "value2",
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);
                expect(await client.hexists(key, field1)).toEqual(1);
                expect(await client.hexists(key, "nonExistingField")).toEqual(
                    0
                );
                expect(await client.hexists("nonExistingKey", field2)).toEqual(
                    0
                );
            });
        },
        config.timeout
    );

    it(
        "hgetall with multiple fields in an existing key and one non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                const field1 = uuidv4();
                const field2 = uuidv4();
                const value = uuidv4();
                const fieldValueMap = {
                    [field1]: value,
                    [field2]: value,
                };
                expect(await client.hset(key, fieldValueMap)).toEqual(2);
                expect(await client.hgetall(key)).toEqual([
                    field1,
                    value,
                    field2,
                    value,
                ]);
                expect(await client.hgetall("nonExistingKey")).toEqual([]);
            });
        },
        config.timeout
    );
}

export function runCommonTests<Context>(config: {
    init: () => Promise<{ context: Context; client: Client }>;
    close: (context: Context, testSucceeded: boolean) => void;
    timeout?: number;
}) {
    const runTest = async (test: (client: Client) => Promise<void>) => {
        const { context, client } = await config.init();
        let testSucceeded = false;

        try {
            await test(client);
            testSucceeded = true;
        } finally {
            config.close(context, testSucceeded);
        }
    };

    it(
        "set and get flow works",
        async () => {
            await runTest((client: Client) => GetAndSetRandomValue(client));
        },
        config.timeout
    );

    it(
        "can handle non-ASCII unicode",
        async () => {
            await runTest(async (client: Client) => {
                const key = uuidv4();
                const value = "שלום hello 汉字";
                await client.set(key, value);
                const result = await client.get(key);
                expect(result).toEqual(value);
            });
        },
        config.timeout
    );

    it(
        "get for missing key returns null",
        async () => {
            await runTest(async (client: Client) => {
                const result = await client.get(uuidv4());

                expect(result).toEqual(null);
            });
        },
        config.timeout
    );

    it(
        "get for empty string",
        async () => {
            await runTest(async (client: Client) => {
                const key = uuidv4();
                await client.set(key, "");
                const result = await client.get(key);

                expect(result).toEqual("");
            });
        },
        config.timeout
    );

    it(
        "send very large values",
        async () => {
            await runTest(async (client: Client) => {
                const WANTED_LENGTH = Math.pow(2, 16);
                const getLongUUID = () => {
                    let id = uuidv4();
                    while (id.length < WANTED_LENGTH) {
                        id += uuidv4();
                    }
                    return id;
                };
                const key = getLongUUID();
                const value = getLongUUID();
                await client.set(key, value);
                const result = await client.get(key);

                expect(result).toEqual(value);
            });
        },
        config.timeout
    );

    it(
        "can handle concurrent operations",
        async () => {
            await runTest(async (client: Client) => {
                const singleOp = async (index: number) => {
                    if (index % 2 === 0) {
                        await GetAndSetRandomValue(client);
                    } else {
                        const result = await client.get(uuidv4());
                        expect(result).toEqual(null);
                    }
                };

                const operations: Promise<void>[] = [];

                for (let i = 0; i < 100; ++i) {
                    operations.push(singleOp(i));
                }

                await Promise.all(operations);
            });
        },
        config.timeout
    );
}
