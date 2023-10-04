import { beforeAll, expect } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import { Logger, ReturnType } from "..";
import { BaseTransaction } from "../build-ts/src/Transaction";

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
    baseTransaction: BaseTransaction
): ReturnType[] {
    const key1 = "{key}" + uuidv4();
    const key2 = "{key}" + uuidv4();
    const key3 = "{key}" + uuidv4();
    const key4 = "{key}" + uuidv4();
    const field = uuidv4();
    const value = uuidv4();
    baseTransaction.set(key1, "bar");
    baseTransaction.set(key2, "baz", {
        conditionalSet: "onlyIfDoesNotExist",
        returnOldValue: true,
    });
    baseTransaction.customCommand("MGET", [key1, key2]);
    baseTransaction.mset({ [key3]: value });
    baseTransaction.mget([key1, key2]);
    baseTransaction.del([key1]);
    baseTransaction.hset(key4, { [field]: value });
    baseTransaction.hget(key4, field);
    baseTransaction.hdel(key4, [field]);
    baseTransaction.hmget(key4, [field]);
    return [
        "OK",
        null,
        ["bar", "baz"],
        "OK",
        ["bar", "baz"],
        1,
        1,
        value,
        1,
        [null],
    ];
}
