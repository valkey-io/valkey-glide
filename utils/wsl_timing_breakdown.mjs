/**
 * Measures per-step timing of cluster_manager.py by running with debug logging
 * and parsing the elapsed time output from each function.
 */
import { execFile } from "child_process";

const SCRIPT = "/mnt/c/dev/valkey-glide/utils/cluster_manager.py";
const RUNS = 5;

function run(args) {
    return new Promise((resolve, reject) => {
        execFile("wsl", ["python3", SCRIPT, "-log", "debug", ...args],
            { timeout: 120000 },
            (err, stdout, stderr) => {
                if (err) { reject(new Error(stderr.slice(0,200))); return; }
                const folder = stdout.split("\n").find(l => l.startsWith("CLUSTER_FOLDER="))?.split("=")[1]?.trim();
                resolve({ stdout, stderr, folder });
            }
        );
    });
}

function stop(folder) {
    return new Promise(resolve => {
        execFile("wsl", ["python3", SCRIPT, "stop", "--cluster-folder", folder],
            { timeout: 30000 }, () => resolve());
    });
}

function parseTimings(stderr) {
    const timings = {};
    const patterns = [
        [/create_servers\(\) Elapsed time: ([\d.]+)/, "create_servers"],
        [/create_cluster .* Elapsed time: ([\d.]+)/, "create_cluster"],
        [/create_replication Elapsed time: ([\d.]+)/, "create_replication"],
        [/generate_tls_certs\(\) Elapsed time: ([\d.]+)/, "generate_tls_certs"],
    ];
    for (const [regex, name] of patterns) {
        const m = stderr.match(regex);
        if (m) timings[name] = parseFloat(m[1]).toFixed(2);
    }
    // Also extract total from stdout log
    const totalMatch = stderr.match(/Created .* in ([\d.]+) seconds/);
    if (totalMatch) timings["total"] = parseFloat(totalMatch[1]).toFixed(2);
    return timings;
}

console.log(`Timing breakdown for cluster 3s+1r (${RUNS} runs)\n`);

for (let i = 1; i <= RUNS; i++) {
    const t0 = Date.now();
    try {
        const { stderr, folder } = await run(["start", "-r", "1", "-n", "3", "--cluster-mode"]);
        const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
        const timings = parseTimings(stderr);
        console.log(`run ${i} (${elapsed}s total):`);
        for (const [k, v] of Object.entries(timings)) {
            console.log(`  ${k}: ${v}s`);
        }
        if (folder) await stop(folder);
    } catch(e) {
        console.log(`run ${i}: FAILED - ${e.message}`);
    }
    await new Promise(r => setTimeout(r, 1000));
}
