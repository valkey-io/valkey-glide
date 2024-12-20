// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/glide/api/options"
)

// SortedSetCommands supports commands and transactions for the "Sorted Set Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#sorted-set
type SortedSetCommands interface {
	// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key of the set.
	//   membersScoreMap - A map of members to their scores.
	//
	// Return value:
	//   Result[int64] - The number of members added to the set.
	//
	// Example:
	//   res, err := client.ZAdd(key, map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	//   fmt.Println(res.Value()) // Output: 3
	//
	// [valkey.io]: https://valkey.io/commands/zadd/
	ZAdd(key string, membersScoreMap map[string]float64) (Result[int64], error)

	// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key of the set.
	//   membersScoreMap - A map of members to their scores.
	//   opts - The options for the command. See [ZAddOptions] for details.
	//
	// Return value:
	//   Result[int64] - The number of members added to the set. If CHANGED is set, the number of members that were updated.
	//
	// Example:
	//   res, err := client.ZAddWithOptions(key, map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0},
	//   									options.NewZAddOptionsBuilder().SetChanged(true).Build())
	//   fmt.Println(res.Value()) // Output: 3
	//
	// [valkey.io]: https://valkey.io/commands/zadd/
	ZAddWithOptions(key string, membersScoreMap map[string]float64, opts *options.ZAddOptions) (Result[int64], error)

	// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key of the set.
	//   member - The member to add to.
	//   increment - The increment to add to the member's score.
	//
	// Return value:
	//   Result[float64] - The new score of the member.
	//
	// Example:
	//   res, err := client.ZAddIncr(key, "one", 1.0)
	//   fmt.Println(res.Value()) // Output: 1.0
	//
	// [valkey.io]: https://valkey.io/commands/zadd/
	ZAddIncr(key string, member string, increment float64) (Result[float64], error)

	// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   key - The key of the set.
	//   member - The member to add to.
	//   increment - The increment to add to the member's score.
	//   opts - The options for the command. See [ZAddOptions] for details.
	//
	// Return value:
	//   Result[float64] - The new score of the member.
	//
	// Example:
	//   res, err := client.ZAddIncrWithOptions(key, "one", 1.0, options.NewZAddOptionsBuilder().SetChanged(true))
	//   fmt.Println(res.Value()) // Output: 1.0
	//
	// [valkey.io]: https://valkey.io/commands/zadd/
	ZAddIncrWithOptions(key string, member string, increment float64, opts *options.ZAddOptions) (Result[float64], error)

	// Increments the score of member in the sorted set stored at key by increment.
	// If member does not exist in the sorted set, it is added with increment as its score.
	// If key does not exist, a new sorted set with the specified member as its sole member
	// is created.
	//
	// see [valkey.io] for details.
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
	//   res, err := client.ZIncrBy("myzset", 2.0, "one")
	//   fmt.Println(res.Value()) // Output: 2.0
	//
	// [valkey.io]: https://valkey.io/commands/zincrby/
	ZIncrBy(key string, increment float64, member string) (Result[float64], error)
}
