// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct RoutingInfo
{
    public ERoutingInfo kind;
    public RoutingInfoUnion value;
}

public enum ERoutingInfo
{
    /// Route to any node at random
    SingleRandom,

    /// Route to any *primary* node
    SingleRandomPrimary,

    /// Route to the node that matches the [Route]
    SingleSpecificNode,

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

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct RoutingInfoByAddress
{
    /// DNS hostname of the node
    public byte* host;

    /// Length of host
    public uint host_length;

    /// port of the node
    public ushort port;
}

public enum RouteSlotAddress
{
    /// The request must be routed to primary node
    Master,

    /// The request may be routed to a replica node.
    /// For example, a GET command can be routed either to replica or primary.
    ReplicaOptional,

    /// The request must be routed to replica node, if one exists.
    /// For example, by user requested routing.
    ReplicaRequired,
}

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct Route
{
    /// DNS hostname of the node
    public ushort slot;

    /// port of the node
    public RouteSlotAddress slot_addr;
}

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct RoutingInfoMultiSlot
{
    public ERoutingInfoMultiResponsePolicy response_policy;
    public ERoutingInfoMultiSlotArgPattern arg_pattern;
    public RoutingInfoMultiSlotPair* routes;
    public uint routes_length;
}

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct RoutingInfoMultiSlotPair
{
    public Route route;
    public long* something;
    public uint something_length;
}

public enum ERoutingInfoMultiSlotArgPattern
{
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

public enum ERoutingInfoMultiResponsePolicy
{
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

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Explicit, CharSet = CharSet.Unicode)]
public struct RoutingInfoUnion
{
    /// Set if ERoutingInfo is ByAddress
    [FieldOffset(0)]
    public RoutingInfoByAddress by_address;

    /// Set if ERoutingInfo is SpecificNode
    [FieldOffset(0)]
    public Route specific_node;

    /// Set if ERoutingInfo is MultiAllNodes or MultiAllMasters
    [FieldOffset(0)]
    public ERoutingInfoMultiResponsePolicy multi;

    /// Set if ERoutingInfo is MultiMultiSlot
    [FieldOffset(0)]
    public RoutingInfoMultiSlot multi_slot;
}
