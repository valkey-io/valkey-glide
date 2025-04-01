// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using NSubstitute;
using Valkey.Glide.Commands;
using Valkey.Glide.Data;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;
using Valkey.Glide.ResponseHandlers;
using Value = Valkey.Glide.InterOp.Value;

namespace Valkey.Glide.UnitTests.CommandSyntax;

public class CustomCommandBuilderTests
{
    private readonly GlideClient _glideClient;
    private readonly INativeClient _nativeClientSubstitute;

    public CustomCommandBuilderTests()
    {
        _nativeClientSubstitute = Substitute.For<INativeClient>();
        _nativeClientSubstitute.SendCommandAsync(Arg.Any<ERequestType>(), Arg.Any<IRoutingInfo>(), Arg.Any<string[]>())
            .ReturnsForAnyArgs(Value.CreateOkay());
        var serializerCollection = new GlideSerializerCollection();
        serializerCollection.RegisterDefaultSerializers();
        serializerCollection.Seal();
        _glideClient = new GlideClient(_nativeClientSubstitute, serializerCollection);
    }

    [Fact(DisplayName = "SET foo \"bar\"")]
    public async Task SimpleSetAsync()
    {
        // Arrange
        var command =
            CustomCommand.Create<NoRouting, ValueGlideResponseHandler, Value, CommandText, CommandText, string>(
                new NoRouting(), new CommandText("SET"), new CommandText("foo"), "bar");

        // Act
        var result = await _glideClient.ExecuteAsync(command);

        // Assert
        await _nativeClientSubstitute.Received(1).SendCommandAsync(ERequestType.CustomCommand, Arg.Any<IRoutingInfo>(),
            ["SET", "foo", "\"bar\""]);
    }

    [Fact(DisplayName = "SET foo \"bar\"")]
    public async Task ThrowsInvalidOperationIfNotAllParametersAreSet()
    {
        // Arrange
        var command =
            new CustomCommand<NoRouting, ValueGlideResponseHandler, Value, CommandText, CommandText, string>
            {
                RoutingInfo = new NoRouting()
            };
        command = command.WithArg1(""); // We only set arg1, arg2 and arg3 are not set.

        // Act
        // Assert
        await Assert.ThrowsAsync<InvalidOperationException>(async () => await _glideClient.ExecuteAsync(command));
    }
}
