// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.Abstract;

internal class ValkeyServer(ConnectionMultiplexer conn, EndPoint endpoint) : IServer
{
    private readonly ConnectionMultiplexer _conn = conn;

    public EndPoint EndPoint { get; } = endpoint;

    public bool IsConnected => true;

    // TODO use sync `Execute` instead of `CustomCommand`

    public RedisProtocol Protocol
    {
        get
        {
            object info = _conn.GetDatabase().CustomCommand(["hello"]).GetAwaiter().GetResult()!;
            return (long)((Dictionary<GlideString, object>)info)["proto"] == 2
                ? RedisProtocol.Resp2 : RedisProtocol.Resp3;
        }
    }

    public Version Version
    {
        get
        {
            object info = _conn.GetDatabase().CustomCommand(["hello"]).GetAwaiter().GetResult()!;
            return new Version(((Dictionary<GlideString, object>)info)["version"].ToString()!);
        }
    }

    public Task<string?> InfoRawAsync(ValkeyValue section = default, CommandFlags ignored = CommandFlags.None)
    {
        InfoOptions.Section[] sections = section.Type == ValkeyValue.StorageType.Null ? [] :
            [Enum.Parse<InfoOptions.Section>(section.ToString(), true)];
        return _conn.GetDatabase().Info(sections).ContinueWith(task => (string?)task.Result);
    }
}
