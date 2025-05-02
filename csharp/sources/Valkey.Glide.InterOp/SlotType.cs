namespace Valkey.Glide.InterOp;

/// <summary>
/// Defines type of the node being addressed.
/// </summary>
public enum SlotType : uint
{
    /// <summary>
    /// Address a primary node.
    /// </summary>
    Primary,

    /// <summary>
    /// Address a replica node.
    /// </summary>
    Replica,
}