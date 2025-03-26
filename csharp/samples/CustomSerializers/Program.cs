using System.Security.Principal;
using CustomSerializers;
using Valkey.Glide.Hosting;

var builder = Host.CreateApplicationBuilder(args);
builder.Services.AddHostedService<Worker>();
builder.Services.AddValkeyGlide("valkey");
builder.Services.ConfigureValkeyGlideTransformers(config => config.RegisterSerializer(new DateTimeGlideSerializer()));

var host = builder.Build();
host.AddGlideCoreLogging();
host.Run();
