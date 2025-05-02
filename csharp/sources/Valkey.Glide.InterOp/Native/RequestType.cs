namespace Valkey.Glide.InterOp.Native;

public enum RequestType : int
{
// TODO: generate this with a bindings generator
    InvalidRequest = 0,
    CustomCommand = 1,
    Info = 1130,
    Get = 1504,
    Set = 1517,
}