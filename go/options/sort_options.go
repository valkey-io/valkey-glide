// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

const (
	// ALPHA subcommand string to include in the SORT and SORT_RO commands.
	ALPHA_COMMAND_STRING = "ALPHA"
	// BY subcommand string to include in the SORT and SORT_RO commands.
	// Supported in cluster mode since Valkey version 8.0 and above.
	BY_COMMAND_STRING = "BY"
	// GET subcommand string to include in the SORT and SORT_RO commands.
	GET_COMMAND_STRING = "GET"
)

// OrderBy specifies the order to sort the elements. Can be ASC (ascending) or DESC(descending).
type OrderBy string

const (
	ASC  OrderBy = "ASC"
	DESC OrderBy = "DESC"
)

// SortOptions struct combines both the base options and additional sorting options
type SortOptions struct {
	Limit       *Limit
	OrderBy     OrderBy
	IsAlpha     bool
	ByPattern   string
	GetPatterns []string
}

func NewSortOptions() *SortOptions {
	return &SortOptions{
		OrderBy: ASC,   // Default order is ascending
		IsAlpha: false, // Default is numeric sorting
	}
}

// Set limits the range of elements.
func (opts *SortOptions) SetLimit(limit Limit) *SortOptions {
	opts.Limit = &limit
	return opts
}

// OrderBy sets the order to sort by (ASC or DESC)
func (opts *SortOptions) SetOrderBy(order OrderBy) *SortOptions {
	opts.OrderBy = order
	return opts
}

// IsAlpha determines whether to sort lexicographically (true) or numerically (false)
func (opts *SortOptions) SetIsAlpha(isAlpha bool) *SortOptions {
	opts.IsAlpha = isAlpha
	return opts
}

// ByPattern - a pattern to sort by external keys instead of by the elements stored at the key themselves. The
// pattern should contain an asterisk (*) as a placeholder for the element values, where the value
// from the key replaces the asterisk to create the key name. For example, if key
// contains IDs of objects, byPattern can be used to sort these IDs based on an
// attribute of the objects, like their weights or timestamps.
// Supported in cluster mode since Valkey version 8.0 and above.
func (opts *SortOptions) SetByPattern(byPattern string) *SortOptions {
	opts.ByPattern = byPattern
	return opts
}

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
func (opts *SortOptions) AddGetPattern(getPattern string) *SortOptions {
	opts.GetPatterns = append(opts.GetPatterns, getPattern)
	return opts
}

// ToArgs creates the arguments to be used in SORT and SORT_RO commands.
func (opts *SortOptions) ToArgs() ([]string, error) {
	var args []string

	if opts.Limit != nil {
		limitArgs, err := opts.Limit.toArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, limitArgs...)
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
	return args, nil
}
