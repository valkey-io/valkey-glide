import { beforeAll, expect } from "@jest/globals";
import { exec } from "child_process";
import { v4 as uuidv4 } from "uuid";
import { Logger } from "..";

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

/// This function takes the first result of the response if it got more than one response (like CME responses).
export function getFirstResult(res: string | string[][]): string {
    if (typeof res == "string") {
        return res;
    }
    return res[0][1];
}
