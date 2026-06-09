// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// triggerLatencySpikeStandalone enables latency monitoring with a low threshold, runs a
// `DEBUG SLEEP` so the server records at least one "command" event, and registers a
// `t.Cleanup` that always restores the previous `latency-monitor-threshold`. The cleanup
// runs even if a later assertion fails or panics, so a failing test cannot leak state to
// later tests sharing the same server.
func (suite *GlideTestSuite) triggerLatencySpikeStandalone(ctx context.Context) {
	t := suite.T()
	client := suite.defaultClient()

	prev, err := client.ConfigGet(ctx, []string{"latency-monitor-threshold"})
	require.NoError(t, err)
	prevThreshold, ok := prev["latency-monitor-threshold"]
	if !ok {
		prevThreshold = "0"
	}

	_, err = client.ConfigSet(ctx, map[string]string{"latency-monitor-threshold": "1"})
	require.NoError(t, err)
	t.Cleanup(func() {
		_, restoreErr := client.ConfigSet(
			context.Background(),
			map[string]string{"latency-monitor-threshold": prevThreshold},
		)
		if restoreErr != nil {
			t.Logf("failed to restore latency-monitor-threshold to %q: %v", prevThreshold, restoreErr)
		}
	})

	_, err = client.CustomCommand(ctx, []string{"DEBUG", "SLEEP", "0.05"})
	if err != nil {
		// `DEBUG` may be disabled (`enable-debug-command no`); the latency commands
		// themselves should still be reachable, so we don't fail the test.
		t.Logf("DEBUG SLEEP unavailable, latency series may be empty: %v", err)
	}
}

func (suite *GlideTestSuite) triggerLatencySpikeCluster(ctx context.Context) {
	t := suite.T()
	client := suite.defaultClusterClient()

	prev, err := client.ConfigGet(ctx, []string{"latency-monitor-threshold"})
	require.NoError(t, err)
	prevThreshold := "0"
	if v, ok := prev["latency-monitor-threshold"]; ok {
		prevThreshold = v
	}

	_, err = client.ConfigSet(ctx, map[string]string{"latency-monitor-threshold": "1"})
	require.NoError(t, err)
	t.Cleanup(func() {
		_, restoreErr := client.ConfigSet(
			context.Background(),
			map[string]string{"latency-monitor-threshold": prevThreshold},
		)
		if restoreErr != nil {
			t.Logf("failed to restore latency-monitor-threshold to %q: %v", prevThreshold, restoreErr)
		}
	})

	_, err = client.CustomCommand(ctx, []string{"DEBUG", "SLEEP", "0.05"})
	if err != nil {
		t.Logf("DEBUG SLEEP unavailable, latency series may be empty: %v", err)
	}
}

// LATENCY HISTORY (standalone)

func (suite *GlideTestSuite) TestLatencyHistory() {
	client := suite.defaultClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeStandalone(ctx)
	// Give the server a brief moment to record the spike.
	time.Sleep(50 * time.Millisecond)

	entries, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	assert.NotNil(t, entries)
	for _, e := range entries {
		assert.False(t, e.Time.IsZero())
		assert.GreaterOrEqual(t, e.Latency, time.Duration(0))
	}

	// An unknown event must not error – the server simply returns an empty array.
	unknown, err := client.LatencyHistory(ctx, "no-such-event")
	require.NoError(t, err)
	assert.Empty(t, unknown)
}

// LATENCY LATEST (standalone)

func (suite *GlideTestSuite) TestLatencyLatest() {
	client := suite.defaultClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeStandalone(ctx)
	time.Sleep(50 * time.Millisecond)

	entries, err := client.LatencyLatest(ctx)
	require.NoError(t, err)
	assert.NotNil(t, entries)
	for _, e := range entries {
		assert.NotEmpty(t, e.EventName)
		assert.False(t, e.Time.IsZero())
		assert.GreaterOrEqual(t, e.Latest, time.Duration(0))
		assert.GreaterOrEqual(t, e.Maximum, e.Latest)
	}
}

// LATENCY RESET (standalone)

func (suite *GlideTestSuite) TestLatencyReset() {
	client := suite.defaultClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeStandalone(ctx)
	time.Sleep(50 * time.Millisecond)

	resetCount, err := client.LatencyReset(ctx)
	require.NoError(t, err)
	assert.GreaterOrEqual(t, resetCount, int64(0))

	// After reset, history for "command" should be empty.
	hist, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	assert.Empty(t, hist)
}

func (suite *GlideTestSuite) TestLatencyResetWithEvents() {
	client := suite.defaultClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeStandalone(ctx)
	time.Sleep(50 * time.Millisecond)

	resetCount, err := client.LatencyReset(ctx, "command")
	require.NoError(t, err)
	assert.GreaterOrEqual(t, resetCount, int64(0))

	hist, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	assert.Empty(t, hist)

	// Resetting an unknown event is a no-op (returns 0).
	unknownReset, err := client.LatencyReset(ctx, "no-such-event")
	require.NoError(t, err)
	assert.Equal(t, int64(0), unknownReset)
}

// LATENCY HISTORY (cluster)

func (suite *GlideTestSuite) TestLatencyHistory_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)
	time.Sleep(50 * time.Millisecond)

	val, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	assert.False(t, val.IsEmpty())

	if val.IsMultiValue() {
		nodes := val.MultiValue()
		assert.NotEmpty(t, nodes)
		for addr, entries := range nodes {
			assert.NotEmpty(t, addr)
			for _, e := range entries {
				assert.False(t, e.Time.IsZero())
				assert.GreaterOrEqual(t, e.Latency, time.Duration(0))
			}
		}
	} else {
		for _, e := range val.SingleValue() {
			assert.False(t, e.Time.IsZero())
			assert.GreaterOrEqual(t, e.Latency, time.Duration(0))
		}
	}
}

func (suite *GlideTestSuite) TestLatencyHistoryWithOptions_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)
	time.Sleep(50 * time.Millisecond)

	val, err := client.LatencyHistoryWithOptions(ctx, "command", options.RouteOption{Route: config.AllNodes})
	require.NoError(t, err)
	require.True(t, val.IsMultiValue(), "AllNodes should produce a multi-value response")
	for addr := range val.MultiValue() {
		assert.NotEmpty(t, addr)
	}

	// Random route resolves to a single node.
	single, err := client.LatencyHistoryWithOptions(ctx, "command", options.RouteOption{Route: config.RandomRoute})
	require.NoError(t, err)
	assert.True(t, single.IsSingleValue(), "RandomRoute should resolve to a single-value ClusterValue")
	for _, e := range single.SingleValue() {
		assert.False(t, e.Time.IsZero())
		assert.GreaterOrEqual(t, e.Latency, time.Duration(0))
	}

	// Nil route should match the no-options method (default routing → multi-value).
	nilRoute, err := client.LatencyHistoryWithOptions(ctx, "command", options.RouteOption{})
	require.NoError(t, err)
	assert.True(t, nilRoute.IsMultiValue(), "nil route should fall back to default multi-value behavior")
}

// LATENCY LATEST (cluster)

func (suite *GlideTestSuite) TestLatencyLatest_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)
	time.Sleep(50 * time.Millisecond)

	val, err := client.LatencyLatest(ctx)
	require.NoError(t, err)
	assert.False(t, val.IsEmpty())
	if val.IsMultiValue() {
		for addr, entries := range val.MultiValue() {
			assert.NotEmpty(t, addr)
			for _, e := range entries {
				assert.NotEmpty(t, e.EventName)
				assert.GreaterOrEqual(t, e.Maximum, e.Latest)
			}
		}
	}
}

func (suite *GlideTestSuite) TestLatencyLatestWithOptions_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)
	time.Sleep(50 * time.Millisecond)

	val, err := client.LatencyLatestWithOptions(ctx, options.RouteOption{Route: config.AllPrimaries})
	require.NoError(t, err)
	require.True(t, val.IsMultiValue(), "AllPrimaries should produce a multi-value response")
}

// LATENCY RESET (cluster)

func (suite *GlideTestSuite) TestLatencyReset_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)
	time.Sleep(50 * time.Millisecond)

	total, err := client.LatencyReset(ctx)
	require.NoError(t, err)
	assert.GreaterOrEqual(t, total, int64(0))

	// History should be empty after reset on every node.
	val, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	if val.IsMultiValue() {
		for _, entries := range val.MultiValue() {
			assert.Empty(t, entries)
		}
	} else {
		assert.Empty(t, val.SingleValue())
	}
}

func (suite *GlideTestSuite) TestLatencyResetWithEvents_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)
	time.Sleep(50 * time.Millisecond)

	total, err := client.LatencyReset(ctx, "command", "fast-command")
	require.NoError(t, err)
	assert.GreaterOrEqual(t, total, int64(0))

	noop, err := client.LatencyResetWithOptions(
		ctx,
		options.RouteOption{Route: config.AllNodes},
		"no-such-event",
	)
	require.NoError(t, err)
	assert.Equal(t, int64(0), noop)
}

func (suite *GlideTestSuite) TestLatencyResetWithOptions_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)
	time.Sleep(50 * time.Millisecond)

	total, err := client.LatencyResetWithOptions(ctx, options.RouteOption{Route: config.AllNodes})
	require.NoError(t, err)
	assert.GreaterOrEqual(t, total, int64(0))
}

// Sanity: a context cancelled before the call is reported back to the caller verbatim,
// rather than swallowed by the latency response handler.
func (suite *GlideTestSuite) TestLatencyHistory_ContextCancelled() {
	client := suite.defaultClient()
	t := suite.T()

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.LatencyHistory(ctx, "command")
	require.Error(t, err)
	assert.Equal(t, context.Canceled.Error(), err.Error())
}
