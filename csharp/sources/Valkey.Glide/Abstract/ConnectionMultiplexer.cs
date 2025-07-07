// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

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
    public static async Task<ConnectionMultiplexer> ConnectAsync(string host, ushort port)
    {
        GlideClient standalone = await GlideClient.CreateClient(new StandaloneClientConfigurationBuilder()
            .WithAddress(host, port).Build());
        string info = await standalone.Info([Section.CLUSTER]);
        return new(await DatabaseImpl.Create(host, port, info.Contains("cluster_enabled:1")));
    }

    public IDatabase GetDatabase() => _db;

    private readonly IDatabase _db;

    private ConnectionMultiplexer(IDatabase db)
    {
        _db = db;
    }
}
