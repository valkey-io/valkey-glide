using System.ComponentModel;
using System.Threading.Tasks;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

public interface INativeClient
{
    [EditorBrowsable(EditorBrowsableState.Advanced)]
    Task<Value> SendCommandAsync(ERequestType requestType, params string[] args);
}
