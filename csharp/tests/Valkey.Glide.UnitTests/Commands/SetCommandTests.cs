using System.Reflection;
using NSubstitute;
using NSubstitute.Core;

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
        IGlideClient? client = Substitute.For<IGlideClient>();
        client.CommandAsync(Arg.Any<ERequestType>(), Arg.Any<string[]>())
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
                Type[]? types = args.Select(e => e.GetType())
                    .Prepend(typeof(IGlideClient))
                    .ToArray();
                MethodInfo? methodInfo = typeof(SetCommands).GetMethod(method, types);
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
        ICall? call = Assert.Single(client.ReceivedCalls());
        Assert.Equal(ERequestType.Set, call.GetArguments()[0]);
        IEnumerable<string>? parameters = (IEnumerable<string>) call.GetArguments()[1]!;
        string? input = string.Join(" ", parameters);
        Assert.Equal(command, input);
    }

    #endregion
}
