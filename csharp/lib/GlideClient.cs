// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Glide.Commands;

using static Glide.ConnectionConfiguration;

namespace Glide;

// TODO add wiki link
/// <summary>
/// Client used for connection to standalone servers. Use <see cref="CreateClient"/> to request a client.
/// </summary>
public sealed class GlideClient : BaseClient, IConnectionManagementCommands, IGenericCommands
{
    private GlideClient() { }

    // TODO add pubsub and other params to example and remarks
    /// <summary>
    /// Creates a new <see cref="GlideClient" /> instance and establishes a connection to a standalone Valkey server.
    /// <example>
    /// <code>
    /// using Glide;
    /// using static Glide.ConnectionConfiguration;
    /// var config = new StandaloneClientConfigurationBuilder()
    ///     .WithAddress("primary.example.com", 6379)
    ///     .WithAddress("replica1.example.com", 6379)
    ///     .WithDataBaseId(1)
    ///     .WithAuthentication("user1", "passwordA")
    ///     .WithTls()
    ///     .WithConnectionRetryStrategy(5, 100, 2)
    ///     .Build();
    /// GlideClient client = await GlideClient.CreateClient(config);
    /// </code>
    /// </example>
    /// </summary>
    /// <remarks>
    /// <b>Remarks:</b>
    ///   <list type="bullet">
    ///     <item>
    ///       <b>Authentication</b>: If credentials are provided, the client will attempt to authenticate using the specified username and password.
    ///     </item>
    ///     <item>
    ///       <b>TLS</b>: If <see cref="ClientConfigurationBuilder{T}.UseTls" /> is set to <see langword="true" />, the client will establish a secure connection using <c>TLS</c>.
    ///     </item>
    ///     <item>
    ///       <b>Reconnection Strategy</b>: The <see cref="RetryStrategy" /> settings define how the client will attempt to reconnect in case of disconnections.
    ///     </item>
    ///   </list>
    /// </remarks>
    /// <param name="config">The configuration options for the client, including server addresses, authentication credentials, TLS settings, database selection, reconnection strategy, and Pub/Sub subscriptions.</param>
    /// <returns>A task that resolves to a connected <see cref="GlideClient" /> instance.</returns>
    public static async Task<GlideClient> CreateClient(StandaloneClientConfiguration config)
        => await CreateClient(config, () => new GlideClient());

    public async Task<object?> CustomCommand(GlideString[] args)
        => await Command(RequestType.CustomCommand, args, resp => HandleServerResponse<object?>(resp, true));
}
