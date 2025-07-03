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
    EndPoint[] IConnectionMultiplexer.GetEndPoints(bool configuredOnly) => throw new NotImplementedException();
    IServer IConnectionMultiplexer.GetServer(string host, int port, object? asyncState) => throw new NotImplementedException();
    IServer IConnectionMultiplexer.GetServer(string hostAndPort, object? asyncState) => throw new NotImplementedException();
    IServer IConnectionMultiplexer.GetServer(IPAddress host, int port) => throw new NotImplementedException();
    IServer IConnectionMultiplexer.GetServer(EndPoint endpoint, object? asyncState) => throw new NotImplementedException();

    IServer[] IConnectionMultiplexer.GetServers()
    { }

    string IConnectionMultiplexer.GetStatus() => throw new NotImplementedException();

    bool IConnectionMultiplexer.IsConnected => true;

    bool IConnectionMultiplexer.IsConnecting => false;

    public static async Task<ConnectionMultiplexer> ConnectAsync(string host, ushort port)
    {
        GlideClient standalone = await GlideClient.CreateClient(new StandaloneClientConfigurationBuilder()
            .WithAddress(host, port).Build());
        string info = await standalone.Info([Section.CLUSTER]);
        return new(await DatabaseImpl.Create(host, port, info.Contains("cluster_enabled:1")));
    }

    // TODO args
    public IDatabase GetDatabase(int db = -1, object? asyncState = null) => _db;

    private readonly IDatabase _db;

    private ConnectionMultiplexer(IDatabase db)
    {
        _db = db;
    }
}
