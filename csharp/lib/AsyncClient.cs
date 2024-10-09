// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

namespace Glide;

public class AsyncClient : IDisposable
{
    #region public methods
    public AsyncClient(string host, uint port, bool useTLS)
    {
        _successCallbackDelegate = SuccessCallback;
        nint successCallbackPointer = Marshal.GetFunctionPointerForDelegate(_successCallbackDelegate);
        _failureCallbackDelegate = FailureCallback;
        nint failureCallbackPointer = Marshal.GetFunctionPointerForDelegate(_failureCallbackDelegate);
        _clientPointer = CreateClientFfi(host, port, useTLS, successCallbackPointer, failureCallbackPointer);
        if (_clientPointer == IntPtr.Zero)
        {
            throw new Exception("Failed creating a client");
        }
    }

    private async Task<string?> Command(IntPtr[] args, int argsCount, RequestType requestType)
    {
        // We need to pin the array in place, in order to ensure that the GC doesn't move it while the operation is running.
        GCHandle pinnedArray = GCHandle.Alloc(args, GCHandleType.Pinned);
        IntPtr pointer = pinnedArray.AddrOfPinnedObject();
        Message<string> message = _messageContainer.GetMessageForCall(args, argsCount);
        CommandFfi(_clientPointer, (ulong)message.Index, (int)requestType, pointer, (uint)argsCount);
        string? result = await message;
        pinnedArray.Free();
        return result;
    }

    public async Task<string?> SetAsync(string key, string value)
    {
        IntPtr[] args = _arrayPool.Rent(2);
        args[0] = Marshal.StringToHGlobalAnsi(key);
        args[1] = Marshal.StringToHGlobalAnsi(value);
        string? result = await Command(args, 2, RequestType.Set);
        _arrayPool.Return(args);
        return result;
    }

    public async Task<string?> GetAsync(string key)
    {
        IntPtr[] args = _arrayPool.Rent(1);
        args[0] = Marshal.StringToHGlobalAnsi(key);
        string? result = await Command(args, 1, RequestType.Get);
        _arrayPool.Return(args);
        return result;
    }

    public void Dispose()
    {
        if (_clientPointer == IntPtr.Zero)
        {
            return;
        }
        _messageContainer.DisposeWithError(null);
        CloseClientFfi(_clientPointer);
        _clientPointer = IntPtr.Zero;
    }

    #endregion public methods

    #region private methods

    private void SuccessCallback(ulong index, IntPtr str)
    {
        string? result = str == IntPtr.Zero ? null : Marshal.PtrToStringAnsi(str);
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        _ = Task.Run(() =>
        {
            Message<string> message = _messageContainer.GetMessage((int)index);
            message.SetResult(result);
        });
    }

    private void FailureCallback(ulong index) =>
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        Task.Run(() =>
        {
            Message<string> message = _messageContainer.GetMessage((int)index);
            message.SetException(new Exception("Operation failed"));
        });

    ~AsyncClient() => Dispose();
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
    private readonly MessageContainer<string> _messageContainer = new();
    private readonly ArrayPool<IntPtr> _arrayPool = ArrayPool<IntPtr>.Shared;

    #endregion private fields

    #region FFI function declarations

    private delegate void StringAction(ulong index, IntPtr str);
    private delegate void FailureAction(ulong index);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    private static extern void CommandFfi(IntPtr client, ulong index, int requestType, IntPtr args, uint argCount);

    private delegate void IntAction(IntPtr arg);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
    private static extern IntPtr CreateClientFfi(string host, uint port, bool useTLS, IntPtr successCallback, IntPtr failureCallback);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_client")]
    private static extern void CloseClientFfi(IntPtr client);

    #endregion

    #region RequestType

    // TODO: generate this with a bindings generator
    private enum RequestType
    {
        InvalidRequest = 0,
        CustomCommand = 1,
        Get = 1504,
        Set = 1517,
    }

    #endregion
}
