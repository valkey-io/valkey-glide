using Scalar.AspNetCore;
using Valkey.Glide.Hosting;

namespace AspireSample.Worker;

public class Program
{
    public static void Main(string[] args)
    {
        WebApplicationBuilder? builder = WebApplication.CreateBuilder(args);
        builder.Services.AddControllers();
        builder.Services.AddOpenApi();
        builder.Services.AddValkeyGlide("valkey"); // We configure the glide client to use the "valkey" connection string here.
        builder.AddServiceDefaults();

        WebApplication? app = builder.Build();
        app.UseHttpsRedirection();
        app.UseAuthorization();
        app.MapOpenApi();
        app.MapScalarApiReference();
        app.MapDefaultEndpoints();
        app.MapControllers();
        app.Run();
    }
}
