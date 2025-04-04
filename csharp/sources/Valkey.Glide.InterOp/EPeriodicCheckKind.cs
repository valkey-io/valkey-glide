namespace Valkey.Glide.InterOp;

/// <summary>
/// Represents the modes available for periodic topology validation within a cluster context.
/// </summary>
/// <seealso cref="PeriodicCheck"/>
public enum EPeriodicCheckKind
{
    /// <summary>
    /// Activates periodic checks at pre-defined intervals.
    /// </summary>
    /// <seealso cref="PeriodicCheck.Enabled"/>
    Enabled        = 0,
    /// <summary>
    /// Deactivates periodic validation entirely.
    /// </summary>
    /// <seealso cref="PeriodicCheck.Disabled"/>
    Disabled       = 1,
    /// <summary>
    /// Activates periodic checks at a given interval.
    /// </summary>
    /// <seealso cref="PeriodicCheck.ManualInterval"/>
    ManualInterval = 2,
}
