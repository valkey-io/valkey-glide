using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using System.Threading.Tasks.Sources;

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

        public async Task SetAsync(string key, string value)
        {
            var (message, task) = GetMessageForCall(key, value);
            SetFfi(connectionPointer, (ulong)message.Index, message.KeyPtr, message.ValuePtr);
            await task;
        }

        public async Task<string?> GetAsync(string key)
        {
            var (message, task) = GetMessageForCall(key, null);
            GetFfi(connectionPointer, (ulong)message.Index, message.KeyPtr);
            return await task;
        }

        #endregion public methods

        #region Message

        /// Reusable source of ValueTask. This object can be allocated once and then reused
        /// to create multiple asynchronous operations, as long as each call to CreateTask
        /// is awaited to completion before the next call begins.
        private class Message<T> : IValueTaskSource<T>
        {
            /// This is the index of the message in an external array, that allows the user to
            /// know how to find the message and set its result.
            public int Index { get; }

            /// The pointer to the unmanaged memory that contains the operation's key.
            public IntPtr KeyPtr { get; private set; }

            /// The pointer to the unmanaged memory that contains the operation's key.
            public IntPtr ValuePtr { get; private set; }

            public Message(int index)
            {
                Index = index;
            }

            /// Triggers a succesful completion of the task returned from the latest call 
            /// to CreateTask.
            public void SetResult(T result) => _source.SetResult(result);

            /// Triggers a failure completion of the task returned from the latest call to
            /// CreateTask.
            public void SetException(Exception exc) => _source.SetException(exc);

            /// This returns a task that will complete once SetException / SetResult are called,
            /// and ensures that the internal state of the message is set-up before the task is created,
            /// and cleaned once it is complete.
            public async Task<T> CreateTask(string key, string? value, AsyncClient client)
            {
                this.client = client;
                this.KeyPtr = Marshal.StringToHGlobalAnsi(key);
                this.ValuePtr = value is null ? IntPtr.Zero : Marshal.StringToHGlobalAnsi(value);
                var result = await new ValueTask<T>(this, _source.Version);
                FreePointers();
                _source.Reset();
                return result;
            }

            private void FreePointers()
            {
                Marshal.FreeHGlobal(KeyPtr);
                if (ValuePtr != IntPtr.Zero)
                {
                    Marshal.FreeHGlobal(ValuePtr);
                }
                client = null;
            }

            // Holding the client prevents it from being CG'd until all operations complete.
            private AsyncClient? client;

            private ManualResetValueTaskSourceCore<T> _source = new ManualResetValueTaskSourceCore<T>()
            {
                RunContinuationsAsynchronously = false
            };

            ValueTaskSourceStatus IValueTaskSource<T>.GetStatus(short token)
                => _source.GetStatus(token);
            void IValueTaskSource<T>.OnCompleted(Action<object?> continuation,
                object? state, short token, ValueTaskSourceOnCompletedFlags flags)
                    => _source.OnCompleted(continuation, state, token, flags);
            T IValueTaskSource<T>.GetResult(short token) => _source.GetResult(token);
        }

        #endregion Message

        #region private methods

        private void SuccessCallback(ulong index, IntPtr str)
        {
            var result = str == IntPtr.Zero ? null : Marshal.PtrToStringAnsi(str);
            // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
            Task.Run(() =>
            {
                var message = messages[(int)index];
                message.SetResult(result);
            });
        }

        private void FailureCallback(ulong index)
        {
            // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
            Task.Run(() =>
            {
                var message = messages[(int)index];
                message.SetException(new Exception("Operation failed"));
            });
        }

        private (Message<string?>, Task<string?>) GetMessageForCall(string key, string? value)
        {
            var message = GetFreeMessage();
            var task = message.CreateTask(key, value, this).ContinueWith(result =>
            {
                availableMessages.Enqueue(message);
                return result.Result;
            });
            return (message, task);
        }

        private Message<string?> GetFreeMessage()
        {
            if (!availableMessages.TryDequeue(out var message))
            {
                lock (messages)
                {
                    var index = messages.Count;
                    message = new Message<string?>(index);
                    messages.Add(message);
                }
            }
            return message;
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

        /// This list allows us random-access to the message in each index,
        /// which means that once we receive a callback with an index, we can
        /// find the message to resolve in constant time.
        private List<Message<string?>> messages = new();

        /// This queue contains the messages that were created and are currently unused by any task,
        /// so they can be reused y new tasks instead of allocating new messages.
        private ConcurrentQueue<Message<string?>> availableMessages = new();

        /// Raw pointer to the underlying native connection.
        private readonly IntPtr connectionPointer;

        #endregion private fields

        #region FFI function declarations

        public delegate void StringAction(ulong index, IntPtr str);
        public delegate void FailureAction(ulong index);
        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "get")]
        public static extern void GetFfi(IntPtr connection, ulong index, IntPtr key);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "set")]
        public static extern void SetFfi(IntPtr connection, ulong index, IntPtr key, IntPtr value);

        public delegate void IntAction(IntPtr arg);
        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_connection")]
        public static extern IntPtr CreateConnectionFfi(String address, IntPtr successCallback, IntPtr failureCallback);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_connection")]
        public static extern void CloseConnectionFfi(IntPtr connection);
        #endregion
    }
}
