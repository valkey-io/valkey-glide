using Valkey.Glide.Commands;
using Valkey.Glide.UnitTests.Fixtures;

namespace Valkey.Glide.UnitTests.Commands;

public class SetCommandTests(ValkeyAspireFixture fixture) : IClassFixture<ValkeyAspireFixture>
{
    #region Simple Set

    [Fact(DisplayName = "SET test value")]
    public async Task SimpleSetWorks()
    {
        // Arrange
        using var glideClient = new GlideClient(fixture.ConnectionRequest);

        // Act
        // Assert
        await glideClient.SetAsync("test", nameof(SimpleSetWorks));
        var result = await glideClient.GetAsync("test");
        Assert.Equal(nameof(SimpleSetWorks), result);

        await glideClient.SetAsync("test", "different value");
        result = await glideClient.GetAsync("test");
        Assert.Equal("different value", result);
    }

    #endregion
}
