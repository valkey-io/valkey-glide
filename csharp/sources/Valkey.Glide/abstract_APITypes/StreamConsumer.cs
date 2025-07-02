namespace Valkey.Glide;

/// <summary>
/// Describes a consumer off a Valkey Stream.
/// </summary>
public readonly struct StreamConsumer
{
    internal StreamConsumer(ValkeyValue name, int pendingMessageCount)
    {
        Name = name;
        PendingMessageCount = pendingMessageCount;
    }

    /// <summary>
    /// The name of the consumer.
    /// </summary>
    public ValkeyValue Name { get; }

    /// <summary>
    /// The number of messages that have been delivered by not yet acknowledged by the consumer.
    /// </summary>
    public int PendingMessageCount { get; }
}
