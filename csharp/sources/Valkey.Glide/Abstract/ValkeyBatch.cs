// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;
using Valkey.Glide.Pipeline;

namespace Valkey.Glide;

internal class ValkeyBatch(BaseClient client) : DatabaseImpl(false), IBatch
{
    private readonly List<ICmd> _commands = [];
    protected TaskCompletionSource<object?[]?> _tcs = new();
    private readonly BaseClient _client = client;
    protected bool _isAtomic = false;

    internal override async Task<T> Command<R, T>(Cmd<R, T> command, Route? route = null)
    {
        int idx = _commands.Count;
        _commands.Add(command);
        // wait for the batch to be executed and then pick command's result
        return await _tcs.Task.ContinueWith(task => (T)task.Result![idx]!);
        // TODO what if transactoin failed?
    }

    protected async Task ExecuteImpl()
    {
        Batch b = new(_isAtomic);
        b.Commands.AddRange(_commands);

        object?[]? res = await _client.Batch(b, true);
        _tcs.SetResult(res);
    }

    public void Execute() => ExecuteImpl().GetAwaiter().GetResult();
}
