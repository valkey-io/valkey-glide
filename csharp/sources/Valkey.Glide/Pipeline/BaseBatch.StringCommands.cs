// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> : IBatchStringCommands where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchStringCommands.Set(GlideString, GlideString)" />
    public T Set(GlideString key, GlideString value) => AddCmd(Request.Set(key, value));

    /// <inheritdoc cref="IBatchStringCommands.Get(GlideString)" />
    public T Get(GlideString key) => AddCmd(Request.Get(key));

    /// <inheritdoc cref="IBatchStringCommands.Strlen(GlideString)" />
    public T Strlen(GlideString key) => AddCmd(Request.Strlen(key));

    IBatchStringCommands IBatchStringCommands.Set(GlideString key, GlideString value) => Set(key, value);
    IBatchStringCommands IBatchStringCommands.Get(GlideString key) => Get(key);
    IBatchStringCommands IBatchStringCommands.Strlen(GlideString key) => Strlen(key);
}
