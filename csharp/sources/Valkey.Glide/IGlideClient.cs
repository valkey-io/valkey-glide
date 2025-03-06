using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

public interface IGlideClient
{
    Task<string?> CommandAsync(ERequestType requestType, params string[] args);
}