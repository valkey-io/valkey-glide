// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

// The TLS CI pass runs `make tls-test`, which selects test methods with `-testify.m Tls`: only suite
// methods whose name contains "Tls" run in that pass. A TLS-requiring test opts out of the plaintext pass
// by calling skipIfTlsDisabled. If such a test is not also selected by the TLS pass, because its name has
// no "Tls", it runs in neither pass and silently loses CI coverage. That is exactly how issue #5509 (TLS
// tests not running in CI) happened. This file scans the package sources and fails if any function that
// gates on TLS has a name the TLS pass would not select, so extending the TLS suite cannot quietly escape
// both passes. It is the naming counterpart of the client-config seam check in client_config_seam_test.go.

import (
	"fmt"
	"io/fs"
	"os"
	"regexp"
	"strings"
	"testing"
	"testing/fstest"

	"github.com/stretchr/testify/require"
)

// tlsPassFilterInfix is the substring the TLS CI pass selects on. It must stay in lockstep with the
// -testify.m argument in the `tls-test` target of go/Makefile, which is `-testify.m Tls`. A test that gates
// on TLS but whose name does not contain this infix is skipped in the plaintext pass and is not selected by
// the TLS pass, so it runs in neither.
const tlsPassFilterInfix = "Tls"

// tlsGateFunc is the helper a test calls to skip itself when the run is not using TLS. A function that
// calls it only exercises its body under TLS, so it has to be reachable from a name the TLS pass selects.
const tlsGateFunc = "skipIfTlsDisabled"

// tlsFilterSeamFile is this file. It embeds sample sources for its own self-test that deliberately contain
// gate calls, so the real scan skips it, for the same reason findClientConfigBypasses skips its seam file.
const tlsFilterSeamFile = "tls_filter_seam_test.go"

// tlsGateHelperAllowlist names functions that call the gate but do not, and should not, carry the TLS infix
// because they are not the unit the filter selects. skipIfNotEnabled is the DNS helper that reaches the
// gate only on its useTLS branch; its TLS-path callers are themselves Tls-named test methods, so it cannot
// hide a test from both passes. Add a name here only with the same reasoning written down next to it.
var tlsGateHelperAllowlist = map[string]bool{
	"skipIfNotEnabled": true,
}

var (
	// A receiver method: func (suite *GlideTestSuite) TestFoo(...). Capture the method name.
	methodDeclRe = regexp.MustCompile(`^func \([^)]*\)\s+(\w+)`)
	// A plain function: func helperFoo(...). Capture the function name.
	funcDeclRe = regexp.MustCompile(`^func (\w+)`)
)

// findUnfilteredTlsGates returns "file:line (funcName)" for every call to the TLS gate whose enclosing
// function would not be selected by the TLS pass filter and is not explicitly allowlisted. It attributes a
// gate call to the nearest preceding function declaration, which also skips the gate's own definition since
// a declaration line is never a call site. It fails the test if there are no Go sources to read at all,
// since an empty scan would otherwise look like a clean result.
func findUnfilteredTlsGates(t *testing.T, sources fs.FS) []string {
	t.Helper()

	names, err := fs.Glob(sources, "*.go")
	require.NoError(t, err)
	require.NotEmpty(t, names, "found no package sources to check")

	gateCall := tlsGateFunc + "("

	violations := []string{}
	for _, name := range names {
		if name == tlsFilterSeamFile {
			continue
		}

		contents, err := fs.ReadFile(sources, name)
		require.NoError(t, err)

		currentFunc := ""
		for i, line := range strings.Split(string(contents), "\n") {
			// A declaration line names the function the following lines belong to; it is never itself a
			// call site, so recording it and moving on also skips the gate's own definition.
			if m := methodDeclRe.FindStringSubmatch(line); m != nil {
				currentFunc = m[1]
				continue
			}
			if m := funcDeclRe.FindStringSubmatch(line); m != nil {
				currentFunc = m[1]
				continue
			}

			if !strings.Contains(line, gateCall) {
				continue
			}
			if currentFunc == "" {
				violations = append(violations, fmt.Sprintf("%s:%d (file scope)", name, i+1))
				continue
			}
			if tlsGateHelperAllowlist[currentFunc] {
				continue
			}
			if !strings.Contains(currentFunc, tlsPassFilterInfix) {
				violations = append(violations, fmt.Sprintf("%s:%d (%s)", name, i+1, currentFunc))
			}
		}
	}

	return violations
}

// TestTlsGatesAreSelectedByTheTlsPass keeps every TLS-gated test reachable from a name the TLS CI pass
// selects. A test that gates on TLS but whose name the filter skips runs in neither pass and quietly loses
// coverage, the regression behind issue #5509. Failing here is cheaper than noticing the gap in production.
func TestTlsGatesAreSelectedByTheTlsPass(t *testing.T) {
	for _, violation := range findUnfilteredTlsGates(t, os.DirFS(".")) {
		t.Errorf(
			"%s gates on %s but its name is not selected by the TLS pass filter %q from go/Makefile; "+
				"give it a name containing %q or register it in tlsGateHelperAllowlist with the reason",
			violation, tlsGateFunc, "-testify.m "+tlsPassFilterInfix, tlsPassFilterInfix,
		)
	}
}

// TestTlsGateCheckCatchesAnUnfilteredGate covers the check above, so a change that stops it from matching
// shows up as a failure here rather than as a clean result. The sample sources live in this file, which is
// why the real scan skips tlsFilterSeamFile.
func TestTlsGateCheckCatchesAnUnfilteredGate(t *testing.T) {
	sources := fstest.MapFS{
		"tls_ok_test.go": {Data: []byte(
			"func (suite *GlideTestSuite) TestTlsHonest() {\n\tskipIfTlsDisabled(suite)\n}\n")},
		"plaintext_bad_test.go": {Data: []byte(
			"func (suite *GlideTestSuite) TestPlaintextOnly() {\n\tskipIfTlsDisabled(suite)\n}\n")},
		"helper_test.go": {Data: []byte(
			"func skipIfNotEnabled(suite *GlideTestSuite, useTLS bool) {\n\tskipIfTlsDisabled(suite)\n}\n")},
		"definition_test.go": {Data: []byte(
			"func skipIfTlsDisabled(suite *GlideTestSuite) {\n\tsuite.T().Skip()\n}\n")},
	}

	require.Equal(
		t,
		[]string{"plaintext_bad_test.go:2 (TestPlaintextOnly)"},
		findUnfilteredTlsGates(t, sources),
	)
}
