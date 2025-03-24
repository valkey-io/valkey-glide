// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Serializers;

namespace Valkey.Glide.Data;

/// <summary>
/// Represents a string-based command text used within the Valkey Glide framework.
/// </summary>
/// <remarks>
/// This structure is a record-based implementation, ensuring immutability and value-based equality.
/// It also implements the <see cref="IHasGlideSerializer{T}"/> interface to provide a mechanism
/// for retrieving a suitable serializer for converting the command text into a parameter representation.
/// </remarks>
public readonly record struct CommandText(string Text) : IHasGlideSerializer<CommandText>
{
    /// <inheritdoc/>
    public IGlideSerializer<CommandText> GetGlideSerializer() => new CommandTextGlideSerializer();


    /// <summary>
    /// Defines custom implicit conversion operators for the <see cref="CommandText"/> type.
    /// </summary>
    /// <remarks>
    /// The operators provide seamless conversion between the <see cref="CommandText"/> record structure
    /// and its underlying string representation. This allows users to convert a string directly into a
    /// <see cref="CommandText"/> instance or extract the string value from an instance of <see cref="CommandText"/>.
    /// </remarks>
    public static implicit operator CommandText(string text) => new(text);

    /// <summary>
    /// Defines an implicit conversion operator for the <see cref="CommandText"/> structure.
    /// </summary>
    /// <remarks>
    /// This operator allows direct assignment of a string to a <see cref="CommandText"/> instance,
    /// streamlining the creation of command text objects.
    /// </remarks>
    public static implicit operator string(CommandText commandText) => commandText.Text;
}
