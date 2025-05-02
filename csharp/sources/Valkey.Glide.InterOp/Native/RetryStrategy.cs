using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

/// <summary>
/// Represents the strategy used to determine how and when to reconnect, in case of connection
/// failures. The time between attempts grows exponentially, to the formula <c>rand(0 ... factor *
/// (exponentBase ^ N))</c>, where <c>N</c> is the number of failed attempts.
/// <para />
/// Once the maximum value is reached, that will remain the time between retry attempts until a
/// reconnect attempt is successful. The client will attempt to reconnect indefinitely.
/// </summary>
[StructLayout(LayoutKind.Sequential)]
public struct RetryStrategy(uint numberOfRetries, uint factor, uint exponentBase)
{
    /// <summary>
    /// Number of retry attempts that the client should perform when disconnected from the server,
    /// where the time between retries increases. Once the retries have reached the maximum value, the
    /// time between retries will remain constant until a reconnect attempt is successful.
    /// </summary>
    public uint NumberOfRetries = numberOfRetries;
    /// <summary>
    /// The multiplier that will be applied to the waiting time between each retry.
    /// </summary>
    public uint Factor = factor;
    /// <summary>
    /// The exponent base configured for the strategy.
    /// </summary>
    public uint ExponentBase = exponentBase;
}
