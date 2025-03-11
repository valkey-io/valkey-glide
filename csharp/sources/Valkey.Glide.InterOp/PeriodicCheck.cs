using System;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Represents the periodic topology check configuration for detecting changes in a cluster's topology.
/// </summary>
/// <seealso cref="ConnectionRequest"/>
/// <seealso cref="EPeriodicCheckKind"/>
/// <seealso cref="Disabled"/>
/// <seealso cref="Enabled"/>
/// <seealso cref="ManualInterval"/>
public struct PeriodicCheck
{
    /// <summary>
    /// Gets or sets the kind of periodic topology check to be performed.
    /// </summary>
    /// <remarks>
    /// This property determines the specific type of periodic check to apply.
    /// Possible values are defined in the <see cref="EPeriodicCheckKind"/> enumeration,
    /// including options such as Enabled, Disabled, or ManualInterval.
    /// Additional configurations may be required depending on the selected kind;
    /// for example, if <see cref="EPeriodicCheckKind.ManualInterval"/> is chosen,
    /// an <see cref="Interval"/> value must also be specified.
    /// </remarks>
    /// <exception cref="ArgumentException">
    /// Thrown when this property is set to <see cref="EPeriodicCheckKind.ManualInterval"/>
    /// but the corresponding <see cref="Interval"/> is null.
    /// </exception>
    public EPeriodicCheckKind Kind { get; set; }

    /// <summary>
    /// Gets or sets the interval for performing periodic topology checks
    /// when the kind of periodic check is set to <see cref="EPeriodicCheckKind.ManualInterval"/>.
    /// </summary>
    /// <remarks>
    /// If <see cref="EPeriodicCheckKind.ManualInterval"/> is specified in the configuration, this property holds the time span
    /// indicating how frequently the checks should occur. When the periodic check mode is not ManualInterval, this value is ignored.
    /// </remarks>
    /// <exception cref="ArgumentException">
    /// Thrown when this property is null and <see cref="EPeriodicCheckKind.ManualInterval"/> is specified.
    /// </exception>
    public TimeSpan? Interval { get; set; }

    /// <summary>
    /// Creates a new instance of <see cref="PeriodicCheck"/> with the check <see cref="Kind"/>
    /// set to <see cref="EPeriodicCheckKind.Disabled"/>.
    /// </summary>
    /// <returns>
    /// An instance of <see cref="PeriodicCheck"/> where the <see cref="EPeriodicCheckKind"/>
    /// is set to <see cref="EPeriodicCheckKind.Disabled"/>.
    /// </returns>
    public static PeriodicCheck Disabled() => new() { Kind = EPeriodicCheckKind.Disabled };

    /// <summary>
    /// Creates a new instance of <see cref="PeriodicCheck"/> with the check <see cref="Kind"/>
    /// set to <see cref="EPeriodicCheckKind.Enabled"/>, enabling periodic checks with default configurations.
    /// </summary>
    /// <returns>
    /// An instance of <see cref="PeriodicCheck"/> where the <see cref="EPeriodicCheckKind"/>
    /// is set to <see cref="EPeriodicCheckKind.Enabled"/>.
    /// </returns>
    public static PeriodicCheck Enabled() => new() { Kind = EPeriodicCheckKind.Enabled };


    /// <summary>
    /// Creates a <see cref="PeriodicCheck"/> instance with a manual interval for periodic topology checks.
    /// </summary>
    /// <remarks>
    /// Creates a new instance of <see cref="PeriodicCheck"/> with the check <see cref="Kind"/>
    /// set to <see cref="EPeriodicCheckKind.ManualInterval"/> and the specified interval configuration.
    /// </remarks>
    /// <param name="interval">
    /// The custom time interval to be used for periodic checks. Must be a valid <see cref="TimeSpan"/> value.
    /// </param>
    /// <returns>
    /// An instance of <see cref="PeriodicCheck"/> where the <see cref="EPeriodicCheckKind"/>
    /// is set to <see cref="EPeriodicCheckKind.ManualInterval"/> and the provided interval is applied.
    /// </returns>
    public static PeriodicCheck ManualInterval(TimeSpan interval)
        => new() { Kind = EPeriodicCheckKind.ManualInterval, Interval = interval };
}
