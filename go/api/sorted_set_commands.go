// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

/** SetCommands supports commands and transactions for the "Sorted Set Commands" group for standalone and cluster clients.
 *
 * See [valkey.io] for details.
 *
 * [valkey.io]: https://valkey.io/commands/?group=sorted-set#sorted-set
 */
type SortedSetCommands interface {
	// Increments the score of member in the sorted set stored at key by increment.
	// If member does not exist in the sorted set, it is added with increment as its score.
	// If key does not exist, a new sorted set with the specified member as its sole member
	// is created.
	//
	// see [valkey zincrby] for details.
	//
	// Parameters:
	//   key - The key of the sorted set.
	//   increment - The score increment.
	//   member - A member of the sorted set.
	//
	// Return value:
	//   The new score of member.
	//
	// Example:
	//
	// [valkey zincrby]: https://valkey.io/commands/zincrby/
	ZIncrBy(key string, increment float64, member string) (Result[float64], error)
}
