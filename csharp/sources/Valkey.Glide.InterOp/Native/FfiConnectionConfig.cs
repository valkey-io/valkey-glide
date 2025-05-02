using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

/// A wrapper for connection request
public class FfiConnectionConfig : Marshallable
{
    private ConnectionRequest _request;
    private readonly List<NodeAddress> _addresses;

    public FfiConnectionConfig(
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

        return InterOpHelpers.StructToPtr(_request);
    }
}
