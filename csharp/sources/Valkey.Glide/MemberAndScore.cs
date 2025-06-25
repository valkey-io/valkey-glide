// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

/// <summary>
/// Represents a member and its score in a sorted set.
/// </summary>
/// <param name="Member">The member name.</param>
/// <param name="Score">The score associated with the member.</param>
public record MemberAndScore(GlideString Member, double Score);
