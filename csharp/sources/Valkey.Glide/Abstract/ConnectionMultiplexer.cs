// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

using Valkey.Glide.Commands;

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

/// <summary>
/// Connection methods common to both standalone and cluster clients.<br />
/// See also <see cref="GlideClient" /> and <see cref="GlideClusterClient" />.
/// </summary>
public sealed class ConnectionMultiplexer : IConnectionMultiplexer
{
    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer" /> instance.
    /// </summary>
    /// <param name="configuration">The string configuration to use for this multiplexer.</param>
    /// <param name="log">The log writer is not supported by GLIDE.</param>
    public static async Task<ConnectionMultiplexer> ConnectAsync(string configuration, TextWriter? log = null)
        => await ConnectAsync(ConfigurationOptions.Parse(configuration), log);

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer" /> instance.
    /// </summary>
    /// <param name="configuration">The string configuration to use for this multiplexer.</param>
    /// <param name="configure">Action to further modify the parsed configuration options.</param>
    /// <param name="log">The log writer is not supported by GLIDE.</param>
    public static async Task<ConnectionMultiplexer> ConnectAsync(string configuration, Action<ConfigurationOptions> configure, TextWriter? log = null)
        => await ConnectAsync(ConfigurationOptions.Parse(configuration).Apply(configure), log);

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer" /> instance.
    /// </summary>
    /// <param name="configuration">The configuration options to use for this multiplexer.</param>
    /// <param name="log">The log writer is not supported by GLIDE.</param>
    public static async Task<ConnectionMultiplexer> ConnectAsync(ConfigurationOptions configuration, TextWriter? log = null)
    {
        Utils.Requires<NotImplementedException>(log == null, "Log writer is not supported by GLIDE");
        var standaloneConfig = CreateClientConfigBuilder<StandaloneClientConfigurationBuilder>(configuration).Build();
        GlideClient standalone = await GlideClient.CreateClient(standaloneConfig);
        string info = await standalone.Info([Section.CLUSTER]);
        BaseClientConfiguration config = info.Contains("cluster_enabled:1")
            ? CreateClientConfigBuilder<ClusterClientConfigurationBuilder>(configuration).Build()
            : standaloneConfig;

        return new(await DatabaseImpl.Create(config));
    }

    /// <inheritdoc cref="ConnectAsync(string, TextWriter?)" />
    public static ConnectionMultiplexer Connect(string configuration, TextWriter? log = null)
        => Connect(ConfigurationOptions.Parse(configuration), log);

    /// <inheritdoc cref="ConnectAsync(string, Action{ConfigurationOptions}, TextWriter?)" />
    public static ConnectionMultiplexer Connect(string configuration, Action<ConfigurationOptions> configure, TextWriter? log = null)
        => Connect(ConfigurationOptions.Parse(configuration).Apply(configure), log);

    /// <inheritdoc cref="ConnectAsync(ConfigurationOptions, TextWriter?)" />
    public static ConnectionMultiplexer Connect(ConfigurationOptions configuration, TextWriter? log = null)
        => ConnectAsync(configuration, log).GetAwaiter().GetResult();

    private static T CreateClientConfigBuilder<T>(ConfigurationOptions configuration)
        where T : ClientConfigurationBuilder<T>, new()
    {
        T config = new();
        foreach (EndPoint ep in configuration.EndPoints)
        {
            string[] parts = ep.ToString()!.Split(':');
            config.Addresses += (parts[0], ushort.Parse(parts[1]));
        }
        config.UseTls = configuration.Ssl;
        _ = configuration.ConnectTimeout.HasValue ? config.ConnectionTimeout = (uint)configuration.ConnectTimeout.Value : 0;
        _ = configuration.ResponseTimeout.HasValue ? config.RequestTimeout = (uint)configuration.ResponseTimeout.Value : 0;
        _ = (configuration.User ?? configuration.Password) is not null ? config.Authentication = (configuration.User, configuration.Password) : new();
        _ = configuration.ClientName is not null ? config.ClientName = configuration.ClientName : "";
        if (configuration.Protocol is not null)
        {
            config.ProtocolVersion = configuration.Protocol switch
            {
                Protocol.Resp2 => ConnectionConfiguration.Protocol.RESP2,
                Protocol.Resp3 => ConnectionConfiguration.Protocol.RESP3,
            };
        }
        if (config is StandaloneClientConfigurationBuilder standalone)
        {
            _ = configuration.DefaultDatabase.HasValue ? standalone.DataBaseId = (uint)configuration.DefaultDatabase.Value : 0;
        }
        _ = configuration.ReconnectRetryPolicy.HasValue ? config.ConnectionRetryStrategy = configuration.ReconnectRetryPolicy.Value : new();
        _ = configuration.ReadFrom.HasValue ? config.ReadFrom = configuration.ReadFrom.Value : new();

        return config;
    }

    public IDatabase GetDatabase() => _db;

    private readonly IDatabase _db;

    private ConnectionMultiplexer(IDatabase db)
    {
        _db = db;
    }
}
