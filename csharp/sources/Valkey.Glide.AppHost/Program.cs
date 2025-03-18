IDistributedApplicationBuilder? builder = DistributedApplication.CreateBuilder(args);

IResourceBuilder<ValkeyResource>? valkey = builder.AddValkey("cache");

builder.Build()
    .Run();
