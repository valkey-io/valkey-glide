using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

using Valkey.Glide.InterOp.Errors;
using Valkey.Glide.InterOp.Internals;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

public sealed class NativeClient : IDisposable
{
    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly Imports.FailureAction _failureCallbackDelegate;

    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly Imports.SuccessAction _successCallbackDelegate;

    /// Raw pointer to the underlying native client.
    private IntPtr _clientPointer;

    private readonly MessageContainer _messageContainer = new();

    private readonly object _lock = new();

    private NativeClient()
    {
        _successCallbackDelegate = SuccessCallback;
        _failureCallbackDelegate = FailureCallback;
    }

    public static async Task<NativeClient> CreateClientAsync(FfiConnectionConfig request)
    {
        var client = new NativeClient();

        nint successCallbackPointer = Marshal.GetFunctionPointerForDelegate(client._successCallbackDelegate);
        nint failureCallbackPointer = Marshal.GetFunctionPointerForDelegate(client._failureCallbackDelegate);

        Message message = client._messageContainer.GetMessageForCall();
        Imports.CreateClientFfi(request.ToPtr(), successCallbackPointer, failureCallbackPointer);
        client._clientPointer = await message; // This will throw an error thru failure callback if any
        return client._clientPointer != IntPtr.Zero
            ? client
            : throw new ConnectionException("Failed creating a client");
    }

    public async Task<T> CommandAsync<T>(RequestType requestType, GlideString[] arguments,
        ResponseHandler<T> responseHandler, Route? route = null) where T : class?
    {
        // 1. Create Cmd which wraps CmdInfo and manages all memory allocations
        Cmd cmd = new(requestType, arguments);

        // 2. Allocate memory for route
        Native.Route? ffiRoute = route?.ToFfi();

        // 3. Sumbit request to the rust part
        Message message = _messageContainer.GetMessageForCall();
        Imports.CommandFfi(_clientPointer, (ulong)message.Index, cmd.ToPtr(), ffiRoute?.ToPtr() ?? IntPtr.Zero);
        // All data must be copied in sync manner, so we

        // 4. Free memories allocated
        ffiRoute?.Dispose();

        cmd.Dispose();

        // 5. Get a response and Handle it
        return responseHandler(await message);
    }


    private void SuccessCallback(ulong index, IntPtr ptr) =>
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        Task.Run(() => _messageContainer.GetMessage((int)index).SetResult(ptr));

    private void FailureCallback(ulong index, IntPtr strPtr, RequestErrorType errType)
    {
        string str = Marshal.PtrToStringAnsi(strPtr)!;
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        _ = Task.Run(() =>
        {
            _messageContainer.GetMessage((int)index).SetException(GlideException.Create(errType, str));
        });
    }

    ~NativeClient()
    {
        Dispose();
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
            Imports.CloseClientFfi(_clientPointer);
            _clientPointer = IntPtr.Zero;
        }
    }

    public override string ToString() => _clientPointer.ToString("X");
    [SuppressMessage("ReSharper", "NonReadonlyMemberInGetHashCode", Justification = "_clientPointer will always be initialalized for NativeClient")]
    public override int GetHashCode() => (int)_clientPointer;
    /// <summary>
    /// Process and convert a server response.
    /// </summary>
    /// <typeparam name="R">GLIDE's return type.</typeparam>
    /// <typeparam name="T">Command's return type.</typeparam>
    /// <param name="response"></param>
    /// <param name="isNullable"></param>
    /// <param name="converter">Optional function to convert <typeparamref name="R" /> to <typeparamref name="T" />.</param>
    /// <returns></returns>
    /// <exception cref="Exception"></exception>
    public static T HandleServerResponse<R, T>(IntPtr response, bool isNullable, Func<R, T> converter)
        where T : class? where R : class?
    {
        try
        {
            object? value = ResponseHandler.HandleResponse(response);
            if (value is null)
            {
                if (isNullable)
                {
#pragma warning disable CS8603 // Possible null reference return.
                    return null;
#pragma warning restore CS8603 // Possible null reference return.
                }

                throw new Exception(
                    $"Unexpected return type from Glide: got null expected {typeof(T).GetRealTypeName()}");
            }

            return value is R
                ? converter((value as R)!)
                : throw new RequestException(
                    $"Unexpected return type from Glide: got {value?.GetType().GetRealTypeName()} expected {typeof(T).GetRealTypeName()}");
        }
        finally
        {
            Imports.FreeResponse(response);
        }
    }

}
