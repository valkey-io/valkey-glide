namespace Valkey.Glide;

/// <summary>
/// Describes a Valkey Stream with an associated array of entries.
/// </summary>
public readonly struct ValkeyStream
{
    internal ValkeyStream(ValkeyKey key, StreamEntry[] entries)
    {
        Key = key;
        Entries = entries;
    }

    /// <summary>
    /// The key for the stream.
    /// </summary>
    public ValkeyKey Key { get; }

    /// <summary>
    /// An array of entries contained within the stream.
    /// </summary>
    public StreamEntry[] Entries { get; }
}
