using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

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
    /// Init strategy with <seealso cref="ReadFromStrategy.Primary"/> or <seealso cref="ReadFromStrategy.PreferReplica"/> strategy.
    /// </summary>
    /// <param name="strategy">Either <seealso cref="ReadFromStrategy.Primary"/> or <seealso cref="ReadFromStrategy.PreferReplica"/>.</param>
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
    /// Init strategy with <seealso cref="ReadFromStrategy.AzAffinity"/> or <seealso cref="ReadFromStrategy.AzAffinityReplicasAndPrimary"/> strategy and an Availability Zone.
    /// </summary>
    /// <param name="strategy">Either <seealso cref="ReadFromStrategy.AzAffinity"/> or <seealso cref="ReadFromStrategy.AzAffinityReplicasAndPrimary"/>.</param>
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