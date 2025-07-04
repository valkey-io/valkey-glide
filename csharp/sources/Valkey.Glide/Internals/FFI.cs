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
                _ptr = IntPtr.Zero;
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

    internal class Batch : Marshallable
    {
        private readonly Cmd[] _cmds;
        private IntPtr[] _cmdPtrs;
        private GCHandle _pinnedCmds;
        private BatchInfo _batch;

        public Batch(Cmd[] cmds, bool isAtomic)
        {
            _cmds = cmds;
            _batch = new() { IsAtomic = isAtomic, CmdCount = (nuint)cmds.Length };
            _cmdPtrs = [];
        }

        protected override void FreeMemory()
        {
            for (int i = 0; i < _cmds.Length; i++)
            {
                _cmds[i].Dispose();
            }
            _pinnedCmds.Free();
            ArrayPool<IntPtr>.Shared.Return(_cmdPtrs);
        }

        protected override IntPtr AllocateAndCopy()
        {
            // 1. Allocate memory for commands and marshal them
            _cmdPtrs = ArrayPool<IntPtr>.Shared.Rent(_cmds.Length);
            for (int i = 0; i < _cmds.Length; i++)
            {
                _cmdPtrs[i] = _cmds[i].ToPtr();
            }

            // 2. Pin it
            _pinnedCmds = GCHandle.Alloc(_cmdPtrs, GCHandleType.Pinned);
            _batch.Cmds = _pinnedCmds.AddrOfPinnedObject();

            return StructToPtr(_batch);
        }
    }

    // A wrapper for a route
    internal class Route : Marshallable
    {
        private readonly RouteInfo _info;

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

        protected override void FreeMemory() { }

        protected override IntPtr AllocateAndCopy() => StructToPtr(_info);
    }

    internal class BatchOptions : Marshallable
    {
        private BatchOptionsInfo _info;
        private readonly Route? _route;

        public BatchOptions(
            bool? retryServerError = false,
            bool? retryConnectionError = false,
            uint? timeout = null,
            Route? route = null
            )
        {
            _route = route;
            _info = new()
            {
                RetryServerError = retryServerError ?? false,
                RetryConnectionError = retryConnectionError ?? false,
                HasTimeout = timeout is not null,
                Timeout = timeout ?? 0,
                Route = IntPtr.Zero,
            };
        }

        protected override void FreeMemory() => _route?.Dispose();

        protected override IntPtr AllocateAndCopy()
        {
            _info.Route = _route?.ToPtr() ?? IntPtr.Zero;
            return StructToPtr(_info);
        }
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

    [StructLayout(LayoutKind.Sequential)]
    private struct BatchInfo
    {
        public nuint CmdCount;
        public IntPtr Cmds;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsAtomic;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct BatchOptionsInfo
    {
        [MarshalAs(UnmanagedType.U1)]
        public bool RetryServerError;
        [MarshalAs(UnmanagedType.U1)]
        public bool RetryConnectionError;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasTimeout;
        public uint Timeout;
        public IntPtr Route;
    }

    // TODO: generate this with a bindings generator
    internal enum RequestType : int
    {
        InvalidRequest = 0,
        CustomCommand = 1,
        Info = 1130,
        Get = 1504,
        Set = 1517,
        SAdd = 1201,
        SRem = 1214,
        SMembers = 1209,
        SCard = 1202,
        SInterCard = 1206,
        SPop = 1212,
        SDiff = 1203,
        SDiffStore = 1204,
        SInter = 1205,
        SInterStore = 1207,
        SUnion = 1216,
        SUnionStore = 1217,
        Echo = 320,
        Ping = 322,
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

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    internal struct NodeAddress
    {
        [MarshalAs(UnmanagedType.LPStr)]
        public string Host;
        public ushort Port;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    internal struct AuthenticationInfo(string? username, string password)
    {
        [MarshalAs(UnmanagedType.LPStr)]
        public string? Username = username;
        [MarshalAs(UnmanagedType.LPStr)]
        public string Password = password;
    }

    internal enum TlsMode : uint
    {
        NoTls = 0,
        SecureTls = 2,
    }
}
