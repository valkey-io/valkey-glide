/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Single-sources `utils/run-modules-ci-locally.sh`'s test invocation against
 * `.github/workflows/node-modules.yml`'s "Run modules tests" step, so the two
 * cannot silently drift apart (the Node equivalent of Python's
 * `tests/test_ci_workflow_sync.py`, R2-H-1). This test does not run either
 * script -- it just asserts that both sides invoke `npm run test:modules`
 * with the same two endpoint flags, so a future edit to one side that isn't
 * mirrored on the other fails here instead of only showing up as a
 * discrepancy in a future review.
 *
 * Node's module test command has no equivalent of pytest's `-k`/`--html`
 * flags (test selection happens via `--testNamePattern` baked into the
 * `test:modules` npm script itself, not passed per-invocation), so this
 * guard is narrower than Python's: it only needs to track the script name
 * and the two endpoint flags.
 */

import { describe, expect, it } from "@jest/globals";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const REPO_ROOT = join(__dirname, "..", "..");
const WORKFLOW_FILE = join(
    REPO_ROOT,
    ".github",
    "workflows",
    "node-modules.yml",
);
const LOCAL_RUNNER_FILE = join(REPO_ROOT, "utils", "run-modules-ci-locally.sh");

const ENDPOINT_FLAGS = ["--standalone-endpoints", "--cluster-endpoints"];

describe("node-modules.yml / run-modules-ci-locally.sh sync", () => {
    it("both invoke npm run test:modules with the same endpoint flags", () => {
        const workflowText = readFileSync(WORKFLOW_FILE, "utf-8");
        const runnerText = readFileSync(LOCAL_RUNNER_FILE, "utf-8");

        expect(workflowText).toContain("npm run test:modules");
        expect(runnerText).toContain("npm run test:modules");

        for (const flag of ENDPOINT_FLAGS) {
            expect(workflowText).toContain(flag);
            expect(runnerText).toContain(flag);
        }
    });

    it("the local runner's test tail lives under node/, matching the workflow's working-directory", () => {
        const workflowText = readFileSync(WORKFLOW_FILE, "utf-8");
        const runnerText = readFileSync(LOCAL_RUNNER_FILE, "utf-8");

        // The CI job's "Run modules tests" step runs with
        // `working-directory: ./node`; the local runner must `cd` there
        // too before invoking the same npm script, or it would silently
        // run a different package.json's scripts.
        expect(workflowText).toMatch(/working-directory:\s*\.\/node/);
        expect(runnerText).toMatch(/cd\s+"\$\{REPO_ROOT\}\/node"/);
    });
});
