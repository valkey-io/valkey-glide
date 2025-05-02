namespace Valkey.Glide.InterOp.Internals;

internal enum ValueType : uint
{
    Null = 0,
    Int = 1,
    Float = 2,
    Bool = 3,
    String = 4,
    Array = 5,
    Map = 6,
    Set = 7,
    BulkString = 8,
    OK = 9,
    Error = 10,
}