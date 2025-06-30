namespace Valkey.Glide;

/// <summary>
/// Track status of a command while communicating with Valkey.
/// </summary>
public enum CommandStatus
{
    /// <summary>
    /// Command status unknown.
    /// </summary>
    Unknown,

    /// <summary>
    /// ConnectionMultiplexer has not yet started writing this command to Valkey.
    /// </summary>
    WaitingToBeSent,

    /// <summary>
    /// Command has been sent to Valkey.
    /// </summary>
    Sent,

    /// <summary>
    /// Command is in the backlog, waiting to be processed and written to Valkey.
    /// </summary>
    WaitingInBacklog,
}
