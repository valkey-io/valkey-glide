// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
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
			initialTimestamp := initialStats["subscription_last_sync_timestamp"]

			t.Logf("Initial sync timestamp: %d", initialTimestamp)

			// Verify timestamp is set (non-zero)
			assert.Greater(t, initialTimestamp, uint64(0),
				"Sync timestamp should be set after successful subscription")

			// Wait a bit and check it's been updated
			time.Sleep(1500 * time.Millisecond)

			var updatedStats map[string]uint64
			if clientType == StandaloneClient {
				updatedStats = receiver.(*glide.Client).GetStatistics()
			} else {
				updatedStats = receiver.(*glide.ClusterClient).GetStatistics()
			}
			updatedTimestamp := updatedStats["subscription_last_sync_timestamp"]

			t.Logf("Updated sync timestamp: %d", updatedTimestamp)

			// Timestamp should be recent (within last few seconds)
			now := uint64(time.Now().UnixMilli())
			assert.Less(t, now-updatedTimestamp, uint64(5000),
				"Sync timestamp should be recent")
		})
	}
}
