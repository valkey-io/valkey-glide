namespace Valkey.Glide.InterOp.Native.Routing;

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