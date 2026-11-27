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

	goArmPattern := regexp.MustCompile(`(?s)\n\s*go\)\n(.*?\n\s*);;`)
	goArmMatch := goArmPattern.FindStringSubmatch(runnerText)
	require.NotEmpty(
		t, goArmMatch,
		"could not find the go) case arm in utils/run-modules-ci-locally.sh -- "+
			"has the case statement's structure changed?",
	)
	goArmText := goArmMatch[1]

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
		t, regexp.MustCompile(`-testify\.m\s+"?\^?TestModule("|\s|$)`), goArmText,
		"utils/run-modules-ci-locally.sh's go) case arm no longer filters to ^TestModule -- it would "+
			"run (or skip) more or fewer tests than CI's modules-test target does.",
	)
}
