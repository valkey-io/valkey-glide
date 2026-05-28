/* eslint no-undef: off */
/* eslint @typescript-eslint/no-require-imports: off */
"use strict";

// Patch Module._resolveFilename to block 'long' resolution from
// @protobufjs/inquire, simulating pnpm strict hoisting where inquire's
// eval-wrapped require("long") fails. This forces protobufjs to return
// JS numbers instead of Long objects for uint64 fields, exercising the
// code path that requires correct 64-bit pointer handling.
const Module = require("module");
const origResolve = Module._resolveFilename;

Module._resolveFilename = function (request, parent, ...rest) {
    if (
        request === "long" &&
        parent &&
        parent.filename &&
        parent.filename.includes("@protobufjs")
    ) {
        throw new Error(
            "Simulated pnpm strict hoisting: long not found from @protobufjs/inquire",
        );
    }

    return origResolve.call(this, request, parent, ...rest);
};

const { GlideClient } = require("@valkey/valkey-glide");
const FreePort = require("find-free-port");
const { startServer, checkWhichCommandAvailable } = require("../utils.js");

const PORT_NUMBER = 4001;

/**
 * Test the processResponse code path (normal commands) under simulated
 * pnpm strict hoisting where respPointer arrives as a JS number.
 */
async function testProcessResponse(port) {
    const client = await GlideClient.createClient({
        addresses: [{ host: "localhost", port }],
    });

    try {
        const key = "pnpm-test-key";
        const value = "pnpm-test-value";

        const setResult = await client.set(key, value);

        if (setResult !== "OK") {
            throw new Error(`set returned '${setResult}', expected 'OK'`);
        }

        const getResult = await client.get(key);

        if (getResult !== value) {
            throw new Error(`get returned '${getResult}', expected '${value}'`);
        }

        console.log("processResponse test passed: set/get round-trip OK");
    } finally {
        client.close();
    }
}

/**
 * Test the notificationToPubSubMessageSafe code path (pubsub) under
 * simulated pnpm strict hoisting where respPointer arrives as a JS number.
 */
async function testPubSub(port) {
    const receivedMessages = [];
    const client = await GlideClient.createClient({
        addresses: [{ host: "localhost", port }],
        pubsubSubscriptions: {
            channelsAndPatterns: {},
            callback: (msg) => {
                receivedMessages.push(msg);
            },
        },
    });

    const publisher = await GlideClient.createClient({
        addresses: [{ host: "localhost", port }],
    });

    try {
        const channel = "test-channel-pnpm";
        const message = "hello-from-pnpm-test";

        await client.subscribeLazy([channel]);

        // Wait for subscription to be established
        await new Promise((resolve) => setTimeout(resolve, 300));

        await publisher.publish(message, channel);

        // Wait for message delivery
        await new Promise((resolve) => setTimeout(resolve, 500));

        if (receivedMessages.length !== 1) {
            throw new Error(
                `Expected 1 message, got ${receivedMessages.length}`,
            );
        }

        const received = receivedMessages[0];

        if (received.message !== message) {
            throw new Error(
                `Expected message '${message}', got '${received.message}'`,
            );
        }

        if (received.channel !== channel) {
            throw new Error(
                `Expected channel '${channel}', got '${received.channel}'`,
            );
        }

        console.log(
            `pubsub test passed: received '${received.message}' on '${received.channel}'`,
        );
    } finally {
        client.close();
        publisher.close();
    }
}

async function main() {
    let serverProcess;

    try {
        const port = await FreePort(PORT_NUMBER);
        const serverCmd = await checkWhichCommandAvailable();
        serverProcess = await startServer(serverCmd, port);

        await testProcessResponse(port);
        await testPubSub(port);

        console.log("All pnpm pointer tests passed");
        process.exit(0);
    } catch (error) {
        console.error("Error:", error.message);
        process.exit(1);
    } finally {
        if (serverProcess) {
            serverProcess.kill();
        }
    }
}

if (require.main === module) {
    main();
}
