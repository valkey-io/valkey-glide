# Valkey GLIDE PubSub Integration Test Plan

## Context

This document outlines the integration test plan for PubSub functionality in Valkey GLIDE. These tests will verify the proper functioning of PubSub features using the CustomCommand interface method available on both GlideClient and GlideClusterClient.

**Current Status**: PubSub commands are not fully implemented in the Valkey GLIDE Go wrapper, so we'll use the CustomCommand interface for testing.

**Implementation Strategy**:

1. Complete all tests for GlideClient first
2. Then implement the same tests for GlideClusterClient
3. Focus on one feature at a time, iterating until it works correctly before moving to the next

## Test Organization

Tests will be organized in a separate test suite (PubSubTestSuite) to allow complete control over client creation and subscription configurations.

## Test Plan Checklist

### GlideClient PubSub Tests

#### Basic Publish/Subscribe ✅

All basic functionality tests are now implemented in `pubsub_basic_test.go`:

- [x] Test message delivery to a single subscriber (`TestPubSub_Basic_ChannelSubscription`)
- [x] Test message delivery to multiple subscribers (`TestPubSub_Basic_MultipleSubscribers`)
- [x] Test single subscriber with many channels subscriptions (`TestPubSub_Basic_ManyChannels`)
- [x] Test message pattern matching (`TestPubSub_Basic_PatternSubscription`)
- [x] Test pattern subscription with many channels (`TestPubSub_Basic_PatternManyChannels`)
- [x] Test combined exact and pattern subscription (one subscriber) (`TestPubSub_Basic_CombinedExactPattern`)
- [x] Test combined exact and pattern subscription (multiple subscribers) (`TestPubSub_Basic_CombinedExactPatternMultipleSubscribers`)

#### Message Handler ✅

Message handler functionality tested through the basic tests above:

- [x] Test message handler receives published messages
- [x] Test message handler with multiple channels
- [x] Test message handler with pattern subscriptions
- [x] Test error handling in message handler
- [ ] Test callback exception handling

#### PubSub Commands Tests

- [ ] Test PUBSUB CHANNELS command
- [ ] Test PUBSUB NUMPAT command
- [ ] Test PUBSUB NUMSUB command

#### Transaction Tests (SKIPPED - Not Currently Supported) ⛔

- [-] Test transaction with all types of messages (SKIP - transactions not supported)
- [-] Test PubSub channels, patterns, and subscribers in transaction (SKIP - transactions not supported)

#### Subscription Configuration

- [-] Test connection handling during network interruptions (`TestSubscriptionReconnection`) - Created but skipped pending research on Java implementation behavior
- [ ] Test reconnection behavior with subscriptions
- [ ] Test subscription behavior under high load

#### Edge Cases

- [ ] Test behavior with high message volume
- [ ] Test behavior when Redis server disconnects
- [ ] Test message ordering guarantees

### GlideClusterClient PubSub Tests

#### Basic Publish/Subscribe

- [ ] Test basic publish and subscribe functionality
- [ ] Test message delivery to multiple subscribers
- [ ] Test unsubscribe functionality
- [ ] Test message pattern matching with PSUBSCRIBE
- [ ] Test sharded subscription
- [ ] Test sharded subscription with many channels
- [ ] Test combined exact, pattern, and sharded subscription (one client)
- [ ] Test combined exact, pattern, and sharded subscription (multiple clients)
- [ ] Test three publishing clients with same channel name (sharded)

#### Message Handler

- [ ] Test message handler receives published messages
- [ ] Test message handler with multiple channels
- [ ] Test message handler with pattern subscriptions
- [ ] Test error handling in message handler
- [ ] Test callback exception handling

#### Cluster-Specific PubSub Commands

- [ ] Test PUBSUB SHARD CHANNELS command
- [ ] Test PUBSUB SHARDNUMSUB command

#### Transaction Tests (SKIPPED - Not Currently Supported) ⛔

- [-] Test transaction with all types of messages (SKIP - transactions not supported)
- [-] Test PubSub channels, patterns, and subscribers in transaction (SKIP - transactions not supported)

#### Edge Cases

- [ ] Test behavior with high message volume
- [ ] Test behavior when cluster nodes disconnect
- [ ] Test message ordering guarantees
- [ ] Test behavior during cluster resharding

## Known Issues and Limitations

- [ ] UNSUBSCRIBE command is not fully implemented in the Rust core (lib.rs:574 "not yet implemented")
  - The `TestUnsubscribeWithGlideClient` test is currently skipped due to this limitation
  - Need to revisit once the Rust implementation is updated
- [ ] Transactions are not currently supported in Go implementation
  - Transaction-related tests are marked as skipped
  - Need to track when transaction support is added to revisit these tests

## Future Refactoring Tasks

- [ ] Refactor test suite to consolidate duplicate code between GlideTestSuite and PubSubTestSuite
- [ ] Update makefile with PubSub tests
- [ ] Update the developer.md file with how to use PubSub API
- [ ] Update CI pipeline to run PubSub tests
- [ ] Test SignalChannel handles multiple messages arriving over time and triggering the signal handler multiple times. This test should be structured so that the messages are not stacking in the queue.
