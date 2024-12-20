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

    // Returns the number of members in the sorted set stored at `key` with scores between `minScore` and `maxScore`.
    //
    // See [valkey.io] for details.
    //
    // Parameters:
    //  key - The key of the set.
    //  minScore - The minimum score to count from. Can be positive/negative infinity, or specific score and inclusivity.
    //  maxScore - The maximum score to count up to. Can be positive/negative infinity, or specific score and inclusivity.
    //
    // Return value:
    // Result[int64] - The number of members in the specified score range.
    //
    // Example:
    // result1, err := client.ZCount("mySet", ScoreLimit(5.0, true), ScoreLimit(10.0, false))
    // result1.value() : 1 // Indicates that there is one member with ScoreLimit 5.0 <= score < 10.0 in the sorted set "mySet".
    //
    // [valkey.io]: https://valkey.io/commands/zcount/
    ZCount(key string, ScoreLimit minScore, ScoreLimit maxScore (Result[int64], error)
}
