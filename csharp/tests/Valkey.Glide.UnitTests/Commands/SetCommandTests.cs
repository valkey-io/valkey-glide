using System.Reflection;
using NSubstitute;
using Valkey.Glide.Commands;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.UnitTests.Fixtures;

namespace Valkey.Glide.UnitTests.Commands;

public class SetCommandTests(ValkeyAspireFixture fixture) : IClassFixture<ValkeyAspireFixture>
{
    #region Syntax Validation

    // @formatter:max_line_length 20000

    [Theory(DisplayName = "Check syntax")]
    [InlineData(nameof(SetCommands.SetAndGetIfEqualAsync), new[] { "foobar", "barfoo", "raboof" }, "foobar \"barfoo\" IFEQ \"raboof\" GET")]
    [InlineData(nameof(SetCommands.SetAndGetIfEqualWithKeepTtlAsync), new[] { "foobar", "barfoo", "raboof" }, "foobar \"barfoo\" IFEQ \"raboof\" GET KEEPTTL")]
    [InlineData(nameof(SetCommands.SetAndGetIfExistsAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" XX GET")]
    [InlineData(nameof(SetCommands.SetAndGetIfExistsWithKeepTtlAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" XX GET KEEPTTL")]
    [InlineData(nameof(SetCommands.SetAndGetIfNotExistsAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" NX GET")]
    [InlineData(nameof(SetCommands.SetAndGetIfNotExistsWithKeepTtlAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" NX GET KEEPTTL")]
    [InlineData(nameof(SetCommands.SetAndGetWithKeepTtlAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" GET KEEPTTL")]
    [InlineData(nameof(SetCommands.SetAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\"")]
    [InlineData(nameof(SetCommands.SetIfEqualAsync), new[] { "foobar", "barfoo", "raboof" }, "foobar \"barfoo\" IFEQ \"raboof\"")]
    [InlineData(nameof(SetCommands.SetIfEqualWithKeepTtlAsync), new[] { "foobar", "barfoo", "raboof" }, "foobar \"barfoo\" IFEQ \"raboof\" KEEPTTL")]
    [InlineData(nameof(SetCommands.SetIfExistsAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" XX")]
    [InlineData(nameof(SetCommands.SetIfExistsWithKeepTtlAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" XX KEEPTTL")]
    [InlineData(nameof(SetCommands.SetIfNotExistAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" NX")]
    [InlineData(nameof(SetCommands.SetIfNotExistsWithKeepTtlAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" NX KEEPTTL")]
    [InlineData(nameof(SetCommands.SetWithKeepTtlAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" KEEPTTL")]
    [InlineData(nameof(SetCommands.SetAndGetAsync), new[] { "foobar", "barfoo" }, "foobar \"barfoo\" GET")]
    [InlineData(nameof(SetCommands.SetAndGetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" IFEQ \"raboof\" GET EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetAndGetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" XX GET EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetAndGetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" NX GET EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetAndGetWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" GET EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" IFEQ \"raboof\" EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" XX EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" NX EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.000" }, "foobar \"barfoo\" EXAT 1741660200")]
    [InlineData(nameof(SetCommands.SetAndGetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" IFEQ \"raboof\" GET PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetAndGetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" XX GET PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetAndGetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" NX GET PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetAndGetWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" GET PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" IFEQ \"raboof\" PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" XX PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" NX PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetWithExpirationAsync), new[] { "foobar", "barfoo", "DT:2025-03-11T02:30:00.001" }, "foobar \"barfoo\" PXAT 1741660200001")]
    [InlineData(nameof(SetCommands.SetAndGetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "TS:00:20:34" }, "foobar \"barfoo\" IFEQ \"raboof\" GET EX 1234")]
    [InlineData(nameof(SetCommands.SetAndGetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:20:34" }, "foobar \"barfoo\" XX GET EX 1234")]
    [InlineData(nameof(SetCommands.SetAndGetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:20:34" }, "foobar \"barfoo\" NX GET EX 1234")]
    [InlineData(nameof(SetCommands.SetAndGetWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:20:34" }, "foobar \"barfoo\" GET EX 1234")]
    [InlineData(nameof(SetCommands.SetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "TS:00:20:34" }, "foobar \"barfoo\" IFEQ \"raboof\" EX 1234")]
    [InlineData(nameof(SetCommands.SetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:20:34" }, "foobar \"barfoo\" XX EX 1234")]
    [InlineData(nameof(SetCommands.SetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:20:34" }, "foobar \"barfoo\" NX EX 1234")]
    [InlineData(nameof(SetCommands.SetWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:20:34" }, "foobar \"barfoo\" EX 1234")]
    [InlineData(nameof(SetCommands.SetAndGetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "TS:00:00:04.3210000" }, "foobar \"barfoo\" IFEQ \"raboof\" GET PX 4321")]
    [InlineData(nameof(SetCommands.SetAndGetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:00:04.3210000" }, "foobar \"barfoo\" XX GET PX 4321")]
    [InlineData(nameof(SetCommands.SetAndGetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:00:04.3210000" }, "foobar \"barfoo\" NX GET PX 4321")]
    [InlineData(nameof(SetCommands.SetAndGetWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:00:04.3210000" }, "foobar \"barfoo\" GET PX 4321")]
    [InlineData(nameof(SetCommands.SetIfEqualWithExpirationAsync), new[] { "foobar", "barfoo", "raboof", "TS:00:00:04.3210000" }, "foobar \"barfoo\" IFEQ \"raboof\" PX 4321")]
    [InlineData(nameof(SetCommands.SetIfExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:00:04.3210000" }, "foobar \"barfoo\" XX PX 4321")]
    [InlineData(nameof(SetCommands.SetIfNotExistsWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:00:04.3210000" }, "foobar \"barfoo\" NX PX 4321")]
    [InlineData(nameof(SetCommands.SetWithExpirationAsync), new[] { "foobar", "barfoo", "TS:00:00:04.3210000" }, "foobar \"barfoo\" PX 4321")]

    // @formatter:max_line_length restore
    public async Task CheckSetCommandSyntaxAsync(string method, object[] args, string command)
    {
        // Arrange
        var client = Substitute.For<IGlideClient>();
        client.CommandAsync(Arg.Any<ERequestType>(), Arg.Any<IEnumerable<string>>())
            .Returns(Task.FromResult(InterOp.Value.CreateNone()));

        // Act
        await Helpers.IgnoreExceptionAsync(
            async () =>
            {
                args = args.Select(
                        e => e switch
                        {
                            string s => s.StartsWith("DT:")
                                ? DateTime.Parse(s.Substring(3))
                                : s.StartsWith("TS:")
                                    ? TimeSpan.Parse(s.Substring(3))
                                    : s,
                            _ => e
                        }
                    )
                    .ToArray();
                var types = args.Select(e => e.GetType())
                    .Prepend(typeof(IGlideClient))
                    .ToArray();
                var methodInfo = typeof(SetCommands).GetMethod(method, types);
                Assert.NotNull(methodInfo);
                switch (methodInfo.Invoke(null, [client, ..args]))
                {
                    case Task<string> t:
                        await t;
                        break;
                    case Task t:
                        await t;
                        break;
                    default:
                        Assert.Fail("Invalid return type");
                        break;
                }
            }
        );

        // Assert
        var call = Assert.Single(client.ReceivedCalls());
        Assert.Equal(ERequestType.Set, call.GetArguments()[0]);
        var parameters = (IEnumerable<string>) call.GetArguments()[1]!;
        var input = string.Join(" ", parameters);
        Assert.Equal(command, input);
    }

    #endregion


    [Fact]
    public async Task TestSetAndGetAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetAsync);

        // Act
        var resultA = await client.SetAndGetAsync(key, "foobar");
        var resultB = await client.SetAndGetAsync(key, "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetAndGetIfEqualAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfEqualAsync);

        // Act
        await client.SetAsync(key, "raboof");
        var resultA = await client.SetAndGetIfEqualAsync(key, "foobar", "something-else");
        var resultB = await client.SetAndGetIfEqualAsync(key, "foobar", "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Equal("raboof", resultB);
    }

    [Fact]
    public async Task TestSetAndGetIfEqualWithExpirationExAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfEqualWithExpirationExAsync);

        // Act
        await client.SetAsync(key, "raboof");
        var resultA = await client.SetAndGetIfEqualWithExpirationAsync(
            key,
            "foobar",
            "something-else",
            TimeSpan.FromSeconds(1234)
        );
        var resultB = await client.SetAndGetIfEqualWithExpirationAsync(
            key,
            "foobar",
            "raboof",
            TimeSpan.FromSeconds(1234)
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("raboof", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfEqualWithExpirationPxAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfEqualWithExpirationPxAsync);

        // Act
        await client.SetAsync(key, "raboof");
        var resultA = await client.SetAndGetIfEqualWithExpirationAsync(
            key,
            "foobar",
            "something-else",
            TimeSpan.FromMilliseconds(1234)
        );
        var resultB = await client.SetAndGetIfEqualWithExpirationAsync(
            key,
            "foobar",
            "raboof",
            TimeSpan.FromMilliseconds(1234)
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("raboof", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfEqualWithExpirationExatAsync()
    {
        // Arrange
        var now = DateTime.UtcNow;
        var ttl = now + TimeSpan.FromSeconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfEqualWithExpirationExatAsync);

        // Act
        await client.SetAsync(key, "raboof");
        var resultA = await client.SetAndGetIfEqualWithExpirationAsync(key, "foobar", "something-else", ttl);
        var resultB = await client.SetAndGetIfEqualWithExpirationAsync(key, "foobar", "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Equal("raboof", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfEqualWithExpirationPxatAsync()
    {
        // Arrange
        var now = DateTime.UtcNow;
        var ttl = now + TimeSpan.FromMilliseconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfEqualWithExpirationPxatAsync);

        // Act
        await client.SetAsync(key, "raboof");
        var resultA = await client.SetAndGetIfEqualWithExpirationAsync(key, "foobar", "something-else", ttl);
        var resultB = await client.SetAndGetIfEqualWithExpirationAsync(key, "foobar", "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Equal("raboof", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfEqualWithKeepTtlAsync()
    {
        // Arrange
        var now = DateTime.UtcNow;
        var ttl = now + TimeSpan.FromMinutes(10);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfEqualWithKeepTtlAsync);

        // Act
        await client.SetWithExpirationAsync(key, "raboof", ttl);
        var resultA = await client.SetAndGetIfEqualWithKeepTtlAsync(key, "foobar", "something-else");
        var resultB = await client.SetAndGetIfEqualWithKeepTtlAsync(key, "foobar", "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfExistsAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfExistsAsync);

        // Act
        var resultA = await client.SetAndGetIfExistsAsync(key, "foobar");
        await client.SetAsync(key, "barfoo");
        var resultB = await client.SetAndGetIfExistsAsync(key, "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Equal("barfoo", resultB);
    }

    [Fact]
    public async Task TestSetAndGetIfExistsWithExpirationExAsync()
    {
        // Arrange
        var ttl = TimeSpan.FromSeconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfExistsWithExpirationExAsync);

        // Act
        var resultA = await client.SetAndGetIfExistsWithExpirationAsync(key, "foobar", ttl);
        await client.SetAsync(key, "barfoo");
        var resultB = await client.SetAndGetIfExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Equal("barfoo", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfExistsWithExpirationPxAsync()
    {
        // Arrange
        var ttl = TimeSpan.FromMilliseconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfExistsWithExpirationPxAsync);

        // Act
        var resultA = await client.SetAndGetIfExistsWithExpirationAsync(key, "foobar", ttl);
        await client.SetAsync(key, "barfoo");
        var resultB = await client.SetAndGetIfExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Equal("barfoo", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfExistsWithExpirationExatAsync()
    {
        // Arrange
        var ttl = DateTime.Now + TimeSpan.FromSeconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfExistsWithExpirationExatAsync);

        // Act
        var resultA = await client.SetAndGetIfExistsWithExpirationAsync(key, "foobar", ttl);
        await client.SetAsync(key, "barfoo");
        var resultB = await client.SetAndGetIfExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Equal("barfoo", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfExistsWithExpirationPxatAsync()
    {
        // Arrange
        var ttl = DateTime.Now + TimeSpan.FromMilliseconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfExistsWithExpirationPxatAsync);

        // Act
        var resultA = await client.SetAndGetIfExistsWithExpirationAsync(key, "foobar", ttl);
        await client.SetAsync(key, "barfoo");
        var resultB = await client.SetAndGetIfExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Equal("barfoo", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfExistsWithKeepTtlAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfExistsWithKeepTtlAsync);

        // Act
        var resultA = await client.SetAndGetIfExistsWithKeepTtlAsync(key, "foobar");
        await client.SetWithExpirationAsync(key, "barfoo", TimeSpan.FromMinutes(10));
        var resultB = await client.SetAndGetIfExistsWithKeepTtlAsync(key, "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Equal("barfoo", resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfNotExistsAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfNotExistsAsync);

        // Act
        var resultA = await client.SetAndGetIfNotExistsAsync(key, "foobar");
        var resultB = await client.SetAndGetIfNotExistsAsync(key, "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Null(resultB);
    }

    [Fact]
    public async Task TestSetAndGetIfNotExistsWithExpirationExAsync()
    {
        // Arrange
        var ttl = TimeSpan.FromSeconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfNotExistsWithExpirationExAsync);

        // Act
        var resultA = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "foobar", ttl);
        var resultB = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Null(resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfNotExistsWithExpirationPxAsync()
    {
        // Arrange
        var ttl = TimeSpan.FromMilliseconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfNotExistsWithExpirationPxAsync);

        // Act
        var resultA = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "foobar", ttl);
        var resultB = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Null(resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfNotExistsWithExpirationExatAsync()
    {
        // Arrange
        var ttl = DateTime.Now + TimeSpan.FromSeconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfNotExistsWithExpirationExatAsync);

        // Act
        var resultA = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "foobar", ttl);
        var resultB = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Null(resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfNotExistsWithExpirationPxatAsync()
    {
        // Arrange
        var ttl = DateTime.Now + TimeSpan.FromMilliseconds(1234);
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfNotExistsWithExpirationPxatAsync);

        // Act
        var resultA = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "foobar", ttl);
        var resultB = await client.SetAndGetIfNotExistsWithExpirationAsync(key, "raboof", ttl);

        // Assert
        Assert.Null(resultA);
        Assert.Null(resultB);
        // ToDo: Create a test for the TTL
    }

    [Fact]
    public async Task TestSetAndGetIfNotExistsWithKeepTtlAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);
        const string key = nameof(SetCommandTests) + "-" + nameof(TestSetAndGetIfNotExistsWithKeepTtlAsync);

        // Act
        var resultA = await client.SetAndGetIfNotExistsWithKeepTtlAsync(key, "foobar");
        var resultB = await client.SetAndGetIfNotExistsWithKeepTtlAsync(key, "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Null(resultB);
        // ToDo: Create a test for the TTL
    }

    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////

    [Fact]
    public async Task TestSetAndGetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetAndGetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetAndGetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetAndGetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetAndGetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetAndGetWithKeepTtlAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetAndGetWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithKeepTtlAsync),
            "foobar"
        );
        var resultB = await client.SetAndGetWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetAndGetWithKeepTtlAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetAsync(nameof(SetCommandTests) + "-" + nameof(TestSetAsync), "foobar");
        var resultB = await client.SetAsync(nameof(SetCommandTests) + "-" + nameof(TestSetAsync), "raboof");

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfEqualAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfEqualAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualAsync),
            "foobar"
        );
        var resultB = await client.SetIfEqualAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfEqualWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfEqualWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfEqualWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfEqualWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfEqualWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfEqualWithKeepTtlAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfEqualWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithKeepTtlAsync),
            "foobar"
        );
        var resultB = await client.SetIfEqualWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfEqualWithKeepTtlAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfExistsAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfExistsAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsAsync),
            "foobar"
        );
        var resultB = await client.SetIfExistsAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfExistsWithKeepTtlAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfExistsWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithKeepTtlAsync),
            "foobar"
        );
        var resultB = await client.SetIfExistsWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfExistsWithKeepTtlAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfNotExistAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfNotExistAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistAsync),
            "foobar"
        );
        var resultB = await client.SetIfNotExistAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfNotExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfNotExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfNotExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfNotExistsWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetIfNotExistsWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetIfNotExistsWithKeepTtlAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetIfNotExistsWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithKeepTtlAsync),
            "foobar"
        );
        var resultB = await client.SetIfNotExistsWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetIfNotExistsWithKeepTtlAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetWithExpirationAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "foobar"
        );
        var resultB = await client.SetWithExpirationAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithExpirationAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }

    [Fact]
    public async Task TestSetWithKeepTtlAsync()
    {
        // Arrange
        using var client = new GlideClient(fixture.ConnectionRequest);

        // Act
        var resultA = await client.SetWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithKeepTtlAsync),
            "foobar"
        );
        var resultB = await client.SetWithKeepTtlAsync(
            nameof(SetCommandTests) + "-" + nameof(TestSetWithKeepTtlAsync),
            "raboof"
        );

        // Assert
        Assert.Null(resultA);
        Assert.Equal("foobar", resultB);
    }
}
