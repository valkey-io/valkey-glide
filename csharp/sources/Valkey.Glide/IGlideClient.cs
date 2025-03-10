using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

public interface IGlideClient
{
    Task<InterOp.Value> CommandAsync(ERequestType requestType, params IEnumerable<string> args);
}
