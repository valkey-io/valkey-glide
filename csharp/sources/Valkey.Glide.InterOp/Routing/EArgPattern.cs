namespace Valkey.Glide.InterOp.Routing;

public enum EArgPattern
{
    /// <summary>
    /// Pattern where only keys are provided in the command.
    /// For example: `MGET key1 key2`
    /// </summary>
    KeysOnly,

    /// <summary>
    /// Pattern where each key is followed by a corresponding value.
    /// For example: `MSET key1 value1 key2 value2`
    /// </summary>
    KeyValuePairs,

    /// <summary>
    /// Pattern where a list of keys is followed by a shared parameter.
    /// For example: `JSON.MGET key1 key2 key3 path`
    /// </summary>
    KeysAndLastArg,

    /// <summary>
    /// Pattern where each key is followed by two associated arguments, forming key-argument-argument triples.
    /// For example: `JSON.MSET key1 path1 value1 key2 path2 value2`
    /// </summary>
    KeyWithTwoArgTriples,
}