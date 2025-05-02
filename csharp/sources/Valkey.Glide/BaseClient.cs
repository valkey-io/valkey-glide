// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

using Valkey.Glide.Commands;
using Valkey.Glide.ConnectionConfiguration;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Errors;
using Valkey.Glide.InterOp.Internals;
using Valkey.Glide.InterOp.Native;


namespace Valkey.Glide;

public abstract class BaseClient : IDisposable, IStringBaseCommands
{
    private readonly BaseClientConfiguration _connectionConfig;
    private NativeClient? _nativeClient;
    private readonly SemaphoreSlim? _semaphore = new SemaphoreSlim(1, 1);


    public BaseClient(BaseClientConfiguration connectionConfig)
    {
        _connectionConfig = connectionConfig;
    }

    protected async ValueTask<NativeClient> GetNativeClientAsync()
    {
        if (_nativeClient is not null)
            return _nativeClient;
        await _semaphore.WaitAsync();
        try
        {
            if (_nativeClient is not null)
                return _nativeClient;
            using FfiConnectionConfig request = _connectionConfig.ToRequest().ToFfi();
            _nativeClient = await NativeClient.CreateClientAsync(request);
            return _nativeClient;
        }
        finally
        {
            _semaphore.Release();
        }
    }


    #region public methods

    public async Task<string> Set(GlideString key, GlideString value)
    {
        NativeClient nativeClient = await GetNativeClientAsync();
        return await nativeClient.CommandAsync(RequestType.Set, [key, value], HandleOk);
    }

    public async Task<GlideString?> Get(GlideString key)
    {
        NativeClient nativeClient = await GetNativeClientAsync();
        return await nativeClient.CommandAsync(RequestType.Get, [key],
            response => HandleServerResponse<GlideString>(response, true));
    }


    public override string ToString() => $"{GetType().Name} {{ {_nativeClient} }}";
    public void Dispose()
    {
        _nativeClient?.Dispose();
        _semaphore?.Dispose();
    }

    [SuppressMessage("ReSharper", "NonReadonlyMemberInGetHashCode",
        Justification = "GetHashCode is only possible after initialization")]
    public override int GetHashCode() => _nativeClient?.GetHashCode() ??
                                         throw new InvalidOperationException(
                                             "Client is not connected. Initialize first, using `await EnsureInitializedAsync()`");

    public async Task EnsureInitializedAsync() => await GetNativeClientAsync();

    #endregion public methods

    #region protected methods

    protected internal static string HandleOk(IntPtr response)
        => HandleServerResponse<string>(response, false);

    protected internal static T HandleServerResponse<T>(IntPtr response, bool isNullable) where T : class?
        => NativeClient.HandleServerResponse<T, T>(response, isNullable, o => o);

    protected static ClusterValue<object?> HandleCustomCommandClusterResponse(IntPtr response, Route? route = null)
        => NativeClient.HandleServerResponse<object, ClusterValue<object?>>(response, true, data
            => (data is string str && str == "OK") || route is SingleNodeRoute ||
               data is not Dictionary<GlideString, object?>
                ? ClusterValue<object?>.OfSingleValue(data)
                : ClusterValue<object?>.OfMultiValue((Dictionary<GlideString, object?>)data));

    /// <summary>
    /// Process and convert a server response that may be a multi-node response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type per node.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="response"></param>
    /// <param name="isNullable"></param>
    /// <param name="converter">Function to convert <typeparamref name="R"/> to <typeparamref name="T"/>.</param>
    protected static ClusterValue<T> HandleClusterValueResponse<R, T>(IntPtr response, bool isNullable, Route route,
        Func<R, T> converter) where T : class?
        => NativeClient.HandleServerResponse<object, ClusterValue<T>>(response, isNullable, data =>
            route is SingleNodeRoute
                ? ClusterValue<T>.OfSingleValue(converter((R)data))
                : ClusterValue<T>.OfMultiValue(((Dictionary<GlideString, object>)data).ConvertValues(converter)));

    /// <summary>
    /// Process and convert a cluster multi-node response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type per node.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="response"></param>
    /// <param name="converter">Function to convert <typeparamref name="R"/> to <typeparamref name="T"/>.</param>
    protected static Dictionary<string, T> HandleMultiNodeResponse<R, T>(IntPtr response, Func<R, T> converter)
        where T : class?
        => NativeClient.HandleServerResponse<Dictionary<GlideString, object>, Dictionary<string, T>>(response, false,
            dict => dict.DownCastKeys().ConvertValues(converter));

    #endregion protected methods

    #region private fields

    private readonly object _lock = new();

    #endregion private fields
}
