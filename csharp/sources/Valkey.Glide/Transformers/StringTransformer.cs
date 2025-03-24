// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Transformers;

public class StringTransformer : IGlideTransformer<string>
{
    public string ToValkeyString(string t) => t.AsRedisString();
}
