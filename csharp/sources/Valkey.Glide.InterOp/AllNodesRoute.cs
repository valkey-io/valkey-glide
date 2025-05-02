using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Route request to all nodes.<br />
/// <b>Warning:</b> Don't use it with write commands, they could be routed to a replica (RO) node and fail.
/// </summary>
public sealed class AllNodesRoute : MultiNodeRoute
{
    internal override Native.Route ToFfi() => new(RouteType.AllNodes);
}