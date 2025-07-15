// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

using Valkey.Glide.Commands;

namespace Valkey.Glide;

/// <summary>
/// Represents the abstract multiplexer API.
/// </summary>
public interface IConnectionMultiplexer : IDisposable, IAsyncDisposable
{
    /// <summary>
    /// Gets all endpoints defined on the multiplexer.
    /// </summary>
    /// <param name="configuredOnly">Whether to return only the explicitly configured endpoints.</param>
    EndPoint[] GetEndPoints(bool configuredOnly = false);

    /// <summary>
    /// Obtain a configuration API for an individual server.
    /// </summary>
    /// <param name="host">The host to get a server for.</param>
    /// <param name="port">The specific port for <paramref name="host"/> to get a server for.</param>
    /// <param name="asyncState">The async state is not supported by GLIDE.</param>
    IServer GetServer(string host, int port, object? asyncState = null);

    /// <summary>
    /// Obtain a configuration API for an individual server.
    /// </summary>
    /// <param name="hostAndPort">The "host:port" string to get a server for.</param>
    /// <param name="asyncState">The async state is not supported by GLIDE.</param>
    IServer GetServer(string hostAndPort, object? asyncState = null);

    /// <summary>
    /// Obtain a configuration API for an individual server.
    /// </summary>
    /// <param name="host">The host to get a server for.</param>
    /// <param name="port">The specific port for <paramref name="host"/> to get a server for.</param>
    IServer GetServer(IPAddress host, int port);

    /// <summary>
    /// Obtain a configuration API for an individual server.
    /// </summary>
    /// <param name="endpoint">The endpoint to get a server for.</param>
    /// <param name="asyncState">The async state is not supported by GLIDE.</param>
    IServer GetServer(EndPoint endpoint, object? asyncState = null);

    /// <summary>
    /// Obtain configuration APIs for all servers in this multiplexer.
    /// </summary>
    IServer[] GetServers();

    /// <summary>
    /// Indicates whether any servers are connected.
    /// </summary>
    bool IsConnected { get; }

    /// <summary>
    /// Indicates whether any servers are connecting.
    /// </summary>
    bool IsConnecting { get; }

    // TODO add link to `SELECT` once implemented
    /// <summary>
    /// Obtain an interactive connection to a database inside server.
    /// </summary>
    /// <param name="db">Not supported. To switch the database, please use `SELECT` command.</param>
    /// <param name="asyncState">The async state is not supported by GLIDE.</param>
    IDatabase GetDatabase(int db = -1, object? asyncState = null);
}
