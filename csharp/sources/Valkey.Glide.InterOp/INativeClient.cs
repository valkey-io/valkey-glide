using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Native client, encapsulating the FFI interops with the glide rust client.
/// </summary>
public interface INativeClient
{
    /// <summary>
    /// Sends a command to the native client with the specified request type and arguments,
    /// and retrieves a result encapsulated in a Value structure.
    /// </summary>
    /// <param name="requestType">The type of request to be sent to the native client.</param>
    /// <param name="args">The arguments associated with the request.</param>
    /// <returns>A task representing the asynchronous operation, containing the result as a Value structure.</returns>
    Task<Value> SendCommandAsync<TRoutingInfo>(ERequestType requestType, TRoutingInfo routingInfo, params string[] args)
        where TRoutingInfo : IRoutingInfo;
}
