// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Serializers;

namespace Valkey.Glide;

/// <summary>
/// A static class containing extension methods for the <see cref="GlideSerializerCollection"/> class.
/// </summary>
/// <remarks>
/// Provides additional methods to extend the functionality of the <see cref="GlideSerializerCollection"/>.
/// These methods simplify the process of configuring and registering common transformers.
/// </remarks>
public static class GlideSerializerCollectionExtensions
{
    /// <summary>
    /// Adds default transformers to the specified <see cref="GlideSerializerCollection"/> instance.
    /// </summary>
    /// <param name="glideSerializerCollection">The <see cref="GlideSerializerCollection"/> instance to which default transformers are added.</param>
    /// <returns>
    /// The same <paramref name="glideSerializerCollection"/>.
    /// </returns>
    public static GlideSerializerCollection RegisterDefaultSerializers(this GlideSerializerCollection glideSerializerCollection)
    {
        glideSerializerCollection.RegisterSerializer(new StringGlideSerializer());
        glideSerializerCollection.RegisterSerializer(new CommandTextGlideSerializer());
        return glideSerializerCollection;
    }
}
