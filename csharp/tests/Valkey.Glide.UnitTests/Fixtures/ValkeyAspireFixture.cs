using Aspire.Hosting;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.UnitTests.Fixtures;

public sealed class ValkeyAspireFixture : IAsyncLifetime
{
    private DistributedApplication? _distributedApplication;

    public async Task InitializeAsync()
    {
        IDistributedApplicationTestingBuilder? appHost = await DistributedApplicationTestingBuilder.CreateAsync<Projects.Valkey_Glide_AppHost>();
        _distributedApplication = await appHost.BuildAsync();
        await _distributedApplication.StartAsync();
        try
        {
            ResourceEvent? resourceEvent = await _distributedApplication.ResourceNotifications.WaitForResourceHealthyAsync(
                "cache",
                WaitBehavior.StopOnResourceUnavailable
            );
            if (resourceEvent.Snapshot.HealthStatus is not HealthStatus.Healthy)
                throw new Exception("Cache is not healthy, aspire initialization failed.");
            UrlSnapshot? url = resourceEvent.Snapshot.Urls.FirstOrDefault();
            if (url is null)
                throw new Exception("Cache has no URL, aspire initialization failed.");
            Uri? uri = new Uri(url.Url);
            Port     = (ushort) uri.Port;
            Host     = uri.Host;
            IsSecure = uri.Scheme == "https";
        }
        catch
        {
            await _distributedApplication.StopAsync();
            await _distributedApplication.DisposeAsync();
            _distributedApplication = null;
            throw;
        }
        NativeClient.Initialize(ELoggerLevel.Trace);
    }

    public InterOp.Node Node => new(Host, Port);
    public bool IsSecure { get; private set; }

    public ushort Port { get; private set; }
    public string Host { get; private set; } = string.Empty;

    public InterOp.ConnectionRequest ConnectionRequest
        => new InterOp.ConnectionRequest([Node])
        {
            TlsMode = IsSecure ? InterOp.ETlsMode.SecureTls : null,
        };

    public async Task DisposeAsync()
    {
        if (_distributedApplication is not null)
        {
            await _distributedApplication.StopAsync();
            await _distributedApplication.DisposeAsync();
            _distributedApplication = null;
        }
    }
}
