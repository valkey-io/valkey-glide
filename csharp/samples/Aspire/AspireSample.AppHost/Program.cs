var builder = DistributedApplication.CreateBuilder(args);


var valkey = builder.AddValkey("valkey");

builder.AddProject<Projects.AspireSample_Worker>("worker")
    .WithReference(valkey)
    .WaitFor(valkey);


builder.Build()
    .Run();
