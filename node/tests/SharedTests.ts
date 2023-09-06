import { expect, it } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import { InfoOptions, ReturnType, SetOptions, parseInfoResponse } from "../";
import { Client, GetAndSetRandomValue, getFirstResult } from "./TestUtilities";

type BaseClient = {
    set: (
        key: string,
        value: string,
        options?: SetOptions
    ) => Promise<string | "OK" | null>;
    get: (key: string) => Promise<string | null>;
    del: (keys: string[]) => Promise<number>;
    configRewrite: () => Promise<"OK">;
    info(options?: InfoOptions[]): Promise<string | string[][]>;
    configResetStat: () => Promise<"OK">;
    incr: (key: string) => Promise<number>;
    incrBy: (key: string, increment: number) => Promise<number>;
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
        "test config rewrite",
        async () => {
            await runTest(async (client: BaseClient) => {
                const serverInfo = await client.info([InfoOptions.Server]);
                const conf_file = parseInfoResponse(getFirstResult(serverInfo))[
                    "config_file"
                ];
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
                        parseInfoResponse(getFirstResult(OldResult))[
                            "total_commands_processed"
                        ]
                    )
                ).toBeGreaterThan(1);
                expect(await client.configResetStat()).toEqual("OK");
                const result = await client.info([InfoOptions.Stats]);
                expect(
                    parseInfoResponse(getFirstResult(result))[
                        "total_commands_processed"
                    ]
                ).toEqual("1");
            });
        },
        config.timeout
    );

    it(
        "incr and incrBy with existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key = uuidv4();
                expect(await client.set(key, "10")).toEqual("OK");
                expect(await client.incr(key)).toEqual(11);
                expect(await client.get(key)).toEqual("11");
                expect(await client.incrBy(key, 4)).toEqual(15);
                expect(await client.get(key)).toEqual("15");
            });
        },
        config.timeout
    );

    it(
        "incr and incrBy with non existing key",
        async () => {
            await runTest(async (client: BaseClient) => {
                const key1 = uuidv4();
                const key2 = uuidv4();
                /// key1 and key2 does not exist, so it set to 0 before performing the operation.
                expect(await client.incr(key1)).toEqual(1);
                expect(await client.get(key1)).toEqual("1");
                expect(await client.incrBy(key2, 2)).toEqual(2);
                expect(await client.get(key2)).toEqual("2");
            });
        },
        config.timeout
    );

    it(
        "incr and incrBy with a key that contains a value of string that can not be represented as integer",
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
