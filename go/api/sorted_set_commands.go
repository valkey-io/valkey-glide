// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/glide/api/options"
)

/** SetCommands supports commands and transactions for the "Sorted Set Commands" group for standalone and cluster clients.
 *
 * See [valkey.io] for details.
 *
 * [valkey.io]: https://valkey.io/commands/?group=sorted-set#sorted-set
 */
type SortedSetCommands interface {
	/**
	 * Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	 *
	 * See [valkey.io] for details.
	 *
	 * Parameters:
	 *   key - The key of the set.
	 *   membersScoreMap - A map of members to their scores.
	 *
	 * Return value:
	 *   Result[int64] - The number of members added to the set.
	 *
	 * Example:
	 * ```go
	 * res, err := client.Zadd(key, map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
	 * fmt.Println(res.Value()) // Output: 3
	 * ```
	 *
	 * [valkey.io]: https://valkey.io/commands/zadd/
	 */
	Zadd(key string, membersScoreMap map[string]float64) (Result[int64], error)

	/**
	 *	Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	 *
	 * See [valkey.io] for details.
	 *
	 * Parameters:
	 *   key - The key of the set.
	 *   membersScoreMap - A map of members to their scores.
	 *   opts - The options for the command. See [ZAddOptions] for details.
	 *
	 * Return value:
	 *   Result[int64] - The number of members added to the set. If CHANGED is set, the number of members that were updated.
	 *
	 * Example:
	 * ```go
	 * res, err := client.ZaddWithOptions(key, map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0}, options.NewZaddOptionsBuilder().SetChanged(true).Build())
	 * fmt.Println(res.Value()) // Output: 3
	 * ```
	 *
	 * [valkey.io]: https://valkey.io/commands/zadd/
	 */
	ZaddWithOptions(key string, membersScoreMap map[string]float64, opts *options.ZAddOptions) (Result[int64], error)

	/**
	 * Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	 *
	 * See [valkey.io] for details.
	 *
	 * Parameters:
	 *   key - The key of the set.
	 *   member - The member to add to.
	 *   increment - The increment to add to the member's score.
	 *
	 * Return value:
	 *   Result[float64] - The new score of the member.
	 *
	 * Example:
	 * ```go
	 * res, err := client.ZaddIncr(key, "one", 1.0)
	 * fmt.Println(res.Value()) // Output: 1.0
	 * ```
	 *
	 * [valkey.io]: https://valkey.io/commands/zadd/
	 */
	ZaddIncr(key string, member string, increment float64) (Result[float64], error)

	/**
	 * Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
	 *
	 * See [valkey.io] for details.
	 *
	 * Parameters:
	 *   key - The key of the set.
	 *   member - The member to add to.
	 *   increment - The increment to add to the member's score.
	 *   opts - The options for the command. See [ZAddOptions] for details.
	 *
	 * Return value:
	 *   Result[float64] - The new score of the member.
	 *
	 * Example:
	 * ```go
	 * res, err := client.ZaddIncrWithOptions(key, "one", 1.0, options.NewZaddOptionsBuilder().SetChanged(true))
	 * fmt.Println(res.Value()) // Output: 1.0
	 * ```
	 *
	 * [valkey.io]: https://valkey.io/commands/zadd/
	 */
	ZaddIncrWithOptions(key string, member string, increment float64, opts *options.ZAddOptions) (Result[float64], error)
}
