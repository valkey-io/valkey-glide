// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using static Valkey.Glide.Route;

namespace Valkey.Glide;

internal class ValkeyServer(DatabaseImpl conn, EndPoint endpoint) : IServer
{
    private readonly DatabaseImpl _conn = conn;

    // TODO use sync `Execute` instead of `CustomCommand`

    /// <summary>
    /// Run <c>HELLO</c> command.
    /// </summary>
    private Dictionary<GlideString, object> Hello()
        => (Dictionary<GlideString, object>)_conn.CustomCommand(["hello"]).GetAwaiter().GetResult()!;

    public EndPoint EndPoint { get; } = endpoint;

    public bool IsConnected => true;

    public Protocol Protocol => (long)Hello()["proto"] == 2 ? Protocol.Resp2 : Protocol.Resp3;

    public Version Version => new(Hello()["version"].ToString()!);

    public ServerType ServerType => Enum.Parse<ServerType>(Hello()["mode"].ToString()!, true);

    public Task<string?> InfoRawAsync(ValkeyValue section = default, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        InfoOptions.Section[] sections = section.Type == ValkeyValue.StorageType.Null ? [] :
            [Enum.Parse<InfoOptions.Section>(section.ToString(), true)];

        return _conn
            .Command(Request.Info(sections), new ByAddressRoute(EndPoint.ToString()!))
            .ContinueWith(task => (string?)task.Result);
    }

    public Task<IGrouping<string, KeyValuePair<string, string>>[]> InfoAsync(ValkeyValue section = default, CommandFlags flags = CommandFlags.None)
        => InfoRawAsync(section, flags).ContinueWith(t
            => Utils.ParseInfoResponse(t.Result!).GroupBy(x => x.Item1, x => x.Item2).ToArray());

    public string? InfoRaw(ValkeyValue section = default, CommandFlags flags = CommandFlags.None)
        => InfoRawAsync(section, flags).GetAwaiter().GetResult();

    public IGrouping<string, KeyValuePair<string, string>>[] Info(ValkeyValue section = default, CommandFlags flags = CommandFlags.None)
        => InfoAsync(section, flags).GetAwaiter().GetResult();
}
