namespace Valkey.Glide.InterOp.Routing;

public enum EResponsePolicy
{
    /// Unspecified response policy
    None = 0,

    /// Wait for one request to succeed and return its results. Return error if all requests fail.
    OneSucceeded = 1,

    /// Returns the first succeeded non-empty result; if all results are empty, returns `Nil`; otherwise, returns the last received error.
    FirstSucceededNonEmptyOrAllEmpty = 3,

    /// Waits for all requests to succeed, and the returns one of the successes. Returns the error on the first received error.
    AllSucceeded = 4,

    /// Aggregate array responses into a single array. Return error on any failed request or on a response that isn't an array.
    CombineArrays = 5,

    /// Handling is not defined by the Redis standard. Will receive a special case
    Special = 6,

    /// Combines multiple map responses into a single map.
    CombineMaps = 7,

    /// Aggregate success results according to a logical bitwise operator. Return error on any failed request or on a response that doesn't conform to 0 or 1.
    AggregateLogicalWithAnd = 50,

    /// Aggregate success results according to a numeric operator. Return error on any failed request or on a response that isn't an integer.
    /// Choose minimal value
    AggregateWithMin = 70,

    /// Aggregate success results according to a numeric operator. Return error on any failed request or on a response that isn't an integer.
    /// Sum all values
    AggregateWithSum = 71,
}