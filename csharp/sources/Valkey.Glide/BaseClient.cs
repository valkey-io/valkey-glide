// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;

using ConnectionRequest = Valkey.Glide.InterOp.ConnectionRequest;

namespace Valkey.Glide;

public abstract class BaseClient : IDisposable, IStringBaseCommands
{
    #region public methods
    public async Task<string> Set(GlideString key, GlideString value)
        => await Command(ERequestType.Set, [key, value], HandleOk);

    public async Task<GlideString?> Get(GlideString key)
        => await Command(ERequestType.Get, [key], response => HandleServerResponse<GlideString>(response, true));


    #endregion public methods

    #region private fields

    private readonly NativeClient _nativeClient;

    #endregion

    #region protected methods
    protected BaseClient(ConnectionRequest request)
    {
        _nativeClient = new NativeClient(request);
    }

    protected delegate T ResponseHandler<T>(object? response);

    protected async Task<T> Command<T>(ERequestType requestType, GlideString[] arguments, ResponseHandler<T> responseHandler, IRoutingInfo? route = null) where T : class?
    {
        var result = await _nativeClient.SendCommandAsync(requestType, route, arguments.Select(e => e.Bytes).ToArray());
        return responseHandler(result);
    }

    protected static string HandleOk(object? response)
        => HandleServerResponse<GlideString, string>(response, false, gs => gs.GetString());

    protected static T HandleServerResponse<T>(object? response, bool isNullable) where T : class?
        => HandleServerResponse<T, T>(response, isNullable, o => o);

    /// <summary>
    /// Process and convert server response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="response"></param>
    /// <param name="isNullable"></param>
    /// <param name="converter">Optional converted to convert <typeparamref name="R"/> to <typeparamref name="T"/>.</param>
    /// <returns></returns>
    /// <exception cref="Exception"></exception>
    protected static T HandleServerResponse<R, T>(object? response, bool isNullable, Func<R, T> converter) where T : class? where R : class?
    {
        if (response is null)
        {
            if (isNullable)
            {
#pragma warning disable CS8603 // Possible null reference return.
                return null;
#pragma warning restore CS8603 // Possible null reference return.
            }
            throw new Exception($"Unexpected return type from Glide: got null expected {typeof(T).Name}");
        }
        response = ConvertByteArrayToGlideString(response);
#pragma warning disable IDE0046 // Convert to conditional expression
        if (response is R)
        {
            return converter((response as R)!);
        }
#pragma warning restore IDE0046 // Convert to conditional expression
        throw new Exception($"Unexpected return type from Glide: got {response?.GetType().Name} expected {typeof(T).Name}");
    }

    protected static object? ConvertByteArrayToGlideString(object? response)
    {
        if (response is null)
        {
            return null;
        }
        if (response is byte[] bytes)
        {
            response = new GlideString(bytes);
        }
        // TODO handle other types
        return response;
    }
    #endregion protected methods

    #region Dispose pattern

    protected virtual void Dispose(bool disposing)
    {
        if (disposing)
        {
            _nativeClient.Dispose();
        }
    }

    public void Dispose()
    {
        Dispose(true);
        GC.SuppressFinalize(this);
    }

    #endregion
}
