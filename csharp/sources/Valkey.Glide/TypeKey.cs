// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
// Original source: https://github.com/X39/cs-x39-util/blob/1aa31b60f965c59ea3ea0b37f701cdc8e0d6c408/X39.Util/TypeKey.cs
// Copyright is hereby granted by X39 under the differing terms of license. Original license: LGPLv3.

namespace Valkey.Glide;

/// <summary>
/// Represents a key that uniquely identifies a Type.
/// </summary>
/// <remarks>
/// This is useful for using a Type as a key in a dictionary.
/// </remarks>
public readonly struct TypeKey : IEquatable<TypeKey>
{
    /// <inheritdoc />
    public bool Equals(TypeKey other) => Type.IsEquivalentTo(other.Type);

    /// <inheritdoc />
    public override bool Equals(object? obj) => obj is TypeKey other && Equals(other);

    /// <inheritdoc />
    public override int GetHashCode() => Type.GetHashCode();

    /// <summary>
    /// Determines whether two <see cref="TypeKey"/> objects are equal.
    /// </summary>
    /// <param name="left">The left <see cref="TypeKey"/> object.</param>
    /// <param name="right">The right <see cref="TypeKey"/> object.</param>
    /// <returns>
    /// <see langword="true"/> if the specified <see cref="TypeKey"/> objects are equal; otherwise, <see langword="false"/>.
    /// </returns>
    public static bool operator ==(TypeKey left, TypeKey right) => left.Equals(right);

    /// <summary>
    /// Determines whether two <see cref="TypeKey"/> objects are not equal.
    /// </summary>
    /// <param name="left">The left <see cref="TypeKey"/> object.</param>
    /// <param name="right">The right <see cref="TypeKey"/> object.</param>
    /// <returns>
    /// <see langword="true"/> if the specified <see cref="TypeKey"/> objects are not equal; otherwise, <see langword="false"/>.
    /// </returns>
    public static bool operator !=(TypeKey left, TypeKey right) => !left.Equals(right);

    /// <summary>
    /// Represents a key that uniquely identifies a Type.
    /// </summary>
    /// <remarks>
    /// This is useful for using a Type as a key in a dictionary.
    /// </remarks>
    public TypeKey(Type type)
    {
        Type = type;
    }

    /// <summary>
    /// The Type that this TypeKey represents.
    /// </summary>
    public Type Type { get; }

    /// <summary>
    /// Defines an implicit operator that converts a TypeKey to a Type.
    /// </summary>
    /// <param name="key">The TypeKey to be converted.</param>
    /// <returns>The corresponding Type object.</returns>
    public static implicit operator Type(TypeKey key) => key.Type;

    /// <summary>
    /// Defines an implicit operator that converts a Type object to a TypeKey.
    /// </summary>
    /// <param name="type">The Type object to be converted.</param>
    /// <returns>The corresponding TypeKey object.</returns>
    public static implicit operator TypeKey(Type type) => new TypeKey(type);
}
