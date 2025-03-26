namespace Valkey.Glide.InterOp.Native.Logging;

public enum ESpanContextKind
{
    /// The new span will be a root span.
    Root,

    /// The new span will be rooted in the current span.
    Current,

    /// The new span has an explicitly-specified parent.
    Explicit,
}