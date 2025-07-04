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

    public async Task<GlideString> GetRange(GlideString key, long start, long end)
        => await Command(Request.GetRange(key, start, end));

    public async Task<long> SetRange(GlideString key, long offset, GlideString value)
        => await Command(Request.SetRange(key, offset, value));

    public async Task<long> Strlen(GlideString key)
        => await Command(Request.Strlen(key));
}
