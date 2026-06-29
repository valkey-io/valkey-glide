/**
 * Jest globalSetup: starts shared standalone and cluster servers once
 * before all tests. Endpoints are passed to test files via environment
 * variables GLIDE_STANDALONE_ENDPOINT and GLIDE_CLUSTER_ENDPOINTS.
 *
 * Only runs when no external endpoints are already configured.
 */
import { execFile } from "child_process";

const PY_SCRIPT_PATH = __dirname + "/../../utils/cluster_manager.py";
const isWindows = process.platform === "win32";

function toWslPath(p: string): string {
    return p
        .replace(/^([A-Za-z]):/, (_, d) => `/mnt/${d.toLowerCase()}`)
        .replace(/\\/g, "/");
}

const scriptPath = isWindows ? toWslPath(PY_SCRIPT_PATH) : PY_SCRIPT_PATH;

function startCluster(
    args: string[],
): Promise<{ folder: string; addresses: string }> {
    return new Promise((resolve, reject) => {
        const [cmd, cmdArgs] = isWindows
            ? ["wsl", ["python3", scriptPath, ...args]]
            : ["python3", [PY_SCRIPT_PATH, ...args]];

        execFile(cmd, cmdArgs, { timeout: 120000 }, (err, stdout) => {
            if (err) {
                reject(err);
                return;
            }
            const folder =
                stdout
                    .split("\n")
                    .find((l) => l.startsWith("CLUSTER_FOLDER="))
                    ?.split("=")[1]
                    ?.trim() ?? "";
            const addresses =
                stdout
                    .split("\n")
                    .find((l) => l.startsWith("CLUSTER_NODES="))
                    ?.split("=")[1]
                    ?.trim() ?? "";
            resolve({ folder, addresses });
        });
    });
}

export default async function globalSetup(): Promise<void> {
    // Skip if external endpoints already provided (CI with pre-started servers)
    if (
        process.env.GLIDE_STANDALONE_ENDPOINT ||
        process.env.GLIDE_CLUSTER_ENDPOINTS
    ) {
        console.log("[globalSetup] Using existing endpoints from environment");
        return;
    }

    console.log("[globalSetup] Starting shared clusters...");
    const t0 = Date.now();

    const [standalone, cluster] = await Promise.all([
        startCluster(["start", "-r", "0", "-n", "1"]),
        startCluster(["start", "-r", "0", "-n", "3", "--cluster-mode"]),
    ]);

    process.env.GLIDE_STANDALONE_ENDPOINT = standalone.addresses;
    process.env.GLIDE_CLUSTER_ENDPOINTS = cluster.addresses;
    process.env.GLIDE_STANDALONE_FOLDER = standalone.folder;
    process.env.GLIDE_CLUSTER_FOLDER = cluster.folder;

    console.log(`[globalSetup] Clusters ready in ${Date.now() - t0}ms`);
    console.log(`[globalSetup] Standalone: ${standalone.addresses}`);
    console.log(`[globalSetup] Cluster: ${cluster.addresses}`);
}
