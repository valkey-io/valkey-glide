import { Logger, RedisClient, RedisClusterClient } from "babushka-rs";

async function sendPingToNode() {
    // When in Redis is in standalone mode, add address of the primary node, and any replicas you'd like to be able to read from.
    const addresses = [
        {
            host: "localhost",
            port: 6379,
        },
    ];
    // Check `ConnectionOptions` for additional options.
    const client = await RedisClient.createClient({
        addresses: addresses,
        // useTLS: true,
    });
    // The empty array signifies that there are no additional arguments.
    const pong = await client.customCommand("PING", []);
    console.log(pong);
    await send_set_and_get(client);
    client.dispose();
}

async function send_set_and_get(client: RedisClient | RedisClusterClient) {
    const set_response = await client.set("foo", "bar");
    console.log(`Set response is = ${set_response}`);
    const get_response = await client.get("foo");
    console.log(`Get response is = ${get_response}`);
}

async function sendPingToRandomNodeInCluster() {
    // When in Redis is cluster mode, add address of any nodes, and the client will find all nodes in the cluster.
    const addresses = [
        {
            host: "localhost",
            port: 6380,
        },
    ];
    // Check `ConnectionOptions` for additional options.
    const client = await RedisClusterClient.createClient({
        addresses: addresses,
        useTLS: true,
    });
    // The empty array signifies that there are no additional arguments.
    const pong = await client.customCommand("PING", [], "randomNode");
    console.log(pong);
    await send_set_and_get(client);
    client.dispose();
}

function setFileLogger() {
    Logger.setLoggerConfig("warn", "babushka.log");
}

function setConsoleLogger() {
    Logger.setLoggerConfig("warn");
}

setFileLogger();
await sendPingToNode();
setConsoleLogger();
await sendPingToRandomNodeInCluster();
