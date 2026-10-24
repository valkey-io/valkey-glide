// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"os"
	"path/filepath"
	"regexp"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestGoModulesCiSync single-sources utils/run-modules-ci-locally.sh's test invocation against
// .github/workflows/go-modules.yml's "Build and run modules tests" step, so the two cannot
// silently drift apart (the Go equivalent of Python's test_ci_workflow_sync.py, Node's
// ServerModulesCiSync.test.ts, and Java's JavaModulesCiSyncTest.java). This test does not run
// either script -- it just asserts that both sides invoke a module-test command with the same
// two endpoint flags, so a future edit to one side that isn't mirrored on the other fails here
// instead of only showing up as a discrepancy in a future review.
func TestGoModulesCiSync(t *testing.T) {
	repoRoot, err := filepath.Abs(filepath.Join("..", ".."))
	require.NoError(t, err)
	workflowFile := filepath.Join(repoRoot, ".github", "workflows", "go-modules.yml")
	runnerFile := filepath.Join(repoRoot, "utils", "run-modules-ci-locally.sh")

	workflowBytes, err := os.ReadFile(workflowFile)
	require.NoError(t, err)
	runnerBytes, err := os.ReadFile(runnerFile)
	require.NoError(t, err)
	workflowText := string(workflowBytes)
	runnerText := string(runnerBytes)

	// The runner script is shared across languages via a `case "$MODULES_TEST_LANG" in ... esac`
	// block, and the python) arm's flag text happens to look like the go) arm's. Extract just the
	// go) arm so a regression confined to that arm (while the python arm's matching text stays
	// intact) is actually caught here, instead of only asserting against the whole file.
	goArmPattern := regexp.MustCompile(`(?s)\n\s*go\)\n(.*?\n\s*);;`)
	goArmMatch := goArmPattern.FindStringSubmatch(runnerText)
	require.NotEmpty(
		t, goArmMatch,
		"could not find the go) case arm in utils/run-modules-ci-locally.sh -- "+
			"has the case statement's structure changed?",
	)
	goArmText := goArmMatch[1]

	// CI invokes `make modules-test standalone-endpoints=... cluster-endpoints=...`; the local
	// runner bypasses the Makefile's HTML-report pipe (a flaky third-party module fetch) but must
	// run the exact same underlying `go test ./integTest/... -testify.m "^TestModule"` with the
	// same two endpoint flags (spelled --standalone-endpoints/--cluster-endpoints at that layer).
	for _, flag := range []string{"standalone-endpoints", "cluster-endpoints"} {
		assert.Contains(
			t, workflowText, flag,
			"go-modules.yml no longer passes %s -- update both files to match.", flag,
		)
		assert.Contains(
			t, goArmText, flag,
			"utils/run-modules-ci-locally.sh's go) case arm is missing %s, which CI's step passes.", flag,
		)
	}

	assert.Contains(
		t, workflowText, "modules-test",
		"go-modules.yml no longer invokes the modules-test make target -- update both files to match.",
	)
	assert.Regexp(
		t, regexp.MustCompile(`-testify\.m\s+"?\^?TestModule`), goArmText,
		"utils/run-modules-ci-locally.sh's go) case arm no longer filters to ^TestModule -- it would "+
			"run (or skip) more or fewer tests than CI's modules-test target does.",
	)
}
