using System.ComponentModel;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Route to the node that matches the [Route]
/// </summary>
/// <param name="slot"></param>
/// <param name="slotAddress"></param>
public sealed class SingleSpecificNode(ushort slot, ESlotAddress slotAddress) : IRoutingInfo
{
    // ToDo: Fill in documentation for slot and slotAddress
    public RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes) => new RoutingInfo
    {
        kind = ERoutingInfo.SingleSpecificNode,
        value = new RoutingInfoUnion
        {
            specific_node = new Slot
            {
                slot = 0,
                slot_addr = slotAddress switch
                {
                    ESlotAddress.Master => RouteSlotAddress.Master,
                    ESlotAddress.ReplicaOptional => RouteSlotAddress.ReplicaOptional,
                    ESlotAddress.ReplicaRequired => RouteSlotAddress.ReplicaRequired,
                    _ => throw new InvalidEnumArgumentException(nameof(slotAddress), (int)slotAddress,
                        typeof(ESlotAddress))
                },
            }
        },
    };
}