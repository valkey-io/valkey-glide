namespace Valkey.Glide.InterOp;

/// <summary>
/// Represents a replication strategy for determining how data should be read from a distributed system.
/// </summary>
/// <remarks>
/// The <see cref="ReplicationStrategy"/> struct is used to select the appropriate replication strategy depending
/// on the desired behavior, such as reading from the primary replica, favoring a secondary replica, or
/// utilizing availability zone-specific options for nearest replicas or fallback to the primary replica.
/// </remarks>
/// <seealso cref="Primary"/>
/// <seealso cref="PreferReplica"/>
/// <seealso cref="AzAffinity"/>
/// <seealso cref="AzAffinityReplicasAndPrimary"/>
public struct ReplicationStrategy
{
    /// <summary>
    /// Gets or sets the type of replication strategy used to determine how data is read from the distributed system.
    /// </summary>
    /// <remarks>
    /// The <see cref="Kind"/> property specifies the primary behavior of the replication strategy, such as
    /// reading from the primary replica, preferring a secondary replica, or utilizing availability zone-based
    /// strategies like <see cref="EReadFromKind.AzAffinity"/> or <see cref="EReadFromKind.AzAffinityReplicasAndPrimary"/>.
    /// This property works in conjunction with other properties like <see cref="ReplicationStrategy.AvailabilityZone"/>
    /// to define the desired system behavior.
    /// </remarks>
    public EReadFromKind Kind { get; set; }

    /// <summary>
    /// Gets or sets the availability zone for which the replication strategy should apply.
    /// </summary>
    /// <remarks>
    /// This property is used to define a specific availability zone when using replication strategies
    /// such as <see cref="EReadFromKind.AzAffinity"/> or <see cref="EReadFromKind.AzAffinityReplicasAndPrimary"/>.
    /// If the replication strategy includes an availability zone-based affinity, this property's value
    /// must be specified to ensure correct behavior.
    /// </remarks>
    public string? AvailabilityZone { get; set; }


    /// <summary>
    /// Creates a <see cref="ReplicationStrategy"/> instance configured to always read from the primary replica.
    /// </summary>
    /// <returns>
    /// A <see cref="ReplicationStrategy"/> instance with the <see cref="EReadFromKind.Primary"/> kind
    /// and no specific availability zone.
    /// </returns>
    public static ReplicationStrategy Primary() => new() { Kind = EReadFromKind.Primary, AvailabilityZone = null };

    /// <summary>
    /// Creates a <see cref="ReplicationStrategy"/> instance configured to prioritize reading from replicas,
    /// falling back to the primary replica if no replicas are available.
    /// </summary>
    /// <returns>
    /// A <see cref="ReplicationStrategy"/> instance with the <see cref="EReadFromKind.PreferReplica"/> kind
    /// and no specific availability zone.
    /// </returns>
    public static ReplicationStrategy PreferReplica()
        => new() { Kind = EReadFromKind.PreferReplica, AvailabilityZone = null };

    /// <summary>
    /// Creates a <see cref="ReplicationStrategy"/> instance configured to prioritize reading from replicas in the specified availability zone.
    /// </summary>
    /// <param name="availabilityZone">The name of the availability zone from which to prioritize reading.</param>
    /// <returns>
    /// A <see cref="ReplicationStrategy"/> instance with the <see cref="EReadFromKind.AzAffinity"/> kind and the specified availability zone.
    /// </returns>
    public static ReplicationStrategy AzAffinity(string availabilityZone)
        => new() { Kind = EReadFromKind.AzAffinity, AvailabilityZone = availabilityZone };

    /// <summary>
    /// Creates a <see cref="ReplicationStrategy"/> instance configured to read from replicas within a specific
    /// availability zone if available, falling back to the primary replica.
    /// </summary>
    /// <param name="availabilityZone">
    /// The identifier for the preferred availability zone for selecting replicas to read from.
    /// </param>
    /// <returns>
    /// A <see cref="ReplicationStrategy"/> instance with the <see cref="EReadFromKind.AzAffinityReplicasAndPrimary"/> kind
    /// and the specified availability zone.
    /// </returns>
    public static ReplicationStrategy AzAffinityReplicasAndPrimary(string availabilityZone)
        => new() { Kind = EReadFromKind.AzAffinityReplicasAndPrimary, AvailabilityZone = availabilityZone };
}
