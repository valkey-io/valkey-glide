namespace Valkey.Glide.InterOp;

public enum EReadFromKind
{
    Primary       = 0,
    PreferReplica = 1,

    // Define using ReadFrom.value
    AzAffinity = 2,

    // Define using ReadFrom.value
    AzAffinityReplicasAndPrimary = 3,
}