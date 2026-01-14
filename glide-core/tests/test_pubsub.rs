// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// These tests require the real synchronizer implementation, not the mock
#![cfg(not(feature = "mock-pubsub"))]

mod utilities;

use redis::PubSubSubscriptionKind;
use rstest::rstest;
use std::collections::HashSet;
use std::time::Duration;
use utilities::block_on_all;
use utilities::cluster::{
    ClusterTopology, LONG_CLUSTER_TEST_TIMEOUT, PubSubTestSetup, RedisCluster,
    generate_test_subscriptions_different_slots, migrate_channel_to_different_node,
    migrate_channels_to_different_nodes, subscribe_and_wait, trigger_failover,
    verify_subscription_addresses_changed, wait_for_node_to_become_primary, wait_for_pubsub_state,
};

const LOG_PREFIX: &str = "test_pubsub";

/// Delay between slot migrations to avoid overwhelming the cluster.
const MIGRATION_DELAY: Duration = Duration::from_millis(0);

/// Timeout for waiting for subscriptions to be established.
const SUBSCRIPTION_TIMEOUT: Duration = Duration::from_secs(2);

/// Timeout for waiting for subscriptions to be re-established after migration.
const RESUBSCRIPTION_TIMEOUT: Duration = Duration::from_secs(5);

#[rstest]
#[case::one_channel(1)]
#[case::hundred_channels(100)]
#[serial_test::serial]
#[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
fn test_sharded_subscriptions_survive_slot_migrations(#[case] num_channels: usize) {
    block_on_all(async {
        let cluster = RedisCluster::new(false, &None, Some(3), Some(0));
        let addresses = cluster.get_server_addresses();
        let mut setup = PubSubTestSetup::new(&addresses).await;

        skip_if_version_below!(setup, "7.0.0");

        let topology = ClusterTopology::from_connection(&mut setup.connection).await;

        let channels_with_slots =
            generate_test_subscriptions_different_slots("sharded", num_channels, false);
        let channels: Vec<Vec<u8>> = channels_with_slots.iter().map(|(c, _)| c.clone()).collect();

        let all_subscribed = subscribe_and_wait(
            &setup.synchronizer,
            &channels,
            PubSubSubscriptionKind::Sharded,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        assert!(
            all_subscribed,
            "All {} sharded subscriptions should be established",
            num_channels
        );

        let subs_before = setup.get_subscriptions_by_address();

        migrate_channels_to_different_nodes(
            &mut setup.connection,
            &topology,
            &channels_with_slots,
            MIGRATION_DELAY,
        )
        .await;

        let all_resubscribed = wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Sharded,
            &channels.iter().cloned().collect(),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        assert!(
            all_resubscribed,
            "All sharded subscriptions should be re-established after migrations"
        );

        let subs_after = setup.get_subscriptions_by_address();
        let (changed, unchanged, not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            &channels,
            PubSubSubscriptionKind::Sharded,
        );

        logger_core::log_debug(
            LOG_PREFIX,
            format!(
                "Subscription address changes: {} changed, {} unchanged, {} not found",
                changed, unchanged, not_found
            ),
        );

        assert_eq!(
            not_found, 0,
            "All subscriptions should be found after migration"
        );
        assert_eq!(
            unchanged, 0,
            "All subscriptions should be found after migration"
        );
        assert_eq!(
            changed, num_channels,
            "All {} sharded subscriptions should have moved to different addresses",
            num_channels
        );
    });
}

#[rstest]
#[case::one_channel(1)]
#[case::many_channels(100)]
#[serial_test::serial]
#[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
fn test_exact_subscriptions_survive_slot_migrations(#[case] num_channels: usize) {
    block_on_all(async {
        let cluster = RedisCluster::new(false, &None, Some(3), Some(0));
        let addresses = cluster.get_server_addresses();
        let mut setup = PubSubTestSetup::new(&addresses).await;

        let topology = ClusterTopology::from_connection(&mut setup.connection).await;
        let channels_with_slots =
            generate_test_subscriptions_different_slots("exact", num_channels, false);
        let channels: Vec<Vec<u8>> = channels_with_slots.iter().map(|(c, _)| c.clone()).collect();

        let all_subscribed = subscribe_and_wait(
            &setup.synchronizer,
            &channels,
            PubSubSubscriptionKind::Exact,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        assert!(
            all_subscribed,
            "All {} exact subscriptions should be established",
            num_channels
        );

        let subs_before = setup.get_subscriptions_by_address();

        migrate_channels_to_different_nodes(
            &mut setup.connection,
            &topology,
            &channels_with_slots,
            MIGRATION_DELAY,
        )
        .await;

        // small sleep to allow for the synchronizer handle_topology to start and unsubscribe
        // Otherwise we will pass the wait_for_pubsub_state immediately on the same address
        tokio::time::sleep(Duration::from_millis(500)).await;

        let all_resubscribed = wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Exact,
            &channels.iter().cloned().collect(),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        assert!(
            all_resubscribed,
            "All exact subscriptions should be re-established after migrations"
        );

        let subs_after = setup.get_subscriptions_by_address();
        let (changed, unchanged, not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            &channels,
            PubSubSubscriptionKind::Exact,
        );

        logger_core::log_info(
            LOG_PREFIX,
            format!(
                "Subscription address changes: {} changed, {} unchanged, {} not found",
                changed, unchanged, not_found
            ),
        );

        assert_eq!(
            not_found, 0,
            "All subscriptions should be found after migration"
        );
        assert_eq!(
            unchanged, 0,
            "All subscriptions should be found after migration"
        );
        assert_eq!(
            changed, num_channels,
            "All {} exact subscriptions should have moved to different addresses",
            num_channels
        );
    });
}

#[rstest]
#[case::one_pattern(1)]
#[case::hundred_patterns(100)]
#[serial_test::serial]
#[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
fn test_pattern_subscriptions_survive_slot_migrations(#[case] num_patterns: usize) {
    block_on_all(async {
        let cluster = RedisCluster::new(false, &None, Some(3), Some(0));
        let addresses = cluster.get_server_addresses();
        let mut setup = PubSubTestSetup::new(&addresses).await;

        let topology = ClusterTopology::from_connection(&mut setup.connection).await;
        let patterns_with_slots =
            generate_test_subscriptions_different_slots("pattern", num_patterns, true);
        let patterns: Vec<Vec<u8>> = patterns_with_slots.iter().map(|(p, _)| p.clone()).collect();

        let all_subscribed = subscribe_and_wait(
            &setup.synchronizer,
            &patterns,
            PubSubSubscriptionKind::Pattern,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        assert!(
            all_subscribed,
            "All {} pattern subscriptions should be established",
            num_patterns
        );

        let subs_before = setup.get_subscriptions_by_address();

        migrate_channels_to_different_nodes(
            &mut setup.connection,
            &topology,
            &patterns_with_slots,
            MIGRATION_DELAY,
        )
        .await;

        // small sleep to allow for the synchronizer handle_topology to start and unsubscribe
        // Otherwise we will pass the wait_for_pubsub_state immediately on the same address
        tokio::time::sleep(Duration::from_millis(500)).await;

        let all_resubscribed = wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Pattern,
            &patterns.iter().cloned().collect(),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        assert!(
            all_resubscribed,
            "All pattern subscriptions should be re-established after migrations"
        );

        let subs_after = setup.get_subscriptions_by_address();
        let (changed, unchanged, not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            &patterns,
            PubSubSubscriptionKind::Pattern,
        );

        logger_core::log_info(
            LOG_PREFIX,
            format!(
                "Subscription address changes: {} changed, {} unchanged, {} not found",
                changed, unchanged, not_found
            ),
        );

        assert_eq!(
            not_found, 0,
            "All subscriptions should be found after migration"
        );
        assert_eq!(
            unchanged, 0,
            "All subscriptions should be found after migration"
        );
        assert_eq!(
            changed, num_patterns,
            "All {} pattern subscriptions should have moved to different addresses",
            num_patterns
        );
    });
}

#[rstest]
#[serial_test::serial]
#[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
fn test_all_subscription_types_survive_same_slot_migration() {
    block_on_all(async {
        let cluster = RedisCluster::new(false, &None, Some(3), Some(0));
        let addresses = cluster.get_server_addresses();
        let mut setup = PubSubTestSetup::new(&addresses).await;

        skip_if_version_below!(setup, "7.0.0");

        let topology = ClusterTopology::from_connection(&mut setup.connection).await;
        let exact_channel = b"{mixed-test}exact-channel".to_vec();
        let pattern = b"{mixed-test}pattern-*".to_vec();
        let sharded_channel = b"{mixed-test}sharded-channel".to_vec();

        let slot = redis::cluster_topology::get_slot(&exact_channel);

        let exact_sub = subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&exact_channel),
            PubSubSubscriptionKind::Exact,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        let pattern_sub = subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&pattern),
            PubSubSubscriptionKind::Pattern,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        let sharded_sub = subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&sharded_channel),
            PubSubSubscriptionKind::Sharded,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;

        assert!(exact_sub, "Exact subscription should be established");
        assert!(pattern_sub, "Pattern subscription should be established");
        assert!(sharded_sub, "Sharded subscription should be established");

        let subs_before = setup.get_subscriptions_by_address();

        let migrated =
            migrate_channel_to_different_node(&mut setup.connection, &topology, slot).await;
        assert!(
            migrated.is_some(),
            "Should have migrated to a different node"
        );

        // small sleep to allow for the synchronizer handle_topology to start and unsubscribe
        // Otherwise we will pass the wait_for_pubsub_state immediately on the same address
        tokio::time::sleep(Duration::from_millis(500)).await;

        let exact_resub = wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Exact,
            &HashSet::from([exact_channel.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        let pattern_resub = wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Pattern,
            &HashSet::from([pattern.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        let sharded_resub = wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Sharded,
            &HashSet::from([sharded_channel.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;

        assert!(exact_resub, "Exact subscription should be re-established");
        assert!(
            pattern_resub,
            "Pattern subscription should be re-established"
        );
        assert!(
            sharded_resub,
            "Sharded subscription should be re-established"
        );

        let subs_after = setup.get_subscriptions_by_address();

        let (exact_changed, _, exact_not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            std::slice::from_ref(&exact_channel),
            PubSubSubscriptionKind::Exact,
        );
        let (pattern_changed, _, pattern_not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            std::slice::from_ref(&pattern),
            PubSubSubscriptionKind::Pattern,
        );
        let (sharded_changed, _, sharded_not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            std::slice::from_ref(&sharded_channel),
            PubSubSubscriptionKind::Sharded,
        );

        assert_eq!(exact_not_found, 0, "Exact subscription should be found");
        assert_eq!(pattern_not_found, 0, "Pattern subscription should be found");
        assert_eq!(sharded_not_found, 0, "Sharded subscription should be found");

        assert_eq!(
            exact_changed, 1,
            "Exact subscription should have moved to different address"
        );
        assert_eq!(
            pattern_changed, 1,
            "Pattern subscription should have moved to different address"
        );
        assert_eq!(
            sharded_changed, 1,
            "Sharded subscription should have moved to different address"
        );
    });
}

#[rstest]
#[serial_test::serial]
#[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
fn test_all_subscription_types_survive_different_slot_migrations() {
    block_on_all(async {
        let cluster = RedisCluster::new(false, &None, Some(3), Some(0));
        let addresses = cluster.get_server_addresses();
        let mut setup = PubSubTestSetup::new(&addresses).await;

        skip_if_version_below!(setup, "7.0.0");

        let topology = ClusterTopology::from_connection(&mut setup.connection).await;
        let exact_channel = b"{exact-diff-500}channel".to_vec();
        let pattern = b"{pattern-diff-8000}*".to_vec();
        let sharded_channel = b"{sharded-diff-15000}channel".to_vec();

        let exact_slot = redis::cluster_topology::get_slot(&exact_channel);
        let pattern_slot = redis::cluster_topology::get_slot(&pattern);
        let sharded_slot = redis::cluster_topology::get_slot(&sharded_channel);

        subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&exact_channel),
            PubSubSubscriptionKind::Exact,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&pattern),
            PubSubSubscriptionKind::Pattern,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&sharded_channel),
            PubSubSubscriptionKind::Sharded,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;

        let subs_before = setup.get_subscriptions_by_address();

        for slot in [exact_slot, pattern_slot, sharded_slot] {
            let _ = migrate_channel_to_different_node(&mut setup.connection, &topology, slot).await;
            tokio::time::sleep(MIGRATION_DELAY).await;
        }

        // small sleep to allow for the synchronizer handle_topology to start and unsubscribe
        // Otherwise we will pass the wait_for_pubsub_state immediately on the same address
        tokio::time::sleep(Duration::from_millis(500)).await;

        wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Exact,
            &HashSet::from([exact_channel.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Pattern,
            &HashSet::from([pattern.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Sharded,
            &HashSet::from([sharded_channel.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;

        let subs_after = setup.get_subscriptions_by_address();

        let (exact_changed, _, _) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            std::slice::from_ref(&exact_channel),
            PubSubSubscriptionKind::Exact,
        );
        let (pattern_changed, _, _) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            std::slice::from_ref(&pattern),
            PubSubSubscriptionKind::Pattern,
        );
        let (sharded_changed, _, _) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            std::slice::from_ref(&sharded_channel),
            PubSubSubscriptionKind::Sharded,
        );

        assert_eq!(
            exact_changed, 1,
            "Exact subscription should have moved to different address"
        );
        assert_eq!(
            pattern_changed, 1,
            "Pattern subscription should have moved to different address"
        );
        assert_eq!(
            sharded_changed, 1,
            "Sharded subscription should have moved to different address"
        );
    });
}

#[rstest]
#[serial_test::serial]
#[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
fn test_all_subscription_types_survive_failover() {
    block_on_all(async {
        let cluster = RedisCluster::new(false, &None, Some(3), Some(1));
        let addresses = cluster.get_server_addresses();
        let mut setup = PubSubTestSetup::new(&addresses).await;

        skip_if_version_below!(setup, "7.0.0");

        let topology = ClusterTopology::from_connection(&mut setup.connection).await;

        // Create channels with same hash tag so they all go to the same slot
        let exact_channel = b"{failover-all}-exact".to_vec();
        let pattern = b"{failover-all}-pattern-*".to_vec();
        let sharded_channel = b"{failover-all}-sharded".to_vec();

        let slot = redis::cluster_topology::get_slot(&exact_channel);

        let primary = topology
            .find_slot_owner(slot)
            .expect("Should find owner for slot");

        let replicas = topology.find_replicas_of(&primary.node_id);
        assert!(
            !replicas.is_empty(),
            "Primary should have at least one replica"
        );
        let replica = replicas[0];

        logger_core::log_info(
            LOG_PREFIX,
            format!(
                "Channels hash to slot {}. Primary {}:{} with replica {}:{}",
                slot, primary.host, primary.port, replica.host, replica.port
            ),
        );

        subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&exact_channel),
            PubSubSubscriptionKind::Exact,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&pattern),
            PubSubSubscriptionKind::Pattern,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;
        subscribe_and_wait(
            &setup.synchronizer,
            std::slice::from_ref(&sharded_channel),
            PubSubSubscriptionKind::Sharded,
            SUBSCRIPTION_TIMEOUT,
        )
        .await;

        let subs_before = setup.get_subscriptions_by_address();

        let failover_initiated = trigger_failover(&mut setup.connection, replica).await;
        assert!(failover_initiated, "Failover should be initiated");

        let became_primary = wait_for_node_to_become_primary(
            &mut setup.connection,
            &replica.node_id,
            Duration::from_secs(30),
        )
        .await;
        assert!(
            became_primary,
            "Replica should become primary after failover"
        );

        wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Exact,
            &HashSet::from([exact_channel.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Pattern,
            &HashSet::from([pattern.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;
        wait_for_pubsub_state(
            &setup.synchronizer,
            PubSubSubscriptionKind::Sharded,
            &HashSet::from([sharded_channel.clone()]),
            true,
            RESUBSCRIPTION_TIMEOUT,
        )
        .await;

        let subs_after = setup.get_subscriptions_by_address();

        let (exact_changed, _, exact_not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            &[exact_channel],
            PubSubSubscriptionKind::Exact,
        );
        let (pattern_changed, _, pattern_not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            &[pattern],
            PubSubSubscriptionKind::Pattern,
        );
        let (sharded_changed, _, sharded_not_found) = verify_subscription_addresses_changed(
            &subs_before,
            &subs_after,
            &[sharded_channel],
            PubSubSubscriptionKind::Sharded,
        );

        assert_eq!(exact_not_found, 0, "Exact subscription should be found");
        assert_eq!(pattern_not_found, 0, "Pattern subscription should be found");
        assert_eq!(sharded_not_found, 0, "Sharded subscription should be found");

        assert_eq!(exact_changed, 1, "Exact subscription should have moved");
        assert_eq!(pattern_changed, 1, "Pattern subscription should have moved");
        assert_eq!(sharded_changed, 1, "Sharded subscription should have moved");

        logger_core::log_info(
            LOG_PREFIX,
            "Test completed: all subscription types survived failover",
        );
    });
}
