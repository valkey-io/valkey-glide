using System.ComponentModel;
using System.Runtime.CompilerServices;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;

namespace Valkey.Glide;

public sealed class GlideClient : IDisposable, IGlideClient
{
    private readonly GlideSerializerCollection _glideSerializerCollection;
    private readonly INativeClient _nativeClient;
    private readonly bool          _ownsNativeClient;

    public GlideClient(INativeClient nativeClient, GlideSerializerCollection? glideSerializerCollection = null)
    {
        _nativeClient     = nativeClient;
        _ownsNativeClient = false;
        if (glideSerializerCollection is null)
        {
            glideSerializerCollection = new GlideSerializerCollection();
            glideSerializerCollection.RegisterDefaultSerializers();
            glideSerializerCollection.Seal();
        }
        _glideSerializerCollection = glideSerializerCollection;
    }

    public GlideClient(InterOp.ConnectionRequest connectionRequest, GlideSerializerCollection? glideSerializerCollection = null)
    {
        _nativeClient     = new NativeClient(connectionRequest);
        _ownsNativeClient = true;
        if (glideSerializerCollection is null)
        {
            glideSerializerCollection = new GlideSerializerCollection();
            glideSerializerCollection.RegisterDefaultSerializers();
            glideSerializerCollection.Seal();
        }
        _glideSerializerCollection = glideSerializerCollection;
    }

    /// <inheritdoc />
    public void Dispose()
    {
        if (_ownsNativeClient)
            ((NativeClient) _nativeClient).Dispose();
    }

    [EditorBrowsable(EditorBrowsableState.Advanced)]
    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public Task<InterOp.Value> SendCommandAsync<TRoutingInfo>(ERequestType requestType, TRoutingInfo routingInfo, params string[] args)
        where TRoutingInfo : IRoutingInfo
        => _nativeClient.SendCommandAsync(requestType, routingInfo, args);

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public string ToParameter<T>(T value) => _glideSerializerCollection.Transform(value);
}
