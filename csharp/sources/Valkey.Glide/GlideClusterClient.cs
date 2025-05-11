// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;
using Valkey.Glide.Pipeline;

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Errors;
using static Valkey.Glide.Internals.FFI;
using static Valkey.Glide.Pipeline.Options;

namespace Valkey.Glide;

// TODO add wiki link
/// <summary>
/// Client used for connection to cluster servers. Use <see cref="CreateClient"/> to request a client.
/// </summary>
public sealed class GlideClusterClient : BaseClient, IGenericClusterCommands, IServerManagementClusterCommands
{
    private GlideClusterClient() { }

    // TODO add pubsub and other params to example and remarks
    /// <summary>
    /// Creates a new <see cref="GlideClusterClient" /> instance and establishes a connection to a cluster of Valkey servers.<br />
    /// Use this static method to create and connect a <see cref="GlideClusterClient" /> to a Valkey Cluster. The client will
    /// automatically handle connection establishment, including cluster topology discovery and handling of authentication and TLS configurations.
    /// </summary>
    /// <remarks>
    /// <b>Remarks:</b>
    /// Use this static method to create and connect a <see cref="GlideClusterClient" /> to a Valkey Cluster.<br />
    /// The client will automatically handle connection establishment, including cluster topology discovery and handling of authentication and TLS configurations.
    /// <list type="bullet">
    ///   <item>
    ///     <b>Authentication</b>: If credentials are provided, the client will attempt to authenticate using the specified username and password.
    ///   </item>
    ///   <item>
    ///     <b>TLS</b>: If <see cref="ClientConfigurationBuilder{T}.UseTls" /> is set to <see langword="true" />, the client will establish a secure connection using <c>TLS</c>.
    ///   </item>
    /// </list>
    /// <example>
    /// <code>
    /// using Glide;
    /// using static Glide.ConnectionConfiguration;
    ///
    /// var config = new ClusterClientConfigurationBuilder()
    ///     .WithAddress("address1.example.com", 6379)
    ///     .WithAddress("address2.example.com", 6379)
    ///     .WithAuthentication("user1", "passwordA")
    ///     .WithTls()
    ///     .Build();
    /// using GlideClusterClient client = await GlideClusterClient.CreateClient(config);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="config">The configuration options for the client, including cluster addresses, authentication credentials, TLS settings, periodic checks, and Pub/Sub subscriptions.</param>
    /// <returns>A task that resolves to a connected <see cref="GlideClient" /> instance.</returns>
    public static async Task<GlideClusterClient> CreateClient(ClusterClientConfiguration config)
        => await CreateClient(config, () => new GlideClusterClient());

    public async Task<object?[]?> Exec(ClusterBatch batch, bool raiseOnError)
        => await Batch(batch, raiseOnError);

    public async Task<object?[]?> Exec(ClusterBatch batch, bool raiseOnError, ClusterBatchOptions options)
        => batch.IsAtomic && options.RetryStrategy is not null
            ? throw new RequestException("Retry strategy is not supported for atomic batches (transactions).")
            : await Batch(batch, raiseOnError, options);

    public async Task<ClusterValue<object?>> CustomCommand(GlideString[] args)
        => await Command(RequestType.CustomCommand, args, resp => HandleCustomCommandClusterResponse(resp));

    public async Task<ClusterValue<object?>> CustomCommand(GlideString[] args, Route route)
        => await Command(RequestType.CustomCommand, args, resp => HandleCustomCommandClusterResponse(resp, route), route);

    public async Task<Dictionary<string, string>> Info() => await Info([]);

    public async Task<Dictionary<string, string>> Info(InfoOptions.Section[] sections)
        => await Command(RequestType.Info, sections.ToGlideStrings(), resp
            => HandleMultiNodeResponse<GlideString, string>(resp, gs => gs.ToString()));

    public async Task<ClusterValue<string>> Info(Route route) => await Info([], route);

    public async Task<ClusterValue<string>> Info(InfoOptions.Section[] sections, Route route)
        => await Command(RequestType.Info, sections.ToGlideStrings(), resp
            => HandleClusterValueResponse<GlideString, string>(resp, false, route, gs => gs.ToString()), route);
}
