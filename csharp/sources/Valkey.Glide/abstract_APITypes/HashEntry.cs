using System;
using System.Collections.Generic;
using System.ComponentModel;

namespace Valkey.Glide;

/// <summary>
/// Describes a hash-field (a name/value pair).
/// </summary>
public readonly struct HashEntry : IEquatable<HashEntry>
{
    internal readonly ValkeyValue name, value;

    /// <summary>
    /// Initializes a <see cref="HashEntry"/> value.
    /// </summary>
    /// <param name="name">The name for this hash entry.</param>
    /// <param name="value">The value for this hash entry.</param>
    public HashEntry(ValkeyValue name, ValkeyValue value)
    {
        this.name = name;
        this.value = value;
    }

    /// <summary>
    /// The name of the hash field.
    /// </summary>
    public ValkeyValue Name => name;

    /// <summary>
    /// The value of the hash field.
    /// </summary>
    public ValkeyValue Value => value;

    /// <summary>
    /// The name of the hash field.
    /// </summary>
    [Browsable(false)]
    [EditorBrowsable(EditorBrowsableState.Never), Obsolete("Please use Name", false)]
    public ValkeyValue Key => name;

    /// <summary>
    /// Converts to a key/value pair.
    /// </summary>
    /// <param name="value">The <see cref="HashEntry"/> to create a <see cref="KeyValuePair{TKey, TValue}"/> from.</param>
    public static implicit operator KeyValuePair<ValkeyValue, ValkeyValue>(HashEntry value) =>
        new KeyValuePair<ValkeyValue, ValkeyValue>(value.name, value.value);

    /// <summary>
    /// Converts from a key/value pair.
    /// </summary>
    /// <param name="value">The <see cref="KeyValuePair{TKey, TValue}"/> to get a <see cref="HashEntry"/> from.</param>
    public static implicit operator HashEntry(KeyValuePair<ValkeyValue, ValkeyValue> value) =>
        new HashEntry(value.Key, value.Value);

    /// <summary>
    /// A "{name}: {value}" string representation of this entry.
    /// </summary>
    public override string ToString() => name + ": " + value;

    /// <inheritdoc/>
    public override int GetHashCode() => name.GetHashCode() ^ value.GetHashCode();

    /// <summary>
    /// Compares two values for equality.
    /// </summary>
    /// <param name="obj">The <see cref="HashEntry"/> to compare to.</param>
    public override bool Equals(object? obj) => obj is HashEntry heObj && Equals(heObj);

    /// <summary>
    /// Compares two values for equality.
    /// </summary>
    /// <param name="other">The <see cref="HashEntry"/> to compare to.</param>
    public bool Equals(HashEntry other) => name == other.name && value == other.value;

    /// <summary>
    /// Compares two values for equality.
    /// </summary>
    /// <param name="x">The first <see cref="HashEntry"/> to compare.</param>
    /// <param name="y">The second <see cref="HashEntry"/> to compare.</param>
    public static bool operator ==(HashEntry x, HashEntry y) => x.name == y.name && x.value == y.value;

    /// <summary>
    /// Compares two values for non-equality.
    /// </summary>
    /// <param name="x">The first <see cref="HashEntry"/> to compare.</param>
    /// <param name="y">The second <see cref="HashEntry"/> to compare.</param>
    public static bool operator !=(HashEntry x, HashEntry y) => x.name != y.name || x.value != y.value;
}
