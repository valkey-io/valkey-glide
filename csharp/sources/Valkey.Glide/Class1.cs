using Microsoft.Extensions.Logging;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

public sealed class GlideClient : IDisposable
{
    private readonly NativeClient _nativeClient;

    public GlideClient(IReadOnlyCollection<Node> nodes, bool useTls)
    {
        _nativeClient = new NativeClient(nodes, useTls);
    }
    public async Task CommandAsync()
    {
        var data = await _nativeClient.SendCommandAsync(ERequestType.Append, "");
    }

    public void Dispose()
    {
        _nativeClient.Dispose();
    }
}
