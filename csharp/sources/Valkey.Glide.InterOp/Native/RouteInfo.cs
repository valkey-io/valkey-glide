using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
internal struct RouteInfo
{
    public RouteType Type;
    public int SlotId;
    [MarshalAs(UnmanagedType.LPStr)] public string? SlotKey;
    public SlotType SlotType;
    [MarshalAs(UnmanagedType.LPStr)] public string? Host;
    public int Port;
}