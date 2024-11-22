/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */


import { GlideClient, GlideClusterClient, GlideFt, Logger, VectorField} from "@valkey/valkey-glide";

// TODO: need to test against MemoryDB instance

async function executeVssCommands() {
    // When Valkey is in standalone mode, add address of the primary node, and any replicas you'd like to be able to read from.
    // JSON modules and VSS modules must be enabled for this to successfully execute, otherwise there will be request errors. 
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
    await vssCreateAndSearch(client);
    client.close();
}

async function vssCreateAndSearch(client: GlideClient | GlideClusterClient) {
    // const value = '{"a": 1.0, "b":2}';
    // const result = await GlideJson.set(client, "doc", "$", value);
    // console.log(result); // 'OK' - Indicates successful setting of the value at path '$' in the key stored at `doc`.
    // const result2 = await GlideJson.get(client, "doc", { path: ["$"] });
    // console.log(result2);

    // expect(result).toBe("OK");
    // expect(result2).toBe(value);

    const vectorField_1: VectorField = {
        type: "VECTOR",
        name: "vec",
        alias: "VEC",
        attributes: {
            algorithm: "HNSW",
            type: "FLOAT32",
            dimensions: 2,
            distanceMetric: "L2",
        },
    };
    const result = await GlideFt.create(client, "index", [vectorField_1]);
    console.log(result); // 'OK' - Indicates successful creation of the index. 

    const result2 = await GlideFt.search(client, "index", "*=>[KNN 2 @VEC $query_vec]");
    console.log(result2); // TODO: response

    expect(result).toBe("OK");
    // expect(result2).toBe(""); // TODO: response

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

await executeVssCommands();
