using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using Microsoft.Extensions.Logging;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

public sealed class GlideClient : IDisposable, IGlideClient
{
    private readonly INativeClient _nativeClient;
    private readonly bool          _ownsNativeClient;

    public GlideClient(INativeClient nativeClient)
    {
        _nativeClient     = nativeClient;
        _ownsNativeClient = false;
    }

    public GlideClient(InterOp.ConnectionRequest connectionRequest)
    {
        _nativeClient     = new NativeClient(connectionRequest);
        _ownsNativeClient = true;
    }

    /// <inheritdoc />
    public void Dispose()
    {
        if (_ownsNativeClient)
            ((NativeClient) _nativeClient).Dispose();
    }

    [EditorBrowsable(EditorBrowsableState.Advanced)]
    public Task<InterOp.Value> CommandAsync(ERequestType requestType, params string[] args)
        => _nativeClient.SendCommandAsync(requestType, args);
}
