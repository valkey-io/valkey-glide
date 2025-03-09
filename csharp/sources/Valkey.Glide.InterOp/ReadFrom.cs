namespace Valkey.Glide.InterOp;

public struct ReadFrom
{
    public EReadFromKind Kind {get;set;}
    public string?       Value {get;set;}


    public static ReadFrom Primary() => new() { Kind       = EReadFromKind.Primary, Value       = null };
    public static ReadFrom PreferReplica() => new() { Kind = EReadFromKind.PreferReplica, Value = null };

    public static ReadFrom AzAffinity(string value) => new() { Kind = EReadFromKind.AzAffinity, Value = value };

    public static ReadFrom AzAffinityReplicasAndPrimary(string value)
        => new() { Kind = EReadFromKind.AzAffinityReplicasAndPrimary, Value = value };
}