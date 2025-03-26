// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.InterOp.Logging;

public readonly struct SpanContext(ESpanContextKind kind, ulong parentId)
{
    public readonly ESpanContextKind Kind = kind;

    /// <summary>
    /// Contains the parent_id if [ESpanContextKind::Explicit] is used
    /// </summary>
    public readonly ulong ParentId = parentId;

    public static SpanContext FromNative(Native.Logging.SpanContext spanContext)
    {
        var kind = (ESpanContextKind)spanContext.kind;
        var parentId = spanContext.parent_id;
        return new SpanContext(kind, parentId);
    }
}
