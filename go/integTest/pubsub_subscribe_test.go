// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

func (suite *GlideTestSuite) TestDynamicSubscribeUnsubscribe() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			tests := []struct {
				name   string
				method SubscriptionMethod
			}{
				{"Blocking", BlockingMethod},
				{"Lazy", LazyMethod},
			}

			for _, tt := range tests {
				t.Run(tt.name, func(t *testing.T) {
					initialChannel := "initial_channel"
					dynamicChannel := "dynamic_channel"

					publisher := suite.createAnyClient(clientType, nil)
					defer publisher.Close()

					// Create subscriber
					channels := []ChannelDefn{
						{Channel: initialChannel, Mode: ExactMode},
					}
					receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
					defer receiver.Close()

					queue, err := receiver.(PubSubQueuer).GetQueue()
					assert.NoError(t, err)

					// Allow subscription to establish
					time.Sleep(100 * time.Millisecond)

					// Publish test message
					ctx := context.Background()
					err = suite.PublishMessage(publisher, clientType, initialChannel, "test_message", false)
					assert.NoError(t, err)

					// Allow time for message to be received
					time.Sleep(100 * time.Millisecond)

					// Verify message received
					select {
					case msg := <-queue.WaitForMessage():
						t.Logf("SUCCESS! Initial subscription works. Message: '%s'", msg.Message)

						// Now test dynamic subscribe using parameterized method
						if clientType == StandaloneClient {
							client := receiver.(*glide.Client)
							if tt.method == LazyMethod {
								err = client.SubscribeLazy(ctx, []string{dynamicChannel})
								assert.NoError(t, err)
								time.Sleep(200 * time.Millisecond) // Wait for lazy subscription
							} else {
								err = client.Subscribe(ctx, []string{dynamicChannel}, 5000)
								assert.NoError(t, err)
							}
						} else {
							client := receiver.(*glide.ClusterClient)
							if tt.method == LazyMethod {
								err = client.SubscribeLazy(ctx, []string{dynamicChannel})
								assert.NoError(t, err)
								time.Sleep(200 * time.Millisecond) // Wait for lazy subscription
							} else {
								err = client.Subscribe(ctx, []string{dynamicChannel}, 5000)
								assert.NoError(t, err)
							}
						}

						err = suite.PublishMessage(publisher, clientType, dynamicChannel, "dynamic_message", false)
						assert.NoError(t, err)

						time.Sleep(100 * time.Millisecond)

						select {
						case msg := <-queue.WaitForMessage():
							t.Logf("Dynamic subscribe SUCCESS! Message: '%s'", msg.Message)
							assert.Equal(t, "dynamic_message", msg.Message)
						case <-time.After(2 * time.Second):
							t.Fatal("Dynamic subscribe failed")
						}
					case <-time.After(2 * time.Second):
						t.Fatal("Initial subscription failed")
					}
				})
			}
		})
	}
}

func (suite *GlideTestSuite) TestDynamicPSubscribeUnsubscribe() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			tests := []struct {
				name   string
				method SubscriptionMethod
			}{
				{"Blocking", BlockingMethod},
				{"Lazy", LazyMethod},
			}

			for _, tt := range tests {
				t.Run(tt.name, func(t *testing.T) {
					initialPattern := "initial_*"
					dynamicPattern := "dynamic_*"

					publisher := suite.createAnyClient(clientType, nil)
					defer publisher.Close()

					channels := []ChannelDefn{
						{Channel: initialPattern, Mode: PatternMode},
					}
					receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
					defer receiver.Close()

					queue, err := receiver.(PubSubQueuer).GetQueue()
					assert.NoError(t, err)

					time.Sleep(200 * time.Millisecond)

					ctx := context.Background()
					err = suite.PublishMessage(publisher, clientType, "initial_test", "test_message", false)
					assert.NoError(t, err)

					time.Sleep(200 * time.Millisecond)

					select {
					case msg := <-queue.WaitForMessage():
						assert.Equal(t, "test_message", msg.Message)

						if clientType == StandaloneClient {
							client := receiver.(*glide.Client)
							if tt.method == LazyMethod {
								err = client.PSubscribeLazy(ctx, []string{dynamicPattern})
								assert.NoError(t, err)
								time.Sleep(200 * time.Millisecond) // Wait for lazy subscription
							} else {
								err = client.PSubscribe(ctx, []string{dynamicPattern}, 5000)
								assert.NoError(t, err)
							}
						} else {
							client := receiver.(*glide.ClusterClient)
							if tt.method == LazyMethod {
								err = client.PSubscribeLazy(ctx, []string{dynamicPattern})
								assert.NoError(t, err)
								time.Sleep(200 * time.Millisecond) // Wait for lazy subscription
							} else {
								err = client.PSubscribe(ctx, []string{dynamicPattern}, 5000)
								assert.NoError(t, err)
							}
						}

						err = suite.PublishMessage(publisher, clientType, "dynamic_test", "dynamic_message", false)
						assert.NoError(t, err)

						time.Sleep(200 * time.Millisecond)

						select {
						case msg := <-queue.WaitForMessage():
							assert.Equal(t, "dynamic_message", msg.Message)
						case <-time.After(2 * time.Second):
							t.Fatal("Dynamic psubscribe failed")
						}
					case <-time.After(2 * time.Second):
						t.Fatal("Initial pattern subscription failed")
					}
				})
			}
		})
	}
}

func (suite *GlideTestSuite) TestDynamicSSubscribeUnsubscribe() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	tests := []struct {
		name   string
		method SubscriptionMethod
	}{
		{"Blocking", BlockingMethod},
		{"Lazy", LazyMethod},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			initialChannel := "initial_shard_channel"
			dynamicChannel := "dynamic_shard_channel"

			publisher := suite.defaultClusterClient()
			defer publisher.Close()

			channels := []ChannelDefn{
				{Channel: initialChannel, Mode: ShardedMode},
			}
			receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			queue, err := receiver.(*glide.ClusterClient).GetQueue()
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			_, err = publisher.Publish(context.Background(), initialChannel, "test_message", true)
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			select {
			case msg := <-queue.WaitForMessage():
				assert.Equal(t, "test_message", msg.Message)

				ctx := context.Background()
				if tt.method == LazyMethod {
					err = receiver.(*glide.ClusterClient).SSubscribeLazy(ctx, []string{dynamicChannel})
					assert.NoError(t, err)
					time.Sleep(200 * time.Millisecond) // Wait for lazy subscription
				} else {
					err = receiver.(*glide.ClusterClient).SSubscribe(ctx, []string{dynamicChannel}, 5000)
					assert.NoError(t, err)
					// No sleep needed for blocking
				}

				_, err = publisher.Publish(context.Background(), dynamicChannel, "dynamic_message", true)
				assert.NoError(t, err)

				time.Sleep(100 * time.Millisecond)

				select {
				case msg := <-queue.WaitForMessage():
					assert.Equal(t, "dynamic_message", msg.Message)
				case <-time.After(2 * time.Second):
					t.Fatal("Dynamic ssubscribe failed")
				}
			case <-time.After(2 * time.Second):
				t.Fatal("Initial sharded subscription failed")
			}
		})
	}
}

func (suite *GlideTestSuite) TestBlockingSubscribeUnsubscribe() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel1 := "blocking_channel1"
			channel2 := "blocking_channel2"

			publisher := suite.createAnyClient(clientType, nil)
			defer publisher.Close()

			channels := []ChannelDefn{
				{Channel: channel1, Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			queue, err := receiver.(PubSubQueuer).GetQueue()
			assert.NoError(t, err)

			time.Sleep(200 * time.Millisecond)

			// Verify initial subscription works
			ctx := context.Background()
			err = suite.PublishMessage(publisher, clientType, channel1, "message1", false)
			assert.NoError(t, err)

			time.Sleep(200 * time.Millisecond)

			select {
			case msg := <-queue.WaitForMessage():
				assert.Equal(t, "message1", msg.Message)

				// Dynamically subscribe to second channel
				if clientType == StandaloneClient {
					err = receiver.(*glide.Client).Subscribe(ctx, []string{channel2}, 5000)
				} else {
					err = receiver.(*glide.ClusterClient).Subscribe(ctx, []string{channel2}, 5000)
				}
				assert.NoError(t, err)

				time.Sleep(200 * time.Millisecond)

				err = suite.PublishMessage(publisher, clientType, channel2, "message2", false)
				assert.NoError(t, err)

				time.Sleep(200 * time.Millisecond)

				select {
				case msg := <-queue.WaitForMessage():
					assert.Equal(t, "message2", msg.Message)

					// Unsubscribe from first channel
					if clientType == StandaloneClient {
						err = receiver.(*glide.Client).Unsubscribe(ctx, []string{channel1}, 5000)
					} else {
						err = receiver.(*glide.ClusterClient).Unsubscribe(ctx, []string{channel1}, 5000)
					}
					assert.NoError(t, err)

					time.Sleep(200 * time.Millisecond)

					// Verify channel1 doesn't receive, but channel2 still does
					err = suite.PublishMessage(publisher, clientType, channel1, "should_not_receive", false)
					assert.NoError(t, err)
					err = suite.PublishMessage(publisher, clientType, channel2, "message3", false)
					assert.NoError(t, err)

					time.Sleep(200 * time.Millisecond)

					select {
					case msg := <-queue.WaitForMessage():
						assert.Equal(t, "message3", msg.Message)
						assert.Equal(t, channel2, msg.Channel)
					case <-time.After(2 * time.Second):
						t.Fatal("Should receive message on channel2")
					}
				case <-time.After(2 * time.Second):
					t.Fatal("Dynamic subscribe to channel2 failed")
				}
			case <-time.After(2 * time.Second):
				t.Fatal("Initial subscription failed")
			}
		})
	}
}

// Verifies that a client created without any subscription config can subscribe
// dynamically and receive messages via polling.
func (suite *GlideTestSuite) TestDynamicSubscribeWithoutConfig() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		channel := "no_config_channel"

		clientType := StandaloneClient
		if _, ok := client.(*glide.ClusterClient); ok {
			clientType = ClusterClient
		}

		var standaloneClient *glide.Client
		var clusterClient *glide.ClusterClient
		if c, ok := client.(*glide.Client); ok {
			standaloneClient = c
		} else if c, ok := client.(*glide.ClusterClient); ok {
			clusterClient = c
		}
		suite.subscribeByMethod(
			standaloneClient,
			clusterClient,
			[]ChannelDefn{{Channel: channel, Mode: ExactMode}},
			BlockingMethod,
			suite.T(),
		)

		publisher := suite.createAnyClient(clientType, nil)
		defer publisher.Close()

		err := suite.PublishMessage(publisher, clientType, channel, "no_config_msg", false)
		assert.NoError(suite.T(), err)

		time.Sleep(200 * time.Millisecond)

		queue, err := client.(PubSubQueuer).GetQueue()
		assert.NoError(suite.T(), err)

		select {
		case msg := <-queue.WaitForMessage():
			assert.Equal(suite.T(), "no_config_msg", msg.Message)
			assert.Equal(suite.T(), channel, msg.Channel)
		case <-time.After(5 * time.Second):
			suite.T().Fatal("Message not received on client created without subscription config")
		}
	})
}

func (suite *GlideTestSuite) TestGetSubscriptions() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel := "test_channel"
			pattern := "test.*"

			channels := []ChannelDefn{
				{Channel: channel, Mode: ExactMode},
				{Channel: pattern, Mode: PatternMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			time.Sleep(100 * time.Millisecond)

			// Call GetSubscriptions in a goroutine with timeout
			type result struct {
				state *models.PubSubState
				err   error
			}
			resultChan := make(chan result, 1)

			go func() {
				var state *models.PubSubState
				var err error
				if clientType == StandaloneClient {
					state, err = receiver.(*glide.Client).GetSubscriptions(context.Background())
				} else {
					state, err = receiver.(*glide.ClusterClient).GetSubscriptions(context.Background())
				}
				resultChan <- result{state, err}
			}()

			select {
			case res := <-resultChan:
				assert.NoError(t, res.err)

				_, hasChannel := res.state.DesiredSubscriptions[models.Exact][channel]
				assert.True(t, hasChannel)
				_, hasPattern := res.state.DesiredSubscriptions[models.Pattern][pattern]
				assert.True(t, hasPattern)
				_, hasChannelActual := res.state.ActualSubscriptions[models.Exact][channel]
				assert.True(t, hasChannelActual)
				_, hasPatternActual := res.state.ActualSubscriptions[models.Pattern][pattern]
				assert.True(t, hasPatternActual)
			case <-time.After(5 * time.Second):
				t.Fatal("GetSubscriptions timed out")
			}
		})
	}
}

func (suite *GlideTestSuite) TestPubSubReconciliationMetrics() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			initialChannel := "metrics_test_channel"

			// Create subscriber
			channels := []ChannelDefn{
				{Channel: initialChannel, Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			// Get statistics
			var stats map[string]uint64
			if clientType == StandaloneClient {
				stats = receiver.(*glide.Client).GetStatistics()
			} else {
				stats = receiver.(*glide.ClusterClient).GetStatistics()
			}

			// Verify pubsub-specific metrics exist
			timestamp := stats["subscription_last_sync_timestamp"]
			outOfSyncCount := stats["subscription_out_of_sync_count"]

			// Verify timestamp is set (non-zero means at least one sync occurred)
			assert.Greater(t, timestamp, uint64(0),
				"Subscription sync timestamp should be set after client creation with subscriptions")

			// Verify out_of_sync_count exists (should be 0 for normal operation)
			assert.GreaterOrEqual(t, outOfSyncCount, uint64(0))
		})
	}
}
