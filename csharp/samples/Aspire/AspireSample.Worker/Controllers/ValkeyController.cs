using Microsoft.AspNetCore.Mvc;
using Valkey.Glide;
using Valkey.Glide.Commands;
using Valkey.Glide.Commands.ExtensionMethods;
using Valkey.Glide.Data;
using Valkey.Glide.InterOp.Routing;

namespace AspireSample.Worker.Controllers;

[ApiController]
[Route("[controller]")]
public class ValkeyController(IGlideClient glideClient) : ControllerBase
{
    [HttpGet("get/{key}")]
    public async Task<string?> Get([FromRoute] string key)
    {
        string? client = await glideClient.GetAsync(key);
        return client;
    }

    [HttpGet("set/{key}")]
    public async Task Set([FromRoute] string key, [FromQuery] string value)
    {
        await glideClient.SetAsync(key, value);
    }

    [HttpGet("set/builder/{key}")]
    public async Task SetBuilder([FromRoute] string key, [FromQuery] string value, [FromQuery] TimeSpan? expiresIn = null)
    {
        var command = SetCommand.Create(key, value);
        if (expiresIn.HasValue)
            command = command.WithExpiresIn(TimeSpan.FromSeconds(100));
        await glideClient.ExecuteAsync(command);
    }

    [HttpGet("custom/set/{key}")]
    public async Task CustomSet([FromRoute] string key, [FromQuery] string value)
    {
        _ = await glideClient.ExecuteAsync(CustomCommand.Create(new NoRouting(), new CommandText("SET"), new CommandText(key), value));
    }
}
