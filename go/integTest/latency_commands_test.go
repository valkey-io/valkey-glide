// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strconv"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// debugSleepArgs is the command used to trigger a latency spike for the "command" event.
var debugSleepArgs = []string{"DEBUG", "SLEEP", "0.05"}

// getUnixSeconds returns the current server time as a Unix timestamp in seconds.
func getUnixSeconds(ctx context.Context, client any) (int64, error) {
	var result []string
	var err error

	// TODO #6166: Use a base client method to call Time() directly.
	if c, ok := client.(*glide.Client); ok {
		result, err = c.Time(ctx)
	} else if c, ok := client.(*glide.ClusterClient); ok {
		result, err = c.Time(ctx)
	}

	if err != nil {
		return 0, err
	}
	return strconv.ParseInt(result[0], 10, 64)
}

// flattenLatencyEntries flattens a ClusterValue of LatencyEntry slices.
func flattenLatencyEntries(val models.ClusterValue[[]models.LatencyEntry]) []models.LatencyEntry {
	if val.IsSingleValue() {
		return val.SingleValue()
	}
	var all []models.LatencyEntry
	for _, entries := range val.MultiValue() {
		all = append(all, entries...)
	}
	return all
}

// flattenLatencyEventInfos flattens a ClusterValue of LatencyEventInfo slices.
func flattenLatencyEventInfos(val models.ClusterValue[[]models.LatencyEventInfo]) []models.LatencyEventInfo {
	if val.IsSingleValue() {
		return val.SingleValue()
	}
	var all []models.LatencyEventInfo
	for _, entries := range val.MultiValue() {
		all = append(all, entries...)
	}
	return all
}

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

	beforeSpike, err := getUnixSeconds(ctx, client)
	require.NoError(t, err)
	suite.triggerLatencySpikeStandalone(ctx)

	entries, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	require.NotEmpty(t, entries)
	for _, e := range entries {
		assert.GreaterOrEqual(t, e.Time.Unix(), beforeSpike)
		assert.Greater(t, e.Duration, time.Duration(0))
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

	beforeSpike, err := getUnixSeconds(ctx, client)
	require.NoError(t, err)
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
	require.NotNil(t, commandInfo)

	assert.GreaterOrEqual(t, commandInfo.LatestTime.Unix(), beforeSpike)
	assert.Greater(t, commandInfo.LatestDuration, time.Duration(0))
	assert.GreaterOrEqual(t, commandInfo.MaxDuration, commandInfo.LatestDuration)

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

	beforeSpike, err := getUnixSeconds(ctx, client)
	require.NoError(t, err)
	suite.triggerLatencySpikeCluster(ctx)

	val, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	require.False(t, val.IsEmpty())

	allEntries := flattenLatencyEntries(val)
	assert.NotEmpty(t, allEntries)
	for _, e := range allEntries {
		assert.GreaterOrEqual(t, e.Time.Unix(), beforeSpike)
		assert.Greater(t, e.Duration, time.Duration(0))
	}

	// Non-existent event returns empty across all nodes.
	unknown, err := client.LatencyHistory(ctx, "no-such-event")
	require.NoError(t, err)
	assert.Empty(t, flattenLatencyEntries(unknown))
}

func (suite *GlideTestSuite) TestLatencyHistoryWithOptions_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	suite.triggerLatencySpikeCluster(ctx)

	// Multi-node (all nodes)
	val, err := client.LatencyHistoryWithOptions(ctx, "command", options.RouteOption{Route: config.AllNodes})
	require.NoError(t, err)
	require.True(t, val.IsMultiValue())
	for addr := range val.MultiValue() {
		assert.NotEmpty(t, addr)
	}

	// Single-node (primary node)
	single, err := client.LatencyHistoryWithOptions(ctx, "command", primarySlotRouteOption)
	require.NoError(t, err)
	assert.True(t, single.IsSingleValue())
	for _, e := range single.SingleValue() {
		assert.False(t, e.Time.IsZero())
		assert.GreaterOrEqual(t, e.Duration, time.Duration(0))
	}

	// Nil route should match the no-options method (default routing → multi-value).
	nilRoute, err := client.LatencyHistoryWithOptions(ctx, "command", options.RouteOption{})
	require.NoError(t, err)
	assert.True(t, nilRoute.IsMultiValue())
}

func (suite *GlideTestSuite) TestLatencyLatest_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	ctx := context.Background()

	beforeSpike, err := getUnixSeconds(ctx, client)
	require.NoError(t, err)
	suite.triggerLatencySpikeCluster(ctx)

	val, err := client.LatencyLatest(ctx)
	require.NoError(t, err)
	require.False(t, val.IsEmpty())

	// Find the latency event info for "command" event across all nodes.
	allInfos := flattenLatencyEventInfos(val)
	var commandInfo *models.LatencyEventInfo
	for i := range allInfos {
		if allInfos[i].EventName == "command" {
			commandInfo = &allInfos[i]
			break
		}
	}
	require.NotNil(t, commandInfo)

	assert.GreaterOrEqual(t, commandInfo.LatestTime.Unix(), beforeSpike)
	assert.Greater(t, commandInfo.LatestDuration, time.Duration(0))
	assert.GreaterOrEqual(t, commandInfo.MaxDuration, commandInfo.LatestDuration)

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

	// Multi-node route (all primaries)
	val, err := client.LatencyLatestWithOptions(ctx, options.RouteOption{Route: config.AllPrimaries})
	require.NoError(t, err)
	require.True(t, val.IsMultiValue())

	// Single-node route (primary node)
	single, err := client.LatencyLatestWithOptions(ctx, primarySlotRouteOption)
	require.NoError(t, err)
	assert.True(t, single.IsSingleValue())
	assert.NotEmpty(t, single.SingleValue())
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
	assert.Empty(t, flattenLatencyEntries(val))
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
	assert.Empty(t, flattenLatencyEntries(val))

	// Unknown event reset is a no-op.
	suite.triggerLatencySpikeCluster(ctx)

	noop, err := client.LatencyReset(ctx, "no-such-event")
	require.NoError(t, err)
	assert.Equal(t, int64(0), noop)

	// "command" data should still persist after unknown reset.
	hist, err := client.LatencyHistory(ctx, "command")
	require.NoError(t, err)
	assert.NotEmpty(t, flattenLatencyEntries(hist))
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
