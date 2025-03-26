using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native.Logging;

[SuppressMessage("ReSharper", "InconsistentNaming")]
public unsafe delegate bool IsEnabledCallback(
    nint ref_data,
    EventData* in_event_data
);
