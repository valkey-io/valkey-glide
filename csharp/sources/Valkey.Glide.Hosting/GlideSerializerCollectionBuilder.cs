// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Hosting;

/// <summary>
/// A builder class responsible for the registration of Glide transformers that implement the <see cref="IGlideSerializer{T}"/> interface.
/// </summary>
/// <remarks>
/// This class allows the chaining of transformers that can be applied to the <see cref="GlideSerializerCollection"/> during runtime.
/// It is primarily used to configure and define the behavior of transformers for use with Valkey Glide.
/// </remarks>
public sealed class GlideSerializerCollectionBuilder
{
    private readonly List<Action<GlideSerializerCollection>> _serializers = new();

    /// <summary>
    /// Registers a serializer to be applied during runtime using the provided serializer instance.
    /// </summary>
    /// <typeparam name="T">The type of the serializer implementing <see cref="IGlideSerializer{T}"/>.</typeparam>
    /// <param name="serializer">The serializer instance to register.</param>
    public void RegisterSerializer<T>(IGlideSerializer<T> serializer)
        => _serializers.Add(d => d.RegisterSerializer(serializer));

    internal List<Action<GlideSerializerCollection>> GetSerializers() => _serializers;
}
