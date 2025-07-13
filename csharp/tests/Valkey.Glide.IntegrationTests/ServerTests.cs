// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

namespace Valkey.Glide.IntegrationTests;

/// <summary>
/// Tests for <see cref="ValkeyServer" /> class.
/// </summary>
public class ServerTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestConnections), MemberType = typeof(TestConfiguration))]
    public void CanGetServers(ConnectionMultiplexer conn, bool isCluster)
    {
        (string host, ushort port) = isCluster
            ? TestConfiguration.CLUSTER_HOSTS[0]
            : TestConfiguration.STANDALONE_HOSTS[0];

        Assert.Equal($"{host}:{port}", conn.GetServer(host, port).EndPoint.ToString());
        Assert.Equal($"{host}:{port}", conn.GetServer($"{host}:{port}").EndPoint.ToString());
        Assert.Equal($"{host}:{port}", conn.GetServer(IPAddress.Parse(host), port).EndPoint.ToString());
        Assert.Equal($"{host}:{port}", conn.GetServer(new IPEndPoint(IPAddress.Parse(host), port)).EndPoint.ToString());

        // TODO currently this returns only primary node on standalone
        // https://github.com/valkey-io/valkey-glide/issues/4293
        Assert.Equal(isCluster
            ? TestConfiguration.CLUSTER_HOSTS.Count
            : 1, conn.GetServers().Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestConnections), MemberType = typeof(TestConfiguration))]
    public async Task CanGetServerInfo(ConnectionMultiplexer conn, bool isCluster)
    {
        foreach (IServer server in conn.GetServers())
        {
            Assert.Equal(conn.RawConfig.Protocol, server.Protocol);
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
