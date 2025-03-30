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

#### Basic Publish/Subscribe

- [ ] Test basic publish and subscribe functionality
  - In Progress
  - Working to resolve issues with message delivery for multiple subscribers.
- [ ] Test message delivery to multiple subscribers
- [ ] Test unsubscribe functionality
- [ ] Test message pattern matching with PSUBSCRIBE

#### Message Handler

- [ ] Test message handler receives published messages
- [ ] Test message handler with multiple channels
- [ ] Test message handler with pattern subscriptions
- [ ] Test error handling in message handler

#### Subscription Configuration

- [ ] Test subscription with different buffer sizes
- [ ] Test subscription timeout configuration
- [ ] Test reconnection behavior with subscriptions

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

#### Subscription Configuration

- [ ] Test subscription with different buffer sizes
- [ ] Test subscription timeout configuration
- [ ] Test reconnection behavior with subscriptions

#### Edge Cases

- [ ] Test behavior with high message volume
- [ ] Test behavior when cluster nodes disconnect
- [ ] Test message ordering guarantees
- [ ] Test behavior during cluster resharding

## Implementation Progress

Current focus: GlideClient Basic Publish/Subscribe tests

## Known Issues and Limitations

- [ ] UNSUBSCRIBE command is not fully implemented in the Rust core (lib.rs:574 "not yet implemented")
  - The `TestUnsubscribeWithGlideClient` test is currently skipped due to this limitation
  - Need to revisit once the Rust implementation is updated

## Future Refactoring Tasks

- [ ] Refactor test suite helpers using generics to consolidate duplicate code between GlideTestSuite and PubSubTestSuite
