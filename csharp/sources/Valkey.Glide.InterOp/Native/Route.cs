namespace Valkey.Glide.InterOp.Native;

/// A wrapper for a route
internal class Route : Marshallable
{
    private readonly RouteInfo _info;
    private readonly IntPtr _ptr = IntPtr.Zero;

    public Route(
        RouteType requestType,
        (int slotId, SlotType slotType)? slotIdInfo = null,
        (string slotKey, SlotType slotType)? slotKeyInfo = null,
        (string host, int port)? address = null)
    {
        _info = new()
        {
            Type = requestType,
            SlotId = slotIdInfo?.slotId ?? 0,
            SlotKey = slotKeyInfo?.slotKey,
            SlotType = slotIdInfo?.slotType ?? slotKeyInfo?.slotType ?? 0,
            Host = address?.host,
            Port = address?.port ?? 0,
        };
    }

    protected override void FreeMemory() => InterOpHelpers.FreeStructPtr(_ptr);

    protected override IntPtr AllocateAndCopy() => InterOpHelpers.StructToPtr(_info);
}
