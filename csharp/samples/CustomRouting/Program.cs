using CustomRouting;
using Valkey.Glide.Hosting;

var builder = Host.CreateApplicationBuilder(args);
builder.Services.AddHostedService<Worker>();
builder.Services.AddValkeyGlide("valkey");

var host = builder.Build();
host.Run();
