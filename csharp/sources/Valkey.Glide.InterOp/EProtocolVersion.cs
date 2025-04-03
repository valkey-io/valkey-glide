namespace Valkey.Glide.InterOp;

public enum EProtocolVersion
{
    /// <see href="https://github.com/redis/redis-specifications/blob/master/protocol/RESP2.md"/>
    Resp2,

    /// <see href="https://github.com/redis/redis-specifications/blob/master/protocol/RESP3.md"/>
    Resp3,
}