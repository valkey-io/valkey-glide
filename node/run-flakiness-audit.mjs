/**
 * Flakiness audit: run each test file N times and report pass rate + failure types.
 * Usage: node run-flakiness-audit.mjs [--runs=N] [--file=pattern] [--output=report.md]
 * Default: 10 runs per file (use --runs=100 for full audit)
 */
import { execSync } from "child_process";
import { readdirSync, writeFileSync, readFileSync } from "fs";

const args = Object.fromEntries(
    process.argv.slice(2)
        .filter(a => a.startsWith("--"))
        .map(a => a.slice(2).split("="))
);

const RUNS = parseInt(args["runs"] ?? "10");
const FILE_PATTERN = args["file"] ?? null;
const OUTPUT = args["output"] ?? "flakiness-report.md";
const TEST_DIR = "tests";

const ALL_FILES = readdirSync(TEST_DIR)
    .filter(f => f.endsWith(".test.ts") && !f.includes("ServerModules") && !f.includes("Timing"))
    .sort();

const files = FILE_PATTERN
    ? ALL_FILES.filter(f => f.toLowerCase().includes(FILE_PATTERN.toLowerCase()))
    : ALL_FILES;

console.log(`Auditing ${files.length} file(s), ${RUNS} runs each\n`);

const FAILURE_PATTERNS = [
    [/Exceeded timeout.*for a hook/,         "TIMEOUT_BEFOREALL"],
    [/Exceeded timeout.*for a test/,         "TIMEOUT_TEST"],
    [/ReadOnly.*read only replica/i,          "READONLY"],
    [/connection attempt timed out/i,        "CONNECTION"],
    [/os error 10048/,                        "PORT_EXHAUSTION"],
    [/Cannot read properties of undefined/,  "SUITE_CRASH"],
    [/Exceeded timeout/,                      "TIMEOUT"],
];

function classifyFailure(output) {
    for (const [pattern, label] of FAILURE_PATTERNS) {
        if (pattern.test(output)) return label;
    }
    return "UNKNOWN";
}

function percentile(arr, p) {
    const s = [...arr].sort((a,b) => a-b);
    return s[Math.floor(s.length * p / 100)] ?? s[s.length-1];
}

const results = [];

for (const file of files) {
    const pattern = file.replace(".test.ts", "");
    console.log(`\n[${file}] running ${RUNS} times...`);
    
    const runs = [];
    const failureCounts = {};
    
    for (let i = 1; i <= RUNS; i++) {
        const t0 = Date.now();
        let status = "PASS", failureType = null;
        
        try {
            execSync(
                `npx jest --testPathPattern="${pattern}" --runInBand --forceExit 2>&1`,
                { encoding: "utf8", timeout: 1800000 }
            );
        } catch(err) {
            status = "FAIL";
            const output = (err.stdout ?? "") + (err.stderr ?? "");
            failureType = classifyFailure(output);
            failureCounts[failureType] = (failureCounts[failureType] ?? 0) + 1;
        }
        
        const duration = (Date.now() - t0) / 1000;
        runs.push({ status, duration, failureType });
        process.stdout.write(`  ${i}/${RUNS} ${status}${failureType ? ` (${failureType})` : ""} ${duration.toFixed(1)}s\n`);
    }
    
    const passed = runs.filter(r => r.status === "PASS").length;
    const durations = runs.map(r => r.duration);
    const summary = {
        file,
        runs: RUNS,
        passed,
        failed: RUNS - passed,
        passRate: `${passed}/${RUNS}`,
        failures: failureCounts,
        timing: {
            min: percentile(durations, 0).toFixed(1),
            median: percentile(durations, 50).toFixed(1),
            p90: percentile(durations, 90).toFixed(1),
            max: percentile(durations, 100).toFixed(1),
        }
    };
    results.push(summary);
    
    const failStr = Object.entries(failureCounts).map(([k,v]) => `${k}×${v}`).join(", ") || "none";
    console.log(`  Result: ${passed}/${RUNS} passed | failures: ${failStr} | p90=${summary.timing.p90}s`);
}

// Generate markdown report
const now = new Date().toISOString().split("T")[0];
const rows = results.map(r => {
    const failStr = Object.entries(r.failures).map(([k,v]) => `${k}×${v}`).join(", ") || "—";
    const icon = r.failed === 0 ? "✅" : r.failed <= RUNS * 0.05 ? "⚠️" : "❌";
    return `| ${icon} ${r.file} | ${r.passRate} | ${failStr} | p90=${r.timing.p90}s |`;
});

const report = `# Flakiness Audit — ${now} (${RUNS} runs each)

| Status | File | Pass Rate | Failures | Timing |
|---|---|---|---|---|
${rows.join("\n")}

## Details
${
    results.filter(r => r.failed > 0).map(r =>
        `### ${r.file}\n- Pass rate: ${r.passRate}\n- Failures: ${JSON.stringify(r.failures)}\n- Timing: min=${r.timing.min}s median=${r.timing.median}s p90=${r.timing.p90}s max=${r.timing.max}s`
    ).join("\n\n") || "_All files passed_"
}
`;

writeFileSync(OUTPUT, report);
console.log(`\nReport written to ${OUTPUT}`);
