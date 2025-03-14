// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

using Glide.Commands;
using Glide.Internals;

using static Glide.ConnectionConfiguration;

namespace Glide;

public abstract class BaseClient : IDisposable, IStringBaseCommands
{
    #region public methods
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

    protected async Task<T> Command<T>(string[] arguments, RequestType requestType) where T : class?
    {
        IntPtr[] args = _arrayPool.Rent(arguments.Length);
        for (int i = 0; i < arguments.Length; i++)
        {
            args[i] = Marshal.StringToHGlobalAnsi(arguments[i]);
        }
        // We need to pin the array in place, in order to ensure that the GC doesn't move it while the operation is running.
        GCHandle pinnedArray = GCHandle.Alloc(args, GCHandleType.Pinned);
        IntPtr pointer = pinnedArray.AddrOfPinnedObject();
        Message message = _messageContainer.GetMessageForCall<T>(args);
        CommandFfi(_clientPointer, (ulong)message.Index, (int)requestType, pointer, (uint)arguments.Length);
        for (int i = 0; i < arguments.Length; i++)
        {
            Marshal.FreeHGlobal(args[i]);
        }
        pinnedArray.Free();
        _arrayPool.Return(args);
#pragma warning disable CS8603 // Possible null reference return.
        return await message as T;
#pragma warning restore CS8603 // Possible null reference return.
    }

    public async Task<string> Set(string key, string value)
        => await Command<string>([key, value], RequestType.Set);

    public async Task<string?> Get(string key)
        => await Command<string?>([key], RequestType.Get);

    private readonly object _lock = new();

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

    #region private methods
    // TODO rework the callback to handle other response types
    private void SuccessCallback(ulong index, IntPtr str)
    {
        string? result = str == IntPtr.Zero ? null : Marshal.PtrToStringAnsi(str);
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

    #endregion private fields

    #region FFI function declarations

    private delegate void StringAction(ulong index, IntPtr str);
    private delegate void FailureAction(ulong index);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    private static extern void CommandFfi(IntPtr client, ulong index, int requestType, IntPtr args, uint argCount);

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
