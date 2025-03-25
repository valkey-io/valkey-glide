using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Route to the node with the given address.
/// </summary>
/// <param name="host"></param>
/// <param name="port"></param>
public sealed class SingleByAddress(string host, ushort port) : IRoutingInfo
{
    // ToDo: Fill in documentation for host and port
    public unsafe RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) => new RoutingInfo
    {
        kind = ERoutingInfo.SingleByAddress,
        value = new RoutingInfoUnion
        {
            by_address = new RoutingInfoByAddress
            {
                host = marshalString(host), host_length = (uint)host.Length, port = port,
            }
        }
    };
}