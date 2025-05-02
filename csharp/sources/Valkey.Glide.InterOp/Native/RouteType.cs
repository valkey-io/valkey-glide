namespace Valkey.Glide.InterOp.Native;

public enum RouteType : uint
{
    Random,
    AllNodes,
    AllPrimaries,
    SlotId,
    SlotType,
    ByAddress,
}
