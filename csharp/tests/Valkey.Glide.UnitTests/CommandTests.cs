// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide.UnitTests;

public class CommandTests
{
    [Fact]
    public void ValidateCommandArgs()
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

    [Fact]
    public void ValidateCommandConverters()
    {
        Assert.Multiple([
            () => Assert.Equal(1, Request.CustomCommand([]).Converter(1)),
            () => Assert.Equal(.1, Request.CustomCommand([]).Converter(.1)),
            () => Assert.Null(Request.CustomCommand([]).Converter(null)),

            () => Assert.Equal("OK", Request.Set("a", "b").Converter("OK")),
            () => Assert.Equal<GlideString>("OK", Request.Get("a").Converter("OK")),
            () => Assert.Null(Request.Get("a").Converter(null)),
            () => Assert.Equal("info", Request.Info([]).Converter("info")),
        ]);
    }
}
