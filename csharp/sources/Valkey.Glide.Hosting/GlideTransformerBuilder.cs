// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Hosting;

/// <summary>
/// A builder class responsible for the registration of Glide transformers that implement the <see cref="IGlideTransformer{T}"/> interface.
/// </summary>
/// <remarks>
/// This class allows the chaining of transformers that can be applied to the <see cref="GlideTransformer"/> during runtime.
/// It is primarily used to configure and define the behavior of transformers for use with Valkey Glide.
/// </remarks>
public sealed class GlideTransformerBuilder
{
    private readonly List<Action<GlideTransformer>> _transformers = new();

    /// <summary>
    /// Registers a transformer to be applied during runtime using the provided transformer instance.
    /// </summary>
    /// <typeparam name="T">The type of the transformer implementing <see cref="IGlideTransformer{T}"/>.</typeparam>
    /// <param name="transformer">The transformer instance to register.</param>
    public void RegisterTransformer<T>(IGlideTransformer<T> transformer)
        => _transformers.Add(d => d.RegisterTransformer(transformer));

    internal List<Action<GlideTransformer>> GetTransformers() => _transformers;
}
