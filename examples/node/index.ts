import { RedisClient, RedisClusterClient, setLoggerConfig } from "babushka-rs";

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
}

function setFileLogger() {
    setLoggerConfig("warn", "babushka.log");
}

function setConsoleLogger() {
    setLoggerConfig("warn");
}

setFileLogger();
await sendPingToNode();
setConsoleLogger();
await sendPingToRandomNodeInCluster();
