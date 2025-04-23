// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

using Valkey.Glide.Pipeline;
using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

using static Valkey.Glide.Pipeline.Options;
using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Internals.FFI;
using static Valkey.Glide.Internals.ResponseHandler;
using static Valkey.Glide.Route;
using System.Diagnostics;

namespace Valkey.Glide;

public abstract class BaseClient : IDisposable, IStringBaseCommands
{
    #region public methods
    public async Task<string> Set(GlideString key, GlideString value)
        => await Command(RequestType.Set, [key, value], HandleOk);

    public async Task<GlideString?> Get(GlideString key)
        => await Command(RequestType.Get, [key], response => HandleServerResponse<GlideString>(response, true));

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
    protected BaseClient(BaseClientConfiguration config)
    {
        _successCallbackDelegate = SuccessCallback;
        nint successCallbackPointer = Marshal.GetFunctionPointerForDelegate(_successCallbackDelegate);
        _failureCallbackDelegate = FailureCallback;
        nint failureCallbackPointer = Marshal.GetFunctionPointerForDelegate(_failureCallbackDelegate);
        nint configPtr = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(ConnectionRequest)));
        Marshal.StructureToPtr(config.ToRequest(), configPtr, false);
        _clientPointer = CreateClientFfi(configPtr, successCallbackPointer, failureCallbackPointer);
        Marshal.FreeHGlobal(configPtr);
        if (_clientPointer == IntPtr.Zero)
        {
            throw new Exception("Failed creating a client");
        }
    }

    internal protected delegate T ResponseHandler<T>(IntPtr response);

    internal async Task<T> Command<T>(RequestType requestType, GlideString[] arguments, ResponseHandler<T> responseHandler, Route? route = null) where T : class?
    {
        // 1. Create Cmd which wraps CmdInfo and manages all memory allocations
        using FFI.Cmd cmd = new(requestType, arguments);

        // 2. Allocate memory for route
        using FFI.Route? ffiRoute = route?.ToFfi();

        // 3. Sumbit request to the rust part
        Message message = _messageContainer.GetMessageForCall();
        CommandFfi(_clientPointer, (ulong)message.Index, cmd.ToPtr(), ffiRoute?.ToPtr() ?? IntPtr.Zero);

        // 4. Get a response and Handle it
        return responseHandler(await message);

        // All memory allocated is auto-freed by `using` operator
    }

    protected async Task<object?[]?> Batch<T>(BaseBatch<T> batch, BaseBatchOptions? options = null) where T : BaseBatch<T>
    {
        // 1. Allocate memory for batch, which allocates all nested Cmds
        using FFI.Batch ffiBatch = batch.ToFFI();

        // 2. Allocate memory for options
        using FFI.BatchOptions? ffiOptions = options?.ToFfi();

        // 3. Sumbit request to the rust part
        Message message = _messageContainer.GetMessageForCall();
        BatchFfi(_clientPointer, (ulong)message.Index, ffiBatch.ToPtr(), ffiOptions?.ToPtr() ?? IntPtr.Zero);

        // 4. Get a response and Handle it
        return HandleServerResponse<object?[]?>(await message, true);

        // All memory allocated is auto-freed by `using` operator
    }

    internal protected static string HandleOk(IntPtr response)
        => HandleServerResponse<GlideString, string>(response, false, gs => gs.GetString());

    internal protected static T HandleServerResponse<T>(IntPtr response, bool isNullable) where T : class?
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
    internal protected static T HandleServerResponse<R, T>(IntPtr response, bool isNullable, Func<R, T> converter) where T : class? where R : class?
    {
        try
        {
            object? value = HandleResponse(response);
            if (value is null)
            {
                if (isNullable)
                {
#pragma warning disable CS8603 // Possible null reference return.
                    return null;
#pragma warning restore CS8603 // Possible null reference return.
                }
                throw new Exception($"Unexpected return type from Glide: got null expected {typeof(T).Name}");
            }
            return value is R
                ? converter((value as R)!)
                : throw new Exception($"Unexpected return type from Glide: got {value?.GetType().Name} expected {typeof(T).Name}");
        }
        finally
        {
            FreeResponse(response);
        }
    }
    #endregion protected methods

    #region private methods
    private void SuccessCallback(ulong index, IntPtr ptr) =>
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        Task.Run(() => _messageContainer.GetMessage((int)index).SetResult(ptr));

    private void FailureCallback(ulong index) =>
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        Task.Run(() => _messageContainer.GetMessage((int)index).SetException(new Exception("Operation failed")));

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
    private readonly MessageContainer _messageContainer = new();
    private readonly ArrayPool<IntPtr> _arrayPool = ArrayPool<IntPtr>.Shared;
    private readonly object _lock = new();

    #endregion private fields

    #region FFI function declarations

    private delegate void SuccessAction(ulong index, IntPtr ptr);
    private delegate void FailureAction(ulong index);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    private static extern void CommandFfi(IntPtr client, ulong index, IntPtr cmdInfo, IntPtr routeInfo);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "batch")]
    private static extern void BatchFfi(IntPtr client, ulong index, IntPtr batch, IntPtr opts);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "free_respose")]
    private static extern void FreeResponse(IntPtr response);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
    private static extern IntPtr CreateClientFfi(IntPtr config, IntPtr successCallback, IntPtr failureCallback);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_client")]
    private static extern void CloseClientFfi(IntPtr client);

    #endregion
}
