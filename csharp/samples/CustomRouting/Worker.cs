using Valkey.Glide;
using Valkey.Glide.Commands;
using Valkey.Glide.InterOp.Routing;

namespace CustomRouting;

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
            var tsResult = await _glideClient.ExecuteAsync(GetCommand.Create(new SingleRandom(), "timestamp"));
            var ts = tsResult.IsString(out var tsString) ? tsString : "<empty>";
            _logger.LogInformation("Worker running at: {Time}, random last ran at: {ValkeyValue}", now, ts);
            await _glideClient.ExecuteAsync(SetCommand.Create(new SingleRandom(), "timestamp", now));

            await Task.Delay(1000, stoppingToken);
        }
    }
}
