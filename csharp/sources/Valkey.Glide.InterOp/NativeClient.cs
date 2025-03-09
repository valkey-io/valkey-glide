using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Valkey.Glide.InterOp.Exceptions;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

public sealed class NativeClient : IDisposable, INativeClient
{
    private static readonly unsafe delegate*<nint, int, Native.Value, void> CommandCallbackFptr = &CommandCallback;
    private static readonly        SemaphoreSlim                            Semaphore           = new(1, 1);
    private static                 bool                                     _initialized;
    private                        nint?                                    _handle;


    public unsafe NativeClient(IReadOnlyCollection<Node> hosts, bool useTls)
    {
        if (!_initialized)
            throw new InvalidOperationException("API is not initialized");
        if (hosts.Count > ushort.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(hosts), hosts.Count, "Too many hosts");
        if (hosts.Count == ushort.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(hosts), hosts.Count, "At least one host is required");
        if (hosts.Count < 0)
            throw new InvalidOperationException("Hosts is negative");
        var hostsArray = new NodeAddress[hosts.Count];
        var index = 0;

        try
        {
            foreach (var host in hosts)
            {
                var ptr = MarshalUtf8String(host.Address);
                hostsArray[index] = new NodeAddress
                {
                    host = ptr,
                    port = host.Port,
                };
                index++;
            }


            fixed (NodeAddress* hostsArrayPtr = hostsArray)
            {
                var result = Imports.create_client_handle(hostsArrayPtr, (ushort) hosts.Count, useTls);
                switch (result.result)
                {
                    case ECreateClientHandleCode.Success:
                        if (result.client_handle == 0)
                            throw new Exception("Unknown Error, ClientHandle is NULL");
                        _handle = result.client_handle;
                        break;
                    case ECreateClientHandleCode.ParameterError:
                        if (result.error_string is null)
                            throw new ParameterException("Unknown parameter exception");
                        throw new ParameterException(
                            HandleString(result.error_string) ?? "Unknown parameter exception"
                        );
                    case ECreateClientHandleCode.ThreadCreationError:
                        if (result.error_string is null)
                            throw new ThreadCreationException("Unknown thread creation exception");
                        throw new ThreadCreationException(
                            HandleString(result.error_string) ?? "Unknown thread creation exception"
                        );
                    case ECreateClientHandleCode.ConnectionTimedOutError:
                        if (result.error_string is null)
                            throw new ConnectionToTimeOutException("Unknown connection timeout exception");
                        throw new ConnectionToTimeOutException(
                            HandleString(result.error_string) ?? "Unknown connection timeout exception"
                        );
                    case ECreateClientHandleCode.ConnectionToFailedError:
                        if (result.error_string is null)
                            throw new ConnectionToFailedException("Unknown connection to failed exception");
                        throw new ConnectionToFailedException(
                            HandleString(result.error_string) ?? "Unknown connection to failed exception"
                        );
                    case ECreateClientHandleCode.ConnectionToClusterFailed:
                        if (result.error_string is null)
                            throw new ConnectionToClusterException("Unknown connection to cluster exception");
                        throw new ConnectionToClusterException(
                            HandleString(result.error_string) ?? "Unknown connection to cluster exception"
                        );
                    case ECreateClientHandleCode.ConnectionIoError:
                        if (result.error_string is null)
                            throw new ConnectionIoException("Unknown io exception");
                        throw new ConnectionIoException(HandleString(result.error_string) ?? "Unknown io exception");
                    default:
                        if (result.error_string is null)
                            throw new Exception("Unknown error");
                        throw new Exception(HandleString(result.error_string));
                }
            }
        }
        finally
        {
            for (var i = 0; i < hostsArray.Length; i++)
            {
                var ptr = hostsArray[i].host;
                MarshalFreeUtf8String(ptr);
            }
        }
    }

    /// <summary>
    /// Initializes the API.
    /// </summary>
    /// <remarks>
    /// <list type="bullet">
    /// <item>This method is safe to be called multiple times but will not have any effect on successive calls.</item>
    /// <item>This method will lock until it is free. Do not use in a hot path!</item>
    /// </list>
    /// </remarks>
    public static unsafe void Initialize(ELoggerLevel loggerLevel = ELoggerLevel.None, string? logFilePath = null)
    {
        Semaphore.Wait();
        try
        {
            if (_initialized)
                return;
            if (logFilePath is not null)
            {
                fixed (char* logFilePathPtr = logFilePath)
                {
                    var result = Imports.system_init(loggerLevel, (byte*) logFilePathPtr);
                    if (result.success == 0 /* is false */)
                        throw new GlideException("Failed to initialize the API.");
                }
            }
            else
            {
                var result = Imports.system_init(loggerLevel, null);
                if (result.success == 0 /* is false */)
                    throw new GlideException("Failed to initialize the API.");
            }

            _initialized = true;
        }
        finally
        {
            Semaphore.Release();
        }
    }

    private static unsafe void MarshalFreeUtf8String(byte* buffer)
    {
        if (buffer is null)
            return;
        var ptr = (nint) buffer - 1;
        if (Marshal.ReadByte(ptr) == 0) // Is allocated on heap
            Marshal.FreeCoTaskMem(ptr);
    }

    private static unsafe byte* MarshalUtf8String(string s, byte* buffer = null, int bufferLength = 0)
    {
        if (buffer is null && bufferLength is not 0)
            throw new ArgumentException("Buffer is null and bufferLength is not 0", nameof(buffer));
        var utf8 = Encoding.UTF8.GetBytes(s);
        if (bufferLength is 0 || utf8.Length > bufferLength - 2)
        {
            nint ptr = Marshal.AllocHGlobal(utf8.Length + 2);
            fixed (byte* utf8Ptr = utf8)
            {
                Marshal.WriteByte(ptr, 0); // Is allocated on heap
                for (var i = 0; i < utf8.Length; i++)
                    Marshal.WriteByte(ptr + i + 1, utf8Ptr[i]);
                Marshal.WriteByte(ptr + utf8.Length + 1, 0);
            }

            return (byte*) ptr + 1;
        }
        else
        {
            var ptr = (nint) buffer;
            fixed (byte* utf8Ptr = utf8)
            {
                Marshal.WriteByte(ptr, 1); // Is allocated on buffer (potentially stack)
                for (var i = 0; i < utf8.Length; i++)
                    Marshal.WriteByte(ptr + i + 1, utf8Ptr[i]);
                Marshal.WriteByte(ptr + utf8.Length + 1, 0);
            }

            return buffer + 1;
        }
    }

    private static unsafe string? HandleString(byte* resultErrorString, int? length = null, bool free = true)
    {
        if (resultErrorString is null)
            return null;
        try
        {
            var len = length ?? Strlen(resultErrorString);
            return Encoding.UTF8.GetString(resultErrorString, len);
        }
        finally
        {
            if (free)
                Imports.free_string(resultErrorString);
        }
    }

    private static unsafe int Strlen(byte* input)
    {
        var i = 0;
        for (; input[i] != 0; i++)
            ;
        return i;
    }

    private static void CommandCallback([In] nint data, [In] int success, [In] Native.Value payload)
    {
        try
        {
            var dataHandle = GCHandle.FromIntPtr(data);
            TaskCompletionSource<Value>? commandCallbackData;
            try
            {
                commandCallbackData = (TaskCompletionSource<Value>) dataHandle.Target;
            }
            finally
            {
                dataHandle.Free();
            }

            if (success != 0 /* is true */)
                commandCallbackData.SetResult(FromNativeValue(payload));
            else
                commandCallbackData.SetException(
                    new Exception(
                        FromNativeValue(payload)
                            .Data
                        ?? "Unknown error"
                    )
                );
        }
        catch (Exception)
        {
            #if DEBUG
            Debugger.Break();
            #endif
            // empty
        }
    }

    private void ReleaseUnmanagedResources()
    {
        if (_handle.HasValue)
            Imports.free_client_handle(_handle.Value);
        _handle = null;
    }

    public void Dispose()
    {
        ReleaseUnmanagedResources();
        GC.SuppressFinalize(this);
    }

    ~NativeClient()
    {
        ReleaseUnmanagedResources();
    }

    public unsafe Task<Value> SendCommandAsync(ERequestType requestType, params string[] args)
    {
        if (_handle is null)
            throw new ObjectDisposedException(nameof(NativeClient), "ClientHandle is null");
        var tcs = new TaskCompletionSource<Value>(TaskCreationOptions.RunContinuationsAsynchronously);

        var dataHandle = GCHandle.Alloc(tcs, GCHandleType.Normal);
        try
        {
            var argsArr = new byte*[args.Length];
            try
            {
                if (args.Length <= 20)
                {
                    for (var i = 0; i < args.Length; i++)
                    {
                        // ReSharper disable once StackAllocInsideLoop
                        // We do this intentionally here in a "low allocation" (max: 20 * 100 bytes) environment
                        var buffer = stackalloc byte[100];
                        var ptr = MarshalUtf8String(args[i], buffer, 100);
                        argsArr[i] = ptr;
                    }
                }
                else
                {
                    for (var i = 0; i < args.Length; i++)
                    {
                        var ptr = MarshalUtf8String(args[i]);
                        argsArr[i] = ptr;
                    }
                }

                CommandResult result;
                fixed (byte** argsArrPtr = argsArr)
                    result = Imports.command(
                        _handle.Value,
                        CommandCallbackFptr,
                        GCHandle.ToIntPtr(dataHandle),
                        requestType,
                        argsArrPtr,
                        argsArr.Length
                    );

                if (result.success != 0 /* is true */)
                    return tcs.Task;
                else
                    throw new Exception(HandleString(result.error_string));
            }
            finally
            {
                foreach (var t in argsArr)
                {
                    MarshalFreeUtf8String(t);
                }
            }
        }
        catch
        {
            dataHandle.Free();
            throw;
        }
    }

    public unsafe Value SendCommand(ERequestType requestType, params string[] args)
    {
        if (_handle is null)
            throw new ObjectDisposedException(nameof(NativeClient), "ClientHandle is null");

        var argsArr = new byte*[args.Length];
        if (args.Length <= 20)
        {
            for (var i = 0; i < args.Length; i++)
            {
                // ReSharper disable once StackAllocInsideLoop
                // We do this intentionally here in a "low allocation" (max: 20 * 100 bytes) environment
                var buffer = stackalloc byte[100];
                var ptr = MarshalUtf8String(args[i], buffer, 100);
                argsArr[i] = ptr;
            }
        }
        else
        {
            for (var i = 0; i < args.Length; i++)
            {
                var ptr = MarshalUtf8String(args[i]);
                argsArr[i] = ptr;
            }
        }

        BlockingCommandResult result;
        fixed (byte** argsArrPtr = argsArr)
            result = Imports.command_blocking(
                _handle.Value,
                requestType,
                argsArrPtr,
                argsArr.Length
            );
        foreach (var arg in argsArr)
        {
            MarshalFreeUtf8String(arg);
        }

        if (result.success != 0 /* is true */)
            return FromNativeValue(result.value);
        else
            throw new Exception(HandleString(result.error_string));
    }

    private static unsafe Value FromNativeValue(Native.Value input, bool free = true)
    {
        try
        {
            switch (input.kind)
            {
                case Native.EValueKind.Nil:
                    return new Value();
                case Native.EValueKind.Int:
                    return new Value(input.data.i);
                case Native.EValueKind.BulkString:
                    return new Value(HandleString(input.data.ptr, (int) input.length, false));
                case Native.EValueKind.Array:
                {
                    var array = new Value[input.length];
                    var ptr = (Native.Value*) input.data.ptr;
                    for (var i = 0; i < input.length; i++)
                    {
                        array[i] = FromNativeValue(ptr[i], false);
                    }

                    return new Value(array);
                }
                case Native.EValueKind.SimpleString:
                    return new Value(HandleString(input.data.ptr, (int) input.length, false));
                case Native.EValueKind.Okay:
                    return new Value();
                case Native.EValueKind.Map:
                {
                    var array = new KeyValuePair<Value, Value>[input.length];
                    var ptr = (Native.Value*) input.data.ptr;
                    for (int i = 0, j = 0; i < input.length; i++, j += 2)
                    {
                        var key = FromNativeValue(ptr[j], false);
                        var value = FromNativeValue(ptr[j + 1], false);
                        array[i] = new KeyValuePair<Value, Value>(key, value);
                    }

                    return new Value(array);
                }
                case Native.EValueKind.Attribute:
                    throw new NotImplementedException();
                case Native.EValueKind.Set:
                {
                    var array = new Value[input.length];
                    var ptr = (Native.Value*) input.data.ptr;
                    for (var i = 0; i < input.length; i++)
                    {
                        array[i] = FromNativeValue(ptr[i], false);
                    }

                    return new Value(array);
                }
                case Native.EValueKind.Double:
                    return new Value(input.data.f);
                case Native.EValueKind.Boolean:
                    return new Value(input.data.i != 0);
                case Native.EValueKind.VerbatimString:
                {
                    var ptr = (StringPair*) input.data.ptr;
                    var key = HandleString(ptr->a_start, (int) (ptr->a_end - ptr->a_start), false);
                    var value = HandleString(ptr->a_start, (int) (ptr->a_end - ptr->a_start), false);

                    return new Value(key, value);
                }
                case Native.EValueKind.BigNumber:
                    throw new NotImplementedException();
                case Native.EValueKind.Push:
                    throw new NotImplementedException();
                default:
                    throw new ArgumentOutOfRangeException();
            }
        }
        finally
        {
            if (free)
                Imports.free_value(input);
        }
    }
}
