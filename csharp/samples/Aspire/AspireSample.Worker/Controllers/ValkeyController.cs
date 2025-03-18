using Microsoft.AspNetCore.Mvc;
using Valkey.Glide;
using Valkey.Glide.Commands;
using Valkey.Glide.InterOp.Native;

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
}
