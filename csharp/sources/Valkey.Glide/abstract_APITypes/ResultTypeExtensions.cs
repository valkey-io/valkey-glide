// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

internal static class ResultTypeExtensions
{
    public static bool IsError(this ResultType value)
        => (value & (ResultType)0b111) == ResultType.Error;

    public static ResultType ToResp2(this ResultType value)
        => value & (ResultType)0b111; // just keep the last 3 bits
}
