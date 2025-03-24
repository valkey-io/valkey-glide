// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Serializers;

/// <summary>
/// Provides a serializer implementation for converting string objects into their Valkey representation.
/// </summary>
public class StringGlideSerializer : IGlideSerializer<string>
{
    public string ToValkey(string t) => t.AsRedisString(); // ToDo: Figure out whether this is actually needed (will be obsolete once Parameter struct is added anyways)
}
