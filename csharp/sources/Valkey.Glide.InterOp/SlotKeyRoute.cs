using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Request routing configuration overrides the <see cref="ReadFromStrategy"/> connection configuration.<br />
/// If <see cref="SlotType.Replica"/> is used, the request will be routed to a replica, even if the strategy is <see cref="ReadFromStrategy.Primary"/>.
/// </summary>
/// <param name="slotKey">The request will be sent to nodes managing this key.</param>
/// <param name="slotType">Defines type of the node being addressed.</param>
public class SlotKeyRoute(string slotKey, SlotType slotType) : SingleNodeRoute
{
    public readonly string SlotKey = slotKey;
    public new readonly SlotType SlotType = slotType;

    internal override Native.Route ToFfi() => new(RouteType.SlotId, slotKeyInfo: (SlotKey, SlotType));
}