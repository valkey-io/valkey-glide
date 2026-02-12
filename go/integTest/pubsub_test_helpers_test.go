// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
)

// PubSubTestCase represents a test case for PubSub functionality
type PubSubTestCase struct {
	Name           string
	ClientType     ClientType
	ReadMethod     MessageReadMethod
	Sharded        bool
	ChannelName    string
	MessageContent string
}

// PatternTestCase represents a test case for pattern subscriptions
type PatternTestCase struct {
	Name           string
	ClientType     ClientType
	ReadMethod     MessageReadMethod
	Pattern        string
	Channels       []string
	MessageContent string
}

// PubSubTestSetup encapsulates the setup for a PubSub test
type PubSubTestSetup struct {
	Publisher        interfaces.BaseClientCommands
	Receiver         interfaces.BaseClientCommands
	Queue            *glide.PubSubMessageQueue
	Channels         []ChannelDefn
	ExpectedMessages map[string]string
}

// CreateStandardTestCases generates standard test cases for all client/method combinations
func CreateStandardTestCases(channelName, messageContent string, includeSharded bool) []PubSubTestCase {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}
	readMethods := []MessageReadMethod{CallbackMethod, WaitForMessageMethod, SignalChannelMethod, SyncLoopMethod}

	var cases []PubSubTestCase

	for _, clientType := range clientTypes {
		for _, readMethod := range readMethods {
			// Regular case
			cases = append(cases, PubSubTestCase{
				Name:           clientType.String() + " with " + readMethod.String(),
				ClientType:     clientType,
				ReadMethod:     readMethod,
				Sharded:        false,
				ChannelName:    channelName,
				MessageContent: messageContent,
			})

			// Sharded case (only for cluster)
			if includeSharded && clientType == ClusterClient {
				cases = append(cases, PubSubTestCase{
					Name:           clientType.String() + " with " + readMethod.String() + " Sharded",
					ClientType:     clientType,
					ReadMethod:     readMethod,
					Sharded:        true,
					ChannelName:    channelName,
					MessageContent: messageContent,
				})
			}
		}
	}

	return cases
}

// SetupPubSubTest creates a complete PubSub test setup
func (suite *GlideTestSuite) SetupPubSubTest(testCase PubSubTestCase, t *testing.T) *PubSubTestSetup {
	if testCase.Sharded {
		suite.SkipIfServerVersionLowerThan("7.0.0", t)
	}

	publisher := suite.createAnyClient(testCase.ClientType, nil)

	channels := []ChannelDefn{
		{Channel: testCase.ChannelName, Mode: getChannelMode(testCase.Sharded)},
	}
	expectedMessages := map[string]string{
		testCase.ChannelName: testCase.MessageContent,
	}

	var receiver interfaces.BaseClientCommands
	var queue *glide.PubSubMessageQueue

	if testCase.ReadMethod != CallbackMethod {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, false, ConfigMethod, t)
		q, err := receiver.(PubSubQueuer).GetQueue()
		assert.Nil(t, err)
		queue = q
	} else {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, true, ConfigMethod, t)
	}

	// Allow subscription to establish
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	return &PubSubTestSetup{
		Publisher:        publisher,
		Receiver:         receiver,
		Queue:            queue,
		Channels:         channels,
		ExpectedMessages: expectedMessages,
	}
}

// SetupMultiSubscriberTest creates a test setup with multiple subscribers
func (suite *GlideTestSuite) SetupMultiSubscriberTest(
	testCase PubSubTestCase,
	numSubscribers int,
	t *testing.T,
) (*PubSubTestSetup, map[int]*glide.PubSubMessageQueue) {
	if testCase.Sharded {
		suite.SkipIfServerVersionLowerThan("7.0.0", t)
	}

	publisher := suite.createAnyClient(testCase.ClientType, nil)

	channels := []ChannelDefn{
		{Channel: testCase.ChannelName, Mode: getChannelMode(testCase.Sharded)},
	}
	expectedMessages := map[string]string{
		testCase.ChannelName: testCase.MessageContent,
	}

	queues := make(map[int]*glide.PubSubMessageQueue)

	for i := 0; i < numSubscribers; i++ {
		if testCase.ReadMethod != CallbackMethod {
			receiver := suite.CreatePubSubReceiver(testCase.ClientType, channels, i+1, false, ConfigMethod, t)
			queue, err := receiver.(PubSubQueuer).GetQueue()
			assert.Nil(t, err)
			queues[i+1] = queue
		} else {
			suite.CreatePubSubReceiver(testCase.ClientType, channels, i+1, true, ConfigMethod, t)
		}
	}

	// Allow subscriptions to establish
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	return &PubSubTestSetup{
		Publisher:        publisher,
		Channels:         channels,
		ExpectedMessages: expectedMessages,
	}, queues
}

// PublishMessage publishes a message using the appropriate client type
func (suite *GlideTestSuite) PublishMessage(
	publisher interfaces.BaseClientCommands,
	clientType ClientType,
	channel, message string,
	sharded bool,
) error {
	if clientType == ClusterClient {
		_, err := publisher.(*glide.ClusterClient).Publish(
			context.Background(),
			channel,
			message,
			sharded,
		)
		return err
	} else {
		_, err := publisher.(*glide.Client).Publish(context.Background(), channel, message)
		return err
	}
}

// CreatePatternTestCases generates test cases for pattern subscriptions
func CreatePatternTestCases(pattern string, channels []string, messageContent string) []PatternTestCase {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}
	readMethods := []MessageReadMethod{CallbackMethod, WaitForMessageMethod, SignalChannelMethod, SyncLoopMethod}

	var cases []PatternTestCase

	for _, clientType := range clientTypes {
		for _, readMethod := range readMethods {

			cases = append(cases, PatternTestCase{
				Name:           clientType.String() + " with " + readMethod.String(),
				ClientType:     clientType,
				ReadMethod:     readMethod,
				Pattern:        pattern,
				Channels:       channels,
				MessageContent: messageContent,
			})
		}
	}

	return cases
}

// SetupPatternTest creates a test setup for pattern subscriptions
func (suite *GlideTestSuite) SetupPatternTest(testCase PatternTestCase, t *testing.T) *PubSubTestSetup {
	publisher := suite.createAnyClient(testCase.ClientType, nil)

	channels := []ChannelDefn{
		{Channel: testCase.Pattern, Mode: PatternMode},
	}
	expectedMessages := map[string]string{
		testCase.Pattern: testCase.MessageContent,
	}

	var receiver interfaces.BaseClientCommands
	var queue *glide.PubSubMessageQueue

	if testCase.ReadMethod != CallbackMethod {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, false, ConfigMethod, t)
		q, err := receiver.(PubSubQueuer).GetQueue()
		assert.Nil(t, err)
		queue = q
	} else {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, true, ConfigMethod, t)
	}

	// Allow subscription to establish
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	return &PubSubTestSetup{
		Publisher:        publisher,
		Receiver:         receiver,
		Queue:            queue,
		Channels:         channels,
		ExpectedMessages: expectedMessages,
	}
}

// ExecuteAndVerifyPatternTest executes a complete pattern subscription test
func (suite *GlideTestSuite) ExecuteAndVerifyPatternTest(testCase PatternTestCase, t *testing.T) {
	setup := suite.SetupPatternTest(testCase, t)
	defer setup.Publisher.Close()
	defer setup.Receiver.Close()

	// Publish test messages to matching channels
	for _, channel := range testCase.Channels {
		err := suite.PublishMessage(setup.Publisher, testCase.ClientType, channel, testCase.MessageContent, false)
		assert.Nil(t, err)
	}

	// Publish a message to a non-matching channel
	err := suite.PublishMessage(setup.Publisher, testCase.ClientType, "other-channel", "should not receive", false)
	assert.Nil(t, err)

	// Allow time for the messages to be received
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	// Verify using the verification function
	queues := make(map[int]*glide.PubSubMessageQueue)
	if setup.Queue != nil {
		queues[1] = setup.Queue
	}
	suite.verifyPubsubMessages(t, setup.ExpectedMessages, queues, testCase.ReadMethod)
}

// MultiChannelTestCase represents a test case for multiple channel subscriptions
type MultiChannelTestCase struct {
	Name           string
	ClientType     ClientType
	ReadMethod     MessageReadMethod
	UseCallback    bool
	Sharded        bool
	ChannelNames   []string
	MessageContent string
}

// CreateMultiChannelTestCases generates test cases for multiple channel subscriptions
func CreateMultiChannelTestCases(channelNames []string, messageContent string, includeSharded bool) []MultiChannelTestCase {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}
	readMethods := []MessageReadMethod{CallbackMethod, WaitForMessageMethod, SignalChannelMethod, SyncLoopMethod}

	var cases []MultiChannelTestCase

	for _, clientType := range clientTypes {
		for _, readMethod := range readMethods {

			// Regular case
			cases = append(cases, MultiChannelTestCase{
				Name:           clientType.String() + " with " + readMethod.String(),
				ClientType:     clientType,
				ReadMethod:     readMethod,
				Sharded:        false,
				ChannelNames:   channelNames,
				MessageContent: messageContent,
			})

			// Sharded case (only for cluster)
			if includeSharded && clientType == ClusterClient {
				cases = append(cases, MultiChannelTestCase{
					Name:           clientType.String() + " with " + readMethod.String() + " Sharded",
					ClientType:     clientType,
					ReadMethod:     readMethod,
					Sharded:        true,
					ChannelNames:   channelNames,
					MessageContent: messageContent,
				})
			}
		}
	}

	return cases
}

// ExecuteAndVerifyMultiChannelTest executes a complete multi-channel test
func (suite *GlideTestSuite) ExecuteAndVerifyMultiChannelTest(testCase MultiChannelTestCase, t *testing.T) {
	if testCase.Sharded {
		suite.SkipIfServerVersionLowerThan("7.0.0", t)
	}

	publisher := suite.createAnyClient(testCase.ClientType, nil)
	defer publisher.Close()

	// Create channel definitions for all channels
	channels := make([]ChannelDefn, len(testCase.ChannelNames))
	for i, channelName := range testCase.ChannelNames {
		channels[i] = ChannelDefn{Channel: channelName, Mode: getChannelMode(testCase.Sharded)}
	}

	// Create expected messages map
	expectedMessages := make(map[string]string)
	for _, channelName := range testCase.ChannelNames {
		expectedMessages[channelName] = testCase.MessageContent
	}

	var receiver interfaces.BaseClientCommands
	queues := make(map[int]*glide.PubSubMessageQueue)
	if testCase.ReadMethod != CallbackMethod {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, false, ConfigMethod, t)
		defer receiver.Close()
		queue, err := receiver.(PubSubQueuer).GetQueue()
		assert.Nil(t, err)
		queues[1] = queue
	} else {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, true, ConfigMethod, t)
		defer receiver.Close()
	}

	// Allow subscription to establish
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	// Publish to all channels
	for _, channelName := range testCase.ChannelNames {
		err := suite.PublishMessage(publisher, testCase.ClientType, channelName, testCase.MessageContent, testCase.Sharded)
		assert.Nil(t, err)
	}

	// Allow time for the messages to be received
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	// Verify using the verification function
	suite.verifyPubsubMessages(t, expectedMessages, queues, testCase.ReadMethod)
}

// CombinedTestCase represents a test case for combined exact and pattern subscriptions
type CombinedTestCase struct {
	Name            string
	ClientType      ClientType
	ReadMethod      MessageReadMethod
	UseCallback     bool
	ExactChannel    string
	Pattern         string
	PatternChannels []string
	MessageContent  string
}

// CreateCombinedTestCases generates test cases for combined exact and pattern subscriptions
func CreateCombinedTestCases(
	exactChannel, pattern string,
	patternChannels []string,
	messageContent string,
) []CombinedTestCase {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}
	readMethods := []MessageReadMethod{CallbackMethod, WaitForMessageMethod, SignalChannelMethod, SyncLoopMethod}

	var cases []CombinedTestCase

	for _, clientType := range clientTypes {
		for _, readMethod := range readMethods {

			cases = append(cases, CombinedTestCase{
				Name:            clientType.String() + " with " + readMethod.String(),
				ClientType:      clientType,
				ReadMethod:      readMethod,
				ExactChannel:    exactChannel,
				Pattern:         pattern,
				PatternChannels: patternChannels,
				MessageContent:  messageContent,
			})
		}
	}

	return cases
}

// ExecuteAndVerifyCombinedTest executes a complete combined exact and pattern test
func (suite *GlideTestSuite) ExecuteAndVerifyCombinedTest(testCase CombinedTestCase, t *testing.T) {
	publisher := suite.createAnyClient(testCase.ClientType, nil)
	defer publisher.Close()

	// Create channel definitions for both exact and pattern subscriptions
	channels := []ChannelDefn{
		{Channel: testCase.ExactChannel, Mode: ExactMode},
		{Channel: testCase.Pattern, Mode: PatternMode},
	}

	// Create expected messages map
	expectedMessages := make(map[string]string)
	expectedMessages[testCase.ExactChannel] = testCase.MessageContent
	expectedMessages[testCase.Pattern] = testCase.MessageContent

	var receiver interfaces.BaseClientCommands
	queues := make(map[int]*glide.PubSubMessageQueue)
	if testCase.ReadMethod != CallbackMethod {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, false, ConfigMethod, t)
		defer receiver.Close()
		queue, err := receiver.(PubSubQueuer).GetQueue()
		assert.Nil(t, err)
		queues[1] = queue
	} else {
		receiver = suite.CreatePubSubReceiver(testCase.ClientType, channels, 1, true, ConfigMethod, t)
		defer receiver.Close()
	}

	// Allow subscription to establish
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	// Publish to exact channel
	err := suite.PublishMessage(publisher, testCase.ClientType, testCase.ExactChannel, testCase.MessageContent, false)
	assert.Nil(t, err)

	// Publish to pattern-matching channels
	for _, channel := range testCase.PatternChannels {
		err := suite.PublishMessage(publisher, testCase.ClientType, channel, testCase.MessageContent, false)
		assert.Nil(t, err)
	}

	// Publish to a non-matching channel
	err = suite.PublishMessage(publisher, testCase.ClientType, "other-channel", "should not receive", false)
	assert.Nil(t, err)

	// Allow time for the messages to be received
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	// Verify using the verification function
	suite.verifyPubsubMessages(t, expectedMessages, queues, testCase.ReadMethod)
}

// ExecuteAndVerifyPubSubTest executes a complete PubSub test
func (suite *GlideTestSuite) ExecuteAndVerifyPubSubTest(testCase PubSubTestCase, t *testing.T) {
	setup := suite.SetupPubSubTest(testCase, t)
	defer setup.Publisher.Close()
	defer setup.Receiver.Close()

	// Publish test message
	err := suite.PublishMessage(
		setup.Publisher,
		testCase.ClientType,
		testCase.ChannelName,
		testCase.MessageContent,
		testCase.Sharded,
	)
	assert.Nil(t, err)

	// Allow time for the message to be received
	time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

	// Verify using the verification function
	queues := make(map[int]*glide.PubSubMessageQueue)
	if setup.Queue != nil {
		queues[1] = setup.Queue
	}
	suite.verifyPubsubMessages(t, setup.ExpectedMessages, queues, testCase.ReadMethod)
}
