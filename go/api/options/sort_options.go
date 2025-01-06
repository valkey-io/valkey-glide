// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

const (
	// LIMIT subcommand string to include in the SORT and SORT_RO commands.
	LIMIT_COMMAND_STRING = "LIMIT"
	// ALPHA subcommand string to include in the SORT and SORT_RO commands.
	ALPHA_COMMAND_STRING = "ALPHA"
	// BY subcommand string to include in the SORT and SORT_RO commands.
	// Supported in cluster mode since Valkey version 8.0 and above.
	BY_COMMAND_STRING = "BY"
	// GET subcommand string to include in the SORT and SORT_RO commands.
	GET_COMMAND_STRING = "GET"
)

// Limit struct represents the range of elements to retrieve
// The LIMIT argument is commonly used to specify a subset of results from the matching elements, similar to the
// LIMIT clause in SQL (e.g., `SELECT LIMIT offset, count`).
type Limit struct {
	// The starting position of the range, zero based.
	Offset int64
	// The maximum number of elements to include in the range. A negative count returns all elementsnfrom the offset.
	Count int64
}

// OrderBy specifies the order to sort the elements. Can be ASC (ascending) or DESC(descending).
type OrderBy string

const (
	ASC  OrderBy = "ASC"
	DESC OrderBy = "DESC"
)

// SortOptions struct combines both the base options and additional sorting options
type SortOptions struct {
	// Limit Limits the range of elements
	Limit *Limit

	// OrderBy sets the order to sort by (ASC or DESC)
	OrderBy OrderBy

	// IsAlpha determines whether to sort lexicographically (true) or numerically (false)
	IsAlpha bool

	// ByPattern - a pattern to sort by external keys instead of by the elements stored at the key themselves. The
	// pattern should contain an asterisk (*) as a placeholder for the element values, where the value
	// from the key replaces the asterisk to create the key name. For example, if key
	// contains IDs of objects, byPattern can be used to sort these IDs based on an
	// attribute of the objects, like their weights or timestamps.
	// Supported in cluster mode since Valkey version 8.0 and above.
	ByPattern string

	// A pattern used to retrieve external keys' values, instead of the elements at key.
	// The pattern should contain an asterisk (*) as a placeholder for the element values, where the
	// value from key replaces the asterisk to create the key name. This
	// allows the sorted elements to be transformed based on the related keys values. For example, if
	// key< contains IDs of users, getPatterns can be used to retrieve
	// specific attributes of these users, such as their names or email addresses. E.g., if
	// getPatterns is name_*, the command will return the values of the keys
	// name_&lt;element&gt; for each sorted element. Multiple getPatterns
	// arguments can be provided to retrieve multiple attributes. The special value # can
	// be used to include the actual element from key being sorted. If not provided, only
	// the sorted elements themselves are returned.
	// Supported in cluster mode since Valkey version 8.0 and above.
	GetPatterns []string // List of patterns to retrieve external keys' values
}

func NewSortOptions() *SortOptions {
	return &SortOptions{
		OrderBy: ASC,   // Default order is ascending
		IsAlpha: false, // Default is numeric sorting
	}
}

func (opts *SortOptions) SetLimit(offset, count int64) *SortOptions {
	opts.Limit = &Limit{Offset: offset, Count: count}
	return opts
}

func (opts *SortOptions) SetOrderBy(order OrderBy) *SortOptions {
	opts.OrderBy = order
	return opts
}

func (opts *SortOptions) SetIsAlpha(isAlpha bool) *SortOptions {
	opts.IsAlpha = isAlpha
	return opts
}

func (opts *SortOptions) SetByPattern(byPattern string) *SortOptions {
	opts.ByPattern = byPattern
	return opts
}

func (opts *SortOptions) AddGetPattern(getPattern string) *SortOptions {
	opts.GetPatterns = append(opts.GetPatterns, getPattern)
	return opts
}

// ToArgs creates the arguments to be used in SORT and SORT_RO commands.
func (opts *SortOptions) ToArgs() []string {
	var args []string

	if opts.Limit != nil {
		args = append(args, LIMIT_COMMAND_STRING, utils.IntToString(opts.Limit.Offset), utils.IntToString(opts.Limit.Count))
	}

	if opts.OrderBy != "" {
		args = append(args, string(opts.OrderBy))
	}

	if opts.IsAlpha {
		args = append(args, ALPHA_COMMAND_STRING)
	}

	if opts.ByPattern != "" {
		args = append(args, BY_COMMAND_STRING, opts.ByPattern)
	}

	for _, getPattern := range opts.GetPatterns {
		args = append(args, GET_COMMAND_STRING, getPattern)
	}
	return args
}
