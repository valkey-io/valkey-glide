import commandLineArgs from "command-line-args";
import { createClient, createCluster } from "redis";

const PORT = 6379;
function getAddress(host, tls, port) {
    const protocol = tls ? "rediss" : "redis";
    return `${protocol}://${host}:${port ?? PORT}`;
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

async function flush_database(host, isCluster, tls, port) {
    const client = await createRedisClient(host, isCluster, tls, port);
    await client.connect();
    if (isCluster) {
        // since the cluster client doesn't support fan-out commands, we need to create a client to each node, and send the flush command there.
        await Promise.all(
            client.getMasters().map((master) => {
                return flush_database(master.host, false, tls, master.port);
            })
        );
    } else {
        await client.flushAll();
        await client.quit();
    }
}

const optionDefinitions = [
    { name: "host", type: String },
    { name: "tls", type: Boolean },
    { name: "clusterModeEnabled", type: Boolean },
    { name: "port", type: Number },
];
const receivedOptions = commandLineArgs(optionDefinitions);

Promise.resolve()
    .then(async () => {
        console.log("Flushing " + receivedOptions.host);
        await flush_database(
            receivedOptions.host,
            receivedOptions.clusterModeEnabled,
            receivedOptions.tls,
            receivedOptions.port
        );
    })
    .then(() => {
        process.exit(0);
    });
