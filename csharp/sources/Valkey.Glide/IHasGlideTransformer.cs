// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

/// <summary>
/// Represents a type that provides a mechanism to retrieve a corresponding <see cref="IGlideTransformer{T}"/> implementation.
/// </summary>
/// <remarks>
/// If a type implements this, it overrides the <see cref="GlideTransformer"/> representation of this.
/// </remarks>
/// <typeparam name="T">The type of the object for which the transformer is used.</typeparam>
public interface IHasGlideTransformer<T>
{
    /// <summary>
    /// Retrieves the transformer that implements <see cref="IGlideTransformer{T}"/> for the specified type.
    /// </summary>
    /// <returns>An instance of <see cref="IGlideTransformer{T}"/> used for transforming objects of type <typeparamref name="T"/>.</returns>
    IGlideTransformer<T> GetGlideTransformer();
}
