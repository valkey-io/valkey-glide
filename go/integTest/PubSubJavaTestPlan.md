# Java PubSub Integration Test Plan

This document outlines the integration tests for PubSub functionality in Java. The tests are grouped into two categories: Standalone Client tests and Cluster Client tests. Each test includes a descriptive name and the corresponding Java test method name for reference.

---

## Standalone Client Tests

- [ ] **Config Error on RESP2 Protocol** (`config_error_on_resp2`)
- [ ] **Exact Subscription Happy Path** (`exact_happy_path`)
- [ ] **Exact Subscription with Many Channels** (`exact_happy_path_many_channels`)
- [ ] **Pattern Subscription** (`pattern`)
- [ ] **Pattern Subscription with Many Channels** (`pattern_many_channels`)
- [ ] **Combined Exact and Pattern Subscription (One Client)** (`combined_exact_and_pattern_one_client`)
- [ ] **Combined Exact and Pattern Subscription (Multiple Clients)** (`combined_exact_and_pattern_multiple_clients`)
- [ ] **Coexistence of Sync and Async Read** (`coexistense_of_sync_and_async_read`)
- [ ] **Transaction with All Types of Messages** (`transaction_with_all_types_of_messages`)
- [ ] **PubSub with Binary Data** (`pubsub_with_binary`)
- [ ] **PubSub Channels** (`pubsub_channels`)
- [ ] **PubSub Number of Patterns** (`pubsub_numpat`)
- [ ] **PubSub Number of Subscribers** (`pubsub_numsub`)
- [ ] **PubSub Channels, Patterns, and Subscribers in Transaction** (`pubsub_channels_and_numpat_and_numsub_in_transaction`)
- [ ] **Callback Exception Handling** (`pubsub_test_callback_exception`)

---

## Cluster Client Tests

- [ ] **Config Error on RESP2 Protocol** (`config_error_on_resp2`)
- [ ] **Exact Subscription Happy Path** (`exact_happy_path`)
- [ ] **Exact Subscription with Many Channels** (`exact_happy_path_many_channels`)
- [ ] **Sharded Subscription** (`sharded_pubsub`)
- [ ] **Sharded Subscription with Many Channels** (`sharded_pubsub_many_channels`)
- [ ] **Pattern Subscription** (`pattern`)
- [ ] **Pattern Subscription with Many Channels** (`pattern_many_channels`)
- [ ] **Combined Exact, Pattern, and Sharded Subscription (One Client)** (`combined_exact_pattern_and_sharded_one_client`)
- [ ] **Combined Exact, Pattern, and Sharded Subscription (Multiple Clients)** (`combined_exact_pattern_and_sharded_multi_client`)
- [ ] **Three Publishing Clients with Same Channel Name (Sharded)** (`three_publishing_clients_same_name_with_sharded`)
- [ ] **Coexistence of Sync and Async Read** (`coexistense_of_sync_and_async_read`)
- [ ] **Transaction with All Types of Messages** (`transaction_with_all_types_of_messages`)
- [ ] **PubSub with Binary Data** (`pubsub_with_binary`)
- [ ] **PubSub Channels** (`pubsub_channels`)
- [ ] **PubSub Number of Patterns** (`pubsub_numpat`)
- [ ] **PubSub Number of Subscribers** (`pubsub_numsub`)
- [ ] **PubSub Channels, Patterns, and Subscribers in Transaction** (`pubsub_channels_and_numpat_and_numsub_in_transaction`)
- [ ] **PubSub Shard Channels** (`pubsub_shard_channels`)
- [ ] **PubSub Shard Number of Subscribers** (`pubsub_shardnumsub`)
- [ ] **Callback Exception Handling** (`pubsub_test_callback_exception`)

---

## Notes

- Disabled tests (e.g., `pubsub_exact_max_size_message`, `pubsub_sharded_max_size_message`) are not included in this checklist as they are currently not testable.
- Ensure that all tests are executed in environments that meet the prerequisites, such as Redis version 7.0.0 or higher for certain features.
