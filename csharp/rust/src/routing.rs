use crate::data::Utf8OrEmptyError;
use crate::helpers;
use redis::cluster_routing::ResponsePolicy;
use std::ffi::{c_char, c_longlong, c_uint, c_ushort};

#[repr(C)]
pub struct RoutingInfo {
    pub kind: ERoutingInfo,
    pub value: RoutingInfoUnion,
}
impl RoutingInfo {
    pub unsafe fn to_redis(&self) -> Result<redis::cluster_routing::RoutingInfo, Utf8OrEmptyError> {
        Ok(match self.kind {
            ERoutingInfo::SingleRandom => redis::cluster_routing::RoutingInfo::SingleNode(
                redis::cluster_routing::SingleNodeRoutingInfo::Random,
            ),
            ERoutingInfo::SingleRandomPrimary => redis::cluster_routing::RoutingInfo::SingleNode(
                redis::cluster_routing::SingleNodeRoutingInfo::RandomPrimary,
            ),
            ERoutingInfo::SingleSpecificNode => {
                let route = self.value.specific_node.to_redis();
                redis::cluster_routing::RoutingInfo::SingleNode(
                    redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(route),
                )
            }
            ERoutingInfo::SingleSpecificKeyedNode => {
                let route = self.value.keyed_specific_node.to_redis()?;
                redis::cluster_routing::RoutingInfo::SingleNode(
                    redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(route),
                )
            }
            ERoutingInfo::SingleByAddress => {
                let address = self.value.by_address.to_redis()?;
                redis::cluster_routing::RoutingInfo::SingleNode(address)
            }
            ERoutingInfo::MultiAllNodes => redis::cluster_routing::RoutingInfo::MultiNode((
                redis::cluster_routing::MultipleNodeRoutingInfo::AllNodes,
                self.value.multi.to_redis(),
            )),
            ERoutingInfo::MultiAllMasters => redis::cluster_routing::RoutingInfo::MultiNode((
                redis::cluster_routing::MultipleNodeRoutingInfo::AllMasters,
                self.value.multi.to_redis(),
            )),
            ERoutingInfo::MultiMultiSlot => {
                let multi_slot = self.value.multi_slot.to_redis();
                redis::cluster_routing::RoutingInfo::MultiNode(multi_slot)
            }
        })
    }
}
#[repr(C)]
pub enum ERoutingInfo {
    /// Route to any node at random
    SingleRandom,
    /// Route to any *primary* node
    SingleRandomPrimary,
    /// Route to the node that matches the [Slot]
    SingleSpecificNode,
    /// Route to the node that matches the [Slot]
    SingleSpecificKeyedNode,
    /// Route to the node with the given address.
    SingleByAddress,

    /// Route to all nodes in the clusters
    MultiAllNodes,
    /// Route to all primaries in the cluster
    MultiAllMasters,
    /// Routes the request to multiple slots.
    /// This variant contains instructions for splitting a multi-slot command (e.g., MGET, MSET) into sub-commands.
    /// Each tuple consists of a `Route` representing the target node for the subcommand,
    /// and a vector of argument indices from the original command that should be copied to each subcommand.
    /// The `MultiSlotArgPattern` specifies the pattern of the command’s arguments, indicating how they are organized
    /// (e.g., only keys, key-value pairs, etc).
    MultiMultiSlot,
}

#[repr(C)]
pub struct RoutingInfoByAddress {
    /// DNS hostname of the node
    pub host: *const c_char,
    /// Length of host
    pub host_length: c_uint,
    /// port of the node
    pub port: c_ushort,
}

impl RoutingInfoByAddress {
    pub(crate) fn to_redis(
        &self,
    ) -> Result<redis::cluster_routing::SingleNodeRoutingInfo, Utf8OrEmptyError> {
        Ok(redis::cluster_routing::SingleNodeRoutingInfo::ByAddress {
            host: helpers::grab_str_not_null(self.host)?,
            port: self.port,
        })
    }
}

#[repr(C)]
pub enum RouteSlotAddress {
    /// The request must be routed to primary node
    Master,
    /// The request may be routed to a replica node.
    /// For example, a GET command can be routed either to replica or primary.
    ReplicaOptional,
    /// The request must be routed to replica node, if one exists.
    /// For example, by user requested routing.
    ReplicaRequired,
}
#[repr(C)]
pub struct Slot {
    /// slot number
    pub slot: c_ushort,
    /// port of the node
    pub slot_addr: RouteSlotAddress,
}

impl Slot {
    pub(crate) fn to_redis(&self) -> redis::cluster_routing::Route {
        redis::cluster_routing::Route::new(
            self.slot,
            match self.slot_addr {
                RouteSlotAddress::Master => redis::cluster_routing::SlotAddr::Master,
                RouteSlotAddress::ReplicaOptional => {
                    redis::cluster_routing::SlotAddr::ReplicaOptional
                }
                RouteSlotAddress::ReplicaRequired => {
                    redis::cluster_routing::SlotAddr::ReplicaRequired
                }
            },
        )
    }
}
#[repr(C)]
pub struct KeyedSlot {
    /// DNS hostname of the node
    pub slot: *const c_char,
    // Length of the slot
    pub slot_length: c_uint,
    /// port of the node
    pub slot_addr: RouteSlotAddress,
}


impl KeyedSlot {
    pub(crate) fn to_redis(&self) -> Result<redis::cluster_routing::Route, Utf8OrEmptyError> {
        let slot = helpers::grab_str_not_null(self.slot)?;
        let slot = redis::cluster_topology::get_slot(slot.as_bytes());

        Ok(redis::cluster_routing::Route::new(
            slot,
            match self.slot_addr {
                RouteSlotAddress::Master => redis::cluster_routing::SlotAddr::Master,
                RouteSlotAddress::ReplicaOptional => {
                    redis::cluster_routing::SlotAddr::ReplicaOptional
                }
                RouteSlotAddress::ReplicaRequired => {
                    redis::cluster_routing::SlotAddr::ReplicaRequired
                }
            },
        ))
    }
}

#[repr(C)]
pub struct RoutingInfoMultiSlot {
    pub response_policy: ERoutingInfoMultiResponsePolicy,
    pub arg_pattern: ERoutingInfoMultiSlotArgPattern,
    pub routes: *const RoutingInfoMultiSlotPair,
    pub routes_length: c_uint,
}

impl RoutingInfoMultiSlot {
    pub(crate) fn to_redis(
        &self,
    ) -> (
        redis::cluster_routing::MultipleNodeRoutingInfo,
        Option<ResponsePolicy>,
    ) {
        let response_policy = self.response_policy.to_redis();
        let arg_pattern = self.arg_pattern.to_redis();
        let value = helpers::grab_vec(self.routes, self.routes_length as usize, |pair| {
            let route = pair.route.to_redis();
            let something = helpers::grab_vec(
                pair.something,
                pair.something_length as usize,
                |something| Ok::<usize, ()>(*something as usize),
            )
                .unwrap();
            Ok::<(redis::cluster_routing::Route, Vec<usize>), ()>((route, something))
        })
            .unwrap();
        (
            redis::cluster_routing::MultipleNodeRoutingInfo::MultiSlot((value, arg_pattern)),
            response_policy,
        )
    }
}

#[repr(C)]
pub struct RoutingInfoMultiSlotPair {
    pub route: Slot,
    pub something: *const c_longlong,
    pub something_length: c_uint,
}
#[repr(C)]
pub enum ERoutingInfoMultiSlotArgPattern {
    /// Pattern where only keys are provided in the command.
    /// For example: `MGET key1 key2`
    KeysOnly,

    /// Pattern where each key is followed by a corresponding value.
    /// For example: `MSET key1 value1 key2 value2`
    KeyValuePairs,

    /// Pattern where a list of keys is followed by a shared parameter.
    /// For example: `JSON.MGET key1 key2 key3 path`
    KeysAndLastArg,

    /// Pattern where each key is followed by two associated arguments, forming key-argument-argument triples.
    /// For example: `JSON.MSET key1 path1 value1 key2 path2 value2`
    KeyWithTwoArgTriples,
}

impl ERoutingInfoMultiSlotArgPattern {
    pub(crate) fn to_redis(&self) -> redis::cluster_routing::MultiSlotArgPattern {
        match self {
            ERoutingInfoMultiSlotArgPattern::KeysOnly => {
                redis::cluster_routing::MultiSlotArgPattern::KeysOnly
            }
            ERoutingInfoMultiSlotArgPattern::KeyValuePairs => {
                redis::cluster_routing::MultiSlotArgPattern::KeyValuePairs
            }
            ERoutingInfoMultiSlotArgPattern::KeysAndLastArg => {
                redis::cluster_routing::MultiSlotArgPattern::KeysAndLastArg
            }
            ERoutingInfoMultiSlotArgPattern::KeyWithTwoArgTriples => {
                redis::cluster_routing::MultiSlotArgPattern::KeyWithTwoArgTriples
            }
        }
    }
}

#[repr(C)]
pub enum ERoutingInfoMultiResponsePolicy {
    /// Unspecified response policy
    None = 0,
    /// Wait for one request to succeed and return its results. Return error if all requests fail.
    OneSucceeded = 1,
    /// Returns the first succeeded non-empty result; if all results are empty, returns `Nil`; otherwise, returns the last received error.
    FirstSucceededNonEmptyOrAllEmpty = 3,
    /// Waits for all requests to succeed, and the returns one of the successes. Returns the error on the first received error.
    AllSucceeded = 4,
    /// Aggregate array responses into a single array. Return error on any failed request or on a response that isn't an array.
    CombineArrays = 5,
    /// Handling is not defined by the Redis standard. Will receive a special case
    Special = 6,
    /// Combines multiple map responses into a single map.
    CombineMaps = 7,

    /// Aggregate success results according to a logical bitwise operator. Return error on any failed request or on a response that doesn't conform to 0 or 1.
    AggregateLogicalWithAnd = 50,

    /// Aggregate success results according to a numeric operator. Return error on any failed request or on a response that isn't an integer.
    /// Choose minimal value
    AggregateWithMin = 70,
    /// Aggregate success results according to a numeric operator. Return error on any failed request or on a response that isn't an integer.
    /// Sum all values
    AggregateWithSum = 71,
}

impl ERoutingInfoMultiResponsePolicy {
    pub(crate) fn to_redis(&self) -> Option<redis::cluster_routing::ResponsePolicy> {
        match self {
            ERoutingInfoMultiResponsePolicy::None => None,
            ERoutingInfoMultiResponsePolicy::OneSucceeded => {
                Some(redis::cluster_routing::ResponsePolicy::OneSucceeded)
            }
            ERoutingInfoMultiResponsePolicy::FirstSucceededNonEmptyOrAllEmpty => {
                Some(redis::cluster_routing::ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty)
            }
            ERoutingInfoMultiResponsePolicy::AllSucceeded => {
                Some(redis::cluster_routing::ResponsePolicy::AllSucceeded)
            }
            ERoutingInfoMultiResponsePolicy::CombineArrays => {
                Some(redis::cluster_routing::ResponsePolicy::CombineArrays)
            }
            ERoutingInfoMultiResponsePolicy::Special => {
                Some(redis::cluster_routing::ResponsePolicy::Special)
            }
            ERoutingInfoMultiResponsePolicy::CombineMaps => {
                Some(redis::cluster_routing::ResponsePolicy::CombineMaps)
            }
            ERoutingInfoMultiResponsePolicy::AggregateLogicalWithAnd => {
                Some(redis::cluster_routing::ResponsePolicy::AggregateLogical(
                    redis::cluster_routing::LogicalAggregateOp::And,
                ))
            }
            ERoutingInfoMultiResponsePolicy::AggregateWithMin => {
                Some(redis::cluster_routing::ResponsePolicy::Aggregate(
                    redis::cluster_routing::AggregateOp::Min,
                ))
            }
            ERoutingInfoMultiResponsePolicy::AggregateWithSum => {
                Some(redis::cluster_routing::ResponsePolicy::Aggregate(
                    redis::cluster_routing::AggregateOp::Sum,
                ))
            }
        }
    }
}

#[repr(C)]
pub union RoutingInfoUnion {
    /// Set if ERoutingInfo is ByAddress
    pub by_address: std::mem::ManuallyDrop<RoutingInfoByAddress>,

    /// Set if ERoutingInfo is SpecificNode
    pub specific_node: std::mem::ManuallyDrop<Slot>,

    /// Set if ERoutingInfo is SpecificNode
    pub keyed_specific_node: std::mem::ManuallyDrop<KeyedSlot>,

    /// Set if ERoutingInfo is MultiAllNodes or MultiAllMasters
    pub multi: std::mem::ManuallyDrop<ERoutingInfoMultiResponsePolicy>,

    /// Set if ERoutingInfo is MultiMultiSlot
    pub multi_slot: std::mem::ManuallyDrop<RoutingInfoMultiSlot>,
}
