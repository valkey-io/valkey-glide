// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

using Glide.Commands;
using Glide.Internals;

namespace Glide;

public abstract class BaseClient : IDisposable, IStringBaseCommands
{
    #region public methods
    protected BaseClient(string host, uint port, bool useTLS)
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

    protected async Task<T> Command<T>(GlideString[] arguments, RequestType requestType) where T : class?
    {
        // 1. Allocate memory for arguments and marshal it
        IntPtr[] args = _arrayPool.Rent(arguments.Length);
        for (int i = 0; i < arguments.Length; i++)
        {
            args[i] = Marshal.AllocHGlobal(arguments[i].Length);
            Marshal.Copy(arguments[i].Bytes, 0, args[i], arguments[i].Length);
        }
        // 2. Pin it
        // We need to pin the array in place, in order to ensure that the GC doesn't move it while the operation is running.
        GCHandle pinnedArray = GCHandle.Alloc(args, GCHandleType.Pinned);
        IntPtr pointer = pinnedArray.AddrOfPinnedObject();
        // 3. Allocate memory for arguments' lenghts and marshal it
        // TODO PIN TOO
        IntPtr argsLenghts = Marshal.AllocHGlobal(arguments.Length * Marshal.SizeOf<uint>());
        int[] lengths = [.. arguments.Select(s => s.Length)];
        Marshal.Copy(lengths, 0, argsLenghts, arguments.Length);

        // 5. Sumbit request to rust part
        Message message = _messageContainer.GetMessageForCall<T>(args);
        CommandFfi(_clientPointer, (ulong)message.Index, (int)requestType, pointer, (uint)arguments.Length, argsLenghts);
        // All data must be copied in sync manner, so we

        // 6. Free memories allocated
        Marshal.FreeHGlobal(argsLenghts);
        for (int i = 0; i < arguments.Length; i++)
        {
            Marshal.FreeHGlobal(args[i]);
        }
        pinnedArray.Free();
        _arrayPool.Return(args);

        // 7. Get a response
        object? response = await message;

        // 8. Handle it
        // TODO handler as `Command` argument
#pragma warning disable CS8603 // Possible null reference return.
#pragma warning disable CS8602 // Dereference of a possibly null reference.
        if (Nullable.GetUnderlyingType(typeof(T)) != null)
        {
            // T is a Nullable<>
            return response is null ? null : throw new NullReferenceException("Received not null: " + response);
        }

        if (response.GetType() == typeof(GlideString) && typeof(string) == typeof(T))
        {
            // Received GlideString but command retuns a string
            return (response as GlideString).ToString() as T;
        }
        return response as T;
#pragma warning restore CS8602 // Dereference of a possibly null reference.
#pragma warning restore CS8603 // Possible null reference return.
    }

    public async Task<string> Set(GlideString key, GlideString value)
        => await Command<string>([key, value], RequestType.Set);

    public async Task<GlideString?> Get(GlideString key)
        => await Command<GlideString?>([key], RequestType.Get);

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
    private void SuccessCallback(ulong index, int strLen, IntPtr strPtr)
    {
        object? result = null;
        if (strPtr != IntPtr.Zero)
        {
            byte[] bytes = new byte[strLen];
            Marshal.Copy(strPtr, bytes, 0, strLen);
            result = bytes.ToGlideString();
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

    #endregion private fields

    #region FFI function declarations

    private delegate void StringAction(ulong index, int strLen, IntPtr strPtr);
    private delegate void FailureAction(ulong index);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    private static extern void CommandFfi(IntPtr client, ulong index, int requestType, IntPtr args, uint argCount, IntPtr argLengths);

    private delegate void IntAction(IntPtr arg);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
    private static extern IntPtr CreateClientFfi(string host, uint port, bool useTLS, IntPtr successCallback, IntPtr failureCallback);

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
