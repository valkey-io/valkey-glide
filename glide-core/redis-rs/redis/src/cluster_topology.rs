//! This module provides the functionality to refresh and calculate the cluster topology for Redis Cluster.

use crate::cluster::get_connection_addr;
#[cfg(feature = "cluster-async")]
use crate::cluster_client::SlotsRefreshRateLimit;
use crate::cluster_routing::Slot;
use crate::cluster_slotmap::{ReadFromReplicaStrategy, SlotMap};
use crate::types::AddressResolver;
use crate::{cluster::TlsMode, ErrorKind, RedisError, RedisResult, Value};
#[cfg(all(feature = "cluster-async", not(feature = "tokio-comp")))]
use async_std::sync::RwLock;
use logger_core::log_warn;
use std::collections::{hash_map::DefaultHasher, HashMap};
use std::hash::{Hash, Hasher};
use std::net::IpAddr;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;
use std::time::{Duration, SystemTime};
#[cfg(all(feature = "cluster-async", feature = "tokio-comp"))]
use tokio::sync::RwLock;
use tracing::info;

// Exponential backoff constants for retrying a slot refresh
/// The default number of refresh topology retries in the same call
pub const DEFAULT_NUMBER_OF_REFRESH_SLOTS_RETRIES: usize = 3;
/// The default base duration for retrying topology refresh
pub const DEFAULT_REFRESH_SLOTS_RETRY_BASE_DURATION_MILLIS: u64 = 500;
/// The default base factor for retrying topology refresh
pub const DEFAULT_REFRESH_SLOTS_RETRY_BASE_FACTOR: f64 = 1.5;
// Constants for the intervals between two independent consecutive refresh slots calls
/// The default wait duration between two consecutive refresh slots calls
#[cfg(feature = "cluster-async")]
pub const DEFAULT_SLOTS_REFRESH_WAIT_DURATION: Duration = Duration::from_secs(15);
/// The default maximum jitter duration to add to the refresh slots wait duration
#[cfg(feature = "cluster-async")]
pub const DEFAULT_SLOTS_REFRESH_MAX_JITTER_MILLI: u64 = 15 * 1000; // 15 seconds

pub(crate) const SLOT_SIZE: u16 = 16384;
pub(crate) type TopologyHash = u64;

/// Represents the state of slot refresh operations.
#[cfg(feature = "cluster-async")]
pub(crate) struct SlotRefreshState {
    /// Indicates if a slot refresh is currently in progress
    pub(crate) in_progress: AtomicBool,
    /// The last slot refresh run timestamp
    pub(crate) last_run: Arc<RwLock<Option<SystemTime>>>,
    pub(crate) rate_limiter: SlotsRefreshRateLimit,
}

#[cfg(feature = "cluster-async")]
impl SlotRefreshState {
    pub(crate) fn new(rate_limiter: SlotsRefreshRateLimit) -> Self {
        Self {
            in_progress: AtomicBool::new(false),
            last_run: Arc::new(RwLock::new(None)),
            rate_limiter,
        }
    }
}

#[derive(Debug)]
pub(crate) struct TopologyView {
    pub(crate) hash_value: TopologyHash,
    pub(crate) nodes_count: u16,
    slots_and_count: (u16, Vec<Slot>),
    address_to_ip_map: HashMap<String, IpAddr>,
}

impl PartialEq for TopologyView {
    fn eq(&self, other: &Self) -> bool {
        self.hash_value == other.hash_value
    }
}

impl Eq for TopologyView {}

pub(crate) fn slot(key: &[u8]) -> u16 {
    crc16::State::<crc16::XMODEM>::calculate(key) % SLOT_SIZE
}

fn get_hashtag(key: &[u8]) -> Option<&[u8]> {
    let open = key.iter().position(|v| *v == b'{')?;

    let close = key[open..].iter().position(|v| *v == b'}')?;

    let rv = &key[open + 1..open + close];
    (!rv.is_empty()).then_some(rv)
}

/// Returns the slot that matches `key`.
pub fn get_slot(key: &[u8]) -> u16 {
    let key = match get_hashtag(key) {
        Some(tag) => tag,
        None => key,
    };

    slot(key)
}

/// Parsed slot data from CLUSTER SLOTS response.
pub(crate) struct ParsedSlotsResult {
    /// Total number of slots covered
    pub(crate) slots_count: u16,
    /// Slot definitions with their ranges and node assignments
    pub(crate) slots: Vec<Slot>,
    /// Mapping from node addresses (hostname:port) to their IP addresses.
    /// Populated from node metadata when available.
    pub(crate) address_to_ip_map: HashMap<String, IpAddr>,
}

/// Parses slot data from raw CLUSTER SLOTS response.
///
/// Extracts slot ranges, node assignments, and IP address mappings from the
/// Redis CLUSTER SLOTS response format.
pub(crate) fn parse_and_count_slots(
    raw_slot_resp: &Value,
    tls: Option<TlsMode>,
    addr_of_answering_node: &str,
    address_resolver: Option<&dyn AddressResolver>,
) -> RedisResult<ParsedSlotsResult> {
    // Parse response.
    let mut slots = Vec::with_capacity(2);
    let mut slots_count = 0;
    let mut address_to_ip_map = HashMap::new();

    if let Value::Array(items) = raw_slot_resp {
        let mut iter = items.iter();
        while let Some(Value::Array(item)) = iter.next() {
            if item.len() < 3 {
                continue;
            }

            // Parse slot range boundaries
            let start = if let Value::Int(start) = item[0] {
                start as u16
            } else {
                continue;
            };

            let end = if let Value::Int(end) = item[1] {
                end as u16
            } else {
                continue;
            };

            // Parses a single node entry from CLUSTER SLOTS response.
            //
            // Node format: [address, port, node_id, metadata]
            // - address: Preferred endpoint (Either an IP address, hostname, or NULL)
            // - metadata: A map or array of key-value pairs of additional networking metadata,
            //   for example ["ip", "..."] or ["hostname", "..."]
            let mut nodes: Vec<String> = item
                .iter()
                .skip(2)
                .filter_map(|node| {
                    if let Value::Array(node) = node {
                        if node.len() < 2 {
                            return None;
                        }
                        // According to the CLUSTER SLOTS documentation:
                        // If the received hostname is an empty string or NULL, clients should utilize the hostname of the responding node.
                        // However, if the received hostname is "?", it should be regarded as an indication of an unknown node.
                        let primary_identifier = if let Value::BulkString(ref bytes) = node[0] {
                            let received_address = String::from_utf8_lossy(bytes);
                            if received_address.is_empty() {
                                addr_of_answering_node.into()
                            } else if received_address == "?" {
                                return None;
                            } else {
                                received_address
                            }
                        } else if let Value::Nil = node[0] {
                            addr_of_answering_node.into()
                        } else {
                            return None;
                        };

                        if primary_identifier.is_empty() {
                            return None;
                        }

                        // Parse port from node[1]
                        let port = if let Value::Int(port) = node[1] {
                            port as u16
                        } else {
                            return None;
                        };

                        // node[2] contains the node ID, which we don't need here.

                        // Extract metadata (IP and/or hostname) from node[3] if present.
                        // The metadata format varies by server version/configuration:
                        // - Older versions or RESP2: Array of alternating key-value pairs
                        // - Newer versions with RESP3: Map of key-value pairs
                        let mut metadata_ip: Option<IpAddr> = None;
                        let mut metadata_hostname: Option<String> = None;
                        if node.len() >= 4 {
                            let mut process_kv = |key_bytes: &[u8], value_bytes: &[u8]| {
                                let key_str = String::from_utf8_lossy(key_bytes);
                                if key_str == "ip" {
                                    metadata_ip =
                                        String::from_utf8_lossy(value_bytes).parse::<IpAddr>().ok();
                                } else if key_str == "hostname" {
                                    let h = String::from_utf8_lossy(value_bytes);
                                    if !h.is_empty() {
                                        metadata_hostname = Some(h.into_owned());
                                    }
                                }
                                // Other keys are ignored - we only need ip and hostname
                            };

                            match &node[3] {
                                // Array format: ["ip", "127.0.0.1", "hostname", "example.com", ...]
                                Value::Array(metadata) => {
                                    if metadata.len() % 2 != 0 {
                                        log_warn(
                                                "cluster_topology",
                                                "Node metadata array has odd length, some entries may be skipped"
                                            );
                                    }
                                    for chunk in metadata.chunks_exact(2) {
                                        if let (Value::BulkString(key), Value::BulkString(value)) =
                                            (&chunk[0], &chunk[1])
                                        {
                                            process_kv(key, value);
                                        }
                                    }
                                }
                                // Map format: {("ip", "127.0.0.1"), ("hostname", "example.com")}
                                Value::Map(metadata) => {
                                    for (key, value) in metadata {
                                        if let (Value::BulkString(key_bytes), Value::BulkString(value_bytes)) =
                                            (key, value)
                                        {
                                            process_kv(key_bytes, value_bytes);
                                        }
                                    }
                                }
                                other => {
                                    log_warn(
                                        "cluster_topology",
                                        format!("Unexpected node metadata format: {:?}", other)
                                    );
                                }
                            }
                        }

                        // Determine canonical hostname and IP based on response format:
                        // - Default/OSS cluster: primary_identifier is IP, hostname may come from metadata
                        // - ElastiCache with TLS: primary_identifier is hostname, IP comes from metadata
                        let (canonical_hostname, resolved_ip) =
                            if let Ok(primary_as_ip) = primary_identifier.parse::<IpAddr>() {
                                // Primary is IP address - use hostname from metadata if available
                                (
                                    metadata_hostname
                                        .unwrap_or_else(|| primary_identifier.into_owned()),
                                    Some(primary_as_ip),
                                )
                            } else {
                                // Primary is hostname - use IP from metadata if available
                                (primary_identifier.into_owned(), metadata_ip)
                            };

                        let connection_addr =
                            get_connection_addr(canonical_hostname, port, tls, None, address_resolver).to_string();

                        // Store IP mapping if we have an IP for this node
                        if let Some(ip) = resolved_ip {
                            address_to_ip_map.insert(connection_addr.clone(), ip);
                        }

                        Some(connection_addr)
                    } else {
                        None
                    }
                })
                .collect();

            if nodes.is_empty() {
                continue;
            }
            slots_count += end - start + 1;

            let mut replicas = nodes.split_off(1);
            // we sort the replicas, because different nodes in a cluster might return the same slot view
            // with different order of the replicas, which might cause the views to be considered evaluated as not equal.
            replicas.sort_unstable();
            slots.push(Slot::new(start, end, nodes.pop().unwrap(), replicas));
        }
    }
    if slots.is_empty() {
        return Err(RedisError::from((
            ErrorKind::ResponseError,
            "Error parsing slots: No healthy node found",
            format!("Raw slot map response: {raw_slot_resp:?}"),
        )));
    }

    Ok(ParsedSlotsResult {
        slots_count,
        slots,
        address_to_ip_map,
    })
}

fn calculate_hash<T: Hash>(t: &T) -> u64 {
    let mut s = DefaultHasher::new();
    t.hash(&mut s);
    s.finish()
}

pub(crate) fn calculate_topology<'a>(
    topology_views: impl Iterator<Item = (&'a str, &'a Value)>,
    curr_retry: usize,
    tls_mode: Option<TlsMode>,
    num_of_queried_nodes: usize,
    read_from_replica: ReadFromReplicaStrategy,
    address_resolver: Option<&dyn AddressResolver>,
) -> RedisResult<(SlotMap, TopologyHash)> {
    let mut hash_view_map = HashMap::new();
    for (host, view) in topology_views {
        if let Ok(ParsedSlotsResult {
            slots_count,
            slots,
            address_to_ip_map,
        }) = parse_and_count_slots(view, tls_mode, host, address_resolver)
        {
            let hash_value = calculate_hash(&(slots_count, &slots));
            let topology_entry = hash_view_map.entry(hash_value).or_insert(TopologyView {
                hash_value,
                nodes_count: 0,
                slots_and_count: (slots_count, slots),
                address_to_ip_map,
            });
            topology_entry.nodes_count += 1;
        }
    }
    let mut non_unique_max_node_count = false;
    let mut vec_iter = hash_view_map.into_values();
    let mut most_frequent_topology = match vec_iter.next() {
        Some(view) => view,
        None => {
            return Err(RedisError::from((
                ErrorKind::ResponseError,
                "No topology views found",
            )));
        }
    };
    // Find the most frequent topology view
    for curr_view in vec_iter {
        match most_frequent_topology
            .nodes_count
            .cmp(&curr_view.nodes_count)
        {
            std::cmp::Ordering::Less => {
                most_frequent_topology = curr_view;
                non_unique_max_node_count = false;
            }
            std::cmp::Ordering::Greater => continue,
            std::cmp::Ordering::Equal => {
                non_unique_max_node_count = true;
                let seen_slot_count = most_frequent_topology.slots_and_count.0;

                // We choose as the greater view the one with higher slot coverage.
                if let std::cmp::Ordering::Less = seen_slot_count.cmp(&curr_view.slots_and_count.0)
                {
                    most_frequent_topology = curr_view;
                }
            }
        }
    }

    let parse_and_built_result = |most_frequent_topology: TopologyView| {
        info!(
            "calculate_topology found topology map:\n{:?}",
            most_frequent_topology
        );
        let slots_data = most_frequent_topology.slots_and_count.1;
        Ok((
            SlotMap::new(
                slots_data,
                most_frequent_topology.address_to_ip_map,
                read_from_replica,
            ),
            most_frequent_topology.hash_value,
        ))
    };

    if non_unique_max_node_count {
        // More than a single most frequent view was found
        // If we reached the last retry, or if we it's a 2-nodes cluster, we'll return a view with the highest slot coverage, and that is one of most agreed on views.
        if curr_retry >= DEFAULT_NUMBER_OF_REFRESH_SLOTS_RETRIES || num_of_queried_nodes < 3 {
            return parse_and_built_result(most_frequent_topology);
        }
        return Err(RedisError::from((
            ErrorKind::ResponseError,
            "Slot refresh error: Failed to obtain a majority in topology views",
        )));
    }

    // The rate of agreement of the topology view is determined by assessing the number of nodes that share this view out of the total number queried
    let agreement_rate = most_frequent_topology.nodes_count as f32 / num_of_queried_nodes as f32;
    const MIN_AGREEMENT_RATE: f32 = 0.2;
    if agreement_rate >= MIN_AGREEMENT_RATE {
        parse_and_built_result(most_frequent_topology)
    } else {
        Err(RedisError::from((
            ErrorKind::ResponseError,
            "Slot refresh error: The accuracy of the topology view is too low",
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cluster_routing::ShardAddrs;

    #[test]
    fn test_get_hashtag() {
        assert_eq!(get_hashtag(&b"foo{bar}baz"[..]), Some(&b"bar"[..]));
        assert_eq!(get_hashtag(&b"foo{}{baz}"[..]), None);
        assert_eq!(get_hashtag(&b"foo{{bar}}zap"[..]), Some(&b"{bar"[..]));
    }

    fn slot_value_with_replicas(start: u16, end: u16, nodes: Vec<(&str, u16)>) -> Value {
        let mut node_values: Vec<Value> = nodes
            .iter()
            .map(|(host, port)| {
                Value::Array(vec![
                    Value::BulkString(host.as_bytes().to_vec()),
                    Value::Int(*port as i64),
                ])
            })
            .collect();
        let mut slot_vec = vec![Value::Int(start as i64), Value::Int(end as i64)];
        slot_vec.append(&mut node_values);
        Value::Array(slot_vec)
    }

    #[derive(Clone, Copy)]
    enum MetadataFormat {
        Array,
        Map,
    }

    fn slot_value_with_metadata(
        start: u16,
        end: u16,
        nodes: Vec<(&str, u16, Option<Vec<(&str, &str)>>)>, // (address, port, metadata)
        format: MetadataFormat,
    ) -> Value {
        let node_values: Vec<Value> = nodes
            .iter()
            .map(|(host, port, metadata)| {
                let mut node_vec = vec![
                    Value::BulkString(host.as_bytes().to_vec()),
                    Value::Int(*port as i64),
                    Value::BulkString(b"node-id-placeholder".to_vec()), // node ID
                ];

                if let Some(meta) = metadata {
                    let metadata_value = match format {
                        MetadataFormat::Array => {
                            let meta_values: Vec<Value> = meta
                                .iter()
                                .flat_map(|(k, v)| {
                                    vec![
                                        Value::BulkString(k.as_bytes().to_vec()),
                                        Value::BulkString(v.as_bytes().to_vec()),
                                    ]
                                })
                                .collect();
                            Value::Array(meta_values)
                        }
                        MetadataFormat::Map => {
                            let meta_pairs: Vec<(Value, Value)> = meta
                                .iter()
                                .map(|(k, v)| {
                                    (
                                        Value::BulkString(k.as_bytes().to_vec()),
                                        Value::BulkString(v.as_bytes().to_vec()),
                                    )
                                })
                                .collect();
                            Value::Map(meta_pairs)
                        }
                    };
                    node_vec.push(metadata_value);
                }

                Value::Array(node_vec)
            })
            .collect();

        let mut slot_vec = vec![Value::Int(start as i64), Value::Int(end as i64)];
        slot_vec.extend(node_values);
        Value::Array(slot_vec)
    }

    fn slot_value(start: u16, end: u16, node: &str, port: u16) -> Value {
        slot_value_with_replicas(start, end, vec![(node, port)])
    }

    fn run_with_both_formats<F>(test_fn: F)
    where
        F: Fn(MetadataFormat),
    {
        test_fn(MetadataFormat::Array);
        test_fn(MetadataFormat::Map);
    }

    #[test]
    fn parse_slots_with_different_replicas_order_returns_the_same_view() {
        let view1 = Value::Array(vec![
            slot_value_with_replicas(
                0,
                4000,
                vec![
                    ("primary1", 6379),
                    ("replica1_1", 6379),
                    ("replica1_2", 6379),
                    ("replica1_3", 6379),
                ],
            ),
            slot_value_with_replicas(
                4001,
                8000,
                vec![
                    ("primary2", 6379),
                    ("replica2_1", 6379),
                    ("replica2_2", 6379),
                    ("replica2_3", 6379),
                ],
            ),
            slot_value_with_replicas(
                8001,
                16383,
                vec![
                    ("primary3", 6379),
                    ("replica3_1", 6379),
                    ("replica3_2", 6379),
                    ("replica3_3", 6379),
                ],
            ),
        ]);

        let view2 = Value::Array(vec![
            slot_value_with_replicas(
                0,
                4000,
                vec![
                    ("primary1", 6379),
                    ("replica1_1", 6379),
                    ("replica1_3", 6379),
                    ("replica1_2", 6379),
                ],
            ),
            slot_value_with_replicas(
                4001,
                8000,
                vec![
                    ("primary2", 6379),
                    ("replica2_2", 6379),
                    ("replica2_3", 6379),
                    ("replica2_1", 6379),
                ],
            ),
            slot_value_with_replicas(
                8001,
                16383,
                vec![
                    ("primary3", 6379),
                    ("replica3_3", 6379),
                    ("replica3_1", 6379),
                    ("replica3_2", 6379),
                ],
            ),
        ]);

        let res1 = parse_and_count_slots(&view1, None, "foo", None).unwrap();
        let res2 = parse_and_count_slots(&view2, None, "foo", None).unwrap();
        assert_eq!(
            calculate_hash(&(res1.slots_count, &res1.slots)),
            calculate_hash(&(res2.slots_count, &res2.slots))
        );
        assert_eq!(res1.slots_count, res2.slots_count);
        assert_eq!(res1.slots.len(), res2.slots.len());
        let check = res1
            .slots
            .into_iter()
            .zip(res2.slots)
            .all(|(first, second)| first.replicas() == second.replicas());
        assert!(check);
    }

    #[test]
    fn parse_slots_returns_slots_with_host_name_if_missing() {
        let view = Value::Array(vec![slot_value(0, 4000, "", 6379)]);

        let ParsedSlotsResult {
            slots_count, slots, ..
        } = parse_and_count_slots(&view, None, "node", None).unwrap();
        assert_eq!(slots_count, 4001);
        assert_eq!(slots[0].master(), "node:6379");
    }

    #[test]
    fn should_parse_and_hash_regardless_of_missing_host_name_and_replicas_order() {
        let view1 = Value::Array(vec![
            slot_value(0, 4000, "", 6379),
            slot_value(4001, 8000, "node2", 6380),
            slot_value_with_replicas(
                8001,
                16383,
                vec![
                    ("node3", 6379),
                    ("replica3_1", 6379),
                    ("replica3_2", 6379),
                    ("replica3_3", 6379),
                ],
            ),
        ]);

        let view2 = Value::Array(vec![
            slot_value(0, 4000, "node1", 6379),
            slot_value(4001, 8000, "node2", 6380),
            slot_value_with_replicas(
                8001,
                16383,
                vec![
                    ("", 6379),
                    ("replica3_3", 6379),
                    ("replica3_2", 6379),
                    ("replica3_1", 6379),
                ],
            ),
        ]);

        let res1 = parse_and_count_slots(&view1, None, "node1", None).unwrap();
        let res2 = parse_and_count_slots(&view2, None, "node3", None).unwrap();

        assert_eq!(
            calculate_hash(&(res1.slots_count, &res1.slots)),
            calculate_hash(&(res2.slots_count, &res2.slots))
        );
        assert_eq!(res1.slots_count, res2.slots_count);
        assert_eq!(res1.slots.len(), res2.slots.len());
        let equality_check = res1
            .slots
            .iter()
            .zip(&res2.slots)
            .all(|(first, second)| first.start == second.start && first.end == second.end);
        assert!(equality_check);
        let replicas_check = res1
            .slots
            .iter()
            .zip(&res2.slots)
            .all(|(first, second)| first.replicas() == second.replicas());
        assert!(replicas_check);
    }

    #[test]
    fn parse_slots_hostname_primary_format_extracts_ip_from_metadata() {
        run_with_both_formats(|format| {
            let view = Value::Array(vec![slot_value_with_metadata(
                0,
                16383,
                vec![
                    (
                        "valkey-node-1.example.com",
                        6379,
                        Some(vec![("ip", "172.31.24.34")]),
                    ),
                    (
                        "valkey-node-2.example.com",
                        6379,
                        Some(vec![("ip", "172.31.24.35")]),
                    ),
                ],
                format,
            )]);

            let ParsedSlotsResult {
                slots_count,
                slots,
                address_to_ip_map,
            } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

            assert_eq!(slots_count, 16384);
            assert_eq!(slots.len(), 1);
            assert_eq!(slots[0].master(), "valkey-node-1.example.com:6379");
            assert_eq!(
                slots[0].replicas(),
                vec!["valkey-node-2.example.com:6379".to_string()]
            );

            assert_eq!(address_to_ip_map.len(), 2);
            assert_eq!(
                address_to_ip_map.get("valkey-node-1.example.com:6379"),
                Some(&"172.31.24.34".parse().unwrap())
            );
            assert_eq!(
                address_to_ip_map.get("valkey-node-2.example.com:6379"),
                Some(&"172.31.24.35".parse().unwrap())
            );
        });
    }

    #[test]
    fn parse_slots_ip_primary_format_extracts_hostname_from_metadata() {
        run_with_both_formats(|format| {
            let view = Value::Array(vec![slot_value_with_metadata(
                0,
                16383,
                vec![
                    (
                        "127.0.0.1",
                        30001,
                        Some(vec![("hostname", "host-1.valkey.example.com")]),
                    ),
                    (
                        "127.0.0.2",
                        30002,
                        Some(vec![("hostname", "host-2.valkey.example.com")]),
                    ),
                ],
                format,
            )]);

            let ParsedSlotsResult {
                slots_count,
                slots,
                address_to_ip_map,
            } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

            assert_eq!(slots_count, 16384);
            assert_eq!(slots.len(), 1);
            assert_eq!(slots[0].master(), "host-1.valkey.example.com:30001");
            assert_eq!(
                slots[0].replicas(),
                vec!["host-2.valkey.example.com:30002".to_string()]
            );

            assert_eq!(address_to_ip_map.len(), 2);
            assert_eq!(
                address_to_ip_map.get("host-1.valkey.example.com:30001"),
                Some(&"127.0.0.1".parse().unwrap())
            );
            assert_eq!(
                address_to_ip_map.get("host-2.valkey.example.com:30002"),
                Some(&"127.0.0.2".parse().unwrap())
            );
        });
    }

    #[test]
    fn parse_slots_valkey_format_without_hostname_uses_ip_as_address() {
        run_with_both_formats(|format| {
            let view = Value::Array(vec![slot_value_with_metadata(
                0,
                16383,
                vec![
                    ("192.168.1.1", 6379, Some(vec![("somekey", "somevalue")])),
                    ("192.168.1.2", 6379, None),
                ],
                format,
            )]);

            let ParsedSlotsResult {
                slots_count,
                slots,
                address_to_ip_map,
            } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

            assert_eq!(slots_count, 16384);
            assert_eq!(slots.len(), 1);
            assert_eq!(slots[0].master(), "192.168.1.1:6379");
            assert_eq!(slots[0].replicas(), vec!["192.168.1.2:6379".to_string()]);

            assert_eq!(address_to_ip_map.len(), 2);
            assert_eq!(
                address_to_ip_map.get("192.168.1.1:6379"),
                Some(&"192.168.1.1".parse().unwrap())
            );
            assert_eq!(
                address_to_ip_map.get("192.168.1.2:6379"),
                Some(&"192.168.1.2".parse().unwrap())
            );
        });
    }

    #[test]
    fn parse_slots_no_metadata_no_ip_mapping() {
        // Standard format without metadata - no IP mappings
        let view = Value::Array(vec![slot_value_with_replicas(
            0,
            16383,
            vec![("node1", 6379), ("replica1", 6379)],
        )]);

        let ParsedSlotsResult {
            slots,
            address_to_ip_map,
            ..
        } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

        assert_eq!(slots[0].master(), "node1:6379");
        assert!(address_to_ip_map.is_empty());
    }

    #[test]
    fn parse_slots_mixed_nodes_with_and_without_ip() {
        run_with_both_formats(|format| {
            let view = Value::Array(vec![slot_value_with_metadata(
                0,
                16383,
                vec![
                    ("primary.example.com", 6379, Some(vec![("ip", "10.0.0.1")])),
                    ("replica.example.com", 6379, None),
                ],
                format,
            )]);

            let ParsedSlotsResult {
                address_to_ip_map, ..
            } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

            assert_eq!(address_to_ip_map.len(), 1);
            assert_eq!(
                address_to_ip_map.get("primary.example.com:6379"),
                Some(&"10.0.0.1".parse().unwrap())
            );
            assert!(!address_to_ip_map.contains_key("replica.example.com:6379"));
        });
    }

    #[test]
    fn parse_slots_invalid_ip_in_metadata_ignored() {
        run_with_both_formats(|format| {
            let view = Value::Array(vec![slot_value_with_metadata(
                0,
                16383,
                vec![("node1.example.com", 6379, Some(vec![("ip", "not-an-ip")]))],
                format,
            )]);

            let ParsedSlotsResult {
                slots,
                address_to_ip_map,
                ..
            } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

            assert_eq!(slots[0].master(), "node1.example.com:6379");
            assert!(address_to_ip_map.is_empty());
        });
    }

    #[test]
    fn parse_slots_multiple_slot_ranges_with_ip_mapping() {
        run_with_both_formats(|format| {
            let view = Value::Array(vec![
                slot_value_with_metadata(
                    0,
                    5461,
                    vec![
                        (
                            "shard1-primary.example.com",
                            6379,
                            Some(vec![("ip", "10.0.1.1")]),
                        ),
                        (
                            "shard1-replica.example.com",
                            6379,
                            Some(vec![("ip", "10.0.1.2")]),
                        ),
                    ],
                    format,
                ),
                slot_value_with_metadata(
                    5462,
                    10922,
                    vec![(
                        "shard2-primary.example.com",
                        6379,
                        Some(vec![("ip", "10.0.2.1")]),
                    )],
                    format,
                ),
                slot_value_with_metadata(
                    10923,
                    16383,
                    vec![(
                        "shard3-primary.example.com",
                        6379,
                        Some(vec![("ip", "10.0.3.1")]),
                    )],
                    format,
                ),
            ]);

            let ParsedSlotsResult {
                slots_count,
                slots,
                address_to_ip_map,
            } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

            assert_eq!(slots_count, 16384);
            assert_eq!(slots.len(), 3);

            assert_eq!(address_to_ip_map.len(), 4);
            assert_eq!(
                address_to_ip_map.get("shard1-primary.example.com:6379"),
                Some(&"10.0.1.1".parse().unwrap())
            );
            assert_eq!(
                address_to_ip_map.get("shard2-primary.example.com:6379"),
                Some(&"10.0.2.1".parse().unwrap())
            );
            assert_eq!(
                address_to_ip_map.get("shard1-replica.example.com:6379"),
                Some(&"10.0.1.2".parse().unwrap())
            );
            assert_eq!(
                address_to_ip_map.get("shard3-primary.example.com:6379"),
                Some(&"10.0.3.1".parse().unwrap())
            );
        });
    }

    #[test]
    fn parse_slots_ipv6_address_in_metadata() {
        run_with_both_formats(|format| {
            let view = Value::Array(vec![slot_value_with_metadata(
                0,
                16383,
                vec![("node1.example.com", 6379, Some(vec![("ip", "2001:db8::1")]))],
                format,
            )]);

            let ParsedSlotsResult {
                address_to_ip_map, ..
            } = parse_and_count_slots(&view, None, "fallback", None).unwrap();

            assert_eq!(address_to_ip_map.len(), 1);
            assert_eq!(
                address_to_ip_map.get("node1.example.com:6379"),
                Some(&"2001:db8::1".parse().unwrap())
            );
        });
    }

    #[test]
    fn parse_slots_empty_hostname_in_metadata_falls_back_to_ip() {
        // ElastiCache (plaintext, cluster mode) returns hostname: "" (empty string)
        // in CLUSTER SLOTS metadata. The parser should treat this as absent and
        // fall back to the IP address from the primary identifier.
        run_with_both_formats(|format| {
            let view = Value::Array(vec![slot_value_with_metadata(
                0,
                16383,
                vec![
                    ("172.20.43.71", 6379, Some(vec![("hostname", "")])),
                    ("172.20.78.117", 6379, Some(vec![("hostname", "")])),
                ],
                format,
            )]);

            let ParsedSlotsResult {
                slots_count,
                slots,
                address_to_ip_map,
            } = parse_and_count_slots(&view, None, "fallback").unwrap();

            assert_eq!(slots_count, 16384);
            assert_eq!(slots.len(), 1);
            // Should use the IP as the address, not the empty hostname
            assert_eq!(slots[0].master(), "172.20.43.71:6379");
            assert_eq!(slots[0].replicas(), vec!["172.20.78.117:6379".to_string()]);

            assert_eq!(address_to_ip_map.len(), 2);
            assert_eq!(
                address_to_ip_map.get("172.20.43.71:6379"),
                Some(&"172.20.43.71".parse().unwrap())
            );
            assert_eq!(
                address_to_ip_map.get("172.20.78.117:6379"),
                Some(&"172.20.78.117".parse().unwrap())
            );
        });
    }

    enum ViewType {
        SingleNodeViewFullCoverage,
        SingleNodeViewMissingSlots,
        TwoNodesViewFullCoverage,
        TwoNodesViewMissingSlots,
    }
    fn get_view(view_type: &ViewType) -> (&str, Value) {
        match view_type {
            ViewType::SingleNodeViewFullCoverage => (
                "first",
                Value::Array(vec![slot_value(0, 16383, "node1", 6379)]),
            ),
            ViewType::SingleNodeViewMissingSlots => (
                "second",
                Value::Array(vec![slot_value(0, 4000, "node1", 6379)]),
            ),
            ViewType::TwoNodesViewFullCoverage => (
                "third",
                Value::Array(vec![
                    slot_value(0, 4000, "node1", 6379),
                    slot_value(4001, 16383, "node2", 6380),
                ]),
            ),
            ViewType::TwoNodesViewMissingSlots => (
                "fourth",
                Value::Array(vec![
                    slot_value(0, 3000, "node3", 6381),
                    slot_value(4001, 16383, "node4", 6382),
                ]),
            ),
        }
    }

    fn get_node_addr(name: &str, port: u16) -> Arc<ShardAddrs> {
        Arc::new(ShardAddrs::new(format!("{name}:{port}").into(), Vec::new()))
    }

    fn collect_shard_addrs(slot_map: &SlotMap) -> Vec<Arc<ShardAddrs>> {
        let mut shard_addrs: Vec<Arc<ShardAddrs>> = slot_map
            .nodes_map()
            .iter()
            .map(|map_item| {
                let shard_addrs = map_item.value().1.clone();
                shard_addrs.clone()
            })
            .collect();
        shard_addrs.sort_unstable();
        shard_addrs
    }

    #[test]
    fn test_topology_calculator_4_nodes_queried_has_a_majority_success() {
        // 4 nodes queried (1 error): Has a majority, single_node_view should be chosen
        let queried_nodes: usize = 4;
        let topology_results = vec![
            get_view(&ViewType::SingleNodeViewFullCoverage),
            get_view(&ViewType::SingleNodeViewFullCoverage),
            get_view(&ViewType::TwoNodesViewFullCoverage),
        ];

        let (topology_view, _) = calculate_topology(
            topology_results.iter().map(|(addr, value)| (*addr, value)),
            1,
            None,
            queried_nodes,
            ReadFromReplicaStrategy::AlwaysFromPrimary,
            None,
        )
        .unwrap();
        let res = collect_shard_addrs(&topology_view);
        let node_1 = get_node_addr("node1", 6379);
        let expected = vec![node_1];
        assert_eq!(res, expected);
    }

    #[test]
    fn test_topology_calculator_3_nodes_queried_no_majority_has_more_retries_raise_error() {
        // 3 nodes queried: No majority, should return an error
        let queried_nodes = 3;
        let topology_results = vec![
            get_view(&ViewType::SingleNodeViewFullCoverage),
            get_view(&ViewType::TwoNodesViewFullCoverage),
            get_view(&ViewType::TwoNodesViewMissingSlots),
        ];
        let topology_view = calculate_topology(
            topology_results.iter().map(|(addr, value)| (*addr, value)),
            1,
            None,
            queried_nodes,
            ReadFromReplicaStrategy::AlwaysFromPrimary,
            None,
        );
        assert!(topology_view.is_err());
    }

    #[test]
    fn test_topology_calculator_3_nodes_queried_no_majority_last_retry_success() {
        // 3 nodes queried:: No majority, last retry, should get the view that has a full slot coverage
        let queried_nodes = 3;
        let topology_results = vec![
            get_view(&ViewType::SingleNodeViewMissingSlots),
            get_view(&ViewType::TwoNodesViewFullCoverage),
            get_view(&ViewType::TwoNodesViewMissingSlots),
        ];
        let (topology_view, _) = calculate_topology(
            topology_results.iter().map(|(addr, value)| (*addr, value)),
            3,
            None,
            queried_nodes,
            ReadFromReplicaStrategy::AlwaysFromPrimary,
            None,
        )
        .unwrap();
        let res = collect_shard_addrs(&topology_view);
        let node_1 = get_node_addr("node1", 6379);
        let node_2 = get_node_addr("node2", 6380);
        let expected = vec![node_1, node_2];
        assert_eq!(res, expected);
    }

    #[test]
    fn test_topology_calculator_2_nodes_queried_no_majority_return_full_slot_coverage_view() {
        // 2 nodes queried: No majority, should get the view that has a full slot coverage
        let queried_nodes = 2;
        let topology_results = [
            get_view(&ViewType::TwoNodesViewFullCoverage),
            get_view(&ViewType::TwoNodesViewMissingSlots),
        ];
        let (topology_view, _) = calculate_topology(
            topology_results.iter().map(|(addr, value)| (*addr, value)),
            1,
            None,
            queried_nodes,
            ReadFromReplicaStrategy::AlwaysFromPrimary,
            None,
        )
        .unwrap();
        let res = collect_shard_addrs(&topology_view);
        let node_1 = get_node_addr("node1", 6379);
        let node_2 = get_node_addr("node2", 6380);
        let expected = vec![node_1, node_2];
        assert_eq!(res, expected);
    }

    #[test]
    fn test_topology_calculator_2_nodes_queried_no_majority_no_full_coverage_prefer_fuller_coverage(
    ) {
        //  2 nodes queried: No majority, no full slot coverage, should return error
        let queried_nodes = 2;
        let topology_results = [
            get_view(&ViewType::SingleNodeViewMissingSlots),
            get_view(&ViewType::TwoNodesViewMissingSlots),
        ];
        let (topology_view, _) = calculate_topology(
            topology_results.iter().map(|(addr, value)| (*addr, value)),
            1,
            None,
            queried_nodes,
            ReadFromReplicaStrategy::AlwaysFromPrimary,
            None,
        )
        .unwrap();
        let res = collect_shard_addrs(&topology_view);
        let node_1 = get_node_addr("node3", 6381);
        let node_2 = get_node_addr("node4", 6382);
        let expected = vec![node_1, node_2];
        assert_eq!(res, expected);
    }

    #[test]
    fn test_topology_calculator_3_nodes_queried_no_full_coverage_prefer_majority() {
        //  2 nodes queried: No majority, no full slot coverage, should return error
        let queried_nodes = 2;
        let topology_results = vec![
            get_view(&ViewType::SingleNodeViewMissingSlots),
            get_view(&ViewType::TwoNodesViewMissingSlots),
            get_view(&ViewType::SingleNodeViewMissingSlots),
        ];
        let (topology_view, _) = calculate_topology(
            topology_results.iter().map(|(addr, value)| (*addr, value)),
            1,
            None,
            queried_nodes,
            ReadFromReplicaStrategy::AlwaysFromPrimary,
            None,
        )
        .unwrap();
        let res = collect_shard_addrs(&topology_view);
        let node_1 = get_node_addr("node1", 6379);
        let expected = vec![node_1];
        assert_eq!(res, expected);
    }

    /// A test implementation of AddressResolver that transforms addresses
    /// by appending a suffix to the host.
    #[derive(Debug)]
    struct TestAddressResolver {
        prefix: String,
    }

    impl TestAddressResolver {
        fn new(prefix: &str) -> Self {
            Self {
                prefix: prefix.to_string(),
            }
        }
    }

    impl crate::types::AddressResolver for TestAddressResolver {
        fn resolve(&self, host: &str, port: u16) -> (String, u16) {
            (format!("{}{}", self.prefix, host), port)
        }
    }

    #[test]
    fn parse_slots_with_address_resolver_transforms_addresses() {
        // Create a resolver that appends ".resolved" to all hostnames
        let resolver: Arc<dyn crate::types::AddressResolver> =
            Arc::new(TestAddressResolver::new("resolved."));

        // Create slot data with a primary and replica
        let view = Value::Array(vec![slot_value_with_replicas(
            0,
            16383,
            vec![("primary.example.com", 6379), ("replica.example.com", 6380)],
        )]);

        // Parse without resolver - addresses should be unchanged
        let result_without_resolver = parse_and_count_slots(&view, None, "fallback", None).unwrap();
        assert_eq!(result_without_resolver.slots.len(), 1);
        assert_eq!(
            result_without_resolver.slots[0].master(),
            "primary.example.com:6379"
        );
        assert_eq!(
            result_without_resolver.slots[0].replicas(),
            &["replica.example.com:6380".to_string()]
        );

        // Parse with resolver - addresses should be transformed
        let result_with_resolver =
            parse_and_count_slots(&view, None, "fallback", Some(&resolver)).unwrap();
        assert_eq!(result_with_resolver.slots.len(), 1);
        assert_eq!(
            result_with_resolver.slots[0].master(),
            "resolved.primary.example.com:6379"
        );
        assert_eq!(
            result_with_resolver.slots[0].replicas(),
            &["resolved.replica.example.com:6380".to_string()]
        );
    }
}
