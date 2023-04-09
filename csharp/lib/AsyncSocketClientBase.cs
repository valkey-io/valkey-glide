using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using Google.Protobuf;

namespace babushka
{
    
    public interface IAsyncSocketClient : IDisposable
    {
        ValueTask<RedisValueBase?> GetAsync(string key);
        ValueTask<RedisValueBase?> IncrAsync(string key);
        ValueTask<RedisValueBase?> MGetAsync(string[] keys);
        Task SetAsync(string key, string value);
    }

    /// <summary>
    /// Basic Async client that implements all the common functionality
    /// It is abstract class that enables different implementations for better performance
    /// </summary>
    public abstract class AsyncSocketClientBase : IDisposable, IAsyncSocketClient
    {
        #region Public Methods

        public async Task SetAsync(string key, string value)
        {
            var (message, task) = messageContainer.GetMessageForCall(null, null);
            var request = new RedisRequest.RedisRequest { CallbackIdx = message.Index, RequestType = RedisRequest.RequestType.SetString, ArgsArray = new() };
            request.ArgsArray.Args.Add(key);
            request.ArgsArray.Args.Add(value);
            WriteToSocket(request);
            await task;
        }

        public async ValueTask<RedisValueBase?> GetAsync(string key)
        {
            var (message, task) = messageContainer.GetMessageForCall(null, null);
            var request = new RedisRequest.RedisRequest { CallbackIdx = message.Index, RequestType = RedisRequest.RequestType.GetString, ArgsArray = new() };
            request.ArgsArray.Args.Add(key);
            WriteToSocket(request);
            return await task;
        }

        public async ValueTask<RedisValueBase?> MGetAsync(string[] keys)
        {
            var (message, task) = messageContainer.GetMessageForCall(null, null);
            var request = new RedisRequest.RedisRequest { CallbackIdx = message.Index, RequestType = RedisRequest.RequestType.MgetStrings, ArgsArray = new() };
            foreach (var key in keys ) {
                request.ArgsArray.Args.Add(key);
            }
            WriteToSocket(request);
            return await task;
        }

        public async ValueTask<RedisValueBase?> IncrAsync(string key)
        {
            var (message, task) = messageContainer.GetMessageForCall(null, null);
            var request = new RedisRequest.RedisRequest { CallbackIdx = message.Index, RequestType = RedisRequest.RequestType.Incr, ArgsArray = new() };
            request.ArgsArray.Args.Add(key);
            WriteToSocket(request);
            return await task;
        }

        public void Dispose()
        {
            DisposeWithError(new ObjectDisposedException(null));
        }
        #endregion

        #region protected structures

        [StructLayout(LayoutKind.Explicit, Pack = 1)]
        internal struct RustRedisValue
        {
            [MarshalAs(UnmanagedType.U8)]
            [FieldOffset(0)] public ValueType Discriminator;

            [MarshalAs(UnmanagedType.I8)]
            [FieldOffset(8)] public long IntValue;

            [MarshalAs(UnmanagedType.I8)]
            [FieldOffset(8)] public long DataSize;

            [MarshalAs(UnmanagedType.SysUInt)]
            [FieldOffset(16)] public IntPtr DataPointer;
        }

        [StructLayout(LayoutKind.Explicit, Pack = 1)]
        internal struct RustRedisBulkValue
        {
            [MarshalAs(UnmanagedType.U8)]
            [FieldOffset(0)] public ValueType Discriminator;

            [MarshalAs(UnmanagedType.I8)]
            [FieldOffset(8)] public long DataSize;

            [MarshalAs(UnmanagedType.ByValArray)]
            [FieldOffset(16)] public RustRedisValue[] Values;
        }
        #endregion

        #region Protected Methods

        protected abstract void WriteToSocket(IMessage writeRequest);

        protected void DisposeWithError(Exception error)
        {
            if (Interlocked.CompareExchange(ref this.disposedFlag, 1, 0) == 1)
            {
                return;
            }
            this.socket!.Close();
            messageContainer.DisposeWithError(error);
        }

        /// Triggers the creation of a Rust-side socket server if one isn't running, and returns the name of the socket the server is listening on.
        protected static Task<string> GetSocketNameAsync()
        {
            var completionSource = new TaskCompletionSource<string>();
            InitCallback initCallback = (IntPtr successPointer, IntPtr errorPointer) =>
            {
                if (successPointer != IntPtr.Zero)
                {
                    var address = Marshal.PtrToStringAnsi(successPointer);
                    if (address is not null)
                    {
                        Logger.Log(Level.Info, "connection", $"socket address {address}");
                        completionSource.SetResult(address);
                    }
                    else
                    {
                        completionSource.SetException(new Exception("Received address that couldn't be converted to string"));
                    }
                }
                else if (errorPointer != IntPtr.Zero)
                {
                    var errorMessage = Marshal.PtrToStringAnsi(errorPointer);
                    completionSource.SetException(new Exception(errorMessage));
                }
                else
                {
                    completionSource.SetException(new Exception("Did not receive results from init callback"));
                }
            };
            var callbackPointer = Marshal.GetFunctionPointerForDelegate(initCallback);
            StartSocketListener(callbackPointer);
            return completionSource.Task;
        }

        internal void ResolveMessage(Message<RedisValueBase?> message, Response.Response response)
        {
            // Work needs to be offloaded from the calling thread, because otherwise we might starve the reader task.
            Task.Run(() =>
            {
                switch (response.ValueCase)
                {
                    case Response.Response.ValueOneofCase.None:
                        message.SetResult(null);
                        break;
                    case Response.Response.ValueOneofCase.ClosingError:
                        DisposeWithError(new Exception("ClosingError"));
                        message.SetException(new Exception("ClosingError"));
                        break;
                    case Response.Response.ValueOneofCase.ConstantResponse:
                        var constResult = new RedisOkValue();
                        message.SetResult(constResult);
                        break;
                    case Response.Response.ValueOneofCase.RequestError:
                        var statusResult = new RedisStatusValue() { Data = response.RequestError };
                        message.SetResult(statusResult);
                        break;
                    case Response.Response.ValueOneofCase.RespPointer:
                        var result = response.RespPointer == 0 ? new RedisNilValue() : RedisValueBase.FromCoreValue((IntPtr)response.RespPointer);
                        message.SetResult(result);
                        FreeMemory((IntPtr)response.RespPointer);
                        break;
                }
            });
        }

        #endregion

        #region Protected Memebers

        protected Socket? socket;
        
        internal readonly MessageContainer messageContainer = new();
        /// 1 when disposed, 0 beforeException thrown: 'System.TypeLoadException' in babushka.dll: 'Could not load type 'babushka.RedisValue' from assembly 'babushka, Version=1.0.0.0, Culture=neutral, PublicKeyToken=null' because it contains an object field at offset 8 that is incorrectly aligned or overlapped by a non-object field.'

        protected int disposedFlag = 0;
        protected bool IsDisposed => disposedFlag == 1;

        #endregion

        #region rust bindings

        protected delegate void InitCallback(IntPtr addressPointer, IntPtr errorPointer);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "start_socket_listener_wrapper")]
        protected static extern void StartSocketListener(IntPtr initCallback);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "string_from_pointer")]
        protected static extern String StringFromPointer(IntPtr pointer);

        [DllImport("libbabushka_csharp", CallingConvention = CallingConvention.Cdecl, EntryPoint = "free_memory")]
        protected static extern String FreeMemory(IntPtr pointer);

        #endregion

    }
}
