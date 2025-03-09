using System;
using System.Collections.Generic;
using System.Linq;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

public class ConnectionRequest(IReadOnlyCollection<Node> nodes)
{
    public ReadFrom? ReadFrom { get; set; }
    public string? ClientName { get; set; }
    public string? AuthUsername { get; set; }
    public string? AuthPassword { get; set; }
    public long DatabaseId { get; set; }
    public EProtocolVersion? Protocol { get; set; }
    public ETlsMode? TlsMode { get; set; }
    public Node[] Addresses { get; set; } = nodes as Node[] ?? nodes.ToArray();
    public bool ClusterModeEnabled { get; set; }
    public uint? RequestTimeout { get; set; }
    public uint? ConnectionTimeout { get; set; }
    public ConnectionRetryStrategy? ConnectionRetryStrategy { get; set; }
    public PeriodicCheck? PeriodicChecks { get; set; }
    public uint? InflightRequestsLimit { get; set; }
    public string? OtelEndpoint { get; set; }

    public ulong? OtelSpanFlushIntervalMs;

    public void Validate()
    {
        if (Addresses.Length > ushort.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(Addresses), Addresses.Length, "Too many hosts");
        if (Addresses.Length == ushort.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(Addresses), Addresses.Length, "At least one host is required");

        if (ReadFrom is { Value: null, Kind: EReadFromKind.AzAffinity or EReadFromKind.AzAffinityReplicasAndPrimary })
            throw new ArgumentException("ReadFrom.Value must be set when using AZ affinity", nameof(ReadFrom));

        if (PeriodicChecks is { Kind: EPeriodicCheckKind.ManualInterval, Interval: null })
            throw new ArgumentException(
                "PeriodicChecks.Interval must be set when using ManualInterval",
                nameof(PeriodicChecks)
            );
    }
}
