using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Request routing configuration overrides the <see cref="ReadFromStrategy"/> connection configuration.<br />
/// If <see cref="SlotType.Replica"/> is used, the request will be routed to a replica, even if the strategy is <see cref="ReadFromStrategy.Primary"/>.
/// </summary>
/// <param name="slotId">Slot number. There are 16384 slots in a Valkey cluster, and each shard manages a slot range.
/// Unless the slot is known, it's better to route using <see cref="SlotKeyRoute"/>.</param>
/// <param name="slotType">Defines type of the node being addressed.</param>
public class SlotIdRoute(int slotId, SlotType slotType) : SingleNodeRoute
{
    public readonly int SlotId = slotId;
    public new readonly SlotType SlotType = slotType;

    internal override Native.Route ToFfi() => new(RouteType.SlotId, slotIdInfo: (SlotId, SlotType));
}