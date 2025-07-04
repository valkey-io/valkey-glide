// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class ConnectionManagementCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestPing_NoMessage(BaseClient client)
    {
        TimeSpan result = await client.PingAsync();
        Assert.True(result >= TimeSpan.Zero);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestPing_WithMessage(BaseClient client)
    {
        ValkeyValue message = "Hello, Valkey!";
        TimeSpan result = await client.PingAsync(message);
        Assert.True(result >= TimeSpan.Zero);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestEcho_SimpleMessage(BaseClient client)
    {
        ValkeyValue message = "Hello, Valkey!";
        ValkeyValue result = await client.EchoAsync(message);
        Assert.Equal(message, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestEcho_BinaryData(BaseClient client)
    {
        byte[] binaryData = [0x00, 0x01, 0x02, 0xFF, 0xFE];
        ValkeyValue result = await client.EchoAsync(binaryData);
        Assert.Equal(binaryData, (byte[]?)result);
    }
}
