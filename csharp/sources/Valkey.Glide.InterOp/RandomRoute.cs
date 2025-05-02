using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Route request to a random node.<br />
/// <b>Warning:</b> Don't use it with write commands, they could be routed to a replica (RO) node and fail.
/// </summary>
public sealed class RandomRoute : SingleNodeRoute
{
    internal override Native.Route ToFfi() => new(RouteType.Random);
}