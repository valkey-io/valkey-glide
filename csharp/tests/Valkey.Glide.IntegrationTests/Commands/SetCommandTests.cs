using Valkey.Glide.Commands.ExtensionMethods;
using Valkey.Glide.IntegrationTests.Fixtures;

namespace Valkey.Glide.IntegrationTests.Commands;

public class SetCommandTests(ValkeyAspireFixture fixture) : IClassFixture<ValkeyAspireFixture>
{
    [Theory]
    [InlineData("foobar")]
    [InlineData("some \"quoted\" value")]
    public async Task SimpleSetAsync(string value)
    {
        // Arrange
        const string key = nameof(SetCommandTests) + "-" + nameof(SimpleSetAsync);
        using GlideClient glideClient = new GlideClient(fixture.ConnectionRequest);

        // Act
        // Assert
        await glideClient.SetAsync(key, value);
    }
}
