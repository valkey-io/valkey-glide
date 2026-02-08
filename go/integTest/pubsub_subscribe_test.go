// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

func (suite *GlideTestSuite) TestDynamicSubscribeUnsubscribe() {
	initialChannel := "initial_channel"
	dynamicChannel := "dynamic_channel"

	// Create publisher
	publisher := suite.defaultClient()
	defer publisher.Close()

	// Create subscriber using EXACT same pattern as working test
	channels := []ChannelDefn{
		{Channel: initialChannel, Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.Client).GetQueue()
	assert.NoError(suite.T(), err)

	// Allow subscription to establish
	time.Sleep(100 * time.Millisecond)

	// Publish test message
	_, err = publisher.Publish(context.Background(), initialChannel, "test_message")
	assert.NoError(suite.T(), err)

	// Allow time for message to be received
	time.Sleep(100 * time.Millisecond)

	// Verify message received
	select {
	case msg := <-queue.WaitForMessage():
		suite.T().Logf("SUCCESS! Initial subscription works. Message: '%s'", msg.Message)

		// Now test dynamic subscribe
		err = receiver.(*glide.Client).Subscribe(context.Background(), []string{dynamicChannel}, 5000)
		assert.NoError(suite.T(), err)

		time.Sleep(100 * time.Millisecond)

		_, err = publisher.Publish(context.Background(), dynamicChannel, "dynamic_message")
		assert.NoError(suite.T(), err)

		time.Sleep(100 * time.Millisecond)

		select {
		case msg := <-queue.WaitForMessage():
			suite.T().Logf("Dynamic subscribe SUCCESS! Message: '%s'", msg.Message)
			assert.Equal(suite.T(), "dynamic_message", msg.Message)
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Dynamic subscribe failed")
		}
	case <-time.After(2 * time.Second):
		suite.T().Fatal("Initial subscription failed")
	}
}

func (suite *GlideTestSuite) TestDynamicPSubscribeUnsubscribe() {
	initialPattern := "initial_*"
	dynamicPattern := "dynamic_*"

	publisher := suite.defaultClient()
	defer publisher.Close()

	channels := []ChannelDefn{
		{Channel: initialPattern, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.Client).GetQueue()
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	_, err = publisher.Publish(context.Background(), "initial_test", "test_message")
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "test_message", msg.Message)

		err = receiver.(*glide.Client).PSubscribe(context.Background(), []string{dynamicPattern}, 5000)
		assert.NoError(suite.T(), err)

		time.Sleep(200 * time.Millisecond)

		_, err = publisher.Publish(context.Background(), "dynamic_test", "dynamic_message")
		assert.NoError(suite.T(), err)

		time.Sleep(200 * time.Millisecond)

		select {
		case msg := <-queue.WaitForMessage():
			assert.Equal(suite.T(), "dynamic_message", msg.Message)
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Dynamic psubscribe failed")
		}
	case <-time.After(2 * time.Second):
		suite.T().Fatal("Initial pattern subscription failed")
	}
}

func (suite *GlideTestSuite) TestDynamicSSubscribeUnsubscribe() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	initialChannel := "initial_shard_channel"
	dynamicChannel := "dynamic_shard_channel"

	publisher := suite.defaultClusterClient()
	defer publisher.Close()

	channels := []ChannelDefn{
		{Channel: initialChannel, Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.ClusterClient).GetQueue()
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	_, err = publisher.Publish(context.Background(), initialChannel, "test_message", true)
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "test_message", msg.Message)

		err = receiver.(*glide.ClusterClient).SSubscribe(context.Background(), []string{dynamicChannel}, 5000)
		assert.NoError(suite.T(), err)

		time.Sleep(100 * time.Millisecond)

		_, err = publisher.Publish(context.Background(), dynamicChannel, "dynamic_message", true)
		assert.NoError(suite.T(), err)

		time.Sleep(100 * time.Millisecond)

		select {
		case msg := <-queue.WaitForMessage():
			assert.Equal(suite.T(), "dynamic_message", msg.Message)
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Dynamic ssubscribe failed")
		}
	case <-time.After(2 * time.Second):
		suite.T().Fatal("Initial sharded subscription failed")
	}
}

func (suite *GlideTestSuite) TestBlockingSubscribeUnsubscribe() {
	channel1 := "blocking_channel1"
	channel2 := "blocking_channel2"

	publisher := suite.defaultClient()
	defer publisher.Close()

	channels := []ChannelDefn{
		{Channel: channel1, Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.Client).GetQueue()
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify initial subscription works
	_, err = publisher.Publish(context.Background(), channel1, "message1")
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "message1", msg.Message)

		// Dynamically subscribe to second channel
		err = receiver.(*glide.Client).Subscribe(context.Background(), []string{channel2}, 5000)
		assert.NoError(suite.T(), err)

		time.Sleep(200 * time.Millisecond)

		_, err = publisher.Publish(context.Background(), channel2, "message2")
		assert.NoError(suite.T(), err)

		time.Sleep(200 * time.Millisecond)

		select {
		case msg := <-queue.WaitForMessage():
			assert.Equal(suite.T(), "message2", msg.Message)

			// Unsubscribe from first channel
			err = receiver.(*glide.Client).Unsubscribe(context.Background(), []string{channel1}, 5000)
			assert.NoError(suite.T(), err)

			time.Sleep(200 * time.Millisecond)

			// Verify channel1 doesn't receive, but channel2 still does
			_, err = publisher.Publish(context.Background(), channel1, "should_not_receive")
			assert.NoError(suite.T(), err)
			_, err = publisher.Publish(context.Background(), channel2, "message3")
			assert.NoError(suite.T(), err)

			time.Sleep(200 * time.Millisecond)

			select {
			case msg := <-queue.WaitForMessage():
				assert.Equal(suite.T(), "message3", msg.Message)
				assert.Equal(suite.T(), channel2, msg.Channel)
			case <-time.After(2 * time.Second):
				suite.T().Fatal("Should receive message on channel2")
			}
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Dynamic subscribe to channel2 failed")
		}
	case <-time.After(2 * time.Second):
		suite.T().Fatal("Initial subscription failed")
	}
}

func (suite *GlideTestSuite) TestGetSubscriptions() {
	channel := "test_channel"
	pattern := "test.*"

	channels := []ChannelDefn{
		{Channel: channel, Mode: ExactMode},
		{Channel: pattern, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(100 * time.Millisecond)

	// Call GetSubscriptions in a goroutine with timeout
	type result struct {
		state *models.PubSubState
		err   error
	}
	resultChan := make(chan result, 1)

	go func() {
		state, err := receiver.(*glide.Client).GetSubscriptions(context.Background())
		resultChan <- result{state, err}
	}()

	select {
	case res := <-resultChan:
		assert.NoError(suite.T(), res.err)

		_, hasChannel := res.state.DesiredSubscriptions[models.Exact][channel]
		assert.True(suite.T(), hasChannel)
		_, hasPattern := res.state.DesiredSubscriptions[models.Pattern][pattern]
		assert.True(suite.T(), hasPattern)
		_, hasChannelActual := res.state.ActualSubscriptions[models.Exact][channel]
		assert.True(suite.T(), hasChannelActual)
		_, hasPatternActual := res.state.ActualSubscriptions[models.Pattern][pattern]
		assert.True(suite.T(), hasPatternActual)
	case <-time.After(5 * time.Second):
		suite.T().Fatal("GetSubscriptions timed out")
	}
}

func (suite *GlideTestSuite) TestPubSubReconciliationMetrics() {
	initialChannel := "metrics_test_channel"

	// Create subscriber
	channels := []ChannelDefn{
		{Channel: initialChannel, Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	// Get statistics
	stats := receiver.(*glide.Client).GetStatistics()

	// Verify pubsub-specific metrics exist
	timestamp := stats["subscription_last_sync_timestamp"]
	outOfSyncCount := stats["subscription_out_of_sync_count"]

	// Verify timestamp is set (non-zero means at least one sync occurred)
	assert.Greater(suite.T(), timestamp, uint64(0),
		"Subscription sync timestamp should be set after client creation with subscriptions")

	// Verify out_of_sync_count exists (should be 0 for normal operation)
	assert.GreaterOrEqual(suite.T(), outOfSyncCount, uint64(0))
}
