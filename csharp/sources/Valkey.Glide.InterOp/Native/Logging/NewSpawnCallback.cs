using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native.Logging;

[SuppressMessage("ReSharper", "InconsistentNaming")]
public unsafe delegate ulong NewSpawnCallback(
    nint ref_data,
    byte* in_message,
    int in_message_length,
    Fields in_fields,
    EventData* in_event_data,
    SpanContext in_span_context
);
