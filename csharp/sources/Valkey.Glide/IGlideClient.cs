using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;

namespace Valkey.Glide;

public interface IGlideClient
{
    Task<InterOp.Value> CommandAsync<TRoutingInfo>(ERequestType requestType, TRoutingInfo routingInfo, params string[] args)
        where TRoutingInfo : IRoutingInfo;
    string Transform<T>(T value);
}
