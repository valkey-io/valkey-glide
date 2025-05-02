using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
public struct AuthenticationInfo(string? username, string password)
{
    [MarshalAs(UnmanagedType.LPStr)]
    public string? Username = username;
    [MarshalAs(UnmanagedType.LPStr)]
    public string Password = password;
}