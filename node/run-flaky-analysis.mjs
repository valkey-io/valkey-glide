/**
 * Runs a single test file N times and collects pass/fail/timing stats.
 * Usage: node run-flaky-analysis.mjs [--file=TestFile] [--runs=N]
 */
import { execSync } from "child_process";

const args = Object.fromEntries(
    process.argv.slice(2)
        .filter(a => a.startsWith("--"))
        .map(a => a.slice(2).split("="))
);

const FILE = args["file"] ?? "NodeDiscoveryMode";
const RUNS = parseInt(args["runs"] ?? "10");

console.log(`Running ${FILE} ${RUNS} times...\n`);

const results = [];

for (let i = 1; i <= RUNS; i++) {
    const t0 = Date.now();
    let passed = 0, failed = 0, status = "PASS", failedTests = [];

    try {
        const output = execSync(
            `npx jest --testPathPattern="${FILE}" --runInBand --forceExit --verbose 2>&1`,
            { encoding: "utf8", timeout: 300000 }
        );
        const summary = output.split("\n").find(l => l.includes("Tests:"));
        passed = parseInt(summary?.match(/(\d+) passed/)?.[1] ?? "0");
    } catch (err) {
        status = "FAIL";
        const output = (err.stdout ?? "") + (err.stderr ?? "");
        const summary = output.split("\n").find(l => l.includes("Tests:"));
        passed = parseInt(summary?.match(/(\d+) passed/)?.[1] ?? "0");
        failed = parseInt(summary?.match(/(\d+) failed/)?.[1] ?? "0");

        // Extract failing test names and errors
        const lines = output.split("\n");
        let inFailure = false;
        let currentTest = "";
        let currentError = [];
        for (const line of lines) {
            if (line.match(/^\s+● .+/)) {
                if (currentTest) failedTests.push({ test: currentTest, error: currentError.join(" ").trim().slice(0, 200) });
                currentTest = line.trim().replace(/^● /, "");
                currentError = [];
                inFailure = true;
            } else if (inFailure && line.trim().length > 0 && !line.includes("──────")) {
                currentError.push(line.trim());
            }
        }
        if (currentTest) failedTests.push({ test: currentTest, error: currentError.join(" ").trim().slice(0, 200) });
    }

    const duration = ((Date.now() - t0) / 1000).toFixed(1);
    results.push({ run: i, status, passed, failed, duration, failedTests });
    console.log(`run ${i}: ${status} | passed=${passed} failed=${failed} | ${duration}s`);
    if (failedTests.length > 0) {
        for (const { test, error } of failedTests) {
            console.log(`  FAIL: ${test}`);
            console.log(`        ${error.slice(0, 150)}`);
        }
    }
}

// Summary
const passes = results.filter(r => r.status === "PASS").length;
const fails = results.filter(r => r.status === "FAIL").length;
const durations = results.map(r => parseFloat(r.duration)).sort((a,b) => a-b);
const median = durations[Math.floor(durations.length/2)];
const p90 = durations[Math.floor(durations.length*0.9)];

console.log(`\n${'='.repeat(50)}`);
console.log(`Results: ${passes}/${RUNS} passed, ${fails}/${RUNS} failed`);
console.log(`Timing:  min=${durations[0]}s  median=${median}s  p90=${p90}s  max=${durations[durations.length-1]}s`);

// Failure frequency
const failCounts = {};
for (const r of results) {
    for (const { test } of r.failedTests) {
        failCounts[test] = (failCounts[test] ?? 0) + 1;
    }
}
if (Object.keys(failCounts).length > 0) {
    console.log(`\nMost frequent failures:`);
    for (const [test, count] of Object.entries(failCounts).sort((a,b) => b[1]-a[1])) {
        console.log(`  ${count}/${RUNS}x: ${test}`);
    }
}
