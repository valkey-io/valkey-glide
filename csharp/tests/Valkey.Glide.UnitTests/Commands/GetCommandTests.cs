using Valkey.Glide.Commands;
using Valkey.Glide.UnitTests.Fixtures;

namespace Valkey.Glide.UnitTests.Commands;

public class GetCommandTests(ValkeyAspireFixture fixture) : IClassFixture<ValkeyAspireFixture>
{
    [Fact(DisplayName = "GET does-not-exist")]
    public async Task NonExistingKeyReturnsNull()
    {
        // Arrange
        using var glideClient = new GlideClient(fixture.ConnectionRequest);

        // Act
        var result = await glideClient.GetAsync("does-not-exist");

        // Assert
        Assert.Null(result);
    }

    [Fact(DisplayName = "GET get-key")]
    public async Task ExistingKeyReturnsValue()
    {
        // Arrange
        using var glideClient = new GlideClient(fixture.ConnectionRequest);
        await glideClient.SetAsync("get-key", nameof(ExistingKeyReturnsValue));

        // Act
        var result = await glideClient.GetAsync("get-key");

        // Assert
        Assert.Equal(nameof(ExistingKeyReturnsValue), result);
    }
}
