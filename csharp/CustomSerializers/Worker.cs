using Valkey.Glide;
using Valkey.Glide.Commands.ExtensionMethods;

namespace CustomSerializers;

public class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly IGlideClient _glideClient;

    public Worker(ILogger<Worker> logger, IGlideClient glideClient)
    {
        _logger = logger;
        _glideClient = glideClient;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            var now = DateTime.Now;
            var ts = await _glideClient.GetAsync("timestamp");
            _logger.LogInformation("Worker running at: {Time}, last ran at: {ValkeyValue}", now, ts);
            await _glideClient.SetAsync("timestamp", now);


            await Task.Delay(1000, stoppingToken);
        }
    }
}
