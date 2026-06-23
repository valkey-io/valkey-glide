/**
 * Runs Jest test files in batches to reduce WSL load.
 * Usage: node run-tests-batched.mjs [--batch-size=N] [--delay=Ms]
 */
import { execSync } from "child_process";
import { readdirSync } from "fs";
import { join } from "path";

const args = Object.fromEntries(
    process.argv.slice(2)
        .filter(a => a.startsWith("--"))
        .map(a => a.slice(2).split("="))
);

const BATCH_SIZE = parseInt(args["batch-size"] ?? "2");
const DELAY_MS = parseInt(args["delay"] ?? "3000");
const TEST_DIR = new URL("./tests", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

// Get all test files, excluding ServerModules (requires special setup)
const testFiles = readdirSync(TEST_DIR)
    .filter(f => f.endsWith(".test.ts") && !f.includes("ServerModules"))
    .map(f => `tests/${f}`);

console.log(`Found ${testFiles.length} test files, running in batches of ${BATCH_SIZE} with ${DELAY_MS}ms delay between batches\n`);

let totalPassed = 0;
let totalFailed = 0;
const failedFiles = [];

for (let i = 0; i < testFiles.length; i += BATCH_SIZE) {
    const batch = testFiles.slice(i, i + BATCH_SIZE);
    const batchNum = Math.floor(i / BATCH_SIZE) + 1;
    const totalBatches = Math.ceil(testFiles.length / BATCH_SIZE);
    
    console.log(`\n=== Batch ${batchNum}/${totalBatches}: ${batch.join(", ")} ===`);
    
    const pattern = batch.map(f => f.replace(".ts", "").replace("tests/", "")).join("|");
    
    try {
        const reportFile = `test-report-batch-${String(batchNum).padStart(2, '0')}.html`;
        const result = execSync(
            `npx jest --testPathPattern="${pattern}" --runInBand --forceExit 2>&1`,
            { encoding: "utf8", timeout: 600000, env: { ...process.env, JEST_HTML_REPORTER_OUTPUT_PATH: reportFile } }
        );
        // Extract summary line
        const summary = result.split("\n").find(l => l.includes("Tests:"));
        console.log(summary ?? "  (no summary found)");
        console.log(`  Report: ${reportFile}`);
        
        const passed = parseInt(summary?.match(/(\d+) passed/)?.[1] ?? "0");
        totalPassed += passed;
    } catch (err) {
        const output = err.stdout ?? "";
        const summary = output.split("\n").find(l => l.includes("Tests:"));
        console.log(`  FAILED: ${summary ?? err.message.slice(0, 200)}`);
        
        const failed = parseInt(summary?.match(/(\d+) failed/)?.[1] ?? "0");
        const passed = parseInt(summary?.match(/(\d+) passed/)?.[1] ?? "0");
        totalFailed += failed;
        totalPassed += passed;
        failedFiles.push(...batch);
    }
    
    // Delay between batches to let WSL recover
    if (i + BATCH_SIZE < testFiles.length) {
        process.stdout.write(`  Waiting ${DELAY_MS}ms before next batch...`);
        await new Promise(r => setTimeout(r, DELAY_MS));
        console.log(" done");
    }
}

console.log(`\n${'='.repeat(60)}`);
console.log(`FINAL: ${totalPassed} passed, ${totalFailed} failed`);
if (failedFiles.length > 0) {
    console.log(`Failed files: ${failedFiles.join(", ")}`);
}
