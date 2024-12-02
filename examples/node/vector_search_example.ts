/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
import {
    Decoder,
    GlideClient,
    GlideClusterClient,
    GlideFt,
    FtSearchOptions,
    FtSearchReturnType,
    Logger,
    VectorField,
} from "@valkey/valkey-glide";

import { v4 as uuidv4 } from "uuid";

const DATA_PROCESSING_TIMEOUT = 1000;

async function executeVssCommands() {
    // When Valkey is in standalone mode, add address of the primary node, and any replicas you'd like to be able to read from.
    // JSON modules and VSS modules must be enabled for this to successfully execute, otherwise there will be request errors.
    const addresses = [
        {
            host: "localhost",
            port: 6380,
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
    const prefix = "{" + uuidv4() + "}:";
    const index = prefix + "index";
    const query = "*=>[KNN 2 @VEC $query_vec]";

    const vectorField_1: VectorField = {
        type: "VECTOR",
        name: "vec",
        alias: "VEC",
        attributes: {
            algorithm: "HNSW",
            distanceMetric: "L2",
            dimensions: 2,
        },
    };

    const createResult = await GlideFt.create(client, index, [vectorField_1], {
        dataType: "HASH",
        prefixes: [prefix],
    });
    console.log(createResult); // 'OK' - Indicates successful creation of the index.

    // Binary buffers are used here for memory management
    const binaryValue1 = Buffer.alloc(8);
    expect(
        await client.hset(Buffer.from(prefix + "0"), [
            // value of <Buffer 00 00 00 00 00 00 00 00 00>
            { field: "vec", value: binaryValue1 },
        ]),
    ).toEqual(1);

    const binaryValue2: Buffer = Buffer.alloc(8);
    binaryValue2[6] = 0x80;
    binaryValue2[7] = 0xbf;
    expect(
        await client.hset(Buffer.from(prefix + "1"), [
            // value of <Buffer 00 00 00 00 00 00 00 80 BF>
            { field: "vec", value: binaryValue2 },
        ]),
    ).toEqual(1);

    // let server digest the data and update index
    const sleep = new Promise((resolve) =>
        setTimeout(resolve, DATA_PROCESSING_TIMEOUT),
    );
    await sleep;

    // Additional search options
    const optionsWithCount: FtSearchOptions = {
        params: [{ key: "query_vec", value: binaryValue1 }],
        timeout: 10000,
        count: true,
    };
    const binaryResultCount: FtSearchReturnType = await GlideFt.search(
        client,
        index,
        query,
        {
            decoder: Decoder.Bytes,
            ...optionsWithCount,
        },
    );
    expect(binaryResultCount).toEqual([2]);

    const options: FtSearchOptions = {
        params: [{ key: "query_vec", value: binaryValue1 }],
        timeout: 10000,
    };
    const searchResult: FtSearchReturnType = await GlideFt.search(
        client,
        index,
        query,
        {
            decoder: Decoder.Bytes,
            ...options,
        },
    );

    const expectedBinaryResult: FtSearchReturnType = [
        2,
        [
            {
                key: Buffer.from(prefix + "1"),
                value: [
                    {
                        key: Buffer.from("vec"),
                        value: binaryValue2,
                    },
                    {
                        key: Buffer.from("__VEC_score"),
                        value: Buffer.from("1"),
                    },
                ],
            },
            {
                key: Buffer.from(prefix + "0"),
                value: [
                    {
                        key: Buffer.from("vec"),
                        value: binaryValue1,
                    },
                    {
                        key: Buffer.from("__VEC_score"),
                        value: Buffer.from("0"),
                    },
                ],
            },
        ],
    ];
    expect(createResult).toBe("OK");
    expect(searchResult).toEqual(expectedBinaryResult);

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
