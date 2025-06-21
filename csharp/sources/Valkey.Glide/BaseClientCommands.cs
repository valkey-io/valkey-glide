// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IStringBaseCommands
{
    public async Task<string> Set(GlideString key, GlideString value)
        => await Command(Request.Set(key, value));

    public async Task<GlideString?> Get(GlideString key)
        => await Command(Request.Get(key));
}
