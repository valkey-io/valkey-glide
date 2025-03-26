namespace Valkey.Glide.InterOp.Logging;

public enum ESpanContextKind
{
    /// <summary>
    /// The new span will be a root span.
    /// </summary>
    Root,

    /// <summary>
    /// The new span will be rooted in the current span.
    /// </summary>
    Current,

    /// <summary>
    /// The new span has an explicitly-specified parent.
    /// </summary>
    Explicit,
}
