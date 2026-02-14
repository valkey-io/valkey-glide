// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// TestSubscriptionMetricsOnACLFailure tests that out-of-sync metric increments on ACL failure
func (suite *GlideTestSuite) TestSubscriptionMetricsOnACLFailure() {
	ctx := context.Background()
	channel := "acl_metrics_channel"
	username := "test_user_acl_metrics"
	password := "test_password_acl"

	// Test standalone
	adminClient := suite.defaultClient()
	defer adminClient.Close()

	// Create user without pubsub permissions
	aclCmd := []string{"ACL", "SETUSER", username, "ON", ">" + password, "~*", "resetchannels", "+@all", "-@pubsub"}
	_, err := adminClient.CustomCommand(ctx, aclCmd)
	assert.NoError(suite.T(), err)

	defer func() {
		adminClient.CustomCommand(ctx, []string{"ACL", "DELUSER", username})
	}()

	// Create listening client and authenticate
	listeningClient := suite.defaultClient()
	defer listeningClient.Close()

	_, err = listeningClient.CustomCommand(ctx, []string{"AUTH", username, password})
	assert.NoError(suite.T(), err)

	// Get initial metrics
	initialStats := listeningClient.GetStatistics()
	initialOutOfSync := initialStats["subscription_out_of_sync_count"]

	// Try to subscribe (will fail due to ACL)
	err = listeningClient.SubscribeLazy(ctx, []string{channel})
	assert.NoError(suite.T(), err) // Lazy doesn't fail immediately

	// Wait for reconciliation attempts
	outOfSyncCount := initialOutOfSync
	for i := 0; i < 15; i++ {
		time.Sleep(1 * time.Second)
		stats := listeningClient.GetStatistics()
		outOfSyncCount = stats["subscription_out_of_sync_count"]
		suite.T().Logf("[%ds] out_of_sync=%d, initial=%d", i+1, outOfSyncCount, initialOutOfSync)
		if outOfSyncCount > initialOutOfSync {
			break
		}
	}

	assert.Greater(suite.T(), outOfSyncCount, initialOutOfSync, "Out-of-sync count should increase")

	// Verify subscription NOT in actual (ACL blocked)
	state, err := listeningClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, inDesired := state.DesiredSubscriptions[models.Exact][channel]
	_, inActual := state.ActualSubscriptions[models.Exact][channel]
	assert.True(suite.T(), inDesired, "Should be in desired")
	assert.False(suite.T(), inActual, "Should NOT be in actual (ACL blocked)")

	// Test cluster
	clusterAdmin := suite.defaultClusterClient()
	defer clusterAdmin.Close()

	_, err = clusterAdmin.CustomCommandWithRoute(ctx, aclCmd, config.AllNodes)
	assert.NoError(suite.T(), err)

	defer func() {
		clusterAdmin.CustomCommandWithRoute(ctx, []string{"ACL", "DELUSER", username}, config.AllNodes)
	}()

	clusterListening := suite.defaultClusterClient()
	defer clusterListening.Close()

	_, err = clusterListening.CustomCommandWithRoute(ctx, []string{"AUTH", username, password}, config.AllNodes)
	assert.NoError(suite.T(), err)

	initialStats = clusterListening.GetStatistics()
	initialOutOfSync = initialStats["subscription_out_of_sync_count"]

	err = clusterListening.SubscribeLazy(ctx, []string{channel})
	assert.NoError(suite.T(), err)

	outOfSyncCount = initialOutOfSync
	for i := 0; i < 15; i++ {
		time.Sleep(1 * time.Second)
		stats := clusterListening.GetStatistics()
		outOfSyncCount = stats["subscription_out_of_sync_count"]
		if outOfSyncCount > initialOutOfSync {
			break
		}
	}

	assert.Greater(suite.T(), outOfSyncCount, initialOutOfSync)
}

// TestSubscriptionMetricsRepeatedFailures tests metric increments on repeated failures
func (suite *GlideTestSuite) TestSubscriptionMetricsRepeatedFailures() {
	ctx := context.Background()
	channel1 := "channel1_repeated"
	channel2 := "channel2_repeated"
	username := "test_user_repeated"
	password := "test_password_repeated"

	// Test standalone
	adminClient := suite.defaultClient()
	defer adminClient.Close()

	aclCmd := []string{"ACL", "SETUSER", username, "ON", ">" + password, "~*", "resetchannels", "+@all", "-@pubsub"}
	_, err := adminClient.CustomCommand(ctx, aclCmd)
	assert.NoError(suite.T(), err)

	defer func() {
		adminClient.CustomCommand(ctx, []string{"ACL", "DELUSER", username})
	}()

	listeningClient := suite.defaultClient()
	defer listeningClient.Close()

	_, err = listeningClient.CustomCommand(ctx, []string{"AUTH", username, password})
	assert.NoError(suite.T(), err)

	initialStats := listeningClient.GetStatistics()
	initialOutOfSync := initialStats["subscription_out_of_sync_count"]

	// Subscribe to multiple channels (all will fail)
	err = listeningClient.SubscribeLazy(ctx, []string{channel1, channel2})
	assert.NoError(suite.T(), err)

	// Wait for reconciliation attempts
	outOfSyncCount := initialOutOfSync
	for i := 0; i < 15; i++ {
		time.Sleep(1 * time.Second)
		stats := listeningClient.GetStatistics()
		outOfSyncCount = stats["subscription_out_of_sync_count"]
		suite.T().Logf("[%ds] out_of_sync=%d", i+1, outOfSyncCount)
		if outOfSyncCount > initialOutOfSync {
			break
		}
	}

	// Metric should increment (failures are batched)
	assert.Greater(suite.T(), outOfSyncCount, initialOutOfSync)

	// Verify neither subscription is active
	state, err := listeningClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, desired1 := state.DesiredSubscriptions[models.Exact][channel1]
	_, desired2 := state.DesiredSubscriptions[models.Exact][channel2]
	_, actual1 := state.ActualSubscriptions[models.Exact][channel1]
	_, actual2 := state.ActualSubscriptions[models.Exact][channel2]

	assert.True(suite.T(), desired1 && desired2, "Both should be in desired")
	assert.False(suite.T(), actual1 || actual2, "Neither should be in actual")

	// Test cluster
	clusterAdmin := suite.defaultClusterClient()
	defer clusterAdmin.Close()

	_, err = clusterAdmin.CustomCommandWithRoute(ctx, aclCmd, config.AllNodes)
	assert.NoError(suite.T(), err)

	defer func() {
		clusterAdmin.CustomCommandWithRoute(ctx, []string{"ACL", "DELUSER", username}, config.AllNodes)
	}()

	clusterListening := suite.defaultClusterClient()
	defer clusterListening.Close()

	_, err = clusterListening.CustomCommandWithRoute(ctx, []string{"AUTH", username, password}, config.AllNodes)
	assert.NoError(suite.T(), err)

	initialStats = clusterListening.GetStatistics()
	initialOutOfSync = initialStats["subscription_out_of_sync_count"]

	err = clusterListening.SubscribeLazy(ctx, []string{channel1, channel2})
	assert.NoError(suite.T(), err)

	outOfSyncCount = initialOutOfSync
	for i := 0; i < 15; i++ {
		time.Sleep(1 * time.Second)
		stats := clusterListening.GetStatistics()
		outOfSyncCount = stats["subscription_out_of_sync_count"]
		if outOfSyncCount > initialOutOfSync {
			break
		}
	}

	assert.Greater(suite.T(), outOfSyncCount, initialOutOfSync)
}
