using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp.Routing;

/// <summary>
/// Routes the request to multiple slots.
/// This variant contains instructions for splitting a multi-slot command (e.g., MGET, MSET) into sub-commands.
/// Each tuple consists of a `Route` representing the target node for the subcommand,
/// and a vector of argument indices from the original command that should be copied to each subcommand.
/// The `MultiSlotArgPattern` specifies the pattern of the command’s arguments, indicating how they are organized
/// (e.g., only keys, key-value pairs, etc).
/// </summary>
/// <param name="responsePolicy"></param>
public sealed class MultiMultiSlot(EResponsePolicy responsePolicy, EArgPattern argPattern, (Route route, List<long> something)[] routes) : IRoutingInfo
{
    // ToDo: Add documentation for responsePolicy
    public unsafe RoutingInfo? ToNative(MarshalString marshalString, MarshalBytes marshalBytes)
    {
        RoutingInfoMultiSlotPair* routesPtr = (RoutingInfoMultiSlotPair*)marshalBytes(sizeof(RoutingInfoMultiSlotPair) * routes.Length);
        for (var i = 0; i < routes.Length; i++)
        {
            var (route, something) = routes[i];
            (routesPtr + i)->route = route;
            (routesPtr + i)->something = (long*)marshalBytes(sizeof(long) * something.Count);
            for (var j = 0; j < something.Count; j++)
            {
                (routesPtr + i)->something[j] = something[j];
            }
            (routesPtr + i)->something_length = (uint)something.Count;;
        }
        return new RoutingInfo
        {
            kind = ERoutingInfo.MultiMultiSlot,
            value = new RoutingInfoUnion
            {
                multi_slot = new RoutingInfoMultiSlot
                {
                    response_policy = responsePolicy.ToNative(),
                    arg_pattern = argPattern.ToNative(),
                    routes = routesPtr,
                    routes_length = (uint)routes.Length,
                }
            }
        };
    }
}