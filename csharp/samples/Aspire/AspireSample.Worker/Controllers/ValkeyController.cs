using Microsoft.AspNetCore.Mvc;
using Valkey.Glide;
using Valkey.Glide.Commands;
using Valkey.Glide.Commands.ExtensionMethods;
using Valkey.Glide.Data;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Routing;
using Valkey.Glide.ResponseHandlers;

namespace AspireSample.Worker.Controllers;

// ValKey.Glide offers different ways to execute commands.
// Common commands, like get and set, have special extension
// methods to allow easier usability.
// Most builders also offer a simplified "Create" method, allowing you to easily create the most common setup
// or, if you want, you can go to the maximum control of what is being requested.
// The different patterns are displayed in this controller.

[ApiController]
[Route("[controller]")]
public class ValkeyController(IGlideClient glideClient) : ControllerBase
{
    [HttpGet("get/{key}")]
    public async Task<string?> Get(
        [FromRoute] string key
    )
    {
        // Here we use the QOL extensions for basic operations
        string? client = await glideClient.GetAsync(key);
        return client;
    }

    [HttpGet("set/{key}")]
    public async Task Set(
        [FromRoute] string key,
        [FromQuery] string value
    )
    {
        // Here we use the QOL extensions for basic operations
        await glideClient.SetAsync(key, value);
    }

    [HttpGet("set/builder/{key}")]
    public async Task SetBuilder(
        [FromRoute] string key,
        [FromQuery] string value,
        [FromQuery] TimeSpan? expiresIn = null
    )
    {
        // Here we use the builder pattern for more control
        var command = SetCommand.Create(key, value);
        if (expiresIn.HasValue)
            command = command.WithExpiresIn(TimeSpan.FromSeconds(100));
        await glideClient.ExecuteAsync(command);
    }

    [HttpGet("custom/set/{key}")]
    public async Task CustomSet(
        [FromRoute] string key,
        [FromQuery] string value
    )
    {
        // Here we construct the builder ourselves, allowing for even more control
        _ = await glideClient.ExecuteAsync(
            new CustomCommand<NoRouting, ValueGlideResponseHandler, Value, CommandText, CommandText, string>
                {
                    RoutingInfo = new NoRouting()
                }
                .WithArg1(new CommandText("SET"))
                .WithArg2(new CommandText(key))
                .WithArg3(value));
    }
}
