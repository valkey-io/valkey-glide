// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestSubscriptionSyncTimestampMetricOnSuccess tests that sync timestamp is updated on successful subscription
func (suite *GlideTestSuite) TestSubscriptionSyncTimestampMetricOnSuccess() {
	channel := "sync_timestamp_test"

	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	// Get initial statistics
	initialStats := receiver.(*glide.Client).GetStatistics()
	initialTimestamp := initialStats["subscription_last_sync_timestamp"]

	suite.T().Logf("Initial sync timestamp: %d", initialTimestamp)

	// Verify timestamp is set (non-zero)
	assert.Greater(suite.T(), initialTimestamp, uint64(0),
		"Sync timestamp should be set after successful subscription")

	// Wait a bit and check it's been updated
	time.Sleep(1500 * time.Millisecond)

	updatedStats := receiver.(*glide.Client).GetStatistics()
	updatedTimestamp := updatedStats["subscription_last_sync_timestamp"]

	suite.T().Logf("Updated sync timestamp: %d", updatedTimestamp)

	// Timestamp should be recent (within last few seconds)
	now := uint64(time.Now().UnixMilli())
	assert.Less(suite.T(), now-updatedTimestamp, uint64(5000),
		"Sync timestamp should be recent")
}
