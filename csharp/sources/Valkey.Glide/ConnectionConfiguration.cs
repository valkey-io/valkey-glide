// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

using Valkey.Glide.Internals;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide;

public abstract class ConnectionConfiguration
{
    #region Structs and Enums definitions
    internal record ConnectionConfig
    {
        public List<NodeAddress> Addresses = [];
        public TlsMode? TlsMode;
        public bool ClusterMode;
        public TimeSpan? RequestTimeout;
        public TimeSpan? ConnectionTimeout;
        public ReadFrom? ReadFrom;
        public RetryStrategy? RetryStrategy;
        public AuthenticationInfo? AuthenticationInfo;
        public uint DatabaseId;
        public Protocol? Protocol;
        public string? ClientName;

        internal FFI.ConnectionConfig ToFfi() =>
            new(Addresses, TlsMode, ClusterMode, (uint?)RequestTimeout?.TotalMilliseconds, (uint?)ConnectionTimeout?.TotalMilliseconds, ReadFrom, RetryStrategy, AuthenticationInfo, DatabaseId, Protocol, ClientName);
    }

    /// <summary>
    /// Represents the strategy used to determine how and when to reconnect, in case of connection
    /// failures. The time between attempts grows exponentially, to the formula <c>rand(0 ... factor *
    /// (exponentBase ^ N))</c>, where <c>N</c> is the number of failed attempts.
    /// <para />
    /// Once the maximum value is reached, that will remain the time between retry attempts until a
    /// reconnect attempt is successful. The client will attempt to reconnect indefinitely.
    /// </summary>
    /// <param name="numberOfRetries"><inheritdoc cref="NumberOfRetries" path="/summary" /></param>
    /// <param name="factor"><inheritdoc cref="Factor" path="/summary" /></param>
    /// <param name="exponentBase"><inheritdoc cref="ExponentBase" path="/summary" /></param>
    /// <param name="jitterPercent"><inheritdoc cref="JitterPercent" path="/summary" /></param>
    [StructLayout(LayoutKind.Sequential)]
    public struct RetryStrategy(uint numberOfRetries, uint factor, uint exponentBase, uint? jitterPercent = null)
    {
        /// <summary>
        /// Number of retry attempts that the client should perform when disconnected from the server,
        /// where the time between retries increases. Once the retries have reached the maximum value, the
        /// time between retries will remain constant until a reconnect attempt is successful.
        /// </summary>
        public uint NumberOfRetries = numberOfRetries;
        /// <summary>
        /// The multiplier that will be applied to the waiting time between each retry.
        /// </summary>
        public uint Factor = factor;
        /// <summary>
        /// The exponent base configured for the strategy.
        /// </summary>
        public uint ExponentBase = exponentBase;
        [MarshalAs(UnmanagedType.U1)]
        internal bool HasJitterPercent = jitterPercent is not null;
        /// <summary>
        /// The Jitter precent configured for the strategy.
        /// </summary>
        public uint JitterPercent = jitterPercent ?? 0;
    }

    /// <summary>
    /// Represents the client's read from strategy and Availability zone if applicable.
    /// </summary>
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    public struct ReadFrom
    {
        public ReadFromStrategy Strategy;
        [MarshalAs(UnmanagedType.LPStr)]
        public string? Az;

        /// <summary>
        /// Init strategy with <seealso cref="ReadFromStrategy.Primary" /> or <seealso cref="ReadFromStrategy.PreferReplica" /> strategy.
        /// </summary>
        /// <param name="strategy">Either <seealso cref="ReadFromStrategy.Primary" /> or <seealso cref="ReadFromStrategy.PreferReplica" />.</param>
        /// <exception cref="ArgumentException">Thrown if another strategy is used.</exception>
        public ReadFrom(ReadFromStrategy strategy)
        {
            if (strategy is ReadFromStrategy.AzAffinity or ReadFromStrategy.AzAffinityReplicasAndPrimary)
            {
                throw new ArgumentException("Availability zone should be set when using `AzAffinity` or `AzAffinityReplicasAndPrimary` strategy.");
            }
            Strategy = strategy;
            Az = null;
        }

        /// <summary>
        /// Init strategy with <seealso cref="ReadFromStrategy.AzAffinity" /> or <seealso cref="ReadFromStrategy.AzAffinityReplicasAndPrimary" /> strategy and an Availability Zone.
        /// </summary>
        /// <param name="strategy">Either <seealso cref="ReadFromStrategy.AzAffinity" /> or <seealso cref="ReadFromStrategy.AzAffinityReplicasAndPrimary" />.</param>
        /// <param name="az">An Availability Zone (AZ).</param>
        /// <exception cref="ArgumentException">Thrown if another strategy is used.</exception>
        public ReadFrom(ReadFromStrategy strategy, string az)
        {
            if (strategy is ReadFromStrategy.Primary or ReadFromStrategy.PreferReplica)
            {
                throw new ArgumentException("Availability zone could be set only when using `AzAffinity` or `AzAffinityReplicasAndPrimary` strategy.");
            }
            Strategy = strategy;
            Az = az;
        }
    }

    /// <summary>
    /// Represents the client's read from strategy.
    /// </summary>
    public enum ReadFromStrategy : uint
    {
        /// <summary>
        /// Always get from primary, in order to get the freshest data.
        /// </summary>
        Primary = 0,
        /// <summary>
        /// Spread the requests between all replicas in a round-robin manner. If no replica is available, route the requests to the primary.
        /// </summary>
        PreferReplica = 1,
        /// <summary>
        /// Spread the read requests between replicas in the same client's Availability Zone (AZ) in a
        /// round-robin manner, falling back to other replicas or the primary if needed.
        /// </summary>
        AzAffinity,
        /// <summary>
        /// Spread the read requests among nodes within the client's Availability Zone (AZ) in a
        /// round-robin manner, prioritizing local replicas, then the local primary, and falling
        /// back to any replica or the primary if needed.
        /// </summary>
        AzAffinityReplicasAndPrimary,
    }

    /// <summary>
    /// Represents the communication protocol with the server.
    /// </summary>
    public enum Protocol : uint
    {
        /// <summary>
        /// Use RESP2 to communicate with the server nodes.
        /// </summary>
        RESP2 = 0,
        /// <summary>
        /// Use RESP3 to communicate with the server nodes.
        /// </summary>
        RESP3 = 1,
    }
    #endregion

    private static readonly string DEFAULT_HOST = "localhost";
    private static readonly ushort DEFAULT_PORT = 6379;

    /// <summary>
    /// Basic class which holds common configuration for all types of clients.<br />
    /// Refer to derived classes for more details: <see cref="StandaloneClientConfiguration" /> and <see cref="ClusterClientConfiguration" />.
    /// </summary>
    public abstract class BaseClientConfiguration
    {
        internal ConnectionConfig Request = new();

        internal ConnectionConfig ToRequest() => Request;
    }

    /// <summary>
    /// Configuration for a standalone client. <br />
    /// Use <see cref="StandaloneClientConfigurationBuilder" /> or
    /// <see cref="StandaloneClientConfiguration(List{ValueTuple{string?, ushort?}}, bool?, TimeSpan?, TimeSpan?, ReadFrom?, RetryStrategy?, string?, string?, uint?, Protocol?, string?)" /> to create an instance.
    /// </summary>
    public sealed class StandaloneClientConfiguration : BaseClientConfiguration
    {
        internal StandaloneClientConfiguration() { }

        /// <summary>
        /// Configuration for a standalone client.
        /// </summary>
        /// <inheritdoc cref="ClientConfigurationBuilder{T}.WithAuthentication(string?, string)" />
        /// <param name="addresses"><inheritdoc cref="ClientConfigurationBuilder{T}.Addresses" path="/summary" /></param>
        /// <param name="useTls"><inheritdoc cref="ClientConfigurationBuilder{T}.UseTls" path="/summary" /></param>
        /// <param name="requestTimeout"><inheritdoc cref="ClientConfigurationBuilder{T}.RequestTimeout" path="/summary" /></param>
        /// <param name="connectionTimeout"><inheritdoc cref="ClientConfigurationBuilder{T}.ConnectionTimeout" path="/summary" /></param>
        /// <param name="readFrom"><inheritdoc cref="ClientConfigurationBuilder{T}.ReadFrom" path="/summary" /></param>
        /// <param name="retryStrategy"><inheritdoc cref="ClientConfigurationBuilder{T}.ConnectionRetryStrategy" path="/summary" /></param>
        /// <param name="databaseId"><inheritdoc cref="StandaloneClientConfigurationBuilder.DataBaseId" path="/summary" /></param>
        /// <param name="protocol"><inheritdoc cref="ClientConfigurationBuilder{T}.ProtocolVersion" path="/summary" /></param>
        /// <param name="clientName"><inheritdoc cref="ClientConfigurationBuilder{T}.ClientName" path="/summary" /></param>
        public StandaloneClientConfiguration(
            List<(string? host, ushort? port)> addresses,
            bool? useTls = null,
            TimeSpan? requestTimeout = null,
            TimeSpan? connectionTimeout = null,
            ReadFrom? readFrom = null,
            RetryStrategy? retryStrategy = null,
            string? username = null,
            string? password = null,
            uint? databaseId = null,
            Protocol? protocol = null,
            string? clientName = null
            )
        {
            StandaloneClientConfigurationBuilder builder = new();
            addresses.ForEach(addr => builder.Addresses += addr);
            builder.UseTls = useTls ?? false;
            _ = requestTimeout.HasValue ? builder.RequestTimeout = requestTimeout.Value : new();
            _ = connectionTimeout.HasValue ? builder.ConnectionTimeout = connectionTimeout.Value : new();
            _ = readFrom.HasValue ? builder.ReadFrom = readFrom.Value : new();
            _ = retryStrategy.HasValue ? builder.ConnectionRetryStrategy = retryStrategy.Value : new();
            _ = (username ?? password) is not null ? builder.Authentication = (username, password!) : new();
            _ = databaseId.HasValue ? builder.DataBaseId = databaseId.Value : new();
            _ = protocol.HasValue ? builder.ProtocolVersion = protocol.Value : new();
            _ = clientName is not null ? builder.ClientName = clientName : "";
            Request = builder.Build().Request;
        }
    }

    /// <summary>
    /// Configuration for a cluster client. Use <see cref="ClusterClientConfigurationBuilder" /> or
    /// <see cref="ClusterClientConfiguration(List{ValueTuple{string?, ushort?}}, bool?, TimeSpan?, TimeSpan?, ReadFrom?, RetryStrategy?, string?, string?, Protocol?, string?)" /> to create an instance.
    /// </summary>
    public sealed class ClusterClientConfiguration : BaseClientConfiguration
    {
        internal ClusterClientConfiguration() { }

        /// <summary>
        /// Configuration for a cluster client.
        /// </summary>
        /// <inheritdoc cref="ClientConfigurationBuilder{T}.WithAuthentication(string?, string)" />
        /// <param name="addresses"><inheritdoc cref="ClientConfigurationBuilder{T}.Addresses" path="/summary" /></param>
        /// <param name="useTls"><inheritdoc cref="ClientConfigurationBuilder{T}.UseTls" path="/summary" /></param>
        /// <param name="requestTimeout"><inheritdoc cref="ClientConfigurationBuilder{T}.RequestTimeout" path="/summary" /></param>
        /// <param name="connectionTimeout"><inheritdoc cref="ClientConfigurationBuilder{T}.ConnectionTimeout" path="/summary" /></param>
        /// <param name="readFrom"><inheritdoc cref="ClientConfigurationBuilder{T}.ReadFrom" path="/summary" /></param>
        /// <param name="retryStrategy"><inheritdoc cref="ClientConfigurationBuilder{T}.ConnectionRetryStrategy" path="/summary" /></param>
        /// <param name="protocol"><inheritdoc cref="ClientConfigurationBuilder{T}.ProtocolVersion" path="/summary" /></param>
        /// <param name="clientName"><inheritdoc cref="ClientConfigurationBuilder{T}.ClientName" path="/summary" /></param>
        public ClusterClientConfiguration(
            List<(string? host, ushort? port)> addresses,
            bool? useTls = null,
            TimeSpan? requestTimeout = null,
            TimeSpan? connectionTimeout = null,
            ReadFrom? readFrom = null,
            RetryStrategy? retryStrategy = null,
            string? username = null,
            string? password = null,
            Protocol? protocol = null,
            string? clientName = null
            )
        {
            ClusterClientConfigurationBuilder builder = new();
            addresses.ForEach(addr => builder.Addresses += addr);
            builder.UseTls = useTls ?? false;
            _ = requestTimeout.HasValue ? builder.RequestTimeout = requestTimeout.Value : new();
            _ = connectionTimeout.HasValue ? builder.ConnectionTimeout = connectionTimeout.Value : new();
            _ = readFrom.HasValue ? builder.ReadFrom = readFrom.Value : new();
            _ = retryStrategy.HasValue ? builder.ConnectionRetryStrategy = retryStrategy.Value : new();
            _ = (username ?? password) is not null ? builder.Authentication = (username, password!) : new();
            _ = protocol.HasValue ? builder.ProtocolVersion = protocol.Value : new();
            _ = clientName is not null ? builder.ClientName = clientName : "";
            Request = builder.Build().Request;
        }
    }

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
            Config = new ConnectionConfig { ClusterMode = clusterMode };
        }

        #region address
        /// <inheritdoc cref="Addresses" />
        /// <b>Add</b> a new address to the list.<br />
        /// See also <seealso cref="Addresses" />.
        protected (string? host, ushort? port) Address
        {
            set => Config.Addresses.Add(new NodeAddress
            {
                Host = value.host ?? DEFAULT_HOST,
                Port = value.port ?? DEFAULT_PORT
            });
        }

        /// <inheritdoc cref="Address" />
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
            Address = (host, DEFAULT_PORT);
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

            /// <inheritdoc cref="WithAddress(string?, ushort?)" />
            public static AddressBuilder operator +(AddressBuilder builder, (string? host, ushort? port) address)
            {
                _ = builder._owner.WithAddress(address.host, address.port);
                return builder;
            }

            /// <inheritdoc cref="WithAddress(string)" />
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
            get => Config.TlsMode == TlsMode.SecureTls;
            set => Config.TlsMode = value ? TlsMode.SecureTls : TlsMode.NoTls;
        }
        /// <inheritdoc cref="UseTls" />
        public T WithTls(bool useTls)
        {
            UseTls = useTls;
            return (T)this;
        }
        /// <inheritdoc cref="UseTls" />
        public T WithTls()
        {
            UseTls = true;
            return (T)this;
        }
        #endregion
        #region Request Timeout
        /// <summary>
        /// The duration that the client should wait for a request to complete. This
        /// duration encompasses sending the request, awaiting for a response from the server, and any
        /// required reconnections or retries. If the specified timeout is exceeded for a pending request,
        /// it will result in a timeout error.<br />
        /// If not explicitly set, a default value of <c>250</c> milliseconds will be used.
        /// </summary>
        public TimeSpan RequestTimeout
        {
            get => Config.RequestTimeout ?? TimeSpan.FromMilliseconds(250);
            set => Config.RequestTimeout = value;
        }
        /// <inheritdoc cref="RequestTimeout" />
        public T WithRequestTimeout(TimeSpan requestTimeout)
        {
            RequestTimeout = requestTimeout;
            return (T)this;
        }
        #endregion
        #region Connection Timeout
        /// <summary>
        /// The duration to wait for a TCP/TLS connection to complete.
        /// This applies both during initial client creation and any reconnections that may occur during request processing.<br />
        /// <b>Note</b>: A high connection timeout may lead to prolonged blocking of the entire command pipeline.<br />
        /// If not explicitly set, a default value of <c>250</c> milliseconds will be used.
        /// </summary>
        public TimeSpan ConnectionTimeout
        {
            get => Config.ConnectionTimeout ?? TimeSpan.FromMilliseconds(250);
            set => Config.ConnectionTimeout = value;
        }
        /// <inheritdoc cref="ConnectionTimeout" />
        public T WithConnectionTimeout(TimeSpan connectionTimeout)
        {
            ConnectionTimeout = connectionTimeout;
            return (T)this;
        }
        #endregion
        #region Read From
        /// <summary>
        /// Configure the client's read from strategy. If not set, <seealso cref="ReadFromStrategy.Primary" /> will be used.
        /// </summary>
        public ReadFrom ReadFrom
        {
            set => Config.ReadFrom = value;
        }
        /// <inheritdoc cref="ReadFrom" />
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
        /// Configure the protocol version to use. If not set, <seealso cref="Protocol.RESP3" /> will be used.<br />
        /// See also <seealso cref="Protocol" />.
        /// </summary>
        public Protocol ProtocolVersion
        {
            get => Config.Protocol ?? Protocol.RESP3;
            set => Config.Protocol = value;
        }

        /// <inheritdoc cref="ProtocolVersion" />
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
            get => Config.ClientName ?? "GlideC#";
            set => Config.ClientName = value;
        }

        /// <inheritdoc cref="ClientName" />
        public T WithClientName(string? clientName)
        {
            ClientName = clientName;
            return (T)this;
        }
        #endregion
        #region Connection Retry Strategy
        /// <summary>
        /// Strategy used to determine how and when to reconnect, in case of connection failures.<br />
        /// See also <seealso cref="RetryStrategy" />
        /// </summary>
        public RetryStrategy ConnectionRetryStrategy
        {
            set => Config.RetryStrategy = value;
        }
        /// <inheritdoc cref="ConnectionRetryStrategy" />
        public T WithConnectionRetryStrategy(RetryStrategy connectionRetryStrategy)
        {
            ConnectionRetryStrategy = connectionRetryStrategy;
            return (T)this;
        }

        /// <inheritdoc cref="ConnectionRetryStrategy" />
        /// <inheritdoc cref="RetryStrategy(uint, uint, uint, uint?)" />
        public T WithConnectionRetryStrategy(uint numberOfRetries, uint factor, uint exponentBase, uint? jitterPercent = null)
            => WithConnectionRetryStrategy(new RetryStrategy(numberOfRetries, factor, exponentBase, jitterPercent));
        #endregion

        internal ConnectionConfig Build() => Config;
    }

    /// <summary>
    /// Represents the configuration settings for a Standalone GLIDE client.
    /// </summary>
    public class StandaloneClientConfigurationBuilder : ClientConfigurationBuilder<StandaloneClientConfigurationBuilder>
    {
        public StandaloneClientConfigurationBuilder() : base(false) { }

        /// <summary>
        /// Complete the configuration with given settings.
        /// </summary>
        public new StandaloneClientConfiguration Build() => new() { Request = base.Build() };

        #region DataBase ID
        /// <summary>
        /// Index of the logical database to connect to.
        /// </summary>
        public uint DataBaseId
        {
            set => Config.DatabaseId = value;
        }
        /// <inheritdoc cref="DataBaseId" />
        public StandaloneClientConfigurationBuilder WithDataBaseId(uint dataBaseId)
        {
            DataBaseId = dataBaseId;
            return this;
        }
        #endregion
    }

    /// <summary>
    /// Represents the configuration settings for a Cluster GLIDE client.<br />
    /// Notes: Currently, the reconnection strategy in cluster mode is not configurable, and exponential backoff with fixed values is used.
    /// </summary>
    public class ClusterClientConfigurationBuilder : ClientConfigurationBuilder<ClusterClientConfigurationBuilder>
    {
        public ClusterClientConfigurationBuilder() : base(true) { }

        /// <summary>
        /// Complete the configuration with given settings.
        /// </summary>
        public new ClusterClientConfiguration Build() => new() { Request = base.Build() };
    }
}
