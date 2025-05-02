// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.ConnectionConfiguration;

internal record ConnectionConfig
{
    public List<NodeAddress> Addresses = [];
    public TlsMode? TlsMode;
    public bool ClusterMode;
    public uint? RequestTimeout;
    public uint? ConnectionTimeout;
    public ReadFrom? ReadFrom;
    public RetryStrategy? RetryStrategy;
    public AuthenticationInfo? AuthenticationInfo;
    public uint DatabaseId;
    public Protocol? Protocol;
    public string? ClientName;

    internal FfiConnectionConfig ToFfi() =>
        new(Addresses, TlsMode, ClusterMode, RequestTimeout, ConnectionTimeout, ReadFrom, RetryStrategy,
            AuthenticationInfo, DatabaseId, Protocol, ClientName);
}
