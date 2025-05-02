using Valkey.Glide.ConnectionConfiguration;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

/// <summary>
/// Builder for configuration of common parameters for standalone and cluster client.
/// </summary>
/// <typeparam name="T">Derived builder class</typeparam>
public abstract class ClientConfigurationBuilder<T>
    where T : ClientConfigurationBuilder<T>, new()
{
    internal ConnectionConfig Config;

    protected ClientConfigurationBuilder(bool clusterMode)
    {
        Config = new ConnectionConfig {ClusterMode = clusterMode};
    }

    #region address

    /// <inheritdoc cref="Addresses"/>
    /// <b>Add</b> a new address to the list.<br />
    /// See also <seealso cref="Addresses"/>.
    protected (string? host, ushort? port) Address
    {
        set => Config.Addresses.Add(new NodeAddress
        {
            Host = value.host ?? ConnectionConfigurationConstants.DEFAULT_HOST, Port = value.port ?? ConnectionConfigurationConstants.DEFAULT_PORT
        });
    }

    /// <inheritdoc cref="Address"/>
    public T WithAddress(string? host, ushort? port)
    {
        Address = (host, port);
        return (T)this;
    }

    /// <summary>
    /// <b>Add</b> a new address to the list with default port.
    /// </summary>
    public T WithAddress(string host)
    {
        Address = (host, ConnectionConfigurationConstants.DEFAULT_PORT);
        return (T)this;
    }

    /// <summary>
    /// Syntax sugar helper class for adding addresses.
    /// </summary>
    public sealed class AddressBuilder
    {
        private readonly ClientConfigurationBuilder<T> _owner;

        internal AddressBuilder(ClientConfigurationBuilder<T> owner)
        {
            _owner = owner;
        }

        /// <inheritdoc cref="WithAddress(string?, ushort?)"/>
        public static AddressBuilder operator +(AddressBuilder builder, (string? host, ushort? port) address)
        {
            _ = builder._owner.WithAddress(address.host, address.port);
            return builder;
        }

        /// <inheritdoc cref="WithAddress(string)"/>
        public static AddressBuilder operator +(AddressBuilder builder, string host)
        {
            _ = builder._owner.WithAddress(host);
            return builder;
        }
    }

    /// <summary>
    /// DNS Addresses and ports of known nodes in the cluster. If the server is in cluster mode the
    /// list can be partial, as the client will attempt to map out the cluster and find all nodes. If
    /// the server is in standalone mode, only nodes whose addresses were provided will be used by the
    /// client.
    /// <para />
    /// For example: <code>
    /// [
    ///   ("sample-address-0001.use1.cache.amazonaws.com", 6378),
    ///   ("sample-address-0002.use2.cache.amazonaws.com"),
    ///   ("sample-address-0002.use3.cache.amazonaws.com", 6380)
    /// ]</code>
    /// </summary>
    public AddressBuilder Addresses
    {
        get => new(this);
        set { } // needed for +=
    }

    #endregion

    #region TLS

    /// <summary>
    /// Configure whether communication with the server should use Transport Level Security.<br />
    /// Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail.
    /// </summary>
    public bool UseTls
    {
        set => Config.TlsMode = value ? TlsMode.SecureTls : TlsMode.NoTls;
    }

    /// <inheritdoc cref="UseTls"/>
    public T WithTls(bool useTls)
    {
        UseTls = useTls;
        return (T)this;
    }

    /// <inheritdoc cref="UseTls"/>
    public T WithTls()
    {
        UseTls = true;
        return (T)this;
    }

    #endregion

    #region Request Timeout

    /// <summary>
    /// The duration in milliseconds that the client should wait for a request to complete. This
    /// duration encompasses sending the request, awaiting for a response from the server, and any
    /// required reconnections or retries. If the specified timeout is exceeded for a pending request,
    /// it will result in a timeout error. If not set, a default value will be used.
    /// </summary>
    public uint RequestTimeout
    {
        set => Config.RequestTimeout = value;
    }

    /// <inheritdoc cref="RequestTimeout"/>
    public T WithRequestTimeout(uint requestTimeout)
    {
        RequestTimeout = requestTimeout;
        return (T)this;
    }

    #endregion

    #region Connection Timeout

    /// <summary>
    /// The duration in milliseconds to wait for a TCP/TLS connection to complete.
    /// This applies both during initial client creation and any reconnections that may occur during request processing.<br />
    /// <b>Note</b>: A high connection timeout may lead to prolonged blocking of the entire command pipeline.<br />
    /// If not explicitly set, a default value of <c>250</c> milliseconds will be used.
    /// </summary>
    public uint ConnectionTimeout
    {
        set => Config.ConnectionTimeout = value;
    }

    /// <inheritdoc cref="ConnectionTimeout"/>
    public T WithConnectionTimeout(uint connectionTimeout)
    {
        ConnectionTimeout = connectionTimeout;
        return (T)this;
    }

    #endregion

    #region Read From

    /// <summary>
    /// Configure the client's read from strategy. If not set, <seealso cref="ReadFromStrategy.Primary"/> will be used.
    /// </summary>
    public ReadFrom ReadFrom
    {
        set => Config.ReadFrom = value;
    }

    /// <inheritdoc cref="ReadFrom"/>
    public T WithReadFrom(ReadFrom readFrom)
    {
        ReadFrom = readFrom;
        return (T)this;
    }

    #endregion

    #region Authentication

    /// <summary>
    /// Configure credentials for authentication process. If none are set, the client will not authenticate itself with the server.
    /// </summary>
    /// <value>
    /// <c>username</c> - The username that will be used for authenticating connections to the servers. If not supplied, <c>"default"</c> will be used.<br />
    /// <c>password</c> - The password that will be used for authenticating connections to the servers.
    /// </value>
    public (string? username, string password) Authentication
    {
        set => Config.AuthenticationInfo = new AuthenticationInfo
        (
            value.username,
            value.password
        );
    }

    /// <summary>
    /// Configure credentials for authentication process. If none are set, the client will not authenticate itself with the server.
    /// </summary>
    /// <param name="username">The username that will be used for authenticating connections to the servers. If not supplied, <c>"default"</c> will be used.</param>
    /// <param name="password">The password that will be used for authenticating connections to the servers.</param>
    public T WithAuthentication(string? username, string password)
    {
        Authentication = (username, password);
        return (T)this;
    }

    #endregion

    #region Protocol

    /// <summary>
    /// Configure the protocol version to use. If not set, <seealso cref="Protocol.RESP3"/> will be used.<br />
    /// See also <seealso cref="Protocol"/>.
    /// </summary>
    public Protocol ProtocolVersion
    {
        set => Config.Protocol = value;
    }

    /// <inheritdoc cref="ProtocolVersion"/>
    public T WithProtocolVersion(Protocol protocol)
    {
        ProtocolVersion = protocol;
        return (T)this;
    }

    #endregion

    #region Client Name

    /// <summary>
    /// Client name to be used for the client. Will be used with <c>CLIENT SETNAME</c> command during connection establishment.
    /// </summary>
    public string? ClientName
    {
        set => Config.ClientName = value;
    }

    /// <inheritdoc cref="ClientName"/>
    public T WithClientName(string? clientName)
    {
        ClientName = clientName;
        return (T)this;
    }

    #endregion

    internal ConnectionConfig Build() => Config;
}
