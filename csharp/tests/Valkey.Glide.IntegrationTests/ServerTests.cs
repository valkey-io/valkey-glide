// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

namespace Valkey.Glide.IntegrationTests;

/// <summary>
/// Tests for <see cref="ValkeyServer" /> class.
/// </summary>
public class ServerTests
{
    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task CanGetServers(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        Assert.Equal($"{host}:{port}", conn.GetServer(host, port).EndPoint.ToString());
        Assert.Equal($"{host}:{port}", conn.GetServer($"{host}:{port}").EndPoint.ToString());
        Assert.Equal($"{host}:{port}", conn.GetServer(IPAddress.Parse(host), port).EndPoint.ToString());
        Assert.Equal($"{host}:{port}", conn.GetServer(new IPEndPoint(IPAddress.Parse(host), port)).EndPoint.ToString());

        // TODO currently this returns only primary node on standalone
        // https://github.com/valkey-io/valkey-glide/issues/4293
        Assert.Equal(isCluster ? TestConfiguration.CLUSTER_HOSTS.Count : 1, conn.GetServers().Length);
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task CanGetServerInfo(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        foreach (IServer server in conn.GetServers())
        {
            // TODO protocol isn't yet configurable
            Assert.Equal(Protocol.Resp3, server.Protocol);
            Assert.Equal(TestConfiguration.SERVER_VERSION, server.Version);
            Assert.Equal(isCluster ? ServerType.Cluster : ServerType.Standalone, server.ServerType);
            string info = (await server.InfoRawAsync("server"))!;
            foreach (string line in info.Split("\r\n"))
            {
                if (line.Contains("tcp_port:"))
                {
                    Assert.Contains(server.EndPoint.ToString()!.Split(':')[1], line);
                    break;
                }
            }

            IGrouping<string, KeyValuePair<string, string>>[] infoParsed = server.Info();
            foreach (IGrouping<string, KeyValuePair<string, string>> data in infoParsed)
            {
                if (data.Key == "Server")
                {
                    bool portFound = false;
                    foreach (KeyValuePair<string, string> pair in data)
                    {
                        if (pair.Key == "tcp_port")
                        {
                            Assert.Equal(pair.Value, server.EndPoint.ToString()!.Split(':')[1]);
                            portFound = true;
                            break;
                        }
                    }
                    Assert.True(portFound);
                    break;
                }
            }
        }
    }
}
