// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using RequestType = Valkey.Glide.Internals.FFI.RequestType;

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public interface IBatch
{
    public IBatch CustomCommand(GlideString[] args);
    public IBatch Get(GlideString key);
    public IBatch Set(GlideString key, GlideString value);
}

// TODO docs for the god of docs
public abstract class BaseBatch<T> : IBatch where T : BaseBatch<T>
{
    private readonly List<FFI.Cmd> _commands = new();

    internal bool _isAtomic { get; private set; }

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

    IBatch IBatch.CustomCommand(GlideString[] args) => CustomCommand(args);
    IBatch IBatch.Get(GlideString key) => Get(key);
    IBatch IBatch.Set(GlideString key, GlideString value) => Set(key, value);
}
