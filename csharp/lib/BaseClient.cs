// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

using Glide.Commands;
using Glide.Internals;

using static Glide.ConnectionConfiguration;
using static Glide.Route;

namespace Glide;

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

    protected delegate T ResponseHandler<T>(object? response);

    protected async Task<T> Command<T>(RequestType requestType, GlideString[] arguments, ResponseHandler<T> responseHandler, Route? route = null) where T : class?
    {
        // 1. Allocate memory for arguments and marshal them
        IntPtr[] args = _arrayPool.Rent(arguments.Length);
        for (int i = 0; i < arguments.Length; i++)
        {
            args[i] = Marshal.AllocHGlobal(arguments[i].Length);
            Marshal.Copy(arguments[i].Bytes, 0, args[i], arguments[i].Length);
        }

        // 2. Pin it
        // We need to pin the array in place, in order to ensure that the GC doesn't move it while the operation is running.
        GCHandle pinnedArgs = GCHandle.Alloc(args, GCHandleType.Pinned);
        IntPtr argsPointer = pinnedArgs.AddrOfPinnedObject();

        // 3. Allocate memory for arguments' lenghts and pin it too
        int[] lengths = ArrayPool<int>.Shared.Rent(arguments.Length);
        for (int i = 0; i < arguments.Length; i++)
        {
            lengths[i] = arguments[i].Length;
        }
        GCHandle pinnedLengths = GCHandle.Alloc(lengths, GCHandleType.Pinned);
        IntPtr lengthsPointer = pinnedLengths.AddrOfPinnedObject();

        // 4. Allocate memory for route
        IntPtr routePtr = IntPtr.Zero;
        if (route is not null)
        {
            routePtr = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(RouteInfo)));
            Marshal.StructureToPtr(route.ToFfi(), routePtr, false);
        }

        // 5. Sumbit request to the rust part
        Message message = _messageContainer.GetMessageForCall();
        CommandFfi(_clientPointer, (ulong)message.Index, (int)requestType, argsPointer, (uint)arguments.Length, lengthsPointer, routePtr);
        // All data must be copied in sync manner, so we

        // 6. Free memories allocated
        if (route is not null)
        {
            Marshal.FreeHGlobal(routePtr);
        }

        for (int i = 0; i < arguments.Length; i++)
        {
            Marshal.FreeHGlobal(args[i]);
        }
        pinnedArgs.Free();
        _arrayPool.Return(args);
        pinnedLengths.Free();
        ArrayPool<int>.Shared.Return(lengths);

        // 7. Get a response and Handle it
        return responseHandler(await message);
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

    #region private methods
    // TODO rework the callback to handle other response types
    private void SuccessCallback(ulong index, int strLen, IntPtr strPtr)
    {
        object? result = null;
        if (strPtr != IntPtr.Zero)
        {
            byte[] bytes = new byte[strLen];
            Marshal.Copy(strPtr, bytes, 0, strLen);
            result = bytes;
        }
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        _ = Task.Run(() =>
        {
            Message message = _messageContainer.GetMessage((int)index);
            message.SetResult(result);
        });
    }

    private void FailureCallback(ulong index) =>
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        Task.Run(() =>
        {
            Message message = _messageContainer.GetMessage((int)index);
            message.SetException(new Exception("Operation failed"));
        });

    ~BaseClient() => Dispose();
    #endregion private methods

    #region private fields

    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly FailureAction _failureCallbackDelegate;

    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly StringAction _successCallbackDelegate;

    /// Raw pointer to the underlying native client.
    private IntPtr _clientPointer;
    private readonly MessageContainer _messageContainer = new();
    private readonly ArrayPool<IntPtr> _arrayPool = ArrayPool<IntPtr>.Shared;
    private readonly object _lock = new();

    #endregion private fields

    #region FFI function declarations

    private delegate void StringAction(ulong index, int strLen, IntPtr strPtr);
    private delegate void FailureAction(ulong index);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    private static extern void CommandFfi(IntPtr client, ulong index, int requestType, IntPtr args, uint argCount, IntPtr argLengths, IntPtr routeInfo);

    private delegate void IntAction(IntPtr arg);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
    private static extern IntPtr CreateClientFfi(IntPtr config, IntPtr successCallback, IntPtr failureCallback);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_client")]
    private static extern void CloseClientFfi(IntPtr client);

    #endregion

    #region RequestType

    // TODO: generate this with a bindings generator
    protected enum RequestType
    {
        InvalidRequest = 0,
        CustomCommand = 1,
        Get = 1504,
        Set = 1517,
    }

    #endregion
}
