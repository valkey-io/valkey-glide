import { RedisClient, RedisClusterClient } from "@aws/glide-for-redis";
import { RedisCluster } from "../../TestUtils.js";


async function runCommands(client) {
    console.log("Executing commands");
    // Set a bunch of keys
    for (let i = 0; i < 100; i++) {
        let res = await client.set(`foo${i}`, "bar");
        if (res !== "OK") {
            throw new Error("Unexpected set response");
        }
    }
    // Get some keys
    for (let i = 0; i < 10; i++) {
        let val = await client.get(`foo${i}`);
        if (val !== "bar") {
            throw new Error("Unexpected value");
        }
    }
    // Run some commands
    let pong = await client.ping();
    if (pong !== "PONG") {
        throw new Error("Unexpected pong");
    }
    let time = await client.time();
    if (time.length !== 2) {
        throw new Error("Unexpected time response");
    }
    console.log("Commands executed");
}

async function closeClintAndServers(client, server) {
    console.log("Closing client");
    client.close();
    console.log("Client closed");

    console.log("Closing servers");
    await server.close();

}

async function testingCluster() {
    try {
        console.log("Testing cluster");
        console.log("Creating cluster");
        let redisCluster = await RedisCluster.createCluster(true,
            3,
            1,
        );
        console.log("Cluster created");

        console.log("Connecting to cluster");
        let addresses = redisCluster.getAddresses().map((address) => { return { host: address[0], port: address[1] } });
        const client = await RedisClusterClient.createClient({ addresses: addresses });
        console.log("Connected to cluster");

        await runCommands(client);

        await closeClintAndServers(client, redisCluster);
        console.log("Done");
    } catch (error) {
        // Need this part just when running in our self-hosted runner
        if (process.platform === "linux" && process.arch in ["arm", "arm64"]) {
            exec(`pkill -f redis`);
            exec(`rm -rf ${clusterFolder}`);
            exec(`rm -rf /tmp/redis*`);
        }
        console.error(error);
    }
}

async function testingStandalone() {
    try {
        console.log("Testing standalone server")
        console.log("Creating Server");
        let redisServer = await RedisCluster.createCluster(false,
            1,
            1,
        );
        console.log("Server created");

        console.log("Connecting to Server");
        let addresses = redisServer.getAddresses().map((address) => { return { host: address[0], port: address[1] } });
        const client = await RedisClient.createClient({ addresses: addresses });
        console.log("Connected to Server");

        await closeClintAndServers(client, redisServer);
        console.log("Done");

    } catch (error) {
        // Need this part just when running in our self-hosted runner
        if (process.platform === "linux" && process.arch in ["arm", "arm64"]) {
            exec(`pkill -f redis`);
            exec(`rm -rf /tmp/redis*`);
        }
        console.error(error);
    }
}


async function main() {
    await testingCluster();
    await testingStandalone();
}

await main();
