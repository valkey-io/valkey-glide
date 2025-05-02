using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
internal struct ConnectionRequest
{
    public nuint AddressCount;
    public IntPtr Addresses; // ** NodeAddress - array pointer
    [MarshalAs(UnmanagedType.U1)] public bool HasTlsMode;
    public TlsMode TlsMode;
    [MarshalAs(UnmanagedType.U1)] public bool ClusterMode;
    [MarshalAs(UnmanagedType.U1)] public bool HasRequestTimeout;
    public uint RequestTimeout;
    [MarshalAs(UnmanagedType.U1)] public bool HasConnectionTimeout;
    public uint ConnectionTimeout;
    [MarshalAs(UnmanagedType.U1)] public bool HasReadFrom;
    public ReadFrom ReadFrom;
    [MarshalAs(UnmanagedType.U1)] public bool HasConnectionRetryStrategy;
    public RetryStrategy ConnectionRetryStrategy;
    [MarshalAs(UnmanagedType.U1)] public bool HasAuthenticationInfo;
    public AuthenticationInfo AuthenticationInfo;
    public uint DatabaseId;
    [MarshalAs(UnmanagedType.U1)] public bool HasProtocol;
    public Protocol Protocol;

    [MarshalAs(UnmanagedType.LPStr)] public string? ClientName;
    // TODO more config params, see ffi.rs
}