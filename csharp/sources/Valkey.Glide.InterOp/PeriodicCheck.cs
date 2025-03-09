using System;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

public enum EPeriodicCheckKind
{
    Enabled        = 0,
    Disabled       = 1,
    ManualInterval = 2,
}

public struct PeriodicCheck
{
    public EPeriodicCheckKind Kind { get; set; }
    public TimeSpan? Interval { get; set; }

    public static PeriodicCheck Disabled => new PeriodicCheck { Kind = EPeriodicCheckKind.Disabled };
    public static PeriodicCheck Enabled => new PeriodicCheck { Kind  = EPeriodicCheckKind.Enabled };

    public static PeriodicCheck ManualInterval(TimeSpan interval)
        => new PeriodicCheck { Kind = EPeriodicCheckKind.ManualInterval, Interval = interval };
}
