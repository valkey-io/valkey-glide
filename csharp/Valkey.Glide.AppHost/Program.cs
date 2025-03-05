var builder = DistributedApplication.CreateBuilder(args);

var valkey = builder.AddValkey("cache");

builder.Build()
    .Run();
