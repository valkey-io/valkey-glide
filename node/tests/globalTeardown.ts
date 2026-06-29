/**
 * Jest globalTeardown: stops shared clusters started by globalSetup.
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

function stopCluster(folder: string): Promise<void> {
    return new Promise((resolve) => {
        const [cmd, cmdArgs] = isWindows
            ? [
                  "wsl",
                  ["python3", scriptPath, "stop", "--cluster-folder", folder],
              ]
            : ["python3", [PY_SCRIPT_PATH, "stop", "--cluster-folder", folder]];

        execFile(cmd, cmdArgs, { timeout: 30000 }, () => resolve());
    });
}

export default async function globalTeardown(): Promise<void> {
    const standaloneFolder = process.env.GLIDE_STANDALONE_FOLDER;
    const clusterFolder = process.env.GLIDE_CLUSTER_FOLDER;

    if (!standaloneFolder && !clusterFolder) return;

    console.log("[globalTeardown] Stopping shared clusters...");

    await Promise.all([
        standaloneFolder ? stopCluster(standaloneFolder) : Promise.resolve(),
        clusterFolder ? stopCluster(clusterFolder) : Promise.resolve(),
    ]);

    console.log("[globalTeardown] Done.");
}
