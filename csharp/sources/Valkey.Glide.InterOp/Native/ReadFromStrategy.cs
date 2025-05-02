namespace Valkey.Glide.InterOp.Native;

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