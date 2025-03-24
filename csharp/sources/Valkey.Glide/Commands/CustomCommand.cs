using Valkey.Glide.Commands.Abstraction;
using Valkey.Glide.InterOp.Native;
using Value = Valkey.Glide.InterOp.Value;

namespace Valkey.Glide.Commands;

public readonly struct CustomCommand<T1> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1));
    }
}
public readonly struct CustomCommand<T1, T2> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2));
    }
}
public readonly struct CustomCommand<T1, T2, T3> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }
    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13), client.Transform(Arg14));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }
    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }
    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13), client.Transform(Arg14), client.Transform(Arg15));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }
    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }
    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }
    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13), client.Transform(Arg14), client.Transform(Arg15), client.Transform(Arg16));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }
    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }
    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }
    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }
    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13), client.Transform(Arg14), client.Transform(Arg15), client.Transform(Arg16), client.Transform(Arg17));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }
    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }
    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }
    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }
    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }
    private T18? Arg18 { get; init; }
    private bool Arg18Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg18(T18 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg18 = arg, Arg18Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        if (!Arg18Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 17));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13), client.Transform(Arg14), client.Transform(Arg15), client.Transform(Arg16), client.Transform(Arg17), client.Transform(Arg18));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }
    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }
    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }
    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }
    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }
    private T18? Arg18 { get; init; }
    private bool Arg18Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg18(T18 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg18 = arg, Arg18Set = true };
    }
    private T19? Arg19 { get; init; }
    private bool Arg19Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg19(T19 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg19 = arg, Arg19Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        if (!Arg18Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 17));
        if (!Arg19Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 18));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13), client.Transform(Arg14), client.Transform(Arg15), client.Transform(Arg16), client.Transform(Arg17), client.Transform(Arg18), client.Transform(Arg19));
    }
}
public readonly struct CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> : IGlideCommand
{
    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }
    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }
    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }
    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }
    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }
    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }
    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }
    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }
    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }
    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }
    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }
    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }
    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }
    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }
    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }
    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }
    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }
    private T18? Arg18 { get; init; }
    private bool Arg18Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg18(T18 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg18 = arg, Arg18Set = true };
    }
    private T19? Arg19 { get; init; }
    private bool Arg19Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg19(T19 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg19 = arg, Arg19Set = true };
    }
    private T20? Arg20 { get; init; }
    private bool Arg20Set { get; init; }
    public CustomCommand<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg20(T20 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg20 = arg, Arg20Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        if (!Arg18Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 17));
        if (!Arg19Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 18));
        if (!Arg20Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 19));
        return client.CommandAsync(ERequestType.CustomCommand, client.Transform(Arg1), client.Transform(Arg2), client.Transform(Arg3), client.Transform(Arg4), client.Transform(Arg5), client.Transform(Arg6), client.Transform(Arg7), client.Transform(Arg8), client.Transform(Arg9), client.Transform(Arg10), client.Transform(Arg11), client.Transform(Arg12), client.Transform(Arg13), client.Transform(Arg14), client.Transform(Arg15), client.Transform(Arg16), client.Transform(Arg17), client.Transform(Arg18), client.Transform(Arg19), client.Transform(Arg20));
    }
}
