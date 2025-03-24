using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Route to all primaries in the cluster
/// </summary>
/// <param name="responsePolicy"></param>
public sealed class MultiAllMasters(EResponsePolicy responsePolicy) : IRoutingInfo
{
    // ToDo: Add documentation for responsePolicy
    public unsafe RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) => new RoutingInfo
    {
        kind = ERoutingInfo.MultiAllMasters, value = new RoutingInfoUnion {multi = responsePolicy.ToNative()}
    };
}