namespace Valkey.Glide.InterOp.Logging;

public enum ESeverity
{
    /// The "trace" level.
    ///
    /// Designates very low priority, often extremely verbose, information.
    Trace = 0,

    /// The "debug" level.
    ///
    /// Designates lower priority information.
    Debug = 1,

    /// The "info" level.
    ///
    /// Designates useful information.
    Info = 2,

    /// The "warn" level.
    ///
    /// Designates hazardous situations.
    Warn = 3,

    /// The "error" level.
    ///
    /// Designates very serious errors.
    Error = 4,
}
