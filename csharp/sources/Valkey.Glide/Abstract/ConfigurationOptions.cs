// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.ComponentModel;
using System.Net;
using System.Text;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

// (Add braces to if statement) | (Convert to conditional expression) | (Naming rule: missing '_') | (Expression value is never used)
#pragma warning disable IDE0011, IDE0046, IDE1006, IDE0058

/// <summary>
/// The options relevant to a set of server connections.
/// </summary>
public sealed class ConfigurationOptions : ICloneable
{
    private static class OptionKeys
    {
        public static int ParseInt32(string key, string value, int minValue = int.MinValue, int maxValue = int.MaxValue)
        {
            if (!Format.TryParseInt32(value, out int tmp)) throw new ArgumentOutOfRangeException(key, $"Keyword '{key}' requires an integer value; the value '{value}' is not recognised.");
            if (tmp < minValue) throw new ArgumentOutOfRangeException(key, $"Keyword '{key}' has a minimum value of '{minValue}'; the value '{tmp}' is not permitted.");
            if (tmp > maxValue) throw new ArgumentOutOfRangeException(key, $"Keyword '{key}' has a maximum value of '{maxValue}'; the value '{tmp}' is not permitted.");
            return tmp;
        }

        internal static bool ParseBoolean(string key, string value)
        {
            if (!Format.TryParseBoolean(value, out bool tmp)) throw new ArgumentOutOfRangeException(key, $"Keyword '{key}' requires a boolean value; the value '{value}' is not recognised.");
            return tmp;
        }

        internal static Protocol ParseRedisProtocol(string key, string value)
        {
            if (TryParseRedisProtocol(value, out Protocol protocol)) return protocol;
            throw new ArgumentOutOfRangeException(key, $"Keyword '{key}' requires a RedisProtocol value or a known protocol version number; the value '{value}' is not recognised.");
        }

        internal const string
            AbortOnConnectFail = "abortConnect",
            AllowAdmin = "allowAdmin",
            ConnectTimeout = "connectTimeout",
            DefaultDatabase = "defaultDatabase",
            KeepAlive = "keepAlive",
            ClientName = "name",
            User = "user",
            Password = "password",
            Proxy = "proxy",
            ResponseTimeout = "responseTimeout",
            Ssl = "ssl",
            Version = "version",
            SetClientLibrary = "setlib",
            Protocol = "protocol"
            ;

        private static readonly Dictionary<string, string> normalizedOptions = new[]
        {
            AbortOnConnectFail,
            AllowAdmin,
            ClientName,
            ConnectTimeout,
            DefaultDatabase,
            KeepAlive,
            User,
            Password,
            Proxy,
            ResponseTimeout,
            Ssl,
            Version,
            SetClientLibrary,
            Protocol,
        }.ToDictionary(x => x, StringComparer.OrdinalIgnoreCase);

        public static string TryNormalize(string value)
        {
            if (value != null && normalizedOptions.TryGetValue(value, out string? tmp))
            {
                return tmp ?? "";
            }
            return value ?? "";
        }
    }

    private bool? ssl;

    private Proxy? proxy;

    private RetryStrategy? reconnectRetryPolicy;

    private ReadFrom? readFrom;

    /// <summary>
    /// Gets or sets whether connect/configuration timeouts should be explicitly notified via a TimeoutException.
    /// </summary>
    public bool AbortOnConnectFail => true;

    /// <summary>
    /// Indicates whether admin operations should be allowed.
    /// </summary>
    public bool AllowAdmin => true;

    /// <summary>
    /// Specifies the time in milliseconds that the system should allow for asynchronous operations.
    /// </summary>
    /// <remarks>
    /// Please use <see cref="ResponseTimeout" /> instead.
    /// </remarks>
    public int? AsyncTimeout => ResponseTimeout;

    /// <summary>
    /// Indicates whether the connection should be encrypted.
    /// </summary>
    [Obsolete("Please use .Ssl instead of .UseSsl."), Browsable(false), EditorBrowsable(EditorBrowsableState.Never)]
    public bool UseSsl
    {
        get => Ssl;
        set => Ssl = value;
    }

    /// <summary>
    /// Gets whether the library should identify itself by library-name/version when possible.
    /// </summary>
    public bool SetClientLibrary => true;

    /// <summary>
    /// Gets the library name to use for CLIENT SETINFO lib-name calls to server during handshake.
    /// </summary>
    public string? LibraryName => "GlideC#";

    /// <summary>
    /// A Boolean value that specifies whether the certificate revocation list is checked during authentication.
    /// </summary>
    public bool CheckCertificateRevocation => false;

    /// <summary>
    /// A Boolean value that specifies whether to use per-command validation of strict protocol validity.
    /// This sends an additional command after EVERY command which incurs measurable overhead.
    /// </summary>
    /// <remarks>
    /// The regular RESP protocol does not include correlation identifiers between requests and responses; in exceptional
    /// scenarios, protocol desynchronization can occur, which may not be noticed immediately; this option adds additional data
    /// to ensure that this cannot occur, at the cost of some (small) additional bandwidth usage.
    /// </remarks>
    public bool HighIntegrity => false;

    /// <summary>
    /// The client name to use for all connections.
    /// </summary>
    public string? ClientName { get; set; }

    /// <summary>
    /// Specifies the time in milliseconds that should be allowed for connection (defaults to 5 seconds unless SyncTimeout is higher).
    /// </summary>
    public int? ConnectTimeout { get; set; }

    /// <summary>
    /// Specifies the default database to be used when calling <see cref="ConnectionMultiplexer.GetDatabase(int, object)" /> without any parameters.
    /// </summary>
    public int? DefaultDatabase { get; set; }

    /// <summary>
    /// The server version to assume.
    /// </summary>
    public Version DefaultVersion => new(8, 0);

    /// <summary>
    /// The endpoints defined for this configuration.
    /// </summary>
    /// <remarks>
    /// This is memoized when a <see cref="ConnectionMultiplexer"/> connects.
    /// Modifying it afterwards will have no effect on already-created multiplexers.
    /// </remarks>
    public EndPointCollection EndPoints { get; init; } = [];

    /// <summary>
    /// Whether to enable PING checks on every heartbeat to ensure network stream consistency.
    /// This is a rare measure to react to any potential network traffic drops ASAP, terminating the connection.
    /// </summary>
    public bool HeartbeatConsistencyChecks => true;

    /// <summary>
    /// Controls how often the connection heartbeats. A heartbeat includes:
    /// - Evaluating if any messages have timed out.
    /// - Evaluating connection status (checking for failures).
    /// - Sending a server message to keep the connection alive if needed.
    /// </summary>
    /// <remarks>
    /// This defaults to 1 second and should not be changed for most use cases.
    /// If for example you want to evaluate whether commands have violated the <see cref="AsyncTimeout"/> at a lower fidelity
    /// than 1000 milliseconds, you could lower this value.
    /// Be aware setting this very low incurs additional overhead of evaluating the above more often.
    /// </remarks>
    public TimeSpan HeartbeatInterval => TimeSpan.FromSeconds(1);

    /// <summary>
    /// Whether exceptions include identifiable details (key names, additional .Data annotations).
    /// </summary>
    public bool IncludeDetailInExceptions => false;

    /// <summary>
    /// Whether exceptions include performance counter details.
    /// </summary>
    /// <remarks>
    /// CPU usage, etc - note that this can be problematic on some platforms.
    /// </remarks>
    public bool IncludePerformanceCountersInExceptions => false;

    /// <summary>
    /// Specifies the time in seconds at which connections should be pinged to ensure validity.
    /// -1 Defaults to 60 Seconds.
    /// </summary>
    public int KeepAlive => 1;

    /// <summary>
    /// The username to use to authenticate with the server.
    /// </summary>
    public string? User { get; set; }

    /// <summary>
    /// The password to use to authenticate with the server.
    /// </summary>
    public string? Password { get; set; }

    /// <summary>
    /// Type of proxy to use (if any).
    /// </summary>
    public Proxy Proxy => Proxy.None;

    /// <summary>
    /// The retry policy to be used for connection reconnects.
    /// </summary>
    public RetryStrategy? ReconnectRetryPolicy
    {
        get => reconnectRetryPolicy;
        set => reconnectRetryPolicy = value;
    }

    /// <summary>
    /// The read from strategy and Availability zone if applicable.
    /// </summary>
    public ReadFrom? ReadFrom
    {
        get => readFrom;
        set => readFrom = value;
    }

    /// <summary>
    /// Indicates whether endpoints should be resolved via DNS before connecting.
    /// </summary>
    public bool ResolveDns => true;

    /// <summary>
    /// Specifies the time in milliseconds that the system should allow for responses before concluding that the socket is unhealthy.<br />
    /// Default value is 250 ms.
    /// </summary>
    public int? ResponseTimeout { get; set; }

    /// <summary>
    /// Indicates whether the connection should be encrypted.
    /// </summary>
    public bool Ssl
    {
        get => ssl ?? false;
        set => ssl = value;
    }

    /// <summary>
    /// Specifies the time in milliseconds that the system should allow for synchronous operations (defaults to 250 milliseconds).
    /// </summary>
    /// <remarks>
    /// Please use <see cref="ResponseTimeout" /> instead.
    /// </remarks>
    public int? SyncTimeout => ResponseTimeout;

    /// <summary>
    /// Parse the configuration from a comma-delimited configuration string.
    /// </summary>
    /// <param name="configuration">The configuration string to parse.</param>
    /// <exception cref="ArgumentNullException"><paramref name="configuration"/> is <see langword="null"/>.</exception>
    /// <exception cref="ArgumentException"><paramref name="configuration"/> is empty.</exception>
    public static ConfigurationOptions Parse(string configuration) => Parse(configuration, false);

    /// <summary>
    /// Parse the configuration from a comma-delimited configuration string.
    /// </summary>
    /// <param name="configuration">The configuration string to parse.</param>
    /// <param name="ignoreUnknown">Whether to ignore unknown elements in <paramref name="configuration"/>.</param>
    /// <exception cref="ArgumentNullException"><paramref name="configuration"/> is <see langword="null"/>.</exception>
    /// <exception cref="ArgumentException"><paramref name="configuration"/> is empty.</exception>
    public static ConfigurationOptions Parse(string configuration, bool ignoreUnknown) =>
        new ConfigurationOptions().DoParse(configuration, ignoreUnknown);

    /// <summary>
    /// Create a copy of the configuration.
    /// </summary>
    public ConfigurationOptions Clone() => new()
    {
        ClientName = ClientName,
        ConnectTimeout = ConnectTimeout,
        User = User,
        Password = Password,
        ssl = ssl,
        proxy = proxy,
        ResponseTimeout = ResponseTimeout,
        DefaultDatabase = DefaultDatabase,
        reconnectRetryPolicy = reconnectRetryPolicy,
        readFrom = readFrom,
        EndPoints = EndPoints.Clone(),
        Protocol = Protocol,
    };

    /// <summary>
    /// Apply settings to configure this instance of <see cref="ConfigurationOptions"/>, e.g. for a specific scenario.
    /// </summary>
    /// <param name="configure">An action that will update the properties of this <see cref="ConfigurationOptions"/> instance.</param>
    /// <returns>This <see cref="ConfigurationOptions"/> instance, with any changes <paramref name="configure"/> made.</returns>
    public ConfigurationOptions Apply(Action<ConfigurationOptions> configure)
    {
        configure?.Invoke(this);
        return this;
    }

    /// <summary>
    /// Resolve the default port for any endpoints that did not have a port explicitly specified.
    /// </summary>
    public void SetDefaultPorts() => EndPoints.SetDefaultPorts(Ssl);

    /// <summary>
    /// Returns the effective configuration string for this configuration, including Redis credentials.
    /// </summary>
    /// <remarks>
    /// Includes password to allow generation of configuration strings used for connecting multiplexer.
    /// </remarks>
    public override string ToString() => ToString(includePassword: true);

    /// <summary>
    /// Returns the effective configuration string for this configuration
    /// with the option to include or exclude the password from the string.
    /// </summary>
    /// <param name="includePassword">Whether to include the password.</param>
    public string ToString(bool includePassword)
    {
        StringBuilder sb = new();
        foreach (EndPoint endpoint in EndPoints)
        {
            Append(sb, Format.ToString(endpoint));
        }
        Append(sb, OptionKeys.ClientName, ClientName);
        Append(sb, OptionKeys.ConnectTimeout, ConnectTimeout);
        Append(sb, OptionKeys.User, User);
        Append(sb, OptionKeys.Password, (includePassword || string.IsNullOrEmpty(Password)) ? Password : "*****");
        Append(sb, OptionKeys.Ssl, ssl);
        Append(sb, OptionKeys.Proxy, proxy);
        Append(sb, OptionKeys.ResponseTimeout, ResponseTimeout);
        Append(sb, OptionKeys.DefaultDatabase, DefaultDatabase);
        Append(sb, OptionKeys.Protocol, FormatProtocol(Protocol));

        return sb.ToString();

        static string? FormatProtocol(Protocol? protocol)
        {
            return protocol switch
            {
                null => null,
                Glide.Protocol.Resp2 => "resp2",
                Glide.Protocol.Resp3 => "resp3",
                _ => protocol.GetValueOrDefault().ToString(),
            };
        }
    }

    private static void Append(StringBuilder sb, object value)
    {
        if (value == null) return;
        string s = Format.ToString(value);
        if (!string.IsNullOrWhiteSpace(s))
        {
            if (sb.Length != 0) sb.Append(',');
            sb.Append(s);
        }
    }

    private static void Append(StringBuilder sb, string prefix, object? value)
    {
        string? s = value?.ToString();
        if (!string.IsNullOrWhiteSpace(s))
        {
            if (sb.Length != 0) sb.Append(',');
            if (!string.IsNullOrEmpty(prefix))
            {
                sb.Append(prefix).Append('=');
            }
            sb.Append(s);
        }
    }

    private void Clear()
    {
        ClientName = User = Password = null;
        ConnectTimeout = ResponseTimeout = null;
        ssl = null;
        readFrom = null;
        reconnectRetryPolicy = null;
        EndPoints.Clear();
    }

    object ICloneable.Clone() => Clone();

    private ConfigurationOptions DoParse(string configuration, bool ignoreUnknown = true)
    {
        if (configuration == null)
        {
            throw new ArgumentNullException(nameof(configuration));
        }

        if (string.IsNullOrWhiteSpace(configuration))
        {
            throw new ArgumentException("is empty", nameof(configuration));
        }

        Clear();

        // break it down by commas
        string[] arr = configuration.Split(',');
        foreach (string paddedOption in arr)
        {
            string option = paddedOption.Trim();

            if (string.IsNullOrWhiteSpace(option)) continue;

            // check for special tokens
            int idx = option.IndexOf('=');
            if (idx > 0)
            {
                string key = option[..idx].Trim();
                string value = option[(idx + 1)..].Trim();

                switch (OptionKeys.TryNormalize(key))
                {
                    case OptionKeys.ClientName:
                        ClientName = value;
                        break;
                    case OptionKeys.ConnectTimeout:
                        ConnectTimeout = OptionKeys.ParseInt32(key, value);
                        break;
                    case OptionKeys.User:
                        User = value;
                        break;
                    case OptionKeys.Password:
                        Password = value;
                        break;
                    case OptionKeys.DefaultDatabase:
                        DefaultDatabase = OptionKeys.ParseInt32(key, value);
                        break;
                    case OptionKeys.Ssl:
                        Ssl = OptionKeys.ParseBoolean(key, value);
                        break;
                    case OptionKeys.Protocol:
                        Protocol = OptionKeys.ParseRedisProtocol(key, value);
                        break;
                    case OptionKeys.ResponseTimeout:
                        ResponseTimeout = OptionKeys.ParseInt32(key, value);
                        break;
                    default:
                        if (!ignoreUnknown) throw new ArgumentException($"Keyword '{key}' is not supported.", key);
                        break;
                }
            }
            else
            {
                if (Format.TryParseEndPoint(option, out EndPoint? ep) && !EndPoints.Contains(ep))
                {
                    EndPoints.Add(ep);
                }
            }
        }
        return this;
    }

    /// <summary>
    /// Specify the connection protocol type.
    /// </summary>
    public Protocol? Protocol { get; set; }

    internal static bool TryParseRedisProtocol(string? value, out Protocol protocol)
    {
        // accept raw integers too, but only trust them if we recognize them
        // (note we need to do this before enums, because Enum.TryParse will
        // accept integers as the raw value, which is not what we want here)
        if (value is not null)
        {
            if (Format.TryParseInt32(value, out int i32))
            {
                switch (i32)
                {
                    case 2:
                        protocol = Glide.Protocol.Resp2;
                        return true;
                    case 3:
                        protocol = Glide.Protocol.Resp3;
                        return true;
                    default:
                        break;
                }
            }
            else
            {
                if (Enum.TryParse(value, true, out protocol)) return true;
            }
        }
        protocol = default;
        return false;
    }
}
#pragma warning restore IDE0011, IDE0046, IDE1006, IDE0058
