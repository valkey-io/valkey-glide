using System.ComponentModel;
using Microsoft.Extensions.Logging;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

public sealed class GlideClient : IDisposable, IGlideClient
{
    private readonly INativeClient _nativeClient;
    private readonly bool _ownsNativeClient;

    public GlideClient(INativeClient nativeClient)
    {
        _nativeClient     = nativeClient;
        _ownsNativeClient = false;
    }

    public GlideClient(
        IReadOnlyCollection<Node> nodes,
        InterOp.ETlsMode useTls)
    {
        _nativeClient     = new NativeClient(new InterOp.ConnectionRequest(nodes));
        _ownsNativeClient = true;
    }

    public void Dispose()
    {
        if (_ownsNativeClient)
            ((NativeClient)_nativeClient).Dispose();
    }

    [EditorBrowsable(EditorBrowsableState.Advanced)]
    public async Task<InterOp.Value> CommandAsync(ERequestType requestType, params string[] args)
    {
        return await _nativeClient.SendCommandAsync(requestType, args);
    }
}
