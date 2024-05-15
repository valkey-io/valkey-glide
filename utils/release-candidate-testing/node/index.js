import { RedisClusterClient } from "@aws/glide-for-redis";
import { exec } from "child_process";

const PYTHON_CLUSTER_SCRIPT_PATH = "../../../utils/cluster_manager.py";
const PYTHON = "python3";
const PYTHON_SCRIPT_RUN = `${PYTHON} ${PYTHON_CLUSTER_SCRIPT_PATH}`;
const CLUSTER_NODES_AND_REPLICAS_PARAMS = `-r 3 -n 3 --cluster-mode`;
let clusterFolder = "";

function parseOutput(input) {
    const lines = input.split(/\r\n|\r|\n/);
    const clusterFolder = lines
        .find((line) => line.startsWith("CLUSTER_FOLDER"))
        ?.split("=")[1];
    const ports = lines
        .find((line) => line.startsWith("CLUSTER_NODES"))
        ?.split("=")[1]
        .split(",")
        .map((address) => address.split(":"))
        .map((address) => [address[0], Number(address[1])]);

    if (clusterFolder === undefined || ports === undefined) {
        throw new Error(`Insufficient data in input: ${input}`);
    }

    return {
        clusterFolder,
        addresses: ports,
    };
}

function createCluster() {
    return new Promise((resolve, reject) => {
        exec(
            `${PYTHON_SCRIPT_RUN} start ${CLUSTER_NODES_AND_REPLICAS_PARAMS}`,
            (error, stdout, stderr) => {
                if (error) {
                    console.error(stderr, stdout, error);
                    try {
                        // Need this part just when running in our self-hosted runner
                        if (
                            process.platform === "linux" &&
                            process.arch in ["arm", "arm64"]
                        ) {
                            exec(`pkill -f redis`);
                            exec(`rm -rf ${clusterFolder}`);
                            exec(`rm -rf /tmp/redis*`);
                        }
                    } catch {
                        (e) => console.error(e);
                    }
                    reject(error);
                } else {
                    const { clusterFolder, addresses } = parseOutput(stdout);
                    resolve({ clusterFolder, addresses });
                }
            }
        );
    });
}
function closeClient() {
    return new Promise((resolve, reject) => {
        exec(
            `${PYTHON_SCRIPT_RUN} stop --cluster-folder ${clusterFolder}`,
            (error, _, stderr) => {
                if (error) {
                    console.error(stderr, stdout, error);
                    exec(`pkill -f redis`);
                    exec(`rm -rf ${clusterFolder}`);
                    exec(`rm -rf /tmp/redis*`);
                    reject(error);
                } else {
                    resolve();
                }
            }
        );
    });
}

async function main() {
    try {
        console.log("Creating cluster");
        let addresses;
        ({ clusterFolder, addresses } = await createCluster());
        console.log("Cluster created");
        addresses = addresses.map((address) => {
            return { host: address[0], port: address[1] };
        });
        console.log("Connecting to cluster");
        const client = await RedisClusterClient.createClient({
            addresses,
        });
        console.log("Connected to cluster");
        console.log("Executing commands");
        // Set a bunch of keys
        for (let i = 0; i < 100; i++) {
            await client.set(`foo${i}`, "bar");
        }
        // Get some keys
        for (let i = 0; i < 10; i++) {
            await client.get(`foo${i}`);
        }
        // Run some commands
        await client.ping();
        await client.time();
        console.log("Commands executed");
        console.log("Closing client");
        client.close();
        // Need this part just when running in our self-hosted runner
        if (process.platform === "linux" && process.arch in ["arm", "arm64"]) {
            console.log("Client closed");
            console.log("Closing cluster");
            await closeClient();
            console.log("Cluster closed");
        }
        console.log("Done");
    } catch (error) {
        console.error(error);
    }
}
await main();
