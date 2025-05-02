using Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide;

/// <summary>
/// Basic class which holds common configuration for all types of clients.<br />
/// Refer to derived classes for more details: <see cref="StandaloneClientConfiguration" /> and <see cref="ClusterClientConfiguration" />.
/// </summary>
public abstract class BaseClientConfiguration
{
    internal ConnectionConfig Request = new();

    internal ConnectionConfig ToRequest() => Request;
}