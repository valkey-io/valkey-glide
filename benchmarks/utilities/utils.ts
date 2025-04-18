/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { GlideClient, GlideClusterClient } from "@valkey/valkey-glide";
import commandLineArgs from "command-line-args";

export const PORT = 6379;

export const SIZE_SET_KEYSPACE = 3000000; // 3 million
export const SIZE_GET_KEYSPACE = 3750000; // 3.75 million

export function getAddress(host: string, tls: boolean, port: number): string {
    const protocol = tls ? "rediss" : "redis";
    return `${protocol}://${host}:${port}`;
}

export async function createValkeyClient(
    host: string,
    isCluster: boolean,
    tls: boolean,
    port: number,
) {
    const clientClass = isCluster ? GlideClusterClient : GlideClient;
    return await clientClass.createClient({
        addresses: [{ host, port: port ?? PORT }],
        useTLS: tls,
    });
}

const optionDefinitions = [
    {
        name: "resultsFile",
        type: String,
        defaultValue: "../results/node-results.json",
    },
    { name: "dataSize", type: String, defaultValue: "100" },
    {
        name: "concurrentTasks",
        type: String,
        multiple: true,
        defaultValue: ["1", "10", "100", "1000"],
    },
    { name: "clients", type: String, defaultValue: "all" },
    { name: "host", type: String, defaultValue: "localhost" },
    { name: "clientCount", type: String, multiple: true, defaultValue: ["1"] },
    { name: "tls", type: Boolean, defaultValue: false },
    { name: "minimal", type: Boolean, defaultValue: false },
    { name: "clusterModeEnabled", type: Boolean, defaultValue: false },
    { name: "port", type: Number, defaultValue: PORT },
];

export const receivedOptions = commandLineArgs(optionDefinitions);

export function generateValue(size: number): string {
    return "0".repeat(size);
}

export function generateKeySet(): string {
    return (Math.floor(Math.random() * SIZE_SET_KEYSPACE) + 1).toString();
}

export function generateKeyGet(): string {
    const range = SIZE_GET_KEYSPACE - SIZE_SET_KEYSPACE;
    return Math.floor(Math.random() * range + SIZE_SET_KEYSPACE + 1).toString();
}
