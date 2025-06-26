// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide.SER_Compat;

// TODO
// 1. IDisposable
/// <summary>
/// Connection methods common to both standalone and cluster clients.<br />
/// This API is obsolete and no longer supported. <br />
/// Please use <see cref="GlideClient" /> or <see cref="GlideClusterClient" /> instead.
/// </summary>
[Obsolete("This API is obsolete and no longer supported. Please use `GlideClient` and `GlideClusterClient` instead.", false)]
public sealed class ConnectionMultiplexer
{
    /*
    // TODO below
    // from SER:
    // ========================

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer"/> instance.
    /// </summary>
    /// <param name="configuration">The string configuration to use for this multiplexer.</param>
    /// <param name="log">The <see cref="TextWriter"/> to log to.</param>
    public static Task<ConnectionMultiplexer> ConnectAsync(string configuration, TextWriter? log = null) =>
        ConnectAsync(ConfigurationOptions.Parse(configuration), log);

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer"/> instance.
    /// </summary>
    /// <param name="configuration">The string configuration to use for this multiplexer.</param>
    /// <param name="configure">Action to further modify the parsed configuration options.</param>
    /// <param name="log">The <see cref="TextWriter"/> to log to.</param>
    public static Task<ConnectionMultiplexer> ConnectAsync(string configuration, Action<ConfigurationOptions> configure, TextWriter? log = null) =>
        ConnectAsync(ConfigurationOptions.Parse(configuration).Apply(configure), log);

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer"/> instance.
    /// </summary>
    /// <param name="configuration">The configuration options to use for this multiplexer.</param>
    /// <param name="log">The <see cref="TextWriter"/> to log to.</param>
    /// <remarks>Note: For Sentinel, do <b>not</b> specify a <see cref="ConfigurationOptions.CommandMap"/> - this is handled automatically.</remarks>
    public static Task<ConnectionMultiplexer> ConnectAsync(ConfigurationOptions configuration, TextWriter? log = null) { ... }

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer"/> instance.
    /// </summary>
    /// <param name="configuration">The string configuration to use for this multiplexer.</param>
    /// <param name="log">The <see cref="TextWriter"/> to log to.</param>
    public static ConnectionMultiplexer Connect(string configuration, TextWriter? log = null) =>
        Connect(ConfigurationOptions.Parse(configuration), log);

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer"/> instance.
    /// </summary>
    /// <param name="configuration">The string configuration to use for this multiplexer.</param>
    /// <param name="configure">Action to further modify the parsed configuration options.</param>
    /// <param name="log">The <see cref="TextWriter"/> to log to.</param>
    public static ConnectionMultiplexer Connect(string configuration, Action<ConfigurationOptions> configure, TextWriter? log = null) =>
        Connect(ConfigurationOptions.Parse(configuration).Apply(configure), log);

    /// <summary>
    /// Creates a new <see cref="ConnectionMultiplexer"/> instance.
    /// </summary>
    /// <param name="configuration">The configuration options to use for this multiplexer.</param>
    /// <param name="log">The <see cref="TextWriter"/> to log to.</param>
    /// <remarks>Note: For Sentinel, do <b>not</b> specify a <see cref="ConfigurationOptions.CommandMap"/> - this is handled automatically.</remarks>
    public static ConnectionMultiplexer Connect(ConfigurationOptions configuration, TextWriter? log = null) { ... }
    */

    // TODO:
    // 1. Use SER-compatible conf
    // 2. Server type auto-detect in GLIDE core maybe
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
