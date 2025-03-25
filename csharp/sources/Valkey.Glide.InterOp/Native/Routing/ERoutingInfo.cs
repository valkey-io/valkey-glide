namespace Valkey.Glide.InterOp.Native.Routing;

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