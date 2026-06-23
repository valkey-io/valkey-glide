/**
 * Compare cluster startup speed:
 * A) Current: script at /mnt/c path, GLIDE_HOME_DIR default
 * B) Optimized: script copied to /tmp, GLIDE_HOME_DIR=/tmp/glide-home
 */
import { execFile, execFileSync } from "child_process";

const RUNS = 5;
const MNT_SCRIPT = "/mnt/c/dev/valkey-glide/utils/cluster_manager.py";
const LOCAL_SCRIPT = "/tmp/glide-utils/cluster_manager.py";

// Setup: copy utils to WSL local filesystem
console.log("Setting up WSL local copy...");
try {
    execFileSync("wsl", ["bash", "-c", "mkdir -p /tmp/glide-utils && cp -r /mnt/c/dev/valkey-glide/utils/. /tmp/glide-utils/"]);
    console.log("Copied utils to /tmp/glide-utils");
} catch(e) {
    console.error("Setup failed:", e.message);
    process.exit(1);
}

function runTest(label, scriptPath, env = {}) {
    return new Promise((resolve, reject) => {
        const args = ["python3", scriptPath, "start", "-r", "1", "-n", "3", "--cluster-mode"];
        const envStr = Object.entries(env).map(([k,v]) => `${k}=${v}`).join(" ");
        const t0 = Date.now();
        execFile(
            "wsl",
            envStr ? ["bash", "-c", `${envStr} python3 ${scriptPath} start -r 1 -n 3 --cluster-mode`] : args,
            { timeout: 120000 },
            (err, stdout) => {
                const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
                if (err) { reject(new Error(err.message)); return; }
                const folder = stdout.split("\n").find(l => l.startsWith("CLUSTER_FOLDER="))?.split("=")[1]?.trim();
                // stop the cluster
                execFile("wsl", ["python3", scriptPath, "stop", "--cluster-folder", folder], { timeout: 30000 }, () => {
                    resolve(parseFloat(elapsed));
                });
            }
        );
    });
}

async function runSuite(label, scriptPath, env = {}) {
    console.log(`\n--- ${label} (${RUNS} runs) ---`);
    const times = [];
    for (let i = 1; i <= RUNS; i++) {
        try {
            const t = await runTest(label, scriptPath, env);
            times.push(t);
            console.log(`  run ${i}: ${t}s`);
        } catch(e) {
            console.log(`  run ${i}: FAILED - ${e.message.slice(0,80)}`);
        }
        await new Promise(r => setTimeout(r, 1000));
    }
    if (times.length > 0) {
        const sorted = [...times].sort((a,b) => a-b);
        const median = sorted[Math.floor(sorted.length/2)];
        const p90 = sorted[Math.floor(sorted.length*0.9)];
        console.log(`  min=${sorted[0]}s  median=${median}s  p90=${p90}s  max=${sorted[sorted.length-1]}s`);
    }
}

await runSuite("A) /mnt/c path (current)", MNT_SCRIPT);
await runSuite("B) /tmp local path + GLIDE_HOME_DIR", LOCAL_SCRIPT, { GLIDE_HOME_DIR: "/tmp/glide-home" });

console.log("\nDone.");
