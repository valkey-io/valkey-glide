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
- [x] Test basic publish and subscribe functionality (`TestBasicPubSubWithGlideClient`)
- [x] Test message delivery to multiple subscribers (`TestMultipleSubscribersWithGlideClient`)
- [x] Test unsubscribe functionality (`TestUnsubscribeWithGlideClient`) - Currently skipped due to Rust implementation limitation
- [x] Test message pattern matching with PSUBSCRIBE (`TestPatternSubscribeWithGlideClient`)

#### Message Handler ✅
Message handler functionality tested through the basic tests above:
- [x] Test message handler receives published messages
- [x] Test message handler with multiple channels 
- [x] Test message handler with pattern subscriptions
- [x] Test error handling in message handler

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

#### Message Handler
- [ ] Test message handler receives published messages
- [ ] Test message handler with multiple channels
- [ ] Test message handler with pattern subscriptions
- [ ] Test error handling in message handler

#### Edge Cases
- [ ] Test behavior with high message volume
- [ ] Test behavior when cluster nodes disconnect
- [ ] Test message ordering guarantees
- [ ] Test behavior during cluster resharding

## Known Issues and Limitations

- [ ] UNSUBSCRIBE command is not fully implemented in the Rust core (lib.rs:574 "not yet implemented")
  - The `TestUnsubscribeWithGlideClient` test is currently skipped due to this limitation
  - Need to revisit once the Rust implementation is updated

## Future Refactoring Tasks

- [ ] Refactor test suite helpers using generics to consolidate duplicate code between GlideTestSuite and PubSubTestSuite
