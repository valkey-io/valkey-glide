// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.TestCommon;

public static class MemberDataHelpers
{
    public static IEnumerable<object[]> MultiCase(
        string input,
        string? prefix = null,
        string? suffix = null,
        int maxPermutations = Int32.MaxValue,
        params object[] additionalArguments)
    {
        return Helpers.MultiCase(input, prefix, suffix, maxPermutations)
            .Select(s => additionalArguments.Length is 0 ? [s] : additionalArguments.Prepend(s).ToArray());
    }
}
