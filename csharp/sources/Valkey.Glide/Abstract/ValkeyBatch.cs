// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;
using Valkey.Glide.Pipeline;

namespace Valkey.Glide;

internal class ValkeyBatch(BaseClient client) : Database(false), IBatch
{
    protected readonly List<ICmd> _commands = [];
    protected TaskCompletionSource<object?[]?> _tcs = new();
    protected readonly BaseClient _client = client;
    protected bool _isAtomic = false;

    internal override async Task<T> Command<R, T>(Cmd<R, T> command, Route? route = null)
    {
        int idx = _commands.Count;
        _commands.Add(command);
        // wait for the batch to be executed and then pick command's result
        object?[]? batchResult = await _tcs.Task;
#pragma warning disable IDE0046 // Convert to conditional expression
        if (batchResult is null) // transaction wasn't submitted due to WATCH or failed condition check
        {
            return await Task.FromCanceled<T>(new(true));
        }
#pragma warning restore IDE0046 // Convert to conditional expression

        return batchResult[idx] is Exception
                ? throw (batchResult[idx] as Exception)!
                : (T)batchResult[idx]!;
    }

    protected virtual bool PreExecCheck() => true;

    protected async Task ExecuteImpl()
    {
        if (_tcs.Task.Status == TaskStatus.RanToCompletion)
        {
            // a batch is already executed
            return;
        }
        if (PreExecCheck())
        {
            if (_commands.Count == 0)
            {
                _tcs.SetResult([]);
                return;
            }
            Batch b = new(_isAtomic);
            b.Commands.AddRange(_commands);

            object?[]? res = await _client.Batch(b, false);
            _tcs.SetResult(res);
        }
        else
        {
            _tcs.SetResult(null);
        }
    }

    public void Execute() => ExecuteImpl().GetAwaiter().GetResult();
}
