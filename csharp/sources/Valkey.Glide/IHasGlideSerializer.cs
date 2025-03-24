// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

/// <summary>
/// Represents a type that provides a mechanism to retrieve a corresponding <see cref="IGlideSerializer{T}"/> implementation.
/// </summary>
/// <remarks>
/// If a type implements this, it overrides the <see cref="GlideSerializerCollection"/> representation of this.
/// </remarks>
/// <typeparam name="T">The type of the object for which the transformer is used.</typeparam>
public interface IHasGlideSerializer<T>
{
    /// <summary>
    /// Retrieves the transformer that implements <see cref="IGlideSerializer{T}"/> for the specified type.
    /// </summary>
    /// <returns>An instance of <see cref="IGlideSerializer{T}"/> used for transforming objects of type <typeparamref name="T"/>.</returns>
    IGlideSerializer<T> GetGlideSerializer();
}
