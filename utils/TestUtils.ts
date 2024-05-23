const PY_SCRIPT_PATH = __dirname + "/cluster_manager.py";
import { execFile } from "child_process";

function parseOutput(input: string): {
    clusterFolder: string;
    addresses: [string, number][];
} {
    const lines = input.split(/\r\n|\r|\n/);
    const clusterFolder = lines
        .find((line) => line.startsWith("CLUSTER_FOLDER"))
        ?.split("=")[1];
    const ports = lines
        .find((line) => line.startsWith("CLUSTER_NODES"))
        ?.split("=")[1]
        .split(",")
        .map((address) => address.split(":"))
        .map((address) => [address[0], Number(address[1])]) as [
        string,
        number
    ][];

    if (clusterFolder === undefined || ports === undefined) {
        throw new Error(`Insufficient data in input: ${input}`);
    }

    return {
        clusterFolder,
        addresses: ports,
    };
}

export class RedisCluster {
    private addresses: [string, number][];
    private clusterFolder: string | undefined;

    private constructor(addresses: [string, number][], clusterFolder?: string) {
        this.addresses = addresses;
        this.clusterFolder = clusterFolder;
    }

    public static createCluster(
        cluster_mode: boolean,
        shardCount: number,
        replicaCount: number,
        loadModule?: string[]
    ): Promise<RedisCluster> {
        return new Promise<RedisCluster>((resolve, reject) => {
            let command = `start -r ${replicaCount} -n ${shardCount}`;

            if (cluster_mode) {
                command += " --cluster-mode";
            }

            if (loadModule) {
                if (loadModule.length === 0) {
                    throw new Error(
                        "Please provide the path(s) to the module(s) you want to load."
                    );
                }

                for (const module of loadModule) {
                    command += ` --load-module ${module}`;
                }
            }

            console.log(command);
            execFile(
                "python3",
                [PY_SCRIPT_PATH, ...command.split(" ")],
                (error, stdout, stderr) => {
                    if (error) {
                        console.error(stderr);
                        reject(error);
                    } else {
                        const { clusterFolder, addresses: ports } =
                            parseOutput(stdout);
                        resolve(new RedisCluster(ports, clusterFolder));
                    }
                }
            );
        });
    }

    public static initFromExistingCluster(
        addresses: [string, number][]
    ): RedisCluster {
        return new RedisCluster(addresses, "");
    }

    public ports(): number[] {
        return this.addresses.map((address) => address[1]);
    }

    public getAddresses(): [string, number][] {
        return this.addresses;
    }

    public async close() {
        if (this.clusterFolder) {
            await new Promise<void>((resolve, reject) => {
                execFile(
                    "python3",
                    [
                        PY_SCRIPT_PATH,
                        `stop`,
                        `--cluster-folder`,
                        `${this.clusterFolder}`,
                    ],
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
    }
}

export default RedisCluster;
