/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Honest per-config real-skip accounting for the wave-2 capability gate.
 *
 * `ServerModulesVs13.test.ts`'s wave-2 tests start with `if (!capability)
 * return;` -- Jest has no first-class "this test is a conditional skip"
 * reporting (`it.skip` only works when the condition is known before
 * collection, not after an async `beforeAll` probe runs), so a gated test
 * that returns early is still counted as "passed" in Jest's own summary.
 * That is an accepted limitation (see the PR body), not something this
 * file tries to work around with `it.skip`/`itIf13` -- this tracker exists
 * purely so the PR body can state the REAL skip count per configuration
 * instead of letting the same "185 passed" row imply every test actually
 * ran its assertions on every engine.
 *
 * `recordGate` is called at every `if (!capability) return;` site (in
 * place of the bare `return`) and increments either the "ran" or "skipped"
 * tally for the current process. `writeWave2SkipReport` (called from the
 * file's top-level `afterAll`) appends one JSON line to
 * `.module-ci-local-scratch/wave2-skip-report.jsonl` describing this run,
 * so `utils/run-modules-ci-locally.sh`'s three separate configurations
 * (each its own process) each contribute their own line rather than
 * overwriting one shared file.
 */

import * as fs from "fs";
import * as path from "path";

interface Wave2SkipTally {
    ran: number;
    skipped: number;
}

const tally: Wave2SkipTally = { ran: 0, skipped: 0 };

/**
 * Call at every wave-2 capability gate site instead of a bare early
 * `return` check. Returns the same boolean the caller should still branch
 * on (`if (!recordGate(capability)) return;`), while tallying whether this
 * call site actually ran (capability present) or was skipped.
 */
export function recordGate(capability: boolean): boolean {
    if (capability) {
        tally.ran++;
    } else {
        tally.skipped++;
    }

    return capability;
}

/**
 * Appends this process's final tally as one JSON line to the shared scratch
 * report, labelled by the emulate-release level the capability probe saw
 * (or `"incapable"` if the module never reached 1.3 capability at all).
 * Best-effort: a write failure (e.g. the scratch dir not existing because
 * this test file is being run outside `run-modules-ci-locally.sh`) is
 * logged, not thrown -- this is reporting, not a correctness gate.
 */
export function writeWave2SkipReport(
    label: string,
    extra: Record<string, unknown> = {},
): void {
    try {
        const repoRoot = path.resolve(__dirname, "..", "..");
        const scratchDir = path.join(repoRoot, ".module-ci-local-scratch");
        fs.mkdirSync(scratchDir, { recursive: true });
        const reportPath = path.join(scratchDir, "wave2-skip-report.jsonl");
        const line =
            JSON.stringify({
                label,
                ran: tally.ran,
                skipped: tally.skipped,
                total: tally.ran + tally.skipped,
                timestamp: new Date().toISOString(),
                ...extra,
            }) + "\n";
        fs.appendFileSync(reportPath, line);
        console.log(
            `[wave2-skip-report] ${label}: ran=${tally.ran} skipped=${tally.skipped} ` +
                `total=${tally.ran + tally.skipped}`,
        );
    } catch (e) {
        console.warn(`[wave2-skip-report] could not write report: ${e}`);
    }
}
