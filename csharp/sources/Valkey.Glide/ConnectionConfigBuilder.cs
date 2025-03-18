using JetBrains.Annotations;
using Valkey.Glide.InterOp;

namespace Valkey.Glide;

/// <summary>
/// Provides a fluent API for building and configuring <see cref="ConnectionRequest"/> instances.
/// </summary>
[PublicAPI]
public sealed class ConnectionConfigBuilder
{
    private readonly List<Node>               _addresses = new();
    private          ReplicationStrategy?     _readFrom;
    private          string?                  _clientName;
    private          string?                  _authUsername;
    private          string?                  _authPassword;
    private          long                     _databaseId;
    private          EProtocolVersion?        _protocol;
    private          ETlsMode?                _tlsMode;
    private          bool                     _clusterModeEnabled;
    private          TimeSpan?                _requestTimeout;
    private          TimeSpan?                _connectionTimeout;
    private          ConnectionRetryStrategy? _connectionRetryStrategy;
    private          PeriodicCheck?           _periodicChecks;
    private          uint?                    _inflightRequestsLimit;
    private          string?                  _otelEndpoint;
    private          TimeSpan?                _otelSpanFlushInterval;

    /// <summary>
    /// Configure the collection of server addresses to connect to.
    /// </summary>
    /// <param name="addresses">A collection of <see cref="Node"/> representing the server addresses and their ports.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithAddresses(params Node[] addresses)
    {
        _addresses.AddRange(addresses);
        return this;
    }

    /// <summary>
    /// Enables or disables <see cref="ConnectionRequest.ClusterMode"/> for the connection configuration.
    /// </summary>
    /// <param name="enabled">A boolean value indicating whether cluster mode should be enabled.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithClusterMode(bool enabled)
    {
        _clusterModeEnabled = enabled;
        return this;
    }

    /// <summary>
    /// Configure the <see cref="ConnectionRequest.ReplicationStrategy"/> to use.
    /// </summary>
    /// <see href="https://valkey.io/topics/replication/"/>
    /// <param name="readFrom">The <see cref="ReplicationStrategy"/> structure to use or null to clear.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithReplicationStrategy(ReplicationStrategy? readFrom)
    {
        _readFrom = readFrom;
        return this;
    }

    /// <summary>
    /// Configure the <see cref="ConnectionRequest.ClientName"/> for the connection.
    /// </summary>
    /// <param name="clientName">The name of the client to use in the connection configuration.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithClientName(string? clientName)
    {
        _clientName = clientName;
        return this;
    }

    /// <summary>
    /// Configures authentication credentials
    /// (<see cref="ConnectionRequest.AuthUsername"/> and <see cref="ConnectionRequest.AuthPassword"/>)
    /// for the connection.
    /// </summary>
    /// <param name="username">The username to use for authentication.</param>
    /// <param name="password">The password to use for authentication.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithAuth(string? username, string? password)
    {
        if (string.IsNullOrWhiteSpace(username) && !string.IsNullOrWhiteSpace(password)
            || !string.IsNullOrWhiteSpace(username) && string.IsNullOrWhiteSpace(password))
            throw new ArgumentException("Username and password must both be set or both be empty.");
        _authUsername = username;
        _authPassword = password;
        return this;
    }

    /// <summary>
    /// Configure the <see cref="ConnectionRequest.DatabaseId"/> for the connection.
    /// </summary>
    /// <param name="databaseId">The identifier of the database to connect to.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithDatabaseId(long databaseId)
    {
        _databaseId = databaseId;
        return this;
    }

    /// <summary>
    /// Configure the <see cref="ConnectionRequest.Protocol"/> to use for the connection.
    /// </summary>
    /// <param name="protocol">The <see cref="EProtocolVersion"/> to be used or null to clear the protocol setting.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithProtocol(EProtocolVersion? protocol)
    {
        _protocol = protocol;
        return this;
    }

    /// <summary>
    /// Configure the <see cref="ConnectionRequest.TlsMode"/> to use for the connection.
    /// </summary>
    /// <param name="tlsMode">The <see cref="ETlsMode"/> to be used or null to clear the TLS mode setting.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithTlsMode(ETlsMode? tlsMode)
    {
        _tlsMode = tlsMode;
        return this;
    }

    /// <summary>
    /// Configures the <see cref="ConnectionRequest.RequestTimeout"/> for the connection.
    /// </summary>
    /// <remarks>
    /// </remarks>
    /// <param name="requestTimeout">A <see cref="TimeSpan"/> representing the timeout duration for requests. A value of <see langword="null"/> indicates no timeout.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithRequestTimeout(TimeSpan? requestTimeout)
    {
        _requestTimeout = requestTimeout;
        return this;
    }

    /// <summary>
    /// Sets the connection timeout for the configuration.
    /// </summary>
    /// <param name="connectionTimeout">The maximum duration to wait for the connection to establish.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithConnectionTimeout(TimeSpan? connectionTimeout)
    {
        _connectionTimeout = connectionTimeout;
        return this;
    }


    /// <summary>
    /// Configures the connection retry strategy for handling reconnection attempts.
    /// </summary>
    /// <param name="connectionRetryStrategy">
    /// An instance of <see cref="ConnectionRetryStrategy"/> specifying the retry behavior, including exponential backoff and retry limits.
    /// </param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithConnectionRetryStrategy(ConnectionRetryStrategy? connectionRetryStrategy)
    {
        _connectionRetryStrategy = connectionRetryStrategy;
        return this;
    }

    /// <summary>
    /// Configures the periodic topology check settings for the connection.
    /// </summary>
    /// <param name="periodicChecks">An instance of <see cref="PeriodicCheck"/> representing the periodic check configuration.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithPeriodicChecks(PeriodicCheck? periodicChecks)
    {
        _periodicChecks = periodicChecks;
        return this;
    }

    /// <summary>
    /// Sets the limit for the number of in-flight requests allowed at a given time.
    /// </summary>
    /// <param name="inflightRequestsLimit">The maximum number of simultaneous in-flight requests.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithInflightRequestsLimit(uint? inflightRequestsLimit)
    {
        _inflightRequestsLimit = inflightRequestsLimit;
        return this;
    }

    /// <summary>
    /// Configures the OpenTelemetry endpoint for exporting telemetry data and specifies the interval for flushing spans.
    /// </summary>
    /// <param name="endpoint">The endpoint URL to which OpenTelemetry spans will be exported.</param>
    /// <param name="flushInterval">The time interval at which spans are flushed to the specified endpoint.</param>
    /// <returns>The <see langword="this"/> reference.</returns>
    public ConnectionConfigBuilder WithOpenTelemetryEndpoint(string? endpoint, TimeSpan? flushInterval = null)
    {
        _otelEndpoint          = endpoint;
        _otelSpanFlushInterval = flushInterval;
        return this;
    }


    /// <summary>
    /// Builds and returns a populated <see cref="ConnectionRequest"/> instance.
    /// </summary>
    /// <returns>A fully constructed <see cref="ConnectionRequest"/> containing the configuration details provided.</returns>
    public ConnectionRequest Build()
    {
        return new ConnectionRequest(_addresses)
        {
            ReplicationStrategy            = _readFrom,
            ClientName                     = _clientName,
            AuthUsername                   = _authUsername,
            AuthPassword                   = _authPassword,
            DatabaseId                     = _databaseId,
            Protocol                       = _protocol,
            TlsMode                        = _tlsMode,
            ClusterMode                    = _clusterModeEnabled,
            RequestTimeout                 = _requestTimeout,
            ConnectionTimeout              = _connectionTimeout,
            ConnectionRetryStrategy        = _connectionRetryStrategy,
            PeriodicChecks                 = _periodicChecks,
            InflightRequestsLimit          = _inflightRequestsLimit,
            OpenTelemetryEndpoint          = _otelEndpoint,
            OpenTelemetrySpanFlushInterval = _otelSpanFlushInterval,
        };
    }

    /// <summary>
    /// Defines an implicit conversion from <see cref="ConnectionConfigBuilder"/>
    /// to <see cref="ConnectionRequest"/>. This enables instances of
    /// <see cref="ConnectionConfigBuilder"/> to be directly used as <see cref="ConnectionRequest"/> objects.
    /// </summary>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to convert.</param>
    /// <returns>A populated <see cref="ConnectionRequest"/> constructed from the current configuration.</returns>
    public static implicit operator ConnectionRequest(ConnectionConfigBuilder builder) => builder.Build();
}
