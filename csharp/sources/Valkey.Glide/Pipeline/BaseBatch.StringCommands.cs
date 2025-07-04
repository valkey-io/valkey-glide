// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchStringCommands.Get(GlideString)" />
    public T Get(GlideString key) => AddCmd(Request.Get(key));

    /// <inheritdoc cref="IBatchStringCommands.Set(GlideString, GlideString)" />
    public T Set(GlideString key, GlideString value) => AddCmd(Request.Set(key, value));

    /// <inheritdoc cref="IBatchStringCommands.Strlen(GlideString)" />
    public T Strlen(GlideString key) => AddCmd(Request.Strlen(key));

    IBatch IBatchStringCommands.Get(GlideString key) => Get(key);
    IBatch IBatchStringCommands.Set(GlideString key, GlideString value) => Set(key, value);
    IBatch IBatchStringCommands.Strlen(GlideString key) => Strlen(key);
}
