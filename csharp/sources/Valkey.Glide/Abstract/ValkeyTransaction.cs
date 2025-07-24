// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

internal class ValkeyTransaction : ValkeyBatch, ITransaction
{
    private readonly List<ConditionResult> _conditions = [];

    public ValkeyTransaction(BaseClient client) : base(client)
    {
        _isAtomic = true;
    }

    public ConditionResult AddCondition(Condition condition)
    {

        ConditionResult res = new(condition);
        _conditions.Add(res);
        return res;
    }

    public bool Execute(CommandFlags flags = CommandFlags.None)
        => ExecuteAsync(flags).GetAwaiter().GetResult();

    public async Task<bool> ExecuteAsync(CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        await ExecuteImpl();
        return _tcs.Task.Result is not null;
    }

    protected override bool PreExecCheck()
    {
        bool allConditionsPassed = true;
        foreach (ConditionResult condition in _conditions)
        {
            // We can't access internals of batch, but we can create a transaction, "downcast" it to batch and patch it
            ValkeyTransaction b = new(_client)
            {
                _isAtomic = false
            };
            b._commands.AddRange(condition.Condition.CreateCommands());
            b.ExecuteImpl().GetAwaiter().GetResult();
            condition.WasSatisfied = condition.Condition.Validate(b._tcs.Task.Result);
            if (!condition.WasSatisfied)
            {
                allConditionsPassed = false;
            }
        }
        return allConditionsPassed;
    }
}
