// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestSubscriptionSyncTimestampMetricOnSuccess tests that sync timestamp is updated on successful subscription
func (suite *GlideTestSuite) TestSubscriptionSyncTimestampMetricOnSuccess() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel := "sync_timestamp_test"

			channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			// Get initial statistics
			var initialStats map[string]uint64
			if clientType == StandaloneClient {
				initialStats = receiver.(*glide.Client).GetStatistics()
			} else {
				initialStats = receiver.(*glide.ClusterClient).GetStatistics()
			}
			initialTimestamp := int64(initialStats["subscription_last_sync_timestamp"])

			t.Logf("Initial sync timestamp: %d", initialTimestamp)

			// Verify timestamp is set (non-zero)
			assert.Greater(t, initialTimestamp, int64(0),
				"Sync timestamp should be set after successful subscription")

			getSyncTimestamp := func() int64 {
				var stats map[string]uint64
				if clientType == StandaloneClient {
					stats = receiver.(*glide.Client).GetStatistics()
				} else {
					stats = receiver.(*glide.ClusterClient).GetStatistics()
				}
				return int64(stats["subscription_last_sync_timestamp"])
			}

			// The sync timestamp is refreshed by the background reconciliation loop using the
			// server clock, while "now" is read from the local clock. Poll until the reported
			// timestamp is recent instead of sleeping a fixed amount and asserting once: a single
			// fixed sleep can race the reconciliation cadence, and small clock skew between the
			// two clocks can make the timestamp appear to be slightly in the future. Using signed
			// arithmetic avoids the unsigned-underflow wraparound that made the previous
			// comparison flaky.
			//
			// recencyWindowMs: how old the last sync is allowed to be.
			// skewToleranceMs: how far the timestamp is allowed to appear ahead of the local clock.
			const (
				recencyWindowMs = int64(5000)
				skewToleranceMs = int64(5000)
			)

			var updatedTimestamp int64
			require.Eventually(t, func() bool {
				updatedTimestamp = getSyncTimestamp()
				if updatedTimestamp <= 0 {
					return false
				}
				age := time.Now().UnixMilli() - updatedTimestamp
				// age < 0  => timestamp is ahead of the local clock (clock skew)
				// age >= 0 => timestamp is in the past; must be within the recency window
				return age >= -skewToleranceMs && age < recencyWindowMs
			}, 15*time.Second, 100*time.Millisecond,
				"Sync timestamp should be refreshed and recent")

			t.Logf("Updated sync timestamp: %d", updatedTimestamp)
		})
	}
}
