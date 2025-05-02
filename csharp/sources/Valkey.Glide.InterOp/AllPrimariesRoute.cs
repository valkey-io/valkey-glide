using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Route request to all primary nodes.
/// </summary>
public sealed class AllPrimariesRoute : MultiNodeRoute
{
    internal override Native.Route ToFfi() => new(RouteType.AllPrimaries);
}