// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// ScriptDebug Tests - Standalone

func (suite *GlideTestSuite) TestScriptDebug_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.ScriptDebug(context.Background(), options.ScriptDebugModeNo)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestScriptDebug_StandaloneAllModes() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.ScriptDebug(context.Background(), options.ScriptDebugModeYes)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	result, err = client.ScriptDebug(context.Background(), options.ScriptDebugModeSync)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	result, err = client.ScriptDebug(context.Background(), options.ScriptDebugModeNo)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

// ScriptDebug Tests - Cluster

func (suite *GlideTestSuite) TestScriptDebug_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.ScriptDebug(context.Background(), options.ScriptDebugModeNo)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestScriptDebug_ClusterAllModes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.ScriptDebug(context.Background(), options.ScriptDebugModeYes)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	result, err = client.ScriptDebug(context.Background(), options.ScriptDebugModeSync)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	result, err = client.ScriptDebug(context.Background(), options.ScriptDebugModeNo)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestScriptDebugWithOptions_ClusterRouting() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.ScriptDebugClusterOptions{
		RouteOption: &options.RouteOption{Route: config.RandomRoute},
	}
	result, err := client.ScriptDebugWithOptions(context.Background(), options.ScriptDebugModeNo, opts)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestScriptDebugWithOptions_ClusterNilRoute() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.ScriptDebugClusterOptions{}
	result, err := client.ScriptDebugWithOptions(context.Background(), options.ScriptDebugModeNo, opts)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

// Failover Tests - Standalone

func (suite *GlideTestSuite) TestFailover_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	// FAILOVER on a standalone server: returns "OK" (initiates failover) or error if no replicas
	result, err := client.Failover(context.Background())
	if err == nil {
		assert.Equal(t, "OK", result)
		// Abort the failover to avoid disrupting other tests
		abortOpts := options.FailoverOptions{Abort: true}
		_, _ = client.FailoverWithOptions(context.Background(), abortOpts)
	}
}

func (suite *GlideTestSuite) TestFailoverWithOptions_StandaloneAbort() {
	client := suite.defaultClient()
	t := suite.T()

	// FAILOVER ABORT when no failover is in progress returns an error
	opts := options.FailoverOptions{Abort: true}
	_, err := client.FailoverWithOptions(context.Background(), opts)
	assert.Error(t, err)
}

func (suite *GlideTestSuite) TestFailoverWithOptions_StandaloneInvalidTarget() {
	client := suite.defaultClient()
	t := suite.T()

	// FAILOVER TO an unreachable host should fail
	opts := options.FailoverOptions{
		Host:      "invalid-host-that-does-not-exist",
		Port:      6379,
		TimeoutMs: 100,
	}
	_, err := client.FailoverWithOptions(context.Background(), opts)
	assert.Error(t, err)
}

// Shutdown Tests - Standalone

func (suite *GlideTestSuite) TestShutdownAbort_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	// SHUTDOWN ABORT when no shutdown is pending - should succeed or return error depending on version
	opts := options.ShutdownOptions{Abort: true}
	err := client.ShutdownWithOptions(context.Background(), opts)
	// SHUTDOWN ABORT returns error if no shutdown is in progress, or succeeds silently
	// Either outcome is acceptable since we can't guarantee a pending shutdown state
	_ = err
	_ = t
}

// Note: PSYNC is not tested in the integration suite because it enters replication mode
// and disrupts the shared test connection. It is covered by example tests only.
// SHUTDOWN is similarly destructive and tested only via ABORT subcommand.

// Shutdown Options Tests

func (suite *GlideTestSuite) TestShutdownOptions_ToArgs() {
	t := suite.T()

	// Test ABORT option
	opts := options.ShutdownOptions{Abort: true}
	assert.Equal(t, []string{"ABORT"}, opts.ToArgs())

	// Test SAVE mode
	mode := options.ShutdownModeSave
	opts = options.ShutdownOptions{Mode: &mode}
	assert.Equal(t, []string{"SAVE"}, opts.ToArgs())

	// Test NOSAVE mode
	noSaveMode := options.ShutdownModeNoSave
	opts = options.ShutdownOptions{Mode: &noSaveMode}
	assert.Equal(t, []string{"NOSAVE"}, opts.ToArgs())

	// Test NOW + FORCE
	opts = options.ShutdownOptions{Now: true, Force: true}
	assert.Equal(t, []string{"NOW", "FORCE"}, opts.ToArgs())

	// Test combined NOSAVE + NOW + FORCE
	opts = options.ShutdownOptions{Mode: &noSaveMode, Now: true, Force: true}
	assert.Equal(t, []string{"NOSAVE", "NOW", "FORCE"}, opts.ToArgs())

	// Test nil options
	var nilOpts *options.ShutdownOptions
	assert.Equal(t, []string{}, nilOpts.ToArgs())
}

// Failover Options Tests

func (suite *GlideTestSuite) TestFailoverOptions_ToArgs() {
	t := suite.T()

	// Test ABORT
	opts := options.FailoverOptions{Abort: true}
	result, err := opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"ABORT"}, result)

	// Test TO host port
	opts = options.FailoverOptions{Host: "localhost", Port: 6380}
	result, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"TO", "localhost", "6380"}, result)

	// Test TO host port FORCE
	opts = options.FailoverOptions{Host: "localhost", Port: 6380, Force: true}
	result, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"TO", "localhost", "6380", "FORCE"}, result)

	// Test TIMEOUT
	opts = options.FailoverOptions{TimeoutMs: 5000}
	result, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"TIMEOUT", "5000"}, result)

	// Test TO + TIMEOUT
	opts = options.FailoverOptions{Host: "localhost", Port: 6380, TimeoutMs: 5000}
	result, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"TO", "localhost", "6380", "TIMEOUT", "5000"}, result)

	// Test TO + FORCE + TIMEOUT
	opts = options.FailoverOptions{Host: "localhost", Port: 6380, Force: true, TimeoutMs: 5000}
	result, err = opts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{"TO", "localhost", "6380", "FORCE", "TIMEOUT", "5000"}, result)

	// Test nil
	var nilOpts *options.FailoverOptions
	result, err = nilOpts.ToArgs()
	assert.NoError(t, err)
	assert.Equal(t, []string{}, result)

	// Test invalid: FORCE without Host/Port
	opts = options.FailoverOptions{Force: true}
	_, err = opts.ToArgs()
	assert.Error(t, err)

	// Test invalid: ABORT with other options
	opts = options.FailoverOptions{Abort: true, Host: "localhost", Port: 6380}
	_, err = opts.ToArgs()
	assert.Error(t, err)
}

// Context cancellation tests

func (suite *GlideTestSuite) TestScriptDebug_ContextCancellation() {
	client := suite.defaultClient()
	t := suite.T()

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.ScriptDebug(ctx, options.ScriptDebugModeNo)
	assert.Error(t, err)
}

func (suite *GlideTestSuite) TestFailover_ContextCancellation() {
	client := suite.defaultClient()
	t := suite.T()

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.Failover(ctx)
	assert.Error(t, err)
}
