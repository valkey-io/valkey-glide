using System.Runtime.InteropServices;

namespace babushka
{
    public class AsyncClient
    {

        #region public methods
        public AsyncClient(string address)
        {
            successCallbackDelegate = SuccessCallback;
            var successCallbackPointer = Marshal.GetFunctionPointerForDelegate(successCallbackDelegate);
            failureCallbackDelegate = FailureCallback;
            var failureCallbackPointer = Marshal.GetFunctionPointerForDelegate(failureCallbackDelegate);
            connectionPointer = CreateConnectionFfi(address, successCallbackPointer, failureCallbackPointer);
            if (connectionPointer == IntPtr.Zero)
            {
                throw new Exception("Failed creating a connection");
            }
        }

        public Task SetAsync(string key, string value)
        {
            var (message, task) = messageContainer.GetMessageForCall(key, value);
            SetFfi(connectionPointer, (ulong)message.Index, message.KeyPtr, message.ValuePtr);
            return task;
        }

        public Task<string?> GetAsync(string key)
        {
            var (message, task) = messageContainer.GetMessageForCall(key, null);
            GetFfi(connectionPointer, (ulong)message.Index, message.KeyPtr);
            return task;
        }

        #endregion public methods

        #region private methods

        private void SuccessCallback(ulong index, IntPtr str)
        {
            var result = str == IntPtr.Zero ? null : Marshal.PtrToStringAnsi(str);
            // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
            Task.Run(() =>
            {
                var message = messageContainer.GetMessage((int)index);
                message.SetResult(result);
            });
        }

        private void FailureCallback(ulong index)
        {
            // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
            Task.Run(() =>
            {
                var message = messageContainer.GetMessage((int)index);
                message.SetException(new Exception("Operation failed"));
            });
        }

        ~AsyncClient()
        {
            CloseConnectionFfi(connectionPointer);
        }
        #endregion private methods

        #region private fields
        /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
        /// and held in order to prevent the cost of marshalling on each function call.
        private FailureAction failureCallbackDelegate;

        /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
        /// and held in order to prevent the cost of marshalling on each function call.
        private StringAction successCallbackDelegate;

        /// Raw pointer to the underlying native connection.
        private readonly IntPtr connectionPointer;

        private readonly MessageContainer messageContainer = new();

        #endregion private fields

        #region FFI function declarations

        private delegate void StringAction(ulong index, IntPtr str);
        private delegate void FailureAction(ulong index);
        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "get")]
        private static extern void GetFfi(IntPtr connection, ulong index, IntPtr key);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "set")]
        private static extern void SetFfi(IntPtr connection, ulong index, IntPtr key, IntPtr value);

        private delegate void IntAction(IntPtr arg);
        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_connection")]
        private static extern IntPtr CreateConnectionFfi(String address, IntPtr successCallback, IntPtr failureCallback);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_connection")]
        private static extern void CloseConnectionFfi(IntPtr connection);
        #endregion
    }
}
