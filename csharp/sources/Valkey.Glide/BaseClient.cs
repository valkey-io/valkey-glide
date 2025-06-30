// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

using Valkey.Glide.Internals;
using Valkey.Glide.Pipeline;

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Errors;
using static Valkey.Glide.Internals.FFI;
using static Valkey.Glide.Internals.ResponseConverters;
using static Valkey.Glide.Internals.ResponseHandler;
using static Valkey.Glide.Pipeline.Options;

namespace Valkey.Glide;

public abstract partial class BaseClient : IDisposable
{
    #region public methods

    /**
    =================================
    SET COMMANDS
    =================================
    */
    public async Task<bool> SetAdd(RedisKey key, RedisValue value, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), value.ToString()];
        return await CommandValueType(RequestType.SAdd, args, response => HandleServerResponseValueType<long, bool>(response, false, res => res == 1));
    }

    public async Task<long> SetAdd(RedisKey key, RedisValue[] values, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), .. values.Select((v) => v.ToString())];
        return await CommandValueType(RequestType.SAdd, args, response => HandleServerResponseValueType<long>(response));
    }

    public async Task<bool> SetRemove(RedisKey key, RedisValue value, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), value.ToString()];
        return await CommandValueType(RequestType.SRem, args, response => HandleServerResponseValueType<long, bool>(response, false, res => res == 1));
    }

    public async Task<long> SetRemove(RedisKey key, RedisValue[] values, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), .. values.Select((v) => v.ToString())];
        return await CommandValueType(RequestType.SRem, args, response => HandleServerResponseValueType<long>(response));
    }

    public async Task<RedisValue[]> SetMembers(RedisKey key, CommandFlags flags = CommandFlags.None)
        => await Command(RequestType.SMembers, [key.ToString()], response => HandleServerResponse<HashSet<object>, RedisValue[]>(response, false, set => set.Select(obj => (RedisValue)obj.ToString()).ToArray()));

    public async Task<long> SetLength(RedisKey key, CommandFlags flags = CommandFlags.None)
        => await CommandValueType(RequestType.SCard, [key.ToString()], response => HandleServerResponseValueType<long>(response));

    public async Task<long> SetIntersectionLength(RedisKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None)
    {
        List<GlideString> args = [keys.Length.ToString(), .. keys.Select(k => k.ToString())];
        if (limit > 0)
        {
            args.Add(Constants.LimitKeyword);
            args.Add(limit.ToString());
        }
        return await CommandValueType(RequestType.SInterCard, args.ToArray(), response => HandleServerResponseValueType<long>(response));
    }

    public async Task<RedisValue> SetPop(RedisKey key, CommandFlags flags = CommandFlags.None)
    {
        return await CommandValueType(RequestType.SPop, [key.ToString()], response =>
        {
            var result = HandleServerResponse<GlideString>(response, true);
            return result is not null ? (RedisValue)result.ToString() : RedisValue.Null;
        });
    }

    public async Task<RedisValue[]> SetPop(RedisKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        GlideString[] args = [key.ToString(), count.ToString()];
        return await Command(RequestType.SPop, args, response => HandleServerResponse<HashSet<object>, RedisValue[]>(response, false, set => set.Select(obj => (RedisValue)obj.ToString()).ToArray()));
    }

    public async Task<RedisValue[]> SetCombine(SetOperation operation, RedisKey first, RedisKey second, CommandFlags flags = CommandFlags.None)
        => await SetCombine(operation, [first, second], flags);

    public async Task<RedisValue[]> SetCombine(SetOperation operation, RedisKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        RequestType requestType = operation switch
        {
            SetOperation.Union => RequestType.SUnion,
            SetOperation.Intersect => RequestType.SInter,
            SetOperation.Difference => RequestType.SDiff,
            _ => throw new ArgumentOutOfRangeException(nameof(operation))
        };

        GlideString[] args = keys.Select(k => (GlideString)k.ToString()).ToArray();
        return await Command(requestType, args, response => HandleServerResponse<HashSet<object>, RedisValue[]>(response, false, set => set.Select(obj => (RedisValue)obj.ToString()).ToArray()));
    }

    public async Task<long> SetCombineAndStore(SetOperation operation, RedisKey destination, RedisKey first, RedisKey second, CommandFlags flags = CommandFlags.None)
        => await SetCombineAndStore(operation, destination, [first, second], flags);

    public async Task<long> SetCombineAndStore(SetOperation operation, RedisKey destination, RedisKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        RequestType requestType = operation switch
        {
            SetOperation.Union => RequestType.SUnionStore,
            SetOperation.Intersect => RequestType.SInterStore,
            SetOperation.Difference => RequestType.SDiffStore,
            _ => throw new ArgumentOutOfRangeException(nameof(operation))
        };

        List<GlideString> args = [destination.ToString()];
        args.AddRange(keys.Select(k => (GlideString)k.ToString()));
        return await CommandValueType(requestType, args.ToArray(), response => HandleServerResponseValueType<long>(response));
    }

    public void Dispose()
    {
        GC.SuppressFinalize(this);
        lock (_lock)
        {
            if (_clientPointer == IntPtr.Zero)
            {
                return;
            }
            _messageContainer.DisposeWithError(null);
            CloseClientFfi(_clientPointer);
            _clientPointer = IntPtr.Zero;
        }
    }

    public override string ToString() => $"{GetType().Name} {{ 0x{_clientPointer:X} }}";

    public override int GetHashCode() => (int)_clientPointer;

    #endregion public methods

    #region protected methods
    protected static async Task<T> CreateClient<T>(BaseClientConfiguration config, Func<T> ctor) where T : BaseClient
    {
        T client = ctor();

        nint successCallbackPointer = Marshal.GetFunctionPointerForDelegate(client._successCallbackDelegate);
        nint failureCallbackPointer = Marshal.GetFunctionPointerForDelegate(client._failureCallbackDelegate);

        using FFI.ConnectionConfig request = config.Request.ToFfi();
        Message message = client._messageContainer.GetMessageForCall();
        CreateClientFfi(request.ToPtr(), successCallbackPointer, failureCallbackPointer);
        client._clientPointer = await message; // This will throw an error thru failure callback if any
        return client._clientPointer != IntPtr.Zero
            ? client
            : throw new ConnectionException("Failed creating a client");
    }

    protected BaseClient()
    {
        _successCallbackDelegate = SuccessCallback;
        _failureCallbackDelegate = FailureCallback;
        _messageContainer = new(this);
    }

    protected internal delegate T ResponseHandler<T>(IntPtr response);

    /// <summary>
    /// </summary>
    /// <typeparam name="R">Type received from server.</typeparam>
    /// <typeparam name="T">Type we return to the user.</typeparam>
    /// <param name="command"></param>
    /// <param name="route"></param>
    /// <returns></returns>
    internal async Task<T> Command<R, T>(Request.Cmd<R, T> command, Route? route = null)
    {
        // 1. Create Cmd which wraps CmdInfo and manages all memory allocations
        using Cmd cmd = command.ToFfi();

        // 2. Allocate memory for route
        using FFI.Route? ffiRoute = route?.ToFfi();

        // 3. Sumbit request to the rust part
        Message message = _messageContainer.GetMessageForCall();
        CommandFfi(_clientPointer, (ulong)message.Index, cmd.ToPtr(), ffiRoute?.ToPtr() ?? IntPtr.Zero);

        // 4. Get a response and Handle it
        IntPtr response = await message;
        try
        {
            return HandleServerValue(HandleResponse(response), command.IsNullable, command.Converter);
        }
        finally
        {
            FreeResponse(response);
        }

        // All memory allocated is auto-freed by `using` operator
    }

    // TODO: remove
    internal async Task<T> CommandValueType<T>(RequestType requestType, GlideString[] arguments, Func<IntPtr, T> responseHandler, Route? route = null) where T : struct
    {
        // 1. Create Cmd which wraps CmdInfo and manages all memory allocations
        using Cmd cmd = command.ToFfi();

        // 2. Allocate memory for route
        using FFI.Route? ffiRoute = route?.ToFfi();

        // 3. Sumbit request to the rust part
        Message message = _messageContainer.GetMessageForCall();
        CommandFfi(_clientPointer, (ulong)message.Index, cmd.ToPtr(), ffiRoute?.ToPtr() ?? IntPtr.Zero);

        // 4. Get a response and Handle it
        IntPtr response = await message;
        try
        {
            return HandleServerValue(HandleResponse(response), command.IsNullable, command.Converter);
        }
        finally
        {
            FreeResponse(response);
        }

        // All memory allocated is auto-freed by `using` operator
    }

    protected async Task<object?[]?> Batch<T>(BaseBatch<T> batch, bool raiseOnError, BaseBatchOptions? options = null) where T : BaseBatch<T>
    {
        // 1. Allocate memory for batch, which allocates all nested Cmds
        using FFI.Batch ffiBatch = batch.ToFFI();

        // 2. Allocate memory for options
        using FFI.BatchOptions? ffiOptions = options?.ToFfi();

        // 3. Sumbit request to the rust part
        Message message = _messageContainer.GetMessageForCall();
        BatchFfi(_clientPointer, (ulong)message.Index, ffiBatch.ToPtr(), raiseOnError, ffiOptions?.ToPtr() ?? IntPtr.Zero);

        // 4. Get a response and Handle it
        IntPtr response = await message;
        try
        {
            return batch.ConvertResponse(HandleServerValue(HandleResponse(response), true, (object?[]? o) => o));
        }
        finally
        {
            FreeResponse(response);
        }

        // All memory allocated is auto-freed by `using` operator
    }
    #endregion protected methods

    #region private methods
    private void SuccessCallback(ulong index, IntPtr ptr) =>
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        Task.Run(() => _messageContainer.GetMessage((int)index).SetResult(ptr));

    private void FailureCallback(ulong index, IntPtr strPtr, RequestErrorType errType)
    {
        string str = Marshal.PtrToStringAnsi(strPtr)!;
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        _ = Task.Run(() => _messageContainer.GetMessage((int)index).SetException(Create(errType, str)));
    }

    ~BaseClient() => Dispose();
    #endregion private methods

    #region private fields

    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly FailureAction _failureCallbackDelegate;

    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly SuccessAction _successCallbackDelegate;

    /// Raw pointer to the underlying native client.
    private IntPtr _clientPointer;
    private readonly MessageContainer _messageContainer;
    private readonly object _lock = new();

    #endregion private fields

    #region FFI function declarations

    private delegate void SuccessAction(ulong index, IntPtr ptr);
    private delegate void FailureAction(ulong index, IntPtr strPtr, RequestErrorType err);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    private static extern void CommandFfi(IntPtr client, ulong index, IntPtr cmdInfo, IntPtr routeInfo);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "batch")]
    private static extern void BatchFfi(IntPtr client, ulong index, IntPtr batch, [MarshalAs(UnmanagedType.U1)] bool raiseOnError, IntPtr opts);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "free_response")]
    private static extern void FreeResponse(IntPtr response);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
    private static extern void CreateClientFfi(IntPtr config, IntPtr successCallback, IntPtr failureCallback);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_client")]
    private static extern void CloseClientFfi(IntPtr client);

    #endregion
}
