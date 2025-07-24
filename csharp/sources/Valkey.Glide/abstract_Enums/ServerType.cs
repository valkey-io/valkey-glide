namespace Valkey.Glide;

/// <summary>
/// Indicates the flavor of a particular server.
/// </summary>
public enum ServerType
{
    /// <summary>
    /// Classic server.
    /// </summary>
    Standalone,

    /// <summary>
    /// Monitoring/configuration sentinel server.
    /// </summary>
    Sentinel,

    /// <summary>
    /// Distributed cluster server.
    /// </summary>
    Cluster,

    /// <summary>
    /// Distributed installation via <a href="https://github.com/twitter/twemproxy">twemproxy</a>.
    /// </summary>
    Twemproxy,

    /// <summary>
    /// Valkey cluster via <a href="https://github.com/envoyproxy/envoy">envoyproxy</a>.
    /// </summary>
    Envoyproxy,
}

internal static class ServerTypeExtensions
{
    /// <summary>
    /// Whether a server type can have only a single primary, meaning an election if multiple are found.
    /// </summary>
    internal static bool HasSinglePrimary(this ServerType type) => type switch
    {
        ServerType.Envoyproxy => false,
        _ => true,
    };

    /// <summary>
    /// Whether a server type supports.
    /// </summary>
    internal static bool SupportsAutoConfigure(this ServerType type) => type switch
    {
        ServerType.Twemproxy => false,
        ServerType.Envoyproxy => false,
        _ => true,
    };
}
