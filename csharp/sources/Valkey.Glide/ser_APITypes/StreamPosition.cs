using System;

namespace Valkey.Glide;

/// <summary>
/// Describes a pair consisting of the Stream Key and the <see cref="Position"/> from which to begin reading a stream.
/// </summary>
public struct StreamPosition
{
    /// <summary>
    /// Read from the beginning of a stream.
    /// </summary>
    public static ValkeyValue Beginning => StreamConstants.ReadMinValue;

    /// <summary>
    /// Read new messages.
    /// </summary>
    public static ValkeyValue NewMessages => StreamConstants.NewMessages;

    /// <summary>
    /// Initializes a <see cref="StreamPosition"/> value.
    /// </summary>
    /// <param name="key">The key for the stream.</param>
    /// <param name="position">The position from which to begin reading the stream.</param>
    public StreamPosition(ValkeyKey key, ValkeyValue position)
    {
        Key = key;
        Position = position;
    }

    /// <summary>
    /// The stream key.
    /// </summary>
    public ValkeyKey Key { get; }

    /// <summary>
    /// The offset at which to begin reading the stream.
    /// </summary>
    public ValkeyValue Position { get; }

    internal static ValkeyValue Resolve(ValkeyValue value, ValkeyCommand command)
    {
        if (value == NewMessages)
        {
            return command switch
            {
                ValkeyCommand.XREAD => throw new InvalidOperationException("StreamPosition.NewMessages cannot be used with StreamRead."),
                ValkeyCommand.XREADGROUP => StreamConstants.UndeliveredMessages,
                ValkeyCommand.XGROUP => StreamConstants.NewMessages,
                // new is only valid for the above
                _ => throw new ArgumentException($"Unsupported command in StreamPosition.Resolve: {command}.", nameof(command)),
            };
        }
        else if (value == StreamPosition.Beginning)
        {
            switch (command)
            {
                case ValkeyCommand.XREAD:
                case ValkeyCommand.XREADGROUP:
                case ValkeyCommand.XGROUP:
                    return StreamConstants.AllMessages;
            }
        }
        return value;
    }
}
