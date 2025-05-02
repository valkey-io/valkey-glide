namespace Valkey.Glide;

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
    public new ClusterClientConfiguration Build() => new() {Request = base.Build()};
}