// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

pub trait Routable {
    fn command(&self) -> Option<Vec<u8>> {
        None
    }
}

#[allow(unused)]
pub struct Route(u16, SlotAddr);

impl Route {
    pub fn new(slot: u16, slot_addr: SlotAddr) -> Self {
        Self(slot, slot_addr)
    }
}

pub enum SlotAddr {
    Master,
    ReplicaOptional,
    ReplicaRequired,
}   


pub enum RoutingInfo {
    SingleNode(SingleNodeRoutingInfo),
    MultiNode((MultipleNodeRoutingInfo, Option<ResponsePolicy>)),
}

pub enum SingleNodeRoutingInfo {
    Random,
    RandomPrimary,
    SpecificNode(Route),
    ByAddress {
        host: String,
        port: u16,
    },
}

pub enum MultipleNodeRoutingInfo {
    AllNodes,
    AllMasters,
    MultiSlot((Vec<(Route, Vec<usize>)>, MultiSlotArgPattern)),
}

pub enum LogicalAggregateOp {
    And,    
}           

pub enum AggregateOp {
    Min,
    Sum,
}

pub enum ResponsePolicy {
    OneSucceeded,
    FirstSucceededNonEmptyOrAllEmpty,
    AllSucceeded,
    AggregateLogical(LogicalAggregateOp),
    Aggregate(AggregateOp),
    CombineArrays,
    Special,
    CombineMaps,
}

impl ResponsePolicy {
    pub fn for_command(_cmd: &[u8]) -> Option<ResponsePolicy> {
        Some(ResponsePolicy::OneSucceeded)
    }
}

pub enum MultiSlotArgPattern {
    KeysOnly,
    KeyValuePairs,
    KeysAndLastArg,
    KeyWithTwoArgTriples,
}
