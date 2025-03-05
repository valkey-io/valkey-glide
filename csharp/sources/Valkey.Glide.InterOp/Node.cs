namespace Valkey.Glide.InterOp;

public struct Node(string address, ushort port)
{
    public string Address { get; set; } = address;
    public ushort Port { get; set; } = port;
}