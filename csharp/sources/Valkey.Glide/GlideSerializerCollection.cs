// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections.Immutable;

namespace Valkey.Glide;

/// <summary>
/// The <see cref="GlideSerializerCollection"/> class manages the registration, sealing, and execution of transformers
/// that implement the <see cref="IGlideSerializer{T}"/> interface.
/// </summary>
/// <remarks>
/// This transformer serves as the core utility for processing transformations using registered transformer
/// implementations. The lifecycle of a <see cref="GlideSerializerCollection"/> involves three primary stages:
/// <list type="number">
/// <item>Registering transformers via the <see cref="RegisterSerializer{T}"/> method.</item>
/// <item>Sealing the transformer using the <see cref="Seal"/> method, which makes it immutable and ready for execution.</item>
/// <item>Applying transformations with <see cref="Transform{T}"/>, which delegates transformation logic to the registered transformers.</item>
/// </list>
/// Once sealed, no further transformers can be registered. Attempting to register or modify the transformer after it has been sealed
/// will result in an exception. Similarly, invoking <see cref="Transform{T}"/> on an unsealed transformer will also result in an exception.
/// </remarks>
public sealed class GlideSerializerCollection
{
    // ToDo: Check whether this can be integrated with TypeDescriptor
    public int Count => _serializersDict.Count;

    private IDictionary<TypeKey, object>? _serializersDict;

    private bool _sealed;

    public void RegisterSerializer<T>(IGlideSerializer<T> serializer)
    {
        // We intentionally lock here to allow concurrent registration of transformers (aka: Mutations of dict)
        lock (this)
        {
            if (_sealed)
                throw new InvalidOperationException(Properties.Language.GlideTransformer_SealedError);
            _serializersDict ??= new Dictionary<TypeKey, object>();
            _serializersDict[typeof(T)] = serializer;
        }
    }

    public void Seal()
    {
        // We intentionally lock here to allow concurrent registration of transformers (aka: Mutations of dict)
        lock (this)
        {
            if (_sealed)
                throw new InvalidOperationException(Properties.Language.GlideTransformer_AlreadySealedError);
            _serializersDict ??= new Dictionary<TypeKey, object>();
            _serializersDict = _serializersDict.ToImmutableDictionary();
            _sealed = true;
        }
    }

    public string Transform<T>(T t)
    {
        // ReSharper disable once InconsistentlySynchronizedField -- We do not need to lock on this. Inconsistencies are "ok"
        if (!_sealed)
            throw new InvalidOperationException(Properties.Language.GlideTransformer_NotSealedError);
        if (t is IHasGlideSerializer<T> hasGlideTransformer)
            return hasGlideTransformer.GetGlideSerializer().ToValkey(t);
        IGlideSerializer<T> serializer = (IGlideSerializer<T>)_serializersDict[typeof(T)];
        return serializer.ToValkey(t);
    }

    // ReSharper disable once InconsistentlySynchronizedField -- this is for tests only
    internal IEnumerable<object> DebugGetTransformers() => _serializersDict?.Values ?? [];
}
