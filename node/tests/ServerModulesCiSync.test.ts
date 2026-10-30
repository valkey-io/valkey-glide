/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Single-sources `utils/run-modules-ci-locally.sh`'s test invocation against
 * `.github/workflows/node-modules.yml`'s "Run modules tests" step, so the two
 * cannot silently drift apart (the Node equivalent of Python's
 * `tests/test_ci_workflow_sync.py`). This test does not run either script --
 * it just asserts that both sides invoke `npm run test:modules` with the
 * same two endpoint flags, so a future edit to one side that isn't mirrored
 * on the other fails here instead of only showing up as a discrepancy in a
 * future review.
 *
 * The outer describe block's name contains "GlideFt" deliberately: both
 * `npm run test:modules`'s `--testNamePattern="(GlideJson|GlideFt)"` and
 * `npm test`'s `--testPathIgnorePatterns=ServerModules` need this file to
 * actually run as part of the modules test job, not be silently excluded by
 * either filter the way its filename and a plain "... sync" describe name
 * would be.
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

/**
 * Slices the `node)` case arm out of the runner script's `case
 * "$MODULES_TEST_LANG" in ... esac` block, from the `node)` label up to
 * its closing `;;`. Narrower than searching the whole file: the Python
 * arm of this same script already contains both endpoint flags, so a
 * whole-file `toContain` check would still pass even if the Node arm
 * specifically lost them (e.g. during a merge-conflict resolution that
 * kept the wrong arm's content). Deliberately simple (a case-insensitive
 * label match, not a shell parser) -- this is a drift GUARD over a small,
 * hand-written script, not a general-purpose arg parser.
 */
function extractNodeCaseArm(runnerText: string): string {
    const nodeArmStart = runnerText.indexOf("node)");

    if (nodeArmStart === -1) {
        throw new Error(
            "run-modules-ci-locally.sh has no `node)` case arm to check",
        );
    }

    const armEnd = runnerText.indexOf(";;", nodeArmStart);

    if (armEnd === -1) {
        throw new Error(
            "run-modules-ci-locally.sh's `node)` case arm has no closing `;;`",
        );
    }

    return runnerText.slice(nodeArmStart, armEnd);
}

describe("GlideFt CI sync: node-modules.yml / run-modules-ci-locally.sh", () => {
    it("both invoke npm run test:modules with the same endpoint flags", () => {
        const workflowText = readFileSync(WORKFLOW_FILE, "utf-8");
        const runnerText = readFileSync(LOCAL_RUNNER_FILE, "utf-8");

        expect(workflowText).toContain("npm run test:modules");
        expect(runnerText).toContain("npm run test:modules");

        const nodeArm = extractNodeCaseArm(runnerText);
        expect(nodeArm).toContain("npm run test:modules");

        for (const flag of ENDPOINT_FLAGS) {
            expect(workflowText).toContain(flag);
            // Checked against the Node arm specifically, not the whole
            // runner script -- the Python arm already contains both
            // flags for its own invocation, so a whole-file check would
            // pass even if the Node arm lost them.
            expect(nodeArm).toContain(flag);
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
