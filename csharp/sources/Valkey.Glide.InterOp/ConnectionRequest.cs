using System;
using System.Collections.Generic;
using System.Linq;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

public class ConnectionRequest(IReadOnlyCollection<Node> nodes)
{
    public ReplicationStrategy? ReplicationStrategy { get; set; }

    /// <summary>
    /// Client name to be used for the client.
    /// Will be used with <c>CLIENT SETNAME</c> command while connecting.
    /// </summary>
    public string? ClientName { get; set; }

    /// <summary>
    /// Username for an authentication process.
    /// If not set, the client will not attempt to authenticate itself against the server.
    /// </summary>
    /// <remarks>
    /// Always set both <see cref="AuthUsername"/> and <see cref="AuthPassword"/> or neither.
    /// </remarks>
    public string? AuthUsername { get; set; }

    /// <summary>
    /// Passwprd for an authentication process.
    /// If not set, the client will not attempt to authenticate itself against the server.
    /// </summary>
    /// <remarks>
    /// Always set both <see cref="AuthUsername"/> and <see cref="AuthPassword"/> or neither.
    /// </remarks>
    public string? AuthPassword { get; set; }

    public long DatabaseId { get; set; }

    /// <summary>
    /// Serialization protocol to be used with the server.
    /// </summary>
    public EProtocolVersion? Protocol { get; set; }

    public ETlsMode? TlsMode { get; set; }

    /// <summary>
    /// DNS Addresses and ports of known nodes in the cluster. If the server is in cluster mode the
    /// list can be partial, as the client will attempt to map out the cluster and find all nodes. If
    /// the server is in standalone mode, only nodes whose addresses were provided will be used by the
    /// client.
    /// </summary>
    /// <example>
    /// <code>
    /// var connectionRequest = new ConnectionRequest()
    /// {
    ///     // ...
    ///     Addresses = [
    ///         new Node("sample-address-0001.use1.cache.amazonaws.com", 6379),
    ///         new Node("sample-address-0002.use2.cache.amazonaws.com", 6379),
    ///     ],
    /// };
    /// </code>
    /// </example>
    public Node[] Addresses { get; set; } = nodes as Node[] ?? nodes.ToArray();

    public bool ClusterMode { get; set; }

    /// <summary>
    /// The duration in that the client should wait for a request to complete.
    /// This duration encompasses sending the request,
    /// awaiting a response from the server,
    /// and any required reconnections or retries.
    /// If the specified timeout is exceeded for a pending request,
    /// it will result in a timeout error.
    /// </summary>
    public TimeSpan? RequestTimeout { get; set; }

    /// <summary>
    /// The duration in milliseconds to wait for a TCP/TLS connection to complete.
    /// This applies both during initial client creation
    /// and any reconnections that may occur during request processing.
    /// </summary>
    /// <remarks>
    /// A high connection timeout may lead to prolonged blocking of the entire command
    /// pipeline.
    /// </remarks>
    public TimeSpan? ConnectionTimeout { get; set; }

    /// <summary>
    /// Defines the strategy to be used for retrying connection attempts
    /// in case of initial connection failure or connection errors.
    /// </summary>
    public ConnectionRetryStrategy? ConnectionRetryStrategy { get; set; }

    /// <summary>
    /// Represents the configuration for periodic checks in the connection request.
    /// Used to specify the behavior and interval of periodic validation checks.
    /// </summary>
    public PeriodicCheck? PeriodicChecks { get; set; }

    /// <summary>
    /// The maximum number of concurrent requests allowed to be in-flight (sent but not yet completed).
    /// Used to control memory usage and prevent overloading the server or encountering issues
    /// with backlog queue handling.
    /// </summary>
    public uint? InflightRequestsLimit { get; set; }

    /// <summary>
    /// The endpoint for OpenTelemetry integration.
    /// Specifies the destination to which traces, metrics, or logs should be sent.
    /// </summary>
    public string? OpenTelemetryEndpoint { get; set; }

    /// <summary>
    /// Specifies the interval for flushing OpenTelemetry spans.
    /// Determines how frequently spans are flushed to the OpenTelemetry collector.
    /// </summary>
    public TimeSpan? OpenTelemetrySpanFlushInterval;

    public void Validate()
    {
        if (Addresses.Length > ushort.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(Addresses), Addresses.Length, "Too many hosts");
        if (Addresses.Length == ushort.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(Addresses), Addresses.Length, "At least one host is required");

        if (ReplicationStrategy is
            {
                AvailabilityZone: null, Kind: EReadFromKind.AzAffinity or EReadFromKind.AzAffinityReplicasAndPrimary
            })
            throw new ArgumentException(
                "ReadFrom.Value must be set when using AZ affinity",
                nameof(ReplicationStrategy)
            );

        if (PeriodicChecks is { Kind: EPeriodicCheckKind.ManualInterval, Interval: null })
            throw new ArgumentException(
                "PeriodicChecks.Interval must be set when using ManualInterval",
                nameof(PeriodicChecks)
            );
    }
}
