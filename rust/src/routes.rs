// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Request routing for cluster clients.
//!
//! Mirrors the Python `glide_shared.routes` module: [`Route`] values are mapped
//! to the `glide-core` `redis::cluster_routing::RoutingInfo` exactly as the
//! socket layer does for the other language bindings.

use redis::cluster_routing::{
    MultipleNodeRoutingInfo, ResponsePolicy, Routable, Route as CoreRoute, RoutingInfo,
    SingleNodeRoutingInfo, SlotAddr,
};
use redis::{Cmd, cluster_topology::get_slot};

/// Whether a slot-based route targets the primary or a replica.
///
/// Mirrors Python `SlotType`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum SlotType {
    /// The primary (master) node owning the slot.
    #[default]
    Primary,
    /// A replica node for the slot. Maps to `ReplicaRequired`, matching core.
    Replica,
}

impl SlotType {
    fn slot_addr(self) -> SlotAddr {
        match self {
            SlotType::Primary => SlotAddr::Master,
            SlotType::Replica => SlotAddr::ReplicaRequired,
        }
    }
}

/// Describes where a command should be routed in cluster mode.
///
/// Mirrors the Python route classes: `AllNodes`, `AllPrimaries`, `RandomNode`,
/// `SlotKeyRoute`, `SlotIdRoute`, `ByAddressRoute`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Route {
    /// Route to all nodes (primaries and replicas).
    AllNodes,
    /// Route to all primary nodes.
    AllPrimaries,
    /// Route to a random node.
    RandomNode,
    /// Route to the node owning the slot for `key`.
    SlotKey {
        /// The key whose slot determines the target node.
        key: Vec<u8>,
        /// Primary or replica.
        slot_type: SlotType,
    },
    /// Route to the node owning the given slot id (0..16383).
    SlotId {
        /// The slot id.
        slot_id: u16,
        /// Primary or replica.
        slot_type: SlotType,
    },
    /// Route to the node with the given host and port.
    ByAddress {
        /// Node hostname.
        host: String,
        /// Node port.
        port: u16,
    },
}

impl Route {
    /// Convenience constructor for a slot-key route by string key.
    pub fn slot_key(key: impl Into<Vec<u8>>, slot_type: SlotType) -> Self {
        Route::SlotKey {
            key: key.into(),
            slot_type,
        }
    }

    /// Convert this route into a `glide-core` [`RoutingInfo`].
    ///
    /// For multi-node routes the response aggregation policy is derived from the
    /// command (when provided), exactly as `glide-core` does internally.
    pub fn to_routing_info(&self, cmd: Option<&Cmd>) -> RoutingInfo {
        let response_policy = || {
            cmd.and_then(|c| c.command())
                .and_then(|name| ResponsePolicy::for_command(&name))
        };
        match self {
            Route::AllNodes => {
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, response_policy()))
            }
            Route::AllPrimaries => {
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, response_policy()))
            }
            Route::RandomNode => RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random),
            Route::SlotKey { key, slot_type } => {
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(CoreRoute::new(
                    get_slot(key),
                    slot_type.slot_addr(),
                )))
            }
            Route::SlotId { slot_id, slot_type } => {
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(CoreRoute::new(
                    *slot_id,
                    slot_type.slot_addr(),
                )))
            }
            Route::ByAddress { host, port } => {
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                    host: host.clone(),
                    port: *port,
                })
            }
        }
    }
}

#[cfg(test)]
mod tests {
    //! Pure-logic routing tests: every [`Route`] variant is lowered into the
    //! correct `glide-core` `RoutingInfo`, including slot computation and the
    //! multi-node response-policy derivation.
    use super::*;

    // ---- simple single-node routes --------------------------------------

    #[test]
    fn random_node_maps_to_single_random() {
        assert!(matches!(
            Route::RandomNode.to_routing_info(None),
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)
        ));
    }

    #[test]
    fn by_address_route_maps() {
        match (Route::ByAddress {
            host: "node1".into(),
            port: 7001,
        })
        .to_routing_info(None)
        {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress { host, port }) => {
                assert_eq!(host, "node1");
                assert_eq!(port, 7001);
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    // ---- multi-node routes + response policy ----------------------------

    #[test]
    fn all_nodes_without_cmd_has_no_policy() {
        match Route::AllNodes.to_routing_info(None) {
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, policy)) => {
                assert!(policy.is_none());
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn all_primaries_without_cmd_has_no_policy() {
        match Route::AllPrimaries.to_routing_info(None) {
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, policy)) => {
                assert!(policy.is_none());
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn all_nodes_with_cmd_lacking_policy_has_none() {
        // GET has no aggregation response policy.
        let cmd = redis::cmd("GET");
        match Route::AllNodes.to_routing_info(Some(&cmd)) {
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, policy)) => {
                assert!(policy.is_none());
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn all_nodes_with_policy_cmd_has_policy() {
        // PING carries the AllSucceeded response policy.
        let cmd = redis::cmd("PING");
        match Route::AllNodes.to_routing_info(Some(&cmd)) {
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, policy)) => {
                assert!(matches!(policy, Some(ResponsePolicy::AllSucceeded)));
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn all_primaries_with_policy_cmd_has_policy() {
        // DEL carries an Aggregate(Sum) response policy.
        let cmd = redis::cmd("DEL");
        match Route::AllPrimaries.to_routing_info(Some(&cmd)) {
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, policy)) => {
                assert!(matches!(policy, Some(ResponsePolicy::Aggregate(_))));
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    // ---- slot-key routes -------------------------------------------------

    #[test]
    fn slot_key_route_computes_slot_and_primary() {
        match Route::slot_key("mykey", SlotType::Primary).to_routing_info(None) {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), get_slot(b"mykey"));
                assert_eq!(route.slot_addr(), SlotAddr::Master);
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn slot_key_route_replica_maps_to_replica_required() {
        match Route::slot_key("mykey", SlotType::Replica).to_routing_info(None) {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), get_slot(b"mykey"));
                assert_eq!(route.slot_addr(), SlotAddr::ReplicaRequired);
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn slot_key_hashtag_maps_to_same_slot_as_inner() {
        // A hashtag "{user1000}" must hash identically to the bare key "user1000".
        let hashed =
            match Route::slot_key("{user1000}.foo", SlotType::Primary).to_routing_info(None) {
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => route.slot(),
                other => panic!("unexpected routing: {other:?}"),
            };
        assert_eq!(hashed, get_slot(b"user1000"));
    }

    #[test]
    fn slot_key_different_keys_generally_differ() {
        let a = get_slot(b"key-a");
        let b = get_slot(b"key-b-different");
        // Not strictly guaranteed, but true for these well-known distinct keys.
        assert_ne!(a, b);
    }

    // ---- slot-id routes --------------------------------------------------

    #[test]
    fn slot_id_route_primary() {
        match (Route::SlotId {
            slot_id: 1000,
            slot_type: SlotType::Primary,
        })
        .to_routing_info(None)
        {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), 1000);
                assert_eq!(route.slot_addr(), SlotAddr::Master);
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn slot_id_route_replica() {
        match (Route::SlotId {
            slot_id: 42,
            slot_type: SlotType::Replica,
        })
        .to_routing_info(None)
        {
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                assert_eq!(route.slot(), 42);
                assert_eq!(route.slot_addr(), SlotAddr::ReplicaRequired);
            }
            other => panic!("unexpected routing: {other:?}"),
        }
    }

    #[test]
    fn slot_id_boundaries() {
        for slot in [0u16, 16383u16] {
            match (Route::SlotId {
                slot_id: slot,
                slot_type: SlotType::Primary,
            })
            .to_routing_info(None)
            {
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route)) => {
                    assert_eq!(route.slot(), slot);
                }
                other => panic!("unexpected routing: {other:?}"),
            }
        }
    }

    // ---- SlotType mapping ------------------------------------------------

    #[test]
    fn slot_type_default_is_primary() {
        assert_eq!(SlotType::default(), SlotType::Primary);
    }

    // ---- dispatch through an in-process mock executor -------------------
    //
    // A stricter check than `to_routing_info()` in isolation: these verify that
    // each `Route` produces the exact `RoutingInfo` that actually reaches the
    // executor when dispatched through the public
    // `CustomCommand::custom_command_with_route` path — including the response
    // policy derived from the command keyword. No server is involved: the mock
    // implements the `CommandExecutor` seam and captures what it is handed.
    mod dispatch {
        use crate::executor::{CommandExecutor, CustomCommand};
        use crate::routes::{Route, SlotType};
        use async_trait::async_trait;
        use redis::cluster_routing::{
            MultipleNodeRoutingInfo, ResponsePolicy, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
        };
        use redis::{Cmd, Value};
        use std::sync::Mutex;

        /// A deterministic, server-free `CommandExecutor` that records the last
        /// command args and routing it was asked to execute.
        #[derive(Default)]
        struct MockExecutor {
            last_args: Mutex<Vec<Vec<u8>>>,
            last_routing: Mutex<Option<RoutingInfo>>,
        }

        #[async_trait]
        impl CommandExecutor for MockExecutor {
            async fn execute_command(
                &self,
                cmd: Cmd,
                routing: Option<RoutingInfo>,
            ) -> crate::error::Result<Value> {
                let args: Vec<Vec<u8>> = cmd
                    .args_iter()
                    .map(|a| match a {
                        redis::Arg::Simple(s) => s.to_vec(),
                        redis::Arg::Cursor => b"0".to_vec(),
                    })
                    .collect();
                *self.last_args.lock().unwrap() = args;
                *self.last_routing.lock().unwrap() = routing;
                Ok(Value::Okay)
            }
        }

        #[tokio::test]
        async fn custom_command_builds_expected_args() {
            let mock = MockExecutor::default();
            mock.custom_command(&["SET", "key", "value"]).await.unwrap();
            let args = mock.last_args.lock().unwrap().clone();
            assert_eq!(
                args,
                vec![b"SET".to_vec(), b"key".to_vec(), b"value".to_vec()]
            );
        }

        #[tokio::test]
        async fn all_primaries_dispatch_derives_response_policy_from_cmd() {
            let mock = MockExecutor::default();
            // PING carries the AllSucceeded response policy, derived from the
            // command keyword built into the Cmd.
            mock.custom_command_with_route(&["PING"], Route::AllPrimaries)
                .await
                .unwrap();
            let routing = mock.last_routing.lock().unwrap().take().expect("routing");
            match routing {
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, policy)) => {
                    assert!(matches!(policy, Some(ResponsePolicy::AllSucceeded)));
                }
                other => panic!("unexpected routing: {other:?}"),
            }
        }

        #[tokio::test]
        async fn all_nodes_dispatch_has_no_policy_for_get() {
            let mock = MockExecutor::default();
            mock.custom_command_with_route(&["GET", "k"], Route::AllNodes)
                .await
                .unwrap();
            let routing = mock.last_routing.lock().unwrap().take().expect("routing");
            match routing {
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, policy)) => {
                    assert!(policy.is_none());
                }
                other => panic!("unexpected routing: {other:?}"),
            }
        }

        #[tokio::test]
        async fn random_node_dispatch() {
            let mock = MockExecutor::default();
            mock.custom_command_with_route(&["GET", "k"], Route::RandomNode)
                .await
                .unwrap();
            let routing = mock.last_routing.lock().unwrap().take().expect("routing");
            assert!(matches!(
                routing,
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)
            ));
        }

        #[tokio::test]
        async fn slot_id_dispatch_specific_node_replica() {
            let mock = MockExecutor::default();
            mock.custom_command_with_route(
                &["GET", "k"],
                Route::SlotId {
                    slot_id: 500,
                    slot_type: SlotType::Replica,
                },
            )
            .await
            .unwrap();
            let routing = mock.last_routing.lock().unwrap().take().expect("routing");
            match routing {
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(r)) => {
                    assert_eq!(r.slot(), 500);
                    assert_eq!(r.slot_addr(), SlotAddr::ReplicaRequired);
                }
                other => panic!("unexpected routing: {other:?}"),
            }
        }

        #[tokio::test]
        async fn by_address_dispatch() {
            let mock = MockExecutor::default();
            mock.custom_command_with_route(
                &["GET", "k"],
                Route::ByAddress {
                    host: "n7".into(),
                    port: 7007,
                },
            )
            .await
            .unwrap();
            let routing = mock.last_routing.lock().unwrap().take().expect("routing");
            match routing {
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress { host, port }) => {
                    assert_eq!(host, "n7");
                    assert_eq!(port, 7007);
                }
                other => panic!("unexpected routing: {other:?}"),
            }
        }
    }
}
