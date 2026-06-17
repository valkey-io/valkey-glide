// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// debugSleepArgs is the command used to trigger a latency spike for the "command" event.
var debugSleepArgs = []string{"DEBUG", "SLEEP", "0.05"}

// triggerLatencySpikeStandalone triggers a latency spike for the "command" event.
//
// Resets any existing latency data first so the spike is recorded against a clean baseline,
// then enables the server-side latency monitor, triggers a latency spike for the "command"
// event, and finally registers a cleanup that restores the original threshold.
func (suite *GlideTestSuite) triggerLatencySpikeStandalone(ctx context.Context) {
	t := suite.T()
	client := suite.defaultClient()

	_, err := client.LatencyReset(ctx)
	require.NoError(t, err)

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

	_, err = client.CustomCommand(ctx, debugSleepArgs)
	if err != nil {
		t.Logf("DEBUG SLEEP unavailable, latency series may be empty: %v", err)
	}
}

// triggerLatencySpikeCluster triggers a latency spike for the "command" event on all cluster nodes.
//
// Resets any existing latency data first so the spike is recorded against a clean baseline,
// then enables the server-side latency monitor, triggers a latency spike for the "command"
// event, and finally registers a cleanup that restores the original threshold.
func (suite *GlideTestSuite) triggerLatencySpikeCluster(ctx context.Context) {
	t := suite.T()
	client := suite.defaultClusterClient()

	_, err := client.LatencyReset(ctx)
	require.NoError(t, err)

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

	_, err = client.CustomCommand(ctx, debugSleepArgs)
	if err != nil {
		t.Logf("DEBUG SLEEP unavailable, latency series may be empty: %v", err)
	}
}

func (suite *GlideTestSuite) TestLatencyHistory() {
	client := suite.defaultClient()
	t := suite.T()
	ctx := context.Background()

	beforeSpike := time.Now().Unix()
	suite.triggerLatencySpikeStandalone(ctx)

	entries, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	require.NotEmpty(t, entries, "expected at least one 'command' latency entry")
	for _, e := range entries {
		assert.GreaterOrEqual(t, e.Time.Unix(), beforeSpike)
		assert.Greater(t, e.Latency, time.Duration(0))
	}

	// An unknown event must not error – the server simply returns an empty array.
	unknown, err := client.LatencyHistory(ctx, "no-such-event")
	require.NoError(t, err)
	assert.Empty(t, unknown)
}

func (suite *GlideTestSuite) TestLatencyLatest() {
	client := suite.defaultClient()
	t := suite.T()
	ctx := context.Background()

	beforeSpike := time.Now().Unix()
	suite.triggerLatencySpikeStandalone(ctx)

	entries, err := client.LatencyLatest(ctx)
	require.NoError(t, err)

	// Find the latency event info for "command" event.
	var commandInfo *models.LatencyEventInfo
	for i := range entries {
		if entries[i].EventName == "command" {
			commandInfo = &entries[i]
			break
		}
	}
	require.NotNil(t, commandInfo, "expected a 'command' event in LATENCY LATEST")

	assert.GreaterOrEqual(t, commandInfo.Time.Unix(), beforeSpike)
	assert.Greater(t, commandInfo.Latest, time.Duration(0))
	assert.GreaterOrEqual(t, commandInfo.Maximum, commandInfo.Latest)

	// Only Valkey 8.1+ populates Sum and Count.
	if suite.serverVersion >= "8.1.0" {
		assert.False(t, commandInfo.Sum.IsNil(), "Sum should be populated for Valkey 8.1+")
		assert.False(t, commandInfo.Count.IsNil(), "Count should be populated for Valkey 8.1+")
		assert.Greater(t, commandInfo.Sum.Value(), time.Duration(0))
		assert.Greater(t, commandInfo.Count.Value(), int64(0))
	} else {
		assert.True(t, commandInfo.Sum.IsNil(), "Sum should be nil before Valkey 8.1")
		assert.True(t, commandInfo.Count.IsNil(), "Count should be nil before Valkey 8.1")
	}
}

func (suite *GlideTestSuite) TestLatencyReset() {
	client := suite.defaultClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeStandalone(ctx)

	resetCount, err := client.LatencyReset(ctx)
	require.NoError(t, err)
	assert.Greater(t, resetCount, int64(0))

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

	resetCount, err := client.LatencyReset(ctx, "command")
	require.NoError(t, err)
	assert.Greater(t, resetCount, int64(0))

	hist, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	assert.Empty(t, hist)

	suite.triggerLatencySpikeStandalone(ctx)

	unknownReset, err := client.LatencyReset(ctx, "no-such-event")
	require.NoError(t, err)
	assert.Equal(t, int64(0), unknownReset)

	// "command" data should still persist after unknown reset.
	hist, err = client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	assert.NotEmpty(t, hist)
}

func (suite *GlideTestSuite) TestLatencyHistory_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	beforeSpike := time.Now().Unix()
	suite.triggerLatencySpikeCluster(ctx)

	val, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	require.False(t, val.IsEmpty())

	total := 0
	checkEntries := func(entries []models.LatencyEntry) {
		for _, e := range entries {
			assert.GreaterOrEqual(t, e.Time.Unix(), beforeSpike)
			assert.Greater(t, e.Latency, time.Duration(0))
			total++
		}
	}

	if val.IsMultiValue() {
		nodes := val.MultiValue()
		assert.NotEmpty(t, nodes)
		for addr, entries := range nodes {
			assert.NotEmpty(t, addr)
			checkEntries(entries)
		}
	} else {
		checkEntries(val.SingleValue())
	}
	assert.Greater(t, total, 0)

	// Non-existent event returns empty across all nodes.
	unknown, err := client.LatencyHistory(ctx, "no-such-event")
	require.NoError(t, err)
	if unknown.IsMultiValue() {
		for _, entries := range unknown.MultiValue() {
			assert.Empty(t, entries)
		}
	} else {
		assert.Empty(t, unknown.SingleValue())
	}
}

func (suite *GlideTestSuite) TestLatencyHistoryWithOptions_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)

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

func (suite *GlideTestSuite) TestLatencyLatest_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	beforeSpike := time.Now().Unix()
	suite.triggerLatencySpikeCluster(ctx)

	val, err := client.LatencyLatest(ctx)
	require.NoError(t, err)
	require.False(t, val.IsEmpty())

	// Find the latency event info for "command" event.
	var commandInfo *models.LatencyEventInfo
	if val.IsMultiValue() {
		for _, entries := range val.MultiValue() {
			for i := range entries {
				if entries[i].EventName == "command" {
					commandInfo = &entries[i]
					break
				}
			}
			if commandInfo != nil {
				break
			}
		}
	} else {
		entries := val.SingleValue()
		for i := range entries {
			if entries[i].EventName == "command" {
				commandInfo = &entries[i]
				break
			}
		}
	}
	require.NotNil(t, commandInfo)

	assert.GreaterOrEqual(t, commandInfo.Time.Unix(), beforeSpike)
	assert.Greater(t, commandInfo.Latest, time.Duration(0))
	assert.GreaterOrEqual(t, commandInfo.Maximum, commandInfo.Latest)

	// Only Valkey 8.1+ populates Sum and Count.
	if suite.serverVersion >= "8.1.0" {
		assert.False(t, commandInfo.Sum.IsNil())
		assert.False(t, commandInfo.Count.IsNil())
		assert.Greater(t, commandInfo.Sum.Value(), time.Duration(0))
		assert.Greater(t, commandInfo.Count.Value(), int64(0))
	} else {
		assert.True(t, commandInfo.Sum.IsNil())
		assert.True(t, commandInfo.Count.IsNil())
	}
}

func (suite *GlideTestSuite) TestLatencyLatestWithOptions_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)

	val, err := client.LatencyLatestWithOptions(ctx, options.RouteOption{Route: config.AllPrimaries})
	require.NoError(t, err)
	require.True(t, val.IsMultiValue(), "AllPrimaries should produce a multi-value response")
}

func (suite *GlideTestSuite) TestLatencyReset_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)

	total, err := client.LatencyReset(ctx)
	require.NoError(t, err)
	assert.Greater(t, total, int64(0))

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

	total, err := client.LatencyReset(ctx, "command")
	require.NoError(t, err)
	assert.Greater(t, total, int64(0))

	// History should be empty after reset.
	val, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	if val.IsMultiValue() {
		for _, entries := range val.MultiValue() {
			assert.Empty(t, entries)
		}
	} else {
		assert.Empty(t, val.SingleValue())
	}

	// Unknown event reset is a no-op.
	suite.triggerLatencySpikeCluster(ctx)

	noop, err := client.LatencyReset(ctx, "no-such-event")
	require.NoError(t, err)
	assert.Equal(t, int64(0), noop)

	// "command" data should still persist after unknown reset.
	hist, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	require.False(t, hist.IsEmpty())
}

func (suite *GlideTestSuite) TestLatencyResetWithOptions_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)

	total, err := client.LatencyResetWithOptions(ctx, options.RouteOption{Route: config.AllNodes})
	require.NoError(t, err)
	assert.Greater(t, total, int64(0))
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
