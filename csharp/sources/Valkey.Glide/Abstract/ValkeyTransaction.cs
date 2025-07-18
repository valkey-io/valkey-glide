// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

internal class ValkeyTransaction : ValkeyBatch, ITransaction
{
    public ValkeyTransaction(BaseClient client) : base(client)
    {
        _isAtomic = true;
    }

    public bool Execute(CommandFlags flags = CommandFlags.None)
        => ExecuteAsync(flags).GetAwaiter().GetResult();

    public async Task<bool> ExecuteAsync(CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        await ExecuteImpl();
        return _tcs.Task.Result is not null;
    }
}
