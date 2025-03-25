using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Route to any *primary* node
/// </summary>
public sealed class SingleRandomPrimary() : IRoutingInfo
{
    public RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) => new RoutingInfo
    {
        kind = ERoutingInfo.SingleRandomPrimary, value = new RoutingInfoUnion(),
    };
}