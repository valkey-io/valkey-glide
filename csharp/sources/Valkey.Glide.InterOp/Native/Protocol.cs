namespace Valkey.Glide.InterOp.Native;

/// <summary>
/// Represents the communication protocol with the server.
/// </summary>
public enum Protocol : uint
{
    /// <summary>
    /// Use RESP3 to communicate with the server nodes.
    /// </summary>
    RESP3 = 0,
    /// <summary>
    /// Use RESP2 to communicate with the server nodes.
    /// </summary>
    RESP2 = 1,
}