import { RedisClusterClient } from "@aws/glide-for-redis";
import { exec } from "child_process";

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

async function createCluster(cluster_mode, shardCount, replicaCount) {
    return new Promise((resolve, reject) => {
        let command = `python3 ../../../utils/cluster_manager.py start -r ${replicaCount} -n ${shardCount}`;

        if (cluster_mode) {
            command += " --cluster-mode";
        }

        console.log(command);
        exec(command, (error, stdout, stderr) => {
            if (error) {
                console.error(stderr);
                reject(error);
            } else {
                const { clusterFolder, addresses } = parseOutput(stdout);
                resolve({ clusterFolder, addresses });
            }
        });
    });
}

// Kill the cluster
async function close(clusterFolder) {
    await new Promise((resolve, reject) => {
        exec(
            `python3 ../../../utils/cluster_manager.py stop --cluster-folder ${clusterFolder}`,
            (error, _, stderr) => {
                if (error) {
                    console.error(stderr);
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
        let { clusterFolder, addresses } = await createCluster(true, 3, 3);
        addresses = addresses.map((address) => {
            return { host: address[0], port: address[1] };
        });
        const client = await RedisClusterClient.createClient({
            addresses,
        });
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
        client.close();
        await close(clusterFolder);
    } catch (error) {
        console.error(error);
        exec(`pkill -f redis`);
    }
}
await main();
