using System;

namespace Valkey.Glide;

/// <summary>
/// Describes a value/expiry pair.
/// </summary>
public readonly struct ValkeyValueWithExpiry
{
    /// <summary>
    /// Creates a <see cref="ValkeyValueWithExpiry"/> from a <see cref="ValkeyValue"/> and a <see cref="Nullable{TimeSpan}"/>.
    /// </summary>
    public ValkeyValueWithExpiry(ValkeyValue value, TimeSpan? expiry)
    {
        Value = value;
        Expiry = expiry;
    }

    /// <summary>
    /// The expiry of this record.
    /// </summary>
    public TimeSpan? Expiry { get; }

    /// <summary>
    /// The value of this record.
    /// </summary>
    public ValkeyValue Value { get; }
}
