using System.Runtime.CompilerServices;

namespace Valkey.Glide;

internal static class InternalExtensions
{
    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public static string AsRedisInteger(this string value) => value;
    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public static string AsRedisCommandText(this string value) => value;
    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public static string AsRedisString(this string value) => $"\"{value.Replace("\"", "\\\"")}\"";
}
