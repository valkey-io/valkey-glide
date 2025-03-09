using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.UnitTests.Fixtures;

namespace Valkey.Glide.UnitTests;

public class NativeClientTests(ValkeyAspireFixture fixture) : IClassFixture<ValkeyAspireFixture>
{
    [Fact]
    public void CanCreateClient()
    {
        // Arrange
        // Act
        using var nativeClient = new NativeClient([fixture.Node], fixture.IsSecure);
        // Assert
        Assert.NotNull(nativeClient);
    }

    [Fact]
    public async Task CanSendGetCommandAsync()
    {
        using var nativeClient = new NativeClient([fixture.Node], fixture.IsSecure);
        var result = await nativeClient.SendCommandAsync(ERequestType.Get, "test");
        Assert.Equivalent(InterOp.EValueKind.None, result.Kind);
    }

    [Fact]
    public void CanSendGetCommandBlocking()
    {
        using var nativeClient = new NativeClient([fixture.Node], fixture.IsSecure);
        var result = nativeClient.SendCommand(ERequestType.Get, "test");
        Assert.Equivalent(InterOp.EValueKind.None, result.Kind);
    }
}
