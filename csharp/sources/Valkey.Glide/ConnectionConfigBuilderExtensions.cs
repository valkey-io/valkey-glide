using JetBrains.Annotations;
using Valkey.Glide.InterOp;

namespace Valkey.Glide;

/// <summary>
/// Provides extension methods for configuring <see cref="ConnectionConfigBuilder"/> instances.
/// </summary>
[PublicAPI]
public static class ConnectionConfigBuilderExtensions
{
    /// <inheritdoc cref="ConnectionConfigBuilder.WithAddresses"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <param name="address">The hostname or IP address of the server.</param>
    /// <param name="port">The port number of the server. The default value is 6379.</param>
    public static ConnectionConfigBuilder WithAddress(
        this ConnectionConfigBuilder builder,
        string address,
        ushort port = ValKeyConstants.DefaultPort
    )
        => builder.WithAddresses(new Node(address, port));

    /// <inheritdoc cref="ConnectionConfigBuilder.WithAddresses"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <param name="node">The port number of the server. The default value is 6379.</param>
    public static ConnectionConfigBuilder WithAddress(this ConnectionConfigBuilder builder, Node node)
        => builder.WithAddresses(node);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithReplicationStrategy"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <remarks>
    /// PRIMARY: Always read from the primary to ensure the freshness of data.
    /// </remarks>
    public static ConnectionConfigBuilder WithReplicationStrategyPrimary(this ConnectionConfigBuilder builder)
        => builder.WithReplicationStrategy(ReplicationStrategy.Primary());

    /// <inheritdoc cref="ConnectionConfigBuilder.WithReplicationStrategy"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <remarks>
    /// PREFER_REPLICA: Distribute requests among all replicas in a round-robin manner.
    ///                 If no replica is available, fallback to the primary.
    /// </remarks>
    public static ConnectionConfigBuilder WithReplicationStrategyReplica(this ConnectionConfigBuilder builder)
        => builder.WithReplicationStrategy(ReplicationStrategy.PreferReplica());

    /// <inheritdoc cref="ConnectionConfigBuilder.WithReplicationStrategy"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <param name="replicaName">The name of the availability zone.</param>
    /// <remarks>
    /// AZ_AFFINITY: Prioritize replicas in the same AZ as the client.
    ///              If no replicas are available in the zone, fallback to other replicas or the primary if needed.
    /// </remarks>
    /// <see href="https://valkey.io/blog/az-affinity-strategy/"/>
    public static ConnectionConfigBuilder WithReplicationStrategyAzAffinity(
        this ConnectionConfigBuilder builder,
        string replicaName
    )
        => builder.WithReplicationStrategy(ReplicationStrategy.AzAffinity(replicaName));

    /// <inheritdoc cref="ConnectionConfigBuilder.WithReplicationStrategy"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <param name="replicaName">The name of the availability zone.</param>
    /// <remarks>
    /// AZ_AFFINITY: Prioritize replicas in the same AZ as the client.
    ///              If no replicas are available in the zone, fallback to other replicas or the primary if needed.
    /// </remarks>
    /// <see href="https://valkey.io/blog/az-affinity-strategy/"/>
    public static ConnectionConfigBuilder WithReplicationStrategyAzAffinityReplicasAndPrimary(
        this ConnectionConfigBuilder builder,
        string replicaName
    )
        => builder.WithReplicationStrategy(ReplicationStrategy.AzAffinityReplicasAndPrimary(replicaName));

    /// <inheritdoc cref="ConnectionConfigBuilder.WithProtocol"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <see href="https://github.com/redis/redis-specifications/blob/master/protocol/RESP2.md"/>
    public static ConnectionConfigBuilder WithProtocolResp2(this ConnectionConfigBuilder builder)
        => builder.WithProtocol(EProtocolVersion.Resp2);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithProtocol"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <see href="https://github.com/redis/redis-specifications/blob/master/protocol/RESP3.md"/>
    public static ConnectionConfigBuilder WithProtocolResp3(this ConnectionConfigBuilder builder)
        => builder.WithProtocol(EProtocolVersion.Resp2);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithTlsMode"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    public static ConnectionConfigBuilder WithNoTls(this ConnectionConfigBuilder builder)
        => builder.WithTlsMode(ETlsMode.NoTls);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithTlsMode"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    public static ConnectionConfigBuilder WithInsecureTls(this ConnectionConfigBuilder builder)
        => builder.WithTlsMode(ETlsMode.InsecureTls);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithTlsMode"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    public static ConnectionConfigBuilder WithSecureTls(this ConnectionConfigBuilder builder)
        => builder.WithTlsMode(ETlsMode.SecureTls);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithClusterMode"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    public static ConnectionConfigBuilder WithClusterMode(this ConnectionConfigBuilder builder)
        => builder.WithClusterMode(true);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithClusterMode"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    public static ConnectionConfigBuilder WithStandaloneMode(this ConnectionConfigBuilder builder)
        => builder.WithClusterMode(false);

    /// <inheritdoc cref="ConnectionConfigBuilder.WithConnectionRetryStrategy"/>
    /// <remarks>
    /// Reconnection is done by calculating a random offset with growing time between attempts.
    /// The formula is as follows: <c>rand(<see cref="factor"/> * (<see cref="exponentialBase"/> * Attempt))</c>
    /// </remarks>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <param name="factor">The multiplier that will be applied to the waiting time between each retry.</param>
    /// <param name="exponentialBase">The exponent base configured for the strategy.</param>
    /// <param name="numberOfRetries">Number of retry attempts that the client should perform when disconnected from the server.</param>
    public static ConnectionConfigBuilder WithConnectionRetryStrategy(
        this ConnectionConfigBuilder builder,
        uint factor,
        uint exponentialBase,
        uint numberOfRetries
    )
        => builder.WithConnectionRetryStrategy(
            new ConnectionRetryStrategy
            {
                Factor          = factor,
                ExponentialBase = exponentialBase,
                NumberOfRetries = numberOfRetries,
            }
        );

    /// <inheritdoc cref="ConnectionConfigBuilder.WithPeriodicChecks"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    public static ConnectionConfigBuilder WithPeriodicCheckDisabled(this ConnectionConfigBuilder builder)
        => builder.WithPeriodicChecks(PeriodicCheck.Disabled());

    /// <inheritdoc cref="ConnectionConfigBuilder.WithPeriodicChecks"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    public static ConnectionConfigBuilder WithPeriodicCheckEnabled(this ConnectionConfigBuilder builder)
        => builder.WithPeriodicChecks(PeriodicCheck.Enabled());

    /// <inheritdoc cref="ConnectionConfigBuilder.WithPeriodicChecks"/>
    /// <param name="builder">The <see cref="ConnectionConfigBuilder"/> instance to configure.</param>
    /// <param name="interval">The interval in which to check the cluster configuration.</param>
    public static ConnectionConfigBuilder WithPeriodicCheckEnabled(
        this ConnectionConfigBuilder builder,
        TimeSpan interval
    )
        => builder.WithPeriodicChecks(PeriodicCheck.Enabled());
}
