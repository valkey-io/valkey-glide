// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
// Adapted from RedisClientsCompatibilityTesting/Node/wrapper/src/Utils.ts

import {
    ElastiCacheClient,
    ElastiCacheClientConfig,
    ServiceOutputTypes,
} from "@aws-sdk/client-elasticache";

const TO_SECONDS = 1000;
const MAX_WAIT_SECONDS = 11;

export async function makeElastiCacheApiCall(
    client: ElastiCacheClient,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    command: any,
): Promise<ServiceOutputTypes> {
    let retries = 10;

    while (true) {
        try {
            return await client.send(command);
        } catch (err) {
            if (retries > 0) {
                retries--;
                await waitBetweenApiCalls();
                console.debug(`[ElastiCache] retrying after error: ${err}`);
            } else {
                throw err;
            }
        }
    }
}

export async function waitBetweenApiCalls(): Promise<void> {
    const seconds = Math.floor(Math.random() * MAX_WAIT_SECONDS + 1);
    await sleep(TO_SECONDS * (seconds + 1));
}

export function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

export function generateRandomStr(length: number): string {
    const chars = "abcdefghijklmnopqrstuvwxyz0123456789";
    let result = "";

    for (let i = 0; i < length; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }

    return result;
}

export function initElastiCacheClient(): ElastiCacheClient {
    const region = process.env.AWS_REGION ?? "us-east-1";
    const config: ElastiCacheClientConfig = {
        region,
        maxAttempts: 10,
        retryMode: "standard",
    };
    return new ElastiCacheClient(config);
}
