// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Transformers;

namespace Valkey.Glide;

/// <summary>
/// A static class containing extension methods for the <see cref="GlideTransformer"/> class.
/// </summary>
/// <remarks>
/// Provides additional methods to extend the functionality of the <see cref="GlideTransformer"/>.
/// These methods simplify the process of configuring and registering common transformers.
/// </remarks>
public static class GlideTransformerExtensions
{
    /// <summary>
    /// Adds default transformers to the specified <see cref="GlideTransformer"/> instance.
    /// </summary>
    /// <param name="glideTransformer">The <see cref="GlideTransformer"/> instance to which default transformers are added.</param>
    /// <returns>
    /// The same <paramref name="glideTransformer"/>.
    /// </returns>
    public static GlideTransformer AddDefaultTransformers(this GlideTransformer glideTransformer)
    {
        glideTransformer.RegisterTransformer(new StringTransformer());
        return glideTransformer;
    }
}
