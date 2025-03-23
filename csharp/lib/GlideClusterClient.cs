// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics.Metrics;
using System.Reflection.Metadata;

using Glide.Commands;

using static Glide.ConnectionConfiguration;

namespace Glide;

// TODO add wiki link
/// <summary>
/// Client used for connection to cluster servers. Use <see cref="CreateClient"/> to request a client.
/// </summary>
public sealed class GlideClusterClient : BaseClient, IGenericClusterCommands
{
    private GlideClusterClient() { }

    // TODO add pubsub and other params to example and remarks
    /// <summary>
    /// Creates a new <see cref="GlideClusterClient" /> instance and establishes a connection to a cluster of Valkey servers.<br />
    /// Use this static method to create and connect a <see cref="GlideClusterClient" /> to a Valkey Cluster. The client will
    /// automatically handle connection establishment, including cluster topology discovery and handling of authentication and TLS configurations.
    /// <example>
    /// <code>
    /// using Glide;
    /// using static Glide.ConnectionConfiguration;
    /// var config = new ClusterClientConfigurationBuilder()
    ///     .WithAddress("address1.example.com", 6379)
    ///     .WithAddress("address2.example.com", 6379)
    ///     .WithAuthentication("user1", "passwordA")
    ///     .WithTls()
    ///     .Build();
    /// GlideClusterClient client = await GlideClusterClient.CreateClient(config);
    /// </code>
    /// </example>
    /// </summary>
    /// <remarks>
    /// <b>Remarks:</b>
    /// Use this static method to create and connect a <see cref="GlideClusterClient" /> to a Valkey Cluster.<br />
    /// The client will automatically handle connection establishment, including cluster topology discovery and handling of authentication and TLS configurations.
    ///   <list type="bullet">
    ///     <item>
    ///       <b>Authentication</b>: If credentials are provided, the client will attempt to authenticate using the specified username and password.
    ///     </item>
    ///     <item>
    ///       <b>TLS</b>: If <see cref="ClientConfigurationBuilder{T}.UseTls" /> is set to <see langword="true" />, the client will establish a secure connection using <c>TLS</c>.
    ///     </item>
    ///   </list>
    /// </remarks>
    /// <param name="config">The configuration options for the client, including cluster addresses, authentication credentials, TLS settings, periodic checks, and Pub/Sub subscriptions.</param>
    /// <returns>A task that resolves to a connected <see cref="GlideClient" /> instance.</returns>
    public static async Task<GlideClusterClient> CreateClient(ClusterClientConfiguration config)
        => await CreateClient(config, () => new GlideClusterClient());

    public async Task<object?> CustomCommand(GlideString[] args, Route? route = null)
        => await Command(RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true), route);
}
