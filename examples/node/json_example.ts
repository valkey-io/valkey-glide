/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    GlideClient,
    GlideClusterClient,
    GlideJson,
    Logger,
} from "@valkey/valkey-glide";

async function executeJsonCommands() {
    // When Valkey is in standalone mode, add address of the primary node, and any replicas you'd like to be able to read from.
    const addresses = [
        {
            host: "localhost",
            port: 6379,
        },
    ];
    // Check `GlideClientConfiguration/GlideClusterClientConfiguration` for additional options.
    const client = await GlideClient.createClient({
        addresses: addresses,
        // if the server uses TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
        // useTLS: true,
        clientName: "test_standalone_client",
    });
    await jsonSetAndGet(client);
    client.close();
}

async function jsonSetAndGet(client: GlideClient | GlideClusterClient) {
    const value = '{"a": 1.0, "b":2}';
    const result = await GlideJson.set(client, "doc", "$", value);
    console.log(result); // 'OK' - Indicates successful setting of the value at path '$' in the key stored at `doc`.
    const result2 = await GlideJson.get(client, "doc", { path: ["$"] });
    console.log(result2);

    expect(result).toBe("OK");
    expect(result2).toBe(value);

    console.log("All examples succeeded.");
}

function setFileLogger() {
    Logger.setLoggerConfig("warn", "glide.log");
}

function setConsoleLogger() {
    Logger.setLoggerConfig("warn");
}

setFileLogger();
setConsoleLogger();
// Enable for standalone mode
await executeJsonCommands();
