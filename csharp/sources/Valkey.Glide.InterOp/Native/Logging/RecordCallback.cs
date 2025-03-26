using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native.Logging;

[SuppressMessage("ReSharper", "InconsistentNaming")]
public unsafe delegate void RecordCallback(
    nint ref_data,
    byte* in_message,
    int in_message_length,
    Fields in_fields,
    ulong in_span_id
);
