// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using static Valkey.Glide.Route;

namespace Valkey.Glide;

internal class ValkeyServer(Database conn, EndPoint endpoint) : IServer
{
    private readonly Database _conn = conn;

    /// <summary>
    /// Run <c>HELLO</c> command.
    /// </summary>
    private Dictionary<GlideString, object> Hello()
        => (Dictionary<GlideString, object>)_conn.CustomCommand(["hello"]).GetAwaiter().GetResult()!;

    public ValkeyResult Execute(string command, params object[] args)
        => ExecuteAsync(command, args).GetAwaiter().GetResult();

    public async Task<ValkeyResult> ExecuteAsync(string command, params object[] args)
        => await ExecuteAsync(command, args.ToList());

    public ValkeyResult Execute(string command, ICollection<object> args, CommandFlags flags = CommandFlags.None)
        => ExecuteAsync(command, args, flags).GetAwaiter().GetResult();

    public async Task<ValkeyResult> ExecuteAsync(string command, ICollection<object> args, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        object? res = await _conn.Command(Request.CustomCommand([command, .. args?.Select(a => a.ToString()!) ?? []]), new ByAddressRoute(EndPoint.ToString()!));
        return ValkeyResult.Create(res);
    }

    private Route MakeRoute()
    {
        (string host, ushort port) = Utils.SplitEndpoint(EndPoint);
        return new ByAddressRoute(host, port);
    }

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
            .Command(Request.Info(sections), MakeRoute())
            .ContinueWith(task => (string?)task.Result);
    }

    public Task<IGrouping<string, KeyValuePair<string, string>>[]> InfoAsync(ValkeyValue section = default, CommandFlags flags = CommandFlags.None)
        => InfoRawAsync(section, flags).ContinueWith(t
            => Utils.ParseInfoResponse(t.Result!).GroupBy(x => x.Item1, x => x.Item2).ToArray());

    public string? InfoRaw(ValkeyValue section = default, CommandFlags flags = CommandFlags.None)
        => InfoRawAsync(section, flags).GetAwaiter().GetResult();

    public IGrouping<string, KeyValuePair<string, string>>[] Info(ValkeyValue section = default, CommandFlags flags = CommandFlags.None)
        => InfoAsync(section, flags).GetAwaiter().GetResult();

    public async Task<TimeSpan> PingAsync(CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await _conn.Command(Request.Ping(), new ByAddressRoute(EndPoint.ToString()!));
    }

    public async Task<TimeSpan> PingAsync(ValkeyValue message, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await _conn.Command(Request.Ping(message), new ByAddressRoute(EndPoint.ToString()!));
    }

    public async Task<ValkeyValue> EchoAsync(ValkeyValue message, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await _conn.Command(Request.Echo(message), new ByAddressRoute(EndPoint.ToString()!));
    }
}
