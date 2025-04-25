// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Route;

namespace Valkey.Glide.Internals;

// FFI-ready structs, helper methods and wrappers
internal class FFI
{
    internal abstract class Marshallable : IDisposable
    {
        private IntPtr _ptr = IntPtr.Zero;

        public IntPtr ToPtr()
        {
            if (_ptr == IntPtr.Zero)
            {
                _ptr = AllocateAndCopy();
            }
            return _ptr;
        }

        public void Dispose()
        {
            if (_ptr != IntPtr.Zero)
            {
                FreeMemory();
                FreeStructPtr(_ptr);
            }
        }

        // All unmanaged memory allocations should happen only on this call and never before.
        protected abstract IntPtr AllocateAndCopy();

        protected abstract void FreeMemory();
    }

    // A wrapper for a command, resposible for marshalling (allocating and freeing) the required data
    internal class Cmd : Marshallable
    {
        private IntPtr[] _argPtrs = [];
        private GCHandle _pinnedArgs;
        private nuint[] _lengths = [];
        private GCHandle _pinnedLengths;
        private readonly GlideString[] _args;
        private CmdInfo _cmd;

        public Cmd(RequestType requestType, GlideString[] arguments)
        {
            _cmd = new() { RequestType = requestType, ArgCount = (nuint)arguments.Length };
            _args = arguments;
        }

        protected override void FreeMemory()
        {
            for (nuint i = 0; i < _cmd.ArgCount; i++)
            {
                Marshal.FreeHGlobal(_argPtrs[i]);
            }
            _pinnedArgs.Free();
            PoolReturn(_argPtrs);
            _pinnedLengths.Free();
            PoolReturn(_lengths);
        }

        protected override IntPtr AllocateAndCopy()
        {
            // 1. Allocate memory for arguments and for for arguments' lenghts
            _argPtrs = PoolRent<IntPtr>(_args.Length);
            _lengths = PoolRent<nuint>(_args.Length);

            // 2. Copy data into allocated array in unmanaged memory
            for (int i = 0; i < _args.Length; i++)
            {
                // 2.1 Copy an argument
                _argPtrs[i] = Marshal.AllocHGlobal(_args[i].Length);
                Marshal.Copy(_args[i].Bytes, 0, _argPtrs[i], _args[i].Length);
                // 2.2 Copy arg's len
                _lengths[i] = (nuint)_args[i].Length;
            }

            // 3. Pin it
            // We need to pin the array in place, in order to ensure that the GC doesn't move it while the operation is running.
            _pinnedArgs = GCHandle.Alloc(_argPtrs, GCHandleType.Pinned);
            _cmd.Args = _pinnedArgs.AddrOfPinnedObject();
            _pinnedLengths = GCHandle.Alloc(_lengths, GCHandleType.Pinned);
            _cmd.ArgLengths = _pinnedLengths.AddrOfPinnedObject();

            return StructToPtr(_cmd);
        }
    }

    // A wrapper for a route
    internal class Route : Marshallable
    {
        private readonly RouteInfo _info;
        private readonly IntPtr _ptr = IntPtr.Zero;

        public Route(
            RouteType requestType,
            (int slotId, SlotType slotType)? slotIdInfo = null,
            (string slotKey, SlotType slotType)? slotKeyInfo = null,
            (string host, int port)? address = null)
        {
            _info = new()
            {
                Type = requestType,
                SlotId = slotIdInfo?.slotId ?? 0,
                SlotKey = slotKeyInfo?.slotKey,
                SlotType = slotIdInfo?.slotType ?? slotKeyInfo?.slotType ?? 0,
                Host = address?.host,
                Port = address?.port ?? 0,
            };
        }

        protected override void FreeMemory() => FreeStructPtr(_ptr);

        protected override IntPtr AllocateAndCopy() => StructToPtr(_info);
    }

    // A wrapper for connection request
    internal class ConnectionConfig : Marshallable
    {
        private ConnectionRequest _request;
        private readonly List<NodeAddress> _addresses;

        public ConnectionConfig(
            List<NodeAddress> addresses,
            TlsMode? tlsMode,
            bool clusterMode,
            uint? requestTimeout,
            uint? connectionTimeout,
            ReadFrom? readFrom,
            RetryStrategy? retryStrategy,
            AuthenticationInfo? authenticationInfo,
            uint databaseId,
            Protocol? protocol,
            string? clientName)
        {
            _addresses = addresses;
            _request = new()
            {
                AddressCount = (nuint)addresses.Count,
                HasTlsMode = tlsMode.HasValue,
                TlsMode = tlsMode ?? default,
                ClusterMode = clusterMode,
                HasRequestTimeout = requestTimeout.HasValue,
                RequestTimeout = requestTimeout ?? default,
                HasConnectionTimeout = connectionTimeout.HasValue,
                ConnectionTimeout = connectionTimeout ?? default,
                HasReadFrom = readFrom.HasValue,
                ReadFrom = readFrom ?? default,
                HasConnectionRetryStrategy = retryStrategy.HasValue,
                ConnectionRetryStrategy = retryStrategy ?? default,
                HasAuthenticationInfo = authenticationInfo.HasValue,
                AuthenticationInfo = authenticationInfo ?? default,
                DatabaseId = databaseId,
                HasProtocol = protocol.HasValue,
                Protocol = protocol ?? default,
                ClientName = clientName,
            };
        }

        protected override void FreeMemory() => Marshal.FreeHGlobal(_request.Addresses);

        protected override IntPtr AllocateAndCopy()
        {
            int addressSize = Marshal.SizeOf(typeof(NodeAddress));
            _request.Addresses = Marshal.AllocHGlobal(addressSize * (int)_request.AddressCount);
            for (int i = 0; i < (int)_request.AddressCount; i++)
            {
                Marshal.StructureToPtr(_addresses[i], _request.Addresses + (i * addressSize), false);
            }
            return StructToPtr(_request);
        }
    }

    private static IntPtr StructToPtr<T>(T @struct) where T : struct
    {
        IntPtr result = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(T)));
        Marshal.StructureToPtr(@struct, result, false);
        return result;
    }

    private static void FreeStructPtr(IntPtr ptr) => Marshal.FreeHGlobal(ptr);

    private static T[] PoolRent<T>(int len) => ArrayPool<T>.Shared.Rent(len);

    private static void PoolReturn<T>(T[] arr) => ArrayPool<T>.Shared.Return(arr);

    [StructLayout(LayoutKind.Sequential)]
    private struct CmdInfo
    {
        public RequestType RequestType;
        public IntPtr Args;
        public nuint ArgCount;
        public IntPtr ArgLengths;
    }

    // TODO: generate this with a bindings generator
    internal enum RequestType : int
    {
        InvalidRequest = 0,
        CustomCommand = 1,
        Get = 1504,
        Set = 1517,
    }

    internal enum RouteType : uint
    {
        Random,
        AllNodes,
        AllPrimaries,
        SlotId,
        SlotType,
        ByAddress,
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    private struct RouteInfo
    {
        public RouteType Type;
        public int SlotId;
        [MarshalAs(UnmanagedType.LPStr)]
        public string? SlotKey;
        public SlotType SlotType;
        [MarshalAs(UnmanagedType.LPStr)]
        public string? Host;
        public int Port;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    private struct ConnectionRequest
    {
        public nuint AddressCount;
        public IntPtr Addresses; // ** NodeAddress - array pointer
        [MarshalAs(UnmanagedType.U1)]
        public bool HasTlsMode;
        public TlsMode TlsMode;
        [MarshalAs(UnmanagedType.U1)]
        public bool ClusterMode;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasRequestTimeout;
        public uint RequestTimeout;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasConnectionTimeout;
        public uint ConnectionTimeout;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasReadFrom;
        public ReadFrom ReadFrom;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasConnectionRetryStrategy;
        public RetryStrategy ConnectionRetryStrategy;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasAuthenticationInfo;
        public AuthenticationInfo AuthenticationInfo;
        public uint DatabaseId;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasProtocol;
        public Protocol Protocol;
        [MarshalAs(UnmanagedType.LPStr)]
        public string? ClientName;
        // TODO more config params, see ffi.rs
    }
}
