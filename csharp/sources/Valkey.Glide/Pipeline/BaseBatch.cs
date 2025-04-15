// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using RequestType = Valkey.Glide.Internals.FFI.RequestType;

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

// TODO docs for the god of docs
public abstract class BaseBatch<T> where T : BaseBatch<T>
{
    private readonly List<FFI.Cmd> _commands = new();
    private readonly bool _isAtomic;

    public BaseBatch(bool isAtomic)
    {
        _isAtomic = isAtomic;
    }

    internal FFI.Batch ToFFI()
    {
        return new(_commands.ToArray(), _isAtomic);
    }

    public T CustomCommand(GlideString[] args)
    {
        _commands.Add(new(RequestType.CustomCommand, args));
        return (T)this;
    }

    public T Get(GlideString key)
    {
        _commands.Add(new(RequestType.Get, [key]));
        return (T)this;
    }

    public T Set(GlideString key, GlideString value)
    {
        _commands.Add(new(RequestType.Set, [key, value]));
        return (T)this;
    }
}
