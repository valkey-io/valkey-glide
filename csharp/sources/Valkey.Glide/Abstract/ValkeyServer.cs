// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections.Generic;
using System.Linq;
using System.Net;

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using static Valkey.Glide.Route;

namespace Valkey.Glide;

internal class ValkeyServer(ConnectionMultiplexer conn, EndPoint endpoint) : IServer
{
    private readonly ConnectionMultiplexer _conn = conn;

    // TODO use sync `Execute` instead of `CustomCommand`

    /// <summary>
    /// Run <c>HELLO</c> command.
    /// </summary>
    private Dictionary<GlideString, object> Hello()
        => (Dictionary<GlideString, object>)_conn.GetDatabase().CustomCommand(["hello"]).GetAwaiter().GetResult()!;

    public EndPoint EndPoint { get; } = endpoint;

    public bool IsConnected => true;

    public RedisProtocol Protocol => (long)Hello()["proto"] == 2 ? RedisProtocol.Resp2 : RedisProtocol.Resp3;

    public Version Version => new(Hello()["version"].ToString()!);

    public ServerType ServerType => Enum.Parse<ServerType>(Hello()["mode"].ToString()!, true);

    public Task<string?> InfoRawAsync(ValkeyValue section = default, CommandFlags ignored = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(ignored == CommandFlags.None, "Command flags are not supported by GLIDE");
        InfoOptions.Section[] sections = section.Type == ValkeyValue.StorageType.Null ? [] :
            [Enum.Parse<InfoOptions.Section>(section.ToString(), true)];

        return (_conn.GetDatabase() as DatabaseImpl)
            .Command(Request.Info(sections), new ByAddressRoute(endpoint.ToString()))
            .ContinueWith(task => (string?)task.Result);
    }

    public Task<IGrouping<string, KeyValuePair<string, string>>[]> InfoAsync(ValkeyValue section = default, CommandFlags ignored = CommandFlags.None)
    {
        return InfoRawAsync(section, ignored).ContinueWith(t =>
        {
            string category = "miscellaneous";
            var list = new List<Tuple<string, KeyValuePair<string, string>>>();
            using var reader = new StringReader(t.Result);
            while (reader.ReadLine() is string line)
            {
                if (string.IsNullOrWhiteSpace(line)) continue;
                if (line.StartsWith("# "))
                {
                    category = line.Substring(2).Trim();
                    continue;
                }
                int idx = line.IndexOf(':');
                if (idx < 0) continue;
                var pair = new KeyValuePair<string, string>(
                    line.Substring(0, idx).Trim(),
                    line.Substring(idx + 1).Trim());
                list.Add(Tuple.Create(category, pair));
            }
            return list.GroupBy(x => x.Item1, x => x.Item2).ToArray();
        });
    }

    public string? InfoRaw(ValkeyValue section = default, CommandFlags ignored = CommandFlags.None)
        => InfoRawAsync(section, ignored).GetAwaiter().GetResult();

    public IGrouping<string, KeyValuePair<string, string>>[] Info(ValkeyValue section = default, CommandFlags ignored = CommandFlags.None)
        => InfoAsync(section, ignored).GetAwaiter().GetResult();
}
