// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

/// <summary>
/// Connection methods common to both standalone and cluster clients.<br />
/// See also <see cref="GlideClient" /> and <see cref="GlideClusterClient" />.
/// </summary>
public sealed class ConnectionMultiplexer : IConnectionMultiplexer
{
    public EndPoint[] GetEndPoints(bool configuredOnly) => throw new NotImplementedException();

    public IServer GetServer(string host, int port, object? asyncState = null)
        => GetServer(Utils.ParseEndPoint(host, port), asyncState);

    public IServer GetServer(string hostAndPort, object? asyncState = null)
        => Utils.TryParseEndPoint(hostAndPort, out IPEndPoint? ep)
            ? GetServer(ep, asyncState)
            : throw new ArgumentException($"The specified host and port could not be parsed: {hostAndPort}", nameof(hostAndPort));

    public IServer GetServer(IPAddress host, int port)
        => GetServer(new IPEndPoint(host, port));

    public IServer GetServer(EndPoint endpoint, object? asyncState = null)
    {
        Utils.Requires<NotImplementedException>(asyncState is null, "Async state is not supported by GLIDE");
        foreach (IServer server in GetServers())
        {
            if (server.EndPoint.Equals(endpoint))
            {
                return server;
            }
        }
        throw new ArgumentException("The specified endpoint is not defined", nameof(endpoint));
    }

    // TODO currently this returns only primary node on standalone
    // https://github.com/valkey-io/valkey-glide/issues/4293
    public IServer[] GetServers()
    {
        // run INFO on all nodes, but disregard the node responses, we need node addresses only
        if (_db.IsCluster)
        {
            Dictionary<string, string> info = _db.Command(Request.Info([]).ToMultiNodeValue(), Route.AllNodes).GetAwaiter().GetResult();
            return [.. info.Keys.Select(addr => new ValkeyServer(_db, IPEndPoint.Parse(addr)))];
        }
        else
        {
            // due to #4293, core ignores route on standalone and always return a single node response
            string info = _db.Command(Request.Info([]), Route.AllNodes).GetAwaiter().GetResult();
            // and there is no way to get IP address from server, assuming localhost (127.0.0.1)
            // we can try to get port only (in some deployments, this info is also missing)
            int port = 6379;
            foreach (string line in info.Split("\r\n"))
            {
                if (line.Contains("tcp_port:"))
                {
                    port = int.Parse(line.Split(':')[1]);
                }
            }
            return [new ValkeyServer(_db, new IPEndPoint(0x100007F, port))];
        }
    }

    public bool IsConnected => true;

    public bool IsConnecting => false;

    public static async Task<ConnectionMultiplexer> ConnectAsync(string host, ushort port)
    {
        GlideClient standalone = await GlideClient.CreateClient(new StandaloneClientConfigurationBuilder()
            .WithAddress(host, port).Build());
        string info = await standalone.Info([Section.CLUSTER]);
        bool isCluster = info.Contains("cluster_enabled:1");
        return new(await DatabaseImpl.Create(host, port, isCluster));
    }

    public IDatabase GetDatabase(int db = -1, object? asyncState = null)
    {
        Utils.Requires<NotImplementedException>(db == -1, "To switch the database, please use `SELECT` command.");
        Utils.Requires<NotImplementedException>(asyncState is null, "Async state is not supported by GLIDE");
        return _db;
    }

    private readonly DatabaseImpl _db;

    private ConnectionMultiplexer(DatabaseImpl db)
    {
        _db = db;
    }
}
