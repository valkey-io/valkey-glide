/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

using System.Runtime.InteropServices;

namespace Glide
{
    public class AsyncClient : IDisposable
    {
        #region public methods
        public AsyncClient(string host, UInt32 port, bool useTLS)
        {
            successCallbackDelegate = SuccessCallback;
            var successCallbackPointer = Marshal.GetFunctionPointerForDelegate(successCallbackDelegate);
            failureCallbackDelegate = FailureCallback;
            var failureCallbackPointer = Marshal.GetFunctionPointerForDelegate(failureCallbackDelegate);
            clientPointer = CreateClientFfi(host, port, useTLS, successCallbackPointer, failureCallbackPointer);
            if (clientPointer == IntPtr.Zero)
            {
                throw new Exception("Failed creating a client");
            }
        }

        public async Task SetAsync(string key, string value)
        {
            var message = messageContainer.GetMessageForCall(key, value);
            SetFfi(clientPointer, (ulong)message.Index, message.KeyPtr, message.ValuePtr);
            await message;
        }

        public async Task<string?> GetAsync(string key)
        {
            var message = messageContainer.GetMessageForCall(key, null);
            GetFfi(clientPointer, (ulong)message.Index, message.KeyPtr);
            return await message;
        }

        public void Dispose()
        {
            if (clientPointer == IntPtr.Zero)
            {
                return;
            }
            messageContainer.DisposeWithError(null);
            CloseClientFfi(clientPointer);
            clientPointer = IntPtr.Zero;
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
            Dispose();
        }
        #endregion private methods

        #region private fields

        /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
        /// and held in order to prevent the cost of marshalling on each function call.
        private FailureAction failureCallbackDelegate;

        /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
        /// and held in order to prevent the cost of marshalling on each function call.
        private StringAction successCallbackDelegate;

        /// Raw pointer to the underlying native client.
        private IntPtr clientPointer;

        private readonly MessageContainer<string> messageContainer = new();

        #endregion private fields

        #region FFI function declarations

        private delegate void StringAction(ulong index, IntPtr str);
        private delegate void FailureAction(ulong index);
        [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "get")]
        private static extern void GetFfi(IntPtr client, ulong index, IntPtr key);

        [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "set")]
        private static extern void SetFfi(IntPtr client, ulong index, IntPtr key, IntPtr value);

        private delegate void IntAction(IntPtr arg);
        [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
        private static extern IntPtr CreateClientFfi(String host, UInt32 port, bool useTLS, IntPtr successCallback, IntPtr failureCallback);

        [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_client")]
        private static extern void CloseClientFfi(IntPtr client);

        #endregion
    }
}
