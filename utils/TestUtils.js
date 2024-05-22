var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { exec } from "child_process";
const PY_SCRIPT_PATH = "~/glide-for-redis/utils/cluster_manager.py";
function parseOutput(input) {
    var _a, _b;
    const lines = input.split(/\r\n|\r|\n/);
    const clusterFolder = (_a = lines
        .find((line) => line.startsWith("CLUSTER_FOLDER"))) === null || _a === void 0 ? void 0 : _a.split("=")[1];
    const ports = (_b = lines
        .find((line) => line.startsWith("CLUSTER_NODES"))) === null || _b === void 0 ? void 0 : _b.split("=")[1].split(",").map((address) => address.split(":")).map((address) => [address[0], Number(address[1])]);
    if (clusterFolder === undefined || ports === undefined) {
        throw new Error(`Insufficient data in input: ${input}`);
    }
    return {
        clusterFolder,
        addresses: ports,
    };
}
export class RedisCluster {
    constructor(addresses, clusterFolder) {
        this.addresses = addresses;
        this.clusterFolder = clusterFolder;
    }
    static createCluster(cluster_mode, shardCount, replicaCount, loadModule) {
        return new Promise((resolve, reject) => {
            let command = `python3 ${PY_SCRIPT_PATH} start -r ${replicaCount} -n ${shardCount}`;
            if (cluster_mode) {
                command += " --cluster-mode";
            }
            if (loadModule) {
                if (loadModule.length === 0) {
                    throw new Error("Please provide the path(s) to the module(s) you want to load.");
                }
                for (const module of loadModule) {
                    command += ` --load-module ${module}`;
                }
            }
            console.log(command);
            exec(command, (error, stdout, stderr) => {
                if (error) {
                    console.error(stderr);
                    reject(error);
                }
                else {
                    const { clusterFolder, addresses: ports } = parseOutput(stdout);
                    resolve(new RedisCluster(ports, clusterFolder));
                }
            });
        });
    }
    static initFromExistingCluster(addresses) {
        return new RedisCluster(addresses, "");
    }
    ports() {
        return this.addresses.map((address) => address[1]);
    }
    getAddresses() {
        return this.addresses;
    }
    close() {
        return __awaiter(this, void 0, void 0, function* () {
            if (this.clusterFolder) {
                yield new Promise((resolve, reject) => {
                    exec(`python3 ${PY_SCRIPT_PATH} stop --cluster-folder ${this.clusterFolder}`, (error, _, stderr) => {
                        if (error) {
                            console.error(stderr);
                            reject(error);
                        }
                        else {
                            resolve();
                        }
                    });
                });
            }
        });
    }
}
