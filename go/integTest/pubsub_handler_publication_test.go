// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"testing"
	"time"

	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

const pubSubTestTimeout = 10 * time.Second

func pubSubTestChannel(t *testing.T) string {
	return fmt.Sprintf("{handler-publication}-%s-%d", t.Name(), time.Now().UnixNano())
}

// publishUntilStopped keeps publishing so a message reaches the subscriber as
// soon as its subscription exists, including while its constructor is still
// running.
func publishUntilStopped(t *testing.T, publish func(ctx context.Context) error) {
	t.Helper()
	stop, done := make(chan struct{}), make(chan struct{})
	go func() {
		defer close(done)
		ticker := time.NewTicker(time.Millisecond)
		defer ticker.Stop()
		for {
			select {
			case <-stop:
				return
			case <-ticker.C:
				ctx, cancel := context.WithTimeout(context.Background(), time.Second)
				err := publish(ctx)
				cancel()
				if err != nil {
					t.Errorf("publish during client creation: %v", err)
					return
				}
			}
		}
	}()
	t.Cleanup(func() {
		close(stop)
		<-done
	})
}

func requireQueuedMessage(t *testing.T, queue *glide.PubSubMessageQueue, channel string) {
	t.Helper()
	select {
	case msg := <-queue.WaitForMessage():
		if msg == nil || msg.Channel != channel || msg.Message != "payload" {
			t.Fatalf("unexpected message on %q: %+v", channel, msg)
		}
	case <-time.After(pubSubTestTimeout):
		t.Fatalf("no message delivered to the queue for %q", channel)
	}
}

type callbackDelivery struct {
	message *models.PubSubMessage
	context any
}

func newDeliveryCallback() (config.MessageCallback, chan callbackDelivery) {
	deliveries := make(chan callbackDelivery, 1)
	return func(message *models.PubSubMessage, ctx any) {
		select {
		case deliveries <- callbackDelivery{message, ctx}:
		default:
		}
	}, deliveries
}

func requireCallbackMessage(t *testing.T, deliveries chan callbackDelivery, channel string, wantContext any) {
	t.Helper()
	select {
	case got := <-deliveries:
		if got.message == nil || got.message.Channel != channel || got.message.Message != "payload" {
			t.Fatalf("unexpected message on %q: %+v", channel, got.message)
		}
		if got.context != wantContext {
			t.Fatalf("callback context = %v, want %v", got.context, wantContext)
		}
	case <-time.After(pubSubTestTimeout):
		t.Fatalf("callback not invoked for %q", channel)
	}
}

func (suite *GlideTestSuite) newStandalonePublisher(t *testing.T, channel string) {
	t.Helper()
	publisher, err := glide.NewClient(suite.defaultClientConfig())
	if err != nil {
		t.Fatalf("publisher: %v", err)
	}
	t.Cleanup(publisher.Close)
	publishUntilStopped(t, func(ctx context.Context) error {
		_, err := publisher.Publish(ctx, channel, "payload")
		return err
	})
}

func (suite *GlideTestSuite) newClusterPublisher(t *testing.T, channel string, sharded bool) {
	t.Helper()
	publisher, err := glide.NewClusterClient(suite.defaultClusterClientConfig())
	if err != nil {
		t.Fatalf("publisher: %v", err)
	}
	t.Cleanup(publisher.Close)
	publishUntilStopped(t, func(ctx context.Context) error {
		_, err := publisher.Publish(ctx, channel, "payload", sharded)
		return err
	})
}

func (suite *GlideTestSuite) TestPubSub_HandlerPublicationStandaloneQueue() {
	t := suite.T()
	if len(suite.standaloneHosts) == 0 {
		t.Skip("no standalone servers in this run")
	}
	channel := pubSubTestChannel(t)
	suite.newStandalonePublisher(t, channel)

	subscriber, err := glide.NewClient(suite.defaultClientConfig().WithSubscriptionConfig(
		config.NewStandaloneSubscriptionConfig().WithSubscription(config.ExactChannelMode, channel)))
	if err != nil {
		t.Fatalf("subscriber: %v", err)
	}
	t.Cleanup(subscriber.Close)

	queue, err := subscriber.GetQueue()
	if err != nil {
		t.Fatalf("GetQueue: %v", err)
	}
	requireQueuedMessage(t, queue, channel)
}

func (suite *GlideTestSuite) TestPubSub_HandlerPublicationStandaloneCallback() {
	t := suite.T()
	if len(suite.standaloneHosts) == 0 {
		t.Skip("no standalone servers in this run")
	}
	channel := pubSubTestChannel(t)
	suite.newStandalonePublisher(t, channel)

	callback, deliveries := newDeliveryCallback()
	subscriber, err := glide.NewClient(suite.defaultClientConfig().WithSubscriptionConfig(
		config.NewStandaloneSubscriptionConfig().
			WithSubscription(config.ExactChannelMode, channel).
			WithCallback(callback, "standalone-context")))
	if err != nil {
		t.Fatalf("subscriber: %v", err)
	}
	t.Cleanup(subscriber.Close)

	requireCallbackMessage(t, deliveries, channel, "standalone-context")
	if _, err := subscriber.GetQueue(); err == nil {
		t.Fatal("GetQueue must fail for a callback subscriber")
	}
}

// A client created without a subscription config still gets a queue handler,
// which later dynamic subscriptions deliver to.
func (suite *GlideTestSuite) TestPubSub_HandlerPublicationStandaloneDynamicSubscribe() {
	t := suite.T()
	if len(suite.standaloneHosts) == 0 {
		t.Skip("no standalone servers in this run")
	}
	channel := pubSubTestChannel(t)
	suite.newStandalonePublisher(t, channel)

	subscriber, err := glide.NewClient(suite.defaultClientConfig())
	if err != nil {
		t.Fatalf("subscriber: %v", err)
	}
	t.Cleanup(subscriber.Close)

	ctx, cancel := context.WithTimeout(context.Background(), pubSubTestTimeout)
	defer cancel()
	if err := subscriber.Subscribe(ctx, []string{channel}, int(pubSubTestTimeout.Milliseconds())); err != nil {
		t.Fatalf("Subscribe: %v", err)
	}
	queue, err := subscriber.GetQueue()
	if err != nil {
		t.Fatalf("GetQueue: %v", err)
	}
	requireQueuedMessage(t, queue, channel)
}

func (suite *GlideTestSuite) TestPubSub_HandlerPublicationClusterQueue() {
	t := suite.T()
	if len(suite.clusterHosts) == 0 {
		t.Skip("no cluster servers in this run")
	}
	channel := pubSubTestChannel(t)
	suite.newClusterPublisher(t, channel, false)

	subscriber, err := glide.NewClusterClient(suite.defaultClusterClientConfig().WithSubscriptionConfig(
		config.NewClusterSubscriptionConfig().WithSubscription(config.ExactClusterChannelMode, channel)))
	if err != nil {
		t.Fatalf("subscriber: %v", err)
	}
	t.Cleanup(subscriber.Close)

	queue, err := subscriber.GetQueue()
	if err != nil {
		t.Fatalf("GetQueue: %v", err)
	}
	requireQueuedMessage(t, queue, channel)
}

func (suite *GlideTestSuite) TestPubSub_HandlerPublicationClusterCallback() {
	t := suite.T()
	if len(suite.clusterHosts) == 0 {
		t.Skip("no cluster servers in this run")
	}
	channel := pubSubTestChannel(t)
	suite.newClusterPublisher(t, channel, false)

	callback, deliveries := newDeliveryCallback()
	subscriber, err := glide.NewClusterClient(suite.defaultClusterClientConfig().WithSubscriptionConfig(
		config.NewClusterSubscriptionConfig().
			WithSubscription(config.ExactClusterChannelMode, channel).
			WithCallback(callback, "cluster-context")))
	if err != nil {
		t.Fatalf("subscriber: %v", err)
	}
	t.Cleanup(subscriber.Close)

	requireCallbackMessage(t, deliveries, channel, "cluster-context")
	if _, err := subscriber.GetQueue(); err == nil {
		t.Fatal("GetQueue must fail for a callback subscriber")
	}
}
