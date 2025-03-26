using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide.InterOp.Native.Logging;

[SuppressMessage("ReSharper", "InconsistentNaming")]
public delegate void RecordFollowsFromCallback(
    nint ref_data,
    ulong in_span_id,
    ulong in_follows_id
);