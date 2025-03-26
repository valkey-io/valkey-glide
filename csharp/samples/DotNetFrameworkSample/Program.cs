using System;
using System.Threading.Tasks;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;
using ConnectionRequest = Valkey.Glide.InterOp.ConnectionRequest;

namespace DotNetFrameworkSample
{
    internal class Program
    {
        public static async Task Main(string[] args)
        {
            // With .Net Framework, we cannot use Valkey.Glide itself and have to fall back to the
            // bare-bone NativeClient from Valkey.Glide.InterOp.
            // Due to our abstractions working directly on the INativeClient, we can continue to use
            // them if we ever upgrade away from .Net Framework, as IGlideClient also implements INativeClient.
            // Thanks to that, using Glide in .Net Framework is "just" creating your own abstractions where needed.
            //
            // It is important to note that all abstractions done by the Valkey.Glide library itself are based around
            // the same, basic functionality you get. Copying hence is a valid strategy to build your own abstractions.
            // However, adding full-featured .Net Framework support is out of scope for the Valkey.Glide package family.
            var connectionRequest = new ConnectionRequest(new Node[] {new Node("localhost")});
            using (var nativeClient = new NativeClient(connectionRequest))
            {
                var guidString = Guid.NewGuid().ToString();
                var setResultValue = await nativeClient.SendCommandAsync(ERequestType.Set, new NoRouting(), "some-key", guidString);
                if (!setResultValue.IsOk())
                    throw new Exception("Failed to set value");

                var resultValue = await nativeClient.SendCommandAsync(ERequestType.Get, new NoRouting(), "some-key");
                if (!resultValue.IsString(out var value) || value != guidString)
                    throw new Exception("Failed to get value");

            }
        }
    }
}
