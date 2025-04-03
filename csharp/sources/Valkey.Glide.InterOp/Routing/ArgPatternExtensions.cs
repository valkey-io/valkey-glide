using System.ComponentModel;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Routing;

namespace Valkey.Glide.InterOp.Routing;

internal static class ArgPatternExtensions
{
    public static ERoutingInfoMultiSlotArgPattern ToNative(this EArgPattern argPattern) =>
        argPattern switch
        {
            EArgPattern.KeysOnly => ERoutingInfoMultiSlotArgPattern.KeysOnly,
            EArgPattern.KeyValuePairs => ERoutingInfoMultiSlotArgPattern.KeyValuePairs,
            EArgPattern.KeysAndLastArg => ERoutingInfoMultiSlotArgPattern.KeysAndLastArg,
            EArgPattern.KeyWithTwoArgTriples => ERoutingInfoMultiSlotArgPattern.KeyWithTwoArgTriples,
            _ => throw new InvalidEnumArgumentException(nameof(argPattern), (int)argPattern,
                typeof(EArgPattern))
        };
}