namespace Valkey.Glide;

internal static class InternalExtensions
{
    public static string AsRedisCommandText(this string value) => value;
    public static string AsRedisString(this string value) => $"\"{value.Replace("\"", "\\\"")}\"";
}
