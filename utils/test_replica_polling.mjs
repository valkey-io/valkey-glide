/**
 * Test: verify primary is always at index 0 in getAddresses() output.
 * Run multiple times to check for ordering consistency.
 */
import { execFile } from "child_process";
import { createConnection } from "net";

const SCRIPT = "/mnt/c/dev/valkey-glide/utils/cluster_manager.py";
const RUNS = 5;

function runWsl(args) {
    return new Promise((resolve, reject) => {
        execFile("wsl", ["python3", SCRIPT, ...args], (err, stdout, stderr) => {
            if (err) reject(new Error(stderr || err.message));
            else resolve(stdout);
        });
    });
}

function parseAddresses(stdout) {
    const line = stdout.split("\n").find(l => l.startsWith("CLUSTER_NODES="));
    const folder = stdout.split("\n").find(l => l.startsWith("CLUSTER_FOLDER="));
    return {
        addresses: line.split("=")[1].split(",").map(a => { const [h,p] = a.split(":"); return [h, Number(p)]; }),
        folder: folder.split("=")[1].trim(),
    };
}

async function getRole(host, port) {
    return new Promise((resolve) => {
        const sock = createConnection({ host, port }, () => sock.write("INFO replication\r\n"));
        let buf = "";
        const timer = setTimeout(() => { sock.destroy(); resolve("unknown"); }, 1000);
        sock.on("data", d => {
            buf += d.toString();
            const m = buf.match(/^role:(\w+)/m);
            if (m) { clearTimeout(timer); sock.destroy(); resolve(m[1]); }
        });
        sock.on("error", () => { clearTimeout(timer); resolve("error"); });
        sock.on("close", () => { clearTimeout(timer); resolve("unknown"); });
    });
}

console.log(`Running ${RUNS} standalone 1s+1r startups, checking address order...\n`);
let primaryFirstCount = 0;

for (let i = 0; i < RUNS; i++) {
    const stdout = await runWsl(["start", "-r", "1", "-n", "1"]);
    const { addresses, folder } = parseAddresses(stdout);
    const roles = await Promise.all(addresses.map(([h, p]) => getRole(h, p)));
    const primaryAtIndex0 = roles[0] === "master";
    if (primaryAtIndex0) primaryFirstCount++;
    console.log(`run ${i+1}: ${addresses.map(([h,p],i) => `${h}:${p}(${roles[i]})`).join(", ")} -> primary at index ${roles.indexOf("master")}`);
    await runWsl(["stop", "--cluster-folder", folder]);
    await new Promise(r => setTimeout(r, 500));
}

console.log(`\nPrimary at index 0: ${primaryFirstCount}/${RUNS} times`);
