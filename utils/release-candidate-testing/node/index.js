/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
import { GlideClient, GlideClusterClient } from "@valkey/valkey-glide";
import { ValkeyCluster } from "../../TestUtils.js";

async function runCommands(client) {
    console.log("Executing commands");
    // Set a bunch of keys
    for (let i = 0; i < 100; i++) {
        let res = await client.set(`foo${i}`, "bar");
        if (res !== "OK") {
            console.log(res);
            throw new Error(`Unexpected set response, expected OK, got ${res}`);
        }
    }
    console.log("Keys sets");
    // Get the keys
    for (let i = 0; i < 10; i++) {
        let val = await client.get(`foo${i}`);
        if (val !== "bar") {
            console.log(val);
            throw new Error(`Unexpected value, expected bar, got ${val}`);
        }
    }
    console.log("Got keys");
    // Run some various commands
    let pong = await client.ping();
    if (pong !== "PONG") {
        console.log(pong);
        throw new Error(`Unexpected ping response, expected PONG, got ${pong}`);
    }
    console.log(`Ponged: ${pong}`);
    // Set a bunch of keys to delete
    let arrayOfkeys = [];
    for (let i = 1; i <= 3; i++) arrayOfkeys.push(`foo${i}`);
    // delete the keys
    let deletedKeysNum = await client.del(arrayOfkeys);
    console.log(`Deleted keys: ${deletedKeysNum}`);
    // check that the correct number of keys were deleted
    if (deletedKeysNum !== 3) {
        console.log(deletedKeysNum);
        throw new Error(
            `Unexpected number of keys deleted, expected 3, got ${deletedKeysNum}`,
        );
    }
    // check that the keys were deleted
    for (let i = 1; i <= 3; i++) {
        let val = await client.get(`foo${i}`);
        if (val !== null) {
            console.log(val);
            throw new Error(`Unexpected value, expected null, got ${val}`);
        }
    }
    console.log("Keys deleted");
    console.log("Commands executed");
}

async function closeClientAndCluster(client, Cluster) {
    console.log("Closing client");
    await client.close();
    console.log("Client closed");

    console.log("Closing Clusters");
    await Cluster.close();
    console.log("Clusters closed");
}

async function getServerVersion(addresses, clusterMode) {
    // General version for those tests
    return "255.255.255";
}

async function clusterTests() {
    try {
        console.log("Testing cluster");
        console.log("Creating cluster");
        let valkeyCluster = await ValkeyCluster.createCluster(
            true,
            3,
            1,
            getServerVersion,
        );
        console.log("Cluster created");

        console.log("Connecting to cluster");
        let addresses = valkeyCluster.getAddresses().map((address) => {
            return { host: address[0], port: address[1] };
        });
        const client = await GlideClusterClient.createClient({
            addresses: addresses,
        });
        console.log("Connected to cluster");

        await runCommands(client);

        await closeClientAndCluster(client, valkeyCluster);
        console.log("Done");
    } catch (error) {
        // Need this part just when running in our self-hosted runner, so if the test fails before closing Clusters we still kill them and clean up
        if (process.platform === "linux" && process.arch in ["arm", "arm64"]) {
            exec(`pkill -f redis`);
            exec(`rm -rf ${clusterFolder}`);
            exec(`rm -rf /tmp/redis*`);
        }
        throw error;
    }
}

async function standaloneTests() {
    try {
        console.log("Testing standalone Cluster");
        console.log("Creating Cluster");
        let valkeyCluster = await ValkeyCluster.createCluster(
            false,
            1,
            1,
            getServerVersion,
        );
        console.log("Cluster created");

        console.log("Connecting to Cluster");
        let addresses = valkeyCluster.getAddresses().map((address) => {
            return { host: address[0], port: address[1] };
        });
        const client = await GlideClient.createClient({ addresses: addresses });
        console.log("Connected to Cluster");

        await closeClientAndCluster(client, valkeyCluster);
        console.log("Done");
    } catch (error) {
        // Need this part just when running in our self-hosted runner, so if the test fails before closing Clusters we still kill them and clean up
        if (process.platform === "linux" && process.arch in ["arm", "arm64"]) {
            exec(`pkill -f redis`);
            exec(`rm -rf /tmp/redis*`);
        }
        throw error;
    }
}

async function main() {
    await clusterTests();
    console.log("Cluster tests passed");
    await standaloneTests();
    console.log("Standalone tests passed");
}

await main();
