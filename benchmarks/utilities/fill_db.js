import commandLineArgs from "command-line-args";
import { createClient, createCluster } from "redis";

const SIZE_SET_KEYSPACE = 3000000; // 3 million

const PORT = 6379;
function getAddress(host, tls, port) {
    const protocol = tls ? "rediss" : "redis";
    return `${protocol}://${host}:${port ?? PORT}`;
}

function generate_value(size) {
    return "0".repeat(size);
}

function createRedisClient(host, isCluster, tls, port) {
    return isCluster
        ? createCluster({
              rootNodes: [{ socket: { host, port: port ?? PORT, tls } }],
              defaults: {
                  socket: {
                      tls,
                  },
              },
              useReplicas: true,
          })
        : createClient({
              url: getAddress(host, tls, port),
          });
}

async function fill_database(data_size, host, isCluster, tls, port) {
    const client = await createRedisClient(host, isCluster, tls, port);
    const data = generate_value(data_size);
    await client.connect();

    const CONCURRENT_SETS = 1000;
    var sets = Array.from(Array(CONCURRENT_SETS).keys()).map(async (index) => {
        for (let i = 0; i < SIZE_SET_KEYSPACE / CONCURRENT_SETS; ++i) {
            var key = (index * CONCURRENT_SETS + index).toString();
            await client.set(key, data);
        }
    });

    await Promise.all(sets);
    await client.quit();
}

const optionDefinitions = [
    { name: "dataSize", type: String },
    { name: "host", type: String },
    { name: "tls", type: Boolean },
    { name: "clusterModeEnabled", type: Boolean },
    { name: "port", type: Number },
];
const receivedOptions = commandLineArgs(optionDefinitions);

Promise.resolve()
    .then(async () => {
        console.log(
            `Filling ${receivedOptions.host} with data size ${receivedOptions.dataSize}`
        );
        await fill_database(
            receivedOptions.dataSize,
            receivedOptions.host,
            receivedOptions.clusterModeEnabled,
            receivedOptions.tls,
            receivedOptions.port
        );
    })
    .then(() => {
        process.exit(0);
    });
