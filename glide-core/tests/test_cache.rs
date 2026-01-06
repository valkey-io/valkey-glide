// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod utilities;

pub(crate) mod test_cache {

    use super::*;
    use glide_core::connection_request::ClientSideCache;
    use glide_core::connection_request::EvictionPolicy;
    use redis::Value;
    use redis::cache::glide_cache::CachedKeyType;
    use rstest::rstest;
    use utilities::cluster::*;
    use utilities::*;

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_basic_cache_hit_with_metrics(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: "test_cache".to_string().into(),
                        max_cache_kb: 1,
                        entry_ttl_seconds: None,
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            // Reset command stats for a clean slate
            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics.client.send_command(&reset_cmd, None).await.ok();

            // Set a key
            let mut set_cmd = redis::Cmd::new();
            set_cmd
                .arg("SET")
                .arg("cache_test_key")
                .arg("cache_test_value");
            let set_result = test_basics.client.send_command(&set_cmd, None).await;
            assert!(set_result.is_ok());

            // First GET - should hit the server (cache miss)
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("cache_test_key");
            let get_result = test_basics.client.send_command(&get_cmd, None).await;
            assert!(get_result.is_ok());
            assert_eq!(
                get_result.unwrap(),
                Value::BulkString(b"cache_test_value".to_vec())
            );

            // Entry count should be 1
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(1));

            // Second GET - should come from cache, NOT hit server
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("cache_test_key");
            let get_result = test_basics.client.send_command(&get_cmd, None).await;
            assert!(get_result.is_ok());
            assert_eq!(
                get_result.unwrap(),
                Value::BulkString(b"cache_test_value".to_vec())
            );

            // Third GET - should also come from cache
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("cache_test_key");
            let get_result = test_basics.client.send_command(&get_cmd, None).await;
            assert!(get_result.is_ok());
            assert_eq!(
                get_result.unwrap(),
                Value::BulkString(b"cache_test_value".to_vec())
            );

            // Verify only 1 GET hit the server
            assert_command_count(&mut test_basics.client, "GET", 1, use_cluster).await;

            // Verify metrics
            let hit_rate = test_basics.client.cache_hit_rate().unwrap();
            let miss_rate = test_basics.client.cache_miss_rate().unwrap();

            let hit_rate = match hit_rate {
                Value::Double(d) => d,
                _ => panic!("Expected Value::Double, got {:?}", hit_rate),
            };
            let miss_rate = match miss_rate {
                Value::Double(d) => d,
                _ => panic!("Expected Value::Double, got {:?}", miss_rate),
            };

            // 1 miss + 2 hits = 3 total
            assert_eq!(hit_rate, 2.0 / 3.0, "Expected 66.67% hit rate");
            assert_eq!(miss_rate, 1.0 / 3.0, "Expected 33.33% miss rate");
            assert!(
                (hit_rate + miss_rate - 1.0).abs() < 0.0001,
                "Rates should sum to 1.0"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_without_metrics(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: "test_cache_no_metrics".to_string().into(),
                        max_cache_kb: 10 * 1024,
                        entry_ttl_seconds: Some(60),
                        eviction_policy: None,
                        enable_metrics: false, // Disabled
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            // Reset command stats for a clean slate
            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics.client.send_command(&reset_cmd, None).await.ok();

            // Cache should work
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("key").arg("value");
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("key");
            test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            // Should come from cache
            test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();

            // Verify only 1 GET hit the server
            assert_command_count(&mut test_basics.client, "GET", 1, use_cluster).await;

            // But metrics should fail
            let hit_rate = test_basics.client.cache_hit_rate();
            assert!(hit_rate.is_err());
            assert!(
                hit_rate
                    .unwrap_err()
                    .to_string()
                    .contains("Cache metrics tracking is not enabled")
            );
            let miss_rate = test_basics.client.cache_miss_rate();
            assert!(miss_rate.is_err());
            assert!(
                miss_rate
                    .unwrap_err()
                    .to_string()
                    .contains("Cache metrics tracking is not enabled")
            );
            let evictions = test_basics.client.cache_evictions();
            assert!(evictions.is_err());
            assert!(
                evictions
                    .unwrap_err()
                    .to_string()
                    .contains("Cache metrics tracking is not enabled")
            );
            let expirations = test_basics.client.cache_expirations();
            assert!(expirations.is_err());
            assert!(
                expirations
                    .unwrap_err()
                    .to_string()
                    .contains("Cache metrics tracking is not enabled")
            );

            // Entry count should still work
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(1));
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_nil_values_not_cached(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: "test_cache_nil".to_string().into(),
                        max_cache_kb: 1,
                        entry_ttl_seconds: Some(60),
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics.client.send_command(&reset_cmd, None).await.ok();

            // GET non-existent key (returns NIL)
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("nonexistent_key");
            let result = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(result, Value::Nil);

            // Entry count should be 0
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(0));

            // GET again - should NOT be cached (NIL values not cached)
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("nonexistent_key");
            let result = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(result, Value::Nil);

            // Both GETs should hit the server
            assert_command_count(&mut test_basics.client, "GET", 2, use_cluster).await;

            // Miss rate should be 100%
            let miss_rate = test_basics.client.cache_miss_rate().unwrap();
            let miss_rate = match miss_rate {
                Value::Double(d) => d,
                _ => panic!("Expected Value::Double, got {:?}", miss_rate),
            };
            assert_eq!(miss_rate, 1.0, "Expected 100% miss rate");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_ttl_expiration(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: "test_cache_ttl".to_string().into(),
                        max_cache_kb: 1,
                        entry_ttl_seconds: Some(2), // 2 seconds
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics.client.send_command(&reset_cmd, None).await.ok();

            // Set and GET
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("ttl_key").arg("ttl_value");
            let set_res = test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();
            assert_eq!(set_res, Value::Okay);

            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("ttl_key");
            let mut get_res = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(get_res, Value::BulkString(b"ttl_value".to_vec()));

            // Entry count should be 1
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(1));

            // Second GET - from cache
            get_res = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(get_res, Value::BulkString(b"ttl_value".to_vec()));

            // Should have 1 GET so far
            assert_command_count(&mut test_basics.client, "GET", 1, use_cluster).await;

            // Wait for TTL to expire
            tokio::time::sleep(tokio::time::Duration::from_secs(3)).await;

            // GET after expiration - should hit server again
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("ttl_key");
            get_res = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(get_res, Value::BulkString(b"ttl_value".to_vec()));

            // Should now have 2 GETs
            assert_command_count(&mut test_basics.client, "GET", 2, use_cluster).await;

            // Expirations should be 1
            let expirations = test_basics.client.cache_expirations().unwrap();
            assert_eq!(expirations, Value::Int(1));

            // Miss rate should be 2 misses out of 3 total GETs = 66.67%
            let miss_rate = test_basics.client.cache_miss_rate().unwrap();
            let miss_rate = match miss_rate {
                Value::Double(d) => d,
                _ => panic!("Expected Value::Double, got {:?}", miss_rate),
            };
            assert_eq!(miss_rate, (2.0 / 3.0), "Expected 66.67% miss rate");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_multiple_keys(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: "test_cache_multi".to_string().into(),
                        max_cache_kb: 1,
                        entry_ttl_seconds: Some(60),
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics.client.send_command(&reset_cmd, None).await.ok();

            // Set 3 keys
            for i in 1..=3 {
                let mut set_cmd = redis::Cmd::new();
                set_cmd
                    .arg("SET")
                    .arg(format!("key{}", i))
                    .arg(format!("value{}", i));
                test_basics
                    .client
                    .send_command(&set_cmd, None)
                    .await
                    .unwrap();
            }

            // GET each key twice (miss + hit)
            for i in 1..=3 {
                let mut get_cmd = redis::Cmd::new();
                get_cmd.arg("GET").arg(format!("key{}", i));
                test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
                test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
            }

            // Should have 3 GETs (one per key, second GETs cached)
            assert_command_count(&mut test_basics.client, "GET", 3, use_cluster).await;

            // Entry count should be 3
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(3));

            // Verify metrics: 3 misses + 3 hits = 50% rate
            let hit_rate = test_basics.client.cache_hit_rate().unwrap();
            let hit_rate = match hit_rate {
                Value::Double(d) => d,
                _ => panic!("Expected Value::Double, got {:?}", hit_rate),
            };
            assert_eq!(hit_rate, 0.5);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_no_cache_all_requests_hit_server(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: None, // No cache
                    ..Default::default()
                },
            )
            .await;

            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics.client.send_command(&reset_cmd, None).await.ok();

            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("key").arg("value");
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            // GET 3 times - all should hit server
            for _ in 0..3 {
                let mut get_cmd = redis::Cmd::new();
                get_cmd.arg("GET").arg("key");
                test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
            }

            // All 3 should hit server
            assert_command_count(&mut test_basics.client, "GET", 3, use_cluster).await;

            // Metrics should error
            let mut result = test_basics.client.cache_hit_rate();
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("Client-side caching is not enabled")
            );
            result = test_basics.client.cache_miss_rate();
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("Client-side caching is not enabled")
            );
            result = test_basics.client.cache_evictions();
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("Client-side caching is not enabled")
            );
            result = test_basics.client.cache_expirations();
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("Client-side caching is not enabled")
            );
            result = test_basics.client.cache_entry_count();
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("Client-side caching is not enabled")
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_eviction_policy_lru(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let cache_id = "test_cache_lru";

            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: cache_id.to_string().into(),
                        max_cache_kb: 1, // 1 KB = 1024 bytes
                        entry_ttl_seconds: None,
                        eviction_policy: Some(EvictionPolicy::LRU.into()),
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            // Use larger values to force eviction faster
            // Each entry: key (~10 bytes) + value (~250 bytes) = ~260 bytes
            // 1024 / 260 = ~3 entries before eviction
            let value = "x".repeat(250);

            // Set and cache 3 keys
            for i in 1..=3 {
                let mut set_cmd = redis::Cmd::new();
                set_cmd.arg("SET").arg(format!("lru_key{}", i)).arg(&value);
                test_basics
                    .client
                    .send_command(&set_cmd, None)
                    .await
                    .unwrap();

                let mut get_cmd = redis::Cmd::new();
                get_cmd.arg("GET").arg(format!("lru_key{}", i));
                test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
            }

            // Cache should have 3 entries
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(3));

            // Access key1 to make it recently used (move to front in LRU)
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("lru_key1");
            test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();

            // Add 2 more keys - should evict key2 and key3 (least recently used)
            for i in 4..=5 {
                let mut set_cmd = redis::Cmd::new();
                set_cmd.arg("SET").arg(format!("lru_key{}", i)).arg(&value);
                test_basics
                    .client
                    .send_command(&set_cmd, None)
                    .await
                    .unwrap();

                let mut get_cmd = redis::Cmd::new();
                get_cmd.arg("GET").arg(format!("lru_key{}", i));
                test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
            }

            // Verify evictions
            let evictions = test_basics.client.cache_evictions().unwrap();
            assert_eq!(evictions, Value::Int(2));

            // TODO: CHECK MEMORY ??????????/
            // // Check cache after eviction
            // let (_entry_count, weighted_size) =
            //     get_cache_stats(cache_id).expect("Cache should exist");

            // // Verify size is under limit
            // assert!(weighted_size <= 1024, "Cache size should not exceed 1 KB");

            // Verify LRU behavior: key1 should still be cached (was accessed recently)
            assert!(
                is_key_cached(cache_id, b"lru_key1", CachedKeyType::String),
                "lru_key1 should still be cached (recently accessed)"
            );

            // key2 and key3 should be evicted (least recently used)
            assert!(
                !is_key_cached(cache_id, b"lru_key2", CachedKeyType::String)
                    && !is_key_cached(cache_id, b"lru_key3", CachedKeyType::String),
                "lru_key2 and lru_key3 should be evicted"
            );

            // New keys should be in cache
            assert!(
                is_key_cached(cache_id, b"lru_key4", CachedKeyType::String)
                    && is_key_cached(cache_id, b"lru_key5", CachedKeyType::String),
                "lru_key4 and lru_key5 should be in cache"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_eviction_policy_lfu(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let cache_id = "test_cache_lfu";

            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: cache_id.to_string().into(),
                        max_cache_kb: 1, // 1 KB
                        entry_ttl_seconds: None,
                        eviction_policy: Some(EvictionPolicy::LFU.into()),
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            // Use larger values to force eviction faster
            // Each entry: key (~10 bytes) + value (~250 bytes) = ~260 bytes
            // 1024 / 260 = ~3 entries before eviction
            let value = "x".repeat(250);

            // Set key1 and access it multiple times (high frequency)
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("lfu_key1").arg(&value);
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            // Access lfu_key1 5 times to build frequency
            for _ in 0..5 {
                let mut get_cmd = redis::Cmd::new();
                get_cmd.arg("GET").arg("lfu_key1");
                test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
            }
            // lfu_key1 frequency = 5

            // Set key2 and access it 2 times (medium frequency)
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("lfu_key2").arg(&value);
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            for _ in 0..2 {
                let mut get_cmd = redis::Cmd::new();
                get_cmd.arg("GET").arg("lfu_key2");
                test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
            }
            // lfu_key2 frequency = 2

            // Set key3 and with minimal access (low frequency)
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("lfu_key3").arg(&value);
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("lfu_key3");
            test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            // lfu_key3 frequency = 1

            // Entry count should be 3
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(3), "Cache should have 3 entries");

            // Set and get key4 - this would trigger eviction of key3 (lowest frequency)
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("lfu_key4").arg(&value);
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("lfu_key4");
            test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();

            // Entry count should still be 3
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(
                entry_count,
                Value::Int(3),
                "Cache should still have 3 entries"
            );

            // Verify evictions
            let evictions = test_basics.client.cache_evictions().unwrap();
            assert_eq!(
                evictions,
                Value::Int(1),
                "One entry should have been evicted"
            );

            // Verify LFU behavior: key1, key2 and key4 should be cached
            assert!(
                is_key_cached(cache_id, b"lfu_key1", CachedKeyType::String)
                    && is_key_cached(cache_id, b"lfu_key2", CachedKeyType::String)
                    && is_key_cached(cache_id, b"lfu_key4", CachedKeyType::String),
                "lfu_key1, lfu_key2 and lfu_key4 should be cached"
            );

            // key3 should be evicted (lowest frequency)
            assert!(
                !is_key_cached(cache_id, b"lfu_key3", CachedKeyType::String),
                "lfu_key3 should be evicted"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_shared_cache_between_clients(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let shared_cache_id = "shared_test_cache";

            // Create first client
            let mut test_basics1 = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: shared_cache_id.to_string().into(),
                        max_cache_kb: 10 * 1024,
                        entry_ttl_seconds: Some(60),
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            // Create second client with same cache_id
            let mut test_basics2 = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: shared_cache_id.to_string().into(),
                        max_cache_kb: 10 * 1024,
                        entry_ttl_seconds: Some(60),
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics1
                .client
                .send_command(&reset_cmd, None)
                .await
                .ok();

            // Client 1 sets and gets a key (populates shared cache)
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("shared_key").arg("shared_value");
            test_basics1
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("shared_key");
            let result = test_basics1
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(result, Value::BulkString(b"shared_value".to_vec()));

            // Entry count should be 1
            let entry_count = test_basics2.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(1));

            // Client 2 gets the same key - should hit shared cache
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("shared_key");
            let result = test_basics2
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(result, Value::BulkString(b"shared_value".to_vec()));

            // Only 1 GET should have hit the server (both clients used shared cache)
            assert_command_count(&mut test_basics1.client, "GET", 1, use_cluster).await;

            // Both clients should show cache hits in their metrics
            let hit_rate1 = test_basics1.client.cache_hit_rate().unwrap();
            let hit_rate2 = test_basics2.client.cache_hit_rate().unwrap();

            // Extract doubles
            let hit_rate1 = match hit_rate1 {
                Value::Double(d) => d,
                _ => panic!("Expected Value::Double"),
            };
            let hit_rate2 = match hit_rate2 {
                Value::Double(d) => d,
                _ => panic!("Expected Value::Double"),
            };

            // Metrics are shared
            assert_eq!(hit_rate1, 0.5);
            assert_eq!(hit_rate2, 0.5);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_wrong_key_type_raises_error(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let cache_id = "test_cache_wrong_key_type_raises_error";

            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: cache_id.to_string().into(),
                        max_cache_kb: 1, // 1 KB
                        entry_ttl_seconds: None,
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            // Set a string key and get it
            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("string_key").arg("string_value");
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();
            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("string_key");
            let result = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(result, Value::BulkString(b"string_value".to_vec()));

            // Entry count should be 1
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(1), "Cache should have 1 entry");

            // Perform HGETALL operation on the same key (wrong type)
            let mut hgetall_cmd = redis::Cmd::new();
            hgetall_cmd.arg("HGETALL").arg("string_key");
            let hgetall_result = test_basics.client.send_command(&hgetall_cmd, None).await;
            assert!(
                hgetall_result.is_err(),
                "HGETALL on string key should error"
            );
            assert!(
                hgetall_result
                    .unwrap_err()
                    .to_string()
                    .contains("WRONGTYPE"),
                "Error should indicate WRONGTYPE"
            );

            // Cache entry count should remain 1
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(
                entry_count,
                Value::Int(1),
                "Cache should still have 1 entry"
            );

            // Server HGETALL count should be 1
            assert_command_count(&mut test_basics.client, "HGETALL", 1, use_cluster).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_cacheable_commands(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let cache_id = "test_cache_cacheable_commands";

            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: cache_id.to_string().into(),
                        max_cache_kb: 1, // 1 KB
                        entry_ttl_seconds: None,
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            // Set a string key and get it
            let mut set_cmd = redis::Cmd::new();
            set_cmd
                .arg("SET")
                .arg("cacheable_key")
                .arg("cacheable_value");
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("cacheable_key");
            let result = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(result, Value::BulkString(b"cacheable_value".to_vec()));

            // Entry count should be 1
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(1), "Cache should have 1 entry");

            // HGETALL cmd- cacheable
            let mut hset_cmd = redis::Cmd::new();
            hset_cmd
                .arg("HSET")
                .arg("hash_key")
                .arg("field1")
                .arg("value1");
            test_basics
                .client
                .send_command(&hset_cmd, None)
                .await
                .unwrap();

            let mut hgetall_cmd = redis::Cmd::new();
            hgetall_cmd.arg("HGETALL").arg("hash_key");
            let hgetall_result = test_basics
                .client
                .send_command(&hgetall_cmd, None)
                .await
                .unwrap();
            assert_eq!(
                hgetall_result,
                Value::Map(vec![(
                    Value::BulkString(b"field1".to_vec()),
                    Value::BulkString(b"value1".to_vec())
                )])
            );

            // Entry count should be 2
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(2), "Cache should have 2 entries");

            // SMEMBERS cmd - cacheable
            let mut sadd_cmd = redis::Cmd::new();
            sadd_cmd.arg("SADD").arg("set_key").arg("member1");
            test_basics
                .client
                .send_command(&sadd_cmd, None)
                .await
                .unwrap();
            let mut smembers_cmd = redis::Cmd::new();
            smembers_cmd.arg("SMEMBERS").arg("set_key");
            let smembers_result = test_basics
                .client
                .send_command(&smembers_cmd, None)
                .await
                .unwrap();
            assert_eq!(
                smembers_result,
                Value::Set(vec![Value::BulkString(b"member1".to_vec())])
            );

            // Entry count should be 3
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(entry_count, Value::Int(3), "Cache should have 3 entries");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cache_oversized_entry_not_cached(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: false,
                    client_side_cache: Some(ClientSideCache {
                        cache_id: "test_cache_oversized".to_string().into(),
                        max_cache_kb: 1, // 1 KB
                        entry_ttl_seconds: None,
                        eviction_policy: None,
                        enable_metrics: true,
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;

            let mut reset_cmd = redis::Cmd::new();
            reset_cmd.arg("CONFIG").arg("RESETSTAT");
            test_basics.client.send_command(&reset_cmd, None).await.ok();

            // Create value larger than cache (2KB > 1KB)
            let large_value = "x".repeat(2048);

            let mut set_cmd = redis::Cmd::new();
            set_cmd.arg("SET").arg("large_key").arg(&large_value);
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            let mut get_cmd = redis::Cmd::new();
            get_cmd.arg("GET").arg("large_key");
            test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();

            // Should NOT be cached (too large)
            let entry_count = test_basics.client.cache_entry_count().unwrap();
            assert_eq!(
                entry_count,
                Value::Int(0),
                "Oversized entry should not be cached"
            );

            // Second GET should hit server again
            test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_command_count(&mut test_basics.client, "GET", 2, use_cluster).await;
        });
    }
}
