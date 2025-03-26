using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;

namespace Valkey.Glide;

public interface IGlideClient : InterOp.INativeClient
{
    string ToParameter<T>(T value);
}
