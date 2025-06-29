// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide.UnitTests;

public class CommandArgsTests
{
    [Fact]
    public async Task ValidateCommandArgs()
    {
        Assert.Multiple([
            () => Assert.Equal(["get", "a"], Request.CustomCommand(["get", "a"]).GetArgs()),
            () => Assert.Equal(["ping", "pong", "pang"], Request.CustomCommand(["ping", "pong", "pang"]).GetArgs()),
            () => Assert.Equal(["get"], Request.CustomCommand(["get"]).GetArgs()),
            () => Assert.Equal([], Request.CustomCommand([]).GetArgs()),

            () => Assert.Equal(["SET", "a", "b"], Request.Set("a", "b").GetArgs()),
            () => Assert.Equal(["GET", "a"], Request.Get("a").GetArgs()),
            () => Assert.Equal(["INFO"], Request.Info([]).GetArgs()),
            () => Assert.Equal(["INFO", "CLIENTS", "CPU"], Request.Info([InfoOptions.Section.CLIENTS, InfoOptions.Section.CPU]).GetArgs()),
        ]);
    }
}
