using Valkey.Glide.InterOp;

namespace Valkey.Glide.Hosting;

/// <summary>
/// A parser that parses an arbitrary string into a valid <see cref="ConnectionRequest"/>.
/// </summary>
public sealed class ConnectionStringParser
{
    /// <summary>
    /// Parses a connection string into a <see cref="ConnectionRequest"/> object.
    /// </summary>
    /// <param name="connectionString">
    /// The connection string containing configuration parameters, such as host, protocol, or client name.
    /// </param>
    /// <returns>
    /// An instance of <see cref="ConnectionRequest"/> created based on the parsed connection string.
    /// </returns>
    /// <exception cref="FormatException">
    /// Thrown when the connection string contains an unrecognized key or an invalid format.
    /// </exception>
    public static ConnectionRequest Parse(string connectionString)
    {
        var builder = new ConnectionConfigBuilder();
        foreach (var s in connectionString.Split(
                     ';',
                     StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries
                 ))
        {
            var splatted = s.Split("=", StringSplitOptions.TrimEntries);

            if (splatted.Length is 1 && s.Contains(':'))
            {
                // We might come from aspire here, giving "localhost:1234" or the user may just
                // have the host:port combination
                splatted = s.Split(":", StringSplitOptions.TrimEntries);
                if (splatted.Length is 2
                    && splatted[1]
                        .All(char.IsDigit))
                {
                    var host = splatted[0];
                    var port = splatted[1];
                    builder.WithAddress(host, ushort.Parse(port));
                    continue;
                }
            }

            var key = splatted.FirstOrDefault() ?? string.Empty;
            var value = splatted.Skip(1)
                            .FirstOrDefault()
                        ?? string.Empty;
            switch (key.ToLower())
            {
                case "host":
                    ParseConnectionStringHost(value, builder);
                    break;
                case "clustered":
                    ParseConnectionStringClustered(value, builder);
                    break;
                case "clientname":
                    ParseConnectionStringClientName(value, builder);
                    break;
                case "protocol":
                    ParseConnectionStringProtocol(value, builder);
                    break;
                case "tls":
                    ParseConnectionStringTls(value, builder);
                    break;
                default:
                    throw new FormatException("Unknown connection string key: " + key);
            }
        }

        return builder.Build();
    }

    private static void ParseConnectionStringTls(string value, ConnectionConfigBuilder builder)
    {
        switch (value.ToLower())
        {
            case "no":
            case "false":
                builder.WithTlsMode(ETlsMode.NoTls);
                break;
            case "yes":
            case "true":
            case "secure":
                builder.WithTlsMode(ETlsMode.SecureTls);
                break;
            case "insecure":
                builder.WithTlsMode(ETlsMode.InsecureTls);
                break;
            default:
                throw new FormatException("Invalid tls value in connection string: " + value);
        }
    }

    private static void ParseConnectionStringProtocol(string value, ConnectionConfigBuilder builder)
    {
        switch (value.ToLower())
        {
            case "resp2":
                builder.WithProtocol(InterOp.EProtocolVersion.Resp2);
                break;
            case "resp3":
                builder.WithProtocol(InterOp.EProtocolVersion.Resp3);
                break;
            default:
                throw new FormatException("Invalid protocol value in connection string: " + value);
        }
    }

    private static void ParseConnectionStringClustered(string value, ConnectionConfigBuilder builder)
    {
        switch (value.ToLower())
        {
            case "":
            case "yes":
            case "true":
                builder.WithClusterMode(true);
                break;
            case "no":
            case "false":
                builder.WithClusterMode(false);
                break;
            default:
                throw new FormatException("Invalid clustered value in connection string: " + value);
        }
    }

    private static void ParseConnectionStringClientName(string value, ConnectionConfigBuilder builder)
    {
        value = value.Trim();
        builder.WithClientName(string.IsNullOrWhiteSpace(value) ? null : value);
    }

    private static void ParseConnectionStringHost(string value, ConnectionConfigBuilder builder)
    {
        var listValues = value.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
        if (listValues.Length is 0)
            throw new FormatException("Invalid host in connection string: " + value);
        foreach (var localValue in listValues)
        {
            var splatted = localValue.Split(":", StringSplitOptions.TrimEntries);
            switch (splatted.Length)
            {
                case 2:
                {
                    var host = splatted[0];
                    if (string.IsNullOrWhiteSpace(host))
                        throw new FormatException("Invalid host in connection string: " + localValue);
                    var port = splatted[1];
                    builder.WithAddress(host, ushort.Parse(port));
                    break;
                }
                case 1:
                    builder.WithAddress(splatted[0]);
                    break;
                default:
                    throw new FormatException("Invalid host in connection string: " + localValue);
            }
        }
    }
}
