namespace Valkey.Glide.InterOp.Native.Routing;

public enum ERoutingInfoMultiSlotArgPattern
{
    /// Pattern where only keys are provided in the command.
    /// For example: `MGET key1 key2`
    KeysOnly,

    /// Pattern where each key is followed by a corresponding value.
    /// For example: `MSET key1 value1 key2 value2`
    KeyValuePairs,

    /// Pattern where a list of keys is followed by a shared parameter.
    /// For example: `JSON.MGET key1 key2 key3 path`
    KeysAndLastArg,

    /// Pattern where each key is followed by two associated arguments, forming key-argument-argument triples.
    /// For example: `JSON.MSET key1 path1 value1 key2 path2 value2`
    KeyWithTwoArgTriples,
}