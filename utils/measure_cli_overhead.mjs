/**
 * Measures valkey-cli subprocess overhead on WSL1.
 * Starts a server, then times individual valkey-cli CLUSTER SLOTS calls.
 */
import { execFile, execFileSync } from "child_process";

const SCRIPT = "/mnt/c/dev/valkey-glide/utils/cluster_manager.py";

function runWsl(args, timeout = 60000) {
    return new Promise((resolve, reject) => {
        execFile("wsl", args, { timeout }, (err, stdout, stderr) => {
            if (err) reject(new Error(stderr || err.message));
            else resolve(stdout);
        });
    });
}

// Start a 3s+1r cluster
console.log("Starting cluster 3s+1r...");
const stdout = await runWsl(["python3", SCRIPT, "start", "-r", "1", "-n", "3", "--cluster-mode"]);
const nodes = stdout.split("\n").find(l => l.startsWith("CLUSTER_NODES=")).split("=")[1].split(",");
const folder = stdout.split("\n").find(l => l.startsWith("CLUSTER_FOLDER=")).split("=")[1].trim();
const [host, port] = nodes[0].split(":");

console.log(`Cluster ready. Measuring valkey-cli overhead on ${host}:${port}\n`);

// Measure 10 sequential valkey-cli calls
const times = [];
for (let i = 0; i < 10; i++) {
    const t0 = Date.now();
    await runWsl(["valkey-cli", "-h", host, "-p", port, "cluster", "slots"]);
    const elapsed = Date.now() - t0;
    times.push(elapsed);
    process.stdout.write(`  call ${i+1}: ${elapsed}ms\n`);
}

const sorted = [...times].sort((a,b) => a-b);
console.log(`\nmin=${sorted[0]}ms  median=${sorted[Math.floor(sorted.length/2)]}ms  p90=${sorted[Math.floor(sorted.length*0.9)]}ms  max=${sorted[sorted.length-1]}ms`);
console.log(`\nWith 6 nodes x median=${sorted[Math.floor(sorted.length/2)]}ms per call + sleep(1)s = ~${((6 * sorted[Math.floor(sorted.length/2)]) / 1000 + 6).toFixed(1)}s minimum`);
console.log(`With 6 nodes x median=${sorted[Math.floor(sorted.length/2)]}ms per call + sleep(0.1)s = ~${((6 * sorted[Math.floor(sorted.length/2)]) / 1000 + 0.6).toFixed(1)}s minimum`);

await runWsl(["python3", SCRIPT, "stop", "--cluster-folder", folder]);
