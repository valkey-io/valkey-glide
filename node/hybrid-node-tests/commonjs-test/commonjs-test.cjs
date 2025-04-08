/* eslint no-undef: off */
/* eslint @typescript-eslint/no-require-imports: off */
"use strict";
const { GlideClient } = require("@valkey/valkey-glide");
const FreePort = require("find-free-port");
const { startServer, checkWhichCommandAvailable } = require("../utils.js");

const PORT_NUMBER = 4001;

/**
 * Run the test workflow
 */
async function runTest(port) {
    const client = await GlideClient.createClient({
        addresses: [{ host: "localhost", port }],
    });

    try {
        const setResult = await client.set("test", "test");
        console.log(setResult);

        const getResult = await client.get("test");
        console.log(getResult);

        if (getResult !== "test") {
            throw new Error("Common Test failed");
        } else {
            console.log("Common Test passed");
        }

        await client.flushall();
    } finally {
        client.close();
    }
}

/**
 * Main function
 */
async function main() {
    console.log("Starting main");
    let serverProcess;

    try {
        // Get an available port
        const port = await FreePort(PORT_NUMBER);

        // Check which server is available
        const serverCmd = await checkWhichCommandAvailable();

        // Start the server
        serverProcess = await startServer(serverCmd, port);

        // Run the test
        await runTest(port);

        console.log("Done");
        process.exit(0);
    } catch (error) {
        console.error("Error:", error.message);

        if (serverProcess) {
            serverProcess.kill();
        }

        process.exit(1);
    }
}

if (require.main === module) {
    main();
}
