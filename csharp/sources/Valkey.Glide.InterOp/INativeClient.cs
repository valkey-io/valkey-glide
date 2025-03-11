using System.ComponentModel;
using System.Threading.Tasks;
using Valkey.Glide.InterOp.Native;

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
    Task<Value> SendCommandAsync(ERequestType requestType, params string[] args);

    /// <summary>
    /// Sends a command to the native client with the specified request type and arguments,
    /// and retrieves a result encapsulated in a Value structure.
    /// </summary>
    /// <remarks>
    /// Blocking call.
    /// Always prefer<see cref="SendCommandAsync"/> over this!
    /// </remarks>
    /// <param name="requestType">The type of request to be sent to the native client.</param>
    /// <param name="args">The arguments associated with the request.</param>
    /// <returns>The result of the command encapsulated in a Value structure.</returns>
    [EditorBrowsable(EditorBrowsableState.Advanced)]
    Value SendCommand(ERequestType requestType, params string[] args);
}
