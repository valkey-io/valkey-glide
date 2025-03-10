using System;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Describes the reconnection strategy the client should use.
/// </summary>
/// <remarks>
/// Reconnection is done by calculating a random offset with growing time between attempts.
/// The formula is as follows: <c>rand(<see cref="Factor"/> * (<see cref="ExponentialBase"/> * Attempt))</c>
/// </remarks>
public struct ConnectionRetryStrategy
{
    /// <summary>
    /// The multiplier that will be applied to the waiting time between each retry.
    /// </summary>
    /// <remarks>
    /// The formula is as follows: <c>rand(<see cref="Factor"/> * (<see cref="ExponentialBase"/> * Attempt))</c>
    /// </remarks>
    public uint Factor { get; set; }

    /// <summary>
    /// The exponent base configured for the strategy.
    /// </summary>
    /// <remarks>
    /// The formula is as follows: <c>rand(<see cref="Factor"/> * (<see cref="ExponentialBase"/> * Attempt))</c>
    /// </remarks>
    public uint ExponentialBase { get; set; }

    // ToDo: Figure out if this should actually be named MaxAttemptImpact or something like that or if it is really
    //       the number of maximum attempts to perform. In both cases: the documentation is missleading.
    /// <summary>
    /// Number of retry attempts that the client should perform when disconnected from the server.
    /// </summary>
    /// <remarks>
    /// Once the retries have reached the maximum timeout (see <see cref="Factor"/> and <see cref="ExponentialBase"/>),
    /// the time between retries will remain constant until a reconnect attempt is successful.
    /// </remarks>
    public uint NumberOfRetries { get; set; }
}
