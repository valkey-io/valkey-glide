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
    public uint Factor { get; set; }
    public uint ExponentialBase { get; set; }
    public uint NumberOfRetries { get; set; }
}
