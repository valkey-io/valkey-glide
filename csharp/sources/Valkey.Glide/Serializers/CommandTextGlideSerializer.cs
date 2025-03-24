// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Data;

namespace Valkey.Glide.Serializers;

/// <summary>
/// A serializer implementation for converting <see cref="CommandText"/> objects
/// into their string representation for use within the Valkey Glide framework.
/// </summary>
public sealed class CommandTextGlideSerializer : IGlideSerializer<CommandText>
{
    public string ToValkey(CommandText t) => t.Text;
}
