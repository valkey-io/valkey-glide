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
public sealed class ConnectionMultiplexer : IConnectionMultiplexer, IDisposable, IAsyncDisposable
{
    /// <inheritdoc cref="ConnectAsync(string, TextWriter?)" />
    public static ConnectionMultiplexer Connect(string configuration, TextWriter? log = null)
        => Connect(ConfigurationOptions.Parse(configuration), log);

    /// <inheritdoc cref="ConnectAsync(string, Action{ConfigurationOptions}, TextWriter?)" />
    public static ConnectionMultiplexer Connect(string configuration, Action<ConfigurationOptions> configure, TextWriter? log = null)
        => Connect(ConfigurationOptions.Parse(configuration).Apply(configure), log);

    /// <inheritdoc cref="ConnectAsync(ConfigurationOptions, TextWriter?)" />
    public static ConnectionMultiplexer Connect(ConfigurationOptions configuration, TextWriter? log = null)
        => ConnectAsync(configuration, log).GetAwaiter().GetResult();

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
        StandaloneClientConfiguration standaloneConfig = CreateClientConfigBuilder<StandaloneClientConfigurationBuilder>(configuration).Build();
        GlideClient standalone = await GlideClient.CreateClient(standaloneConfig);
        string info = await standalone.Info([Section.CLUSTER]);
        BaseClientConfiguration config = info.Contains("cluster_enabled:1")
            ? CreateClientConfigBuilder<ClusterClientConfigurationBuilder>(configuration).Build()
            : standaloneConfig;

        return new(configuration, await DatabaseImpl.Create(config));
    }

    public EndPoint[] GetEndPoints(bool configuredOnly)
        => configuredOnly
            ? [.. RawConfig.EndPoints]
            : [.. GetServers().Select(s => s.EndPoint)];

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
        if (_db!.IsCluster)
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

    public IDatabase GetDatabase(int db = -1, object? asyncState = null)
    {
        Utils.Requires<NotImplementedException>(db == -1, "To switch the database, please use `SELECT` command.");
        Utils.Requires<NotImplementedException>(asyncState is null, "Async state is not supported by GLIDE");
        return _db!;
    }

    public void Dispose()
    {
        GC.SuppressFinalize(this);
        lock (_lock)
        {
            if (_db is null)
            {
                return;
            }
            _db.Dispose();
            _db = null;
        }
    }

    public async ValueTask DisposeAsync() => await Task.Run(Dispose);

    public override string ToString() => _db!.ToString();

    internal ConfigurationOptions RawConfig { private set; get; }

    private readonly object _lock = new();
    private DatabaseImpl? _db;

    private ConnectionMultiplexer(ConfigurationOptions configuration, DatabaseImpl db)
    {
        RawConfig = configuration;
        _db = db;
    }

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
        _ = configuration.ConnectTimeout.HasValue ? config.ConnectionTimeout = TimeSpan.FromMilliseconds(configuration.ConnectTimeout.Value) : new();
        _ = configuration.ResponseTimeout.HasValue ? config.RequestTimeout = TimeSpan.FromMilliseconds(configuration.ResponseTimeout.Value) : new();
        _ = (configuration.User ?? configuration.Password) is not null ? config.Authentication = (configuration.User, configuration.Password!) : new();
        _ = configuration.ClientName is not null ? config.ClientName = configuration.ClientName : "";
        if (configuration.Protocol is not null)
        {
            config.ProtocolVersion = configuration.Protocol switch
            {
                Protocol.Resp2 => ConnectionConfiguration.Protocol.RESP2,
                Protocol.Resp3 => ConnectionConfiguration.Protocol.RESP3,
                _ => throw new ArgumentException($"Unknown value of Protocol: {configuration.Protocol}"),
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
}
