// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/glide/utils"
)

// SetOptions represents optional arguments for the [api.StringCommands.SetWithOptions] command.
//
// See [valkey.io]
//
// [valkey.io]: https://valkey.io/commands/set/
type SetOptions struct {
	// If ConditionalSet is not set the value will be set regardless of prior value existence. If value isn't set because of
	// the condition, [api.StringCommands.SetWithOptions] will return a zero-value string ("").
	ConditionalSet ConditionalSet
	// Set command to return the old value stored at the given key, or a zero-value string ("") if the key did not exist. An
	// error is returned and [api.StringCommands.SetWithOptions] is aborted if the value stored at key is not a string.
	// Equivalent to GET in the valkey API.
	ReturnOldValue bool
	// If not set, no expiry time will be set for the value.
	// Supported ExpiryTypes ("EX", "PX", "EXAT", "PXAT", "KEEPTTL")
	Expiry *Expiry
}

func NewSetOptionsBuilder() *SetOptions {
	return &SetOptions{}
}

func (setOptions *SetOptions) SetConditionalSet(conditionalSet ConditionalSet) *SetOptions {
	setOptions.ConditionalSet = conditionalSet
	return setOptions
}

func (setOptions *SetOptions) SetReturnOldValue(returnOldValue bool) *SetOptions {
	setOptions.ReturnOldValue = returnOldValue
	return setOptions
}

func (setOptions *SetOptions) SetExpiry(expiry *Expiry) *SetOptions {
	setOptions.Expiry = expiry
	return setOptions
}

func (opts *SetOptions) toArgs() ([]string, error) {
	args := []string{}
	var err error
	if opts.ConditionalSet != "" {
		args = append(args, string(opts.ConditionalSet))
	}

	if opts.ReturnOldValue {
		args = append(args, returnOldValue)
	}

	if opts.Expiry != nil {
		switch opts.Expiry.Type {
		case Seconds, Milliseconds, UnixSeconds, UnixMilliseconds:
			args = append(args, string(opts.Expiry.Type), strconv.FormatUint(opts.Expiry.Count, 10))
		case KeepExisting:
			args = append(args, string(opts.Expiry.Type))
		default:
			err = &RequestError{"Invalid expiry type"}
		}
	}

	return args, err
}

// GetExOptions represents optional arguments for the [api.StringCommands.GetExWithOptions] command.
//
// See [valkey.io]
//
// [valkey.io]: https://valkey.io/commands/getex/
type GetExOptions struct {
	// If not set, no expiry time will be set for the value.
	// Supported ExpiryTypes ("EX", "PX", "EXAT", "PXAT", "PERSIST")
	Expiry *Expiry
}

func NewGetExOptionsBuilder() *GetExOptions {
	return &GetExOptions{}
}

func (getExOptions *GetExOptions) SetExpiry(expiry *Expiry) *GetExOptions {
	getExOptions.Expiry = expiry
	return getExOptions
}

func (opts *GetExOptions) toArgs() ([]string, error) {
	args := []string{}
	var err error

	if opts.Expiry != nil {
		switch opts.Expiry.Type {
		case Seconds, Milliseconds, UnixSeconds, UnixMilliseconds:
			args = append(args, string(opts.Expiry.Type), strconv.FormatUint(opts.Expiry.Count, 10))
		case Persist:
			args = append(args, string(opts.Expiry.Type))
		default:
			err = &RequestError{"Invalid expiry type"}
		}
	}

	return args, err
}

const returnOldValue = "GET"

// A ConditionalSet defines whether a new value should be set or not.
type ConditionalSet string

const (
	// OnlyIfExists only sets the key if it already exists. Equivalent to "XX" in the valkey API.
	OnlyIfExists ConditionalSet = "XX"
	// OnlyIfDoesNotExist only sets the key if it does not already exist. Equivalent to "NX" in the valkey API.
	OnlyIfDoesNotExist ConditionalSet = "NX"
)

type ExpireCondition string

const (
	// HasExistingExpiry only sets the key if it already exists. Equivalent to "XX" in the valkey API.
	HasExistingExpiry ExpireCondition = "XX"
	// HasNoExpiry only sets the key if it does not already exist. Equivalent to "NX" in the valkey API.
	HasNoExpiry ExpireCondition = "NX"
	// NewExpiryGreaterThanCurrent only sets the key if its greater than current. Equivalent to "GT" in the valkey API.
	NewExpiryGreaterThanCurrent ExpireCondition = "GT"
	// NewExpiryLessThanCurrent only sets the key if its lesser than current. Equivalent to "LT" in the valkey API.
	NewExpiryLessThanCurrent ExpireCondition = "LT"
)

func (expireCondition ExpireCondition) toString() (string, error) {
	switch expireCondition {
	case HasExistingExpiry:
		return string(HasExistingExpiry), nil
	case HasNoExpiry:
		return string(HasNoExpiry), nil
	case NewExpiryGreaterThanCurrent:
		return string(NewExpiryGreaterThanCurrent), nil
	case NewExpiryLessThanCurrent:
		return string(NewExpiryLessThanCurrent), nil
	default:
		return "", &RequestError{"Invalid expire condition"}
	}
}

// Expiry is used to configure the lifetime of a value.
type Expiry struct {
	Type  ExpiryType
	Count uint64
}

func NewExpiryBuilder() *Expiry {
	return &Expiry{}
}

func (ex *Expiry) SetType(expiryType ExpiryType) *Expiry {
	ex.Type = expiryType
	return ex
}

func (ex *Expiry) SetCount(count uint64) *Expiry {
	ex.Count = count
	return ex
}

// An ExpiryType is used to configure the type of expiration for a value.
type ExpiryType string

const (
	KeepExisting     ExpiryType = "KEEPTTL" // keep the existing expiration of the value
	Seconds          ExpiryType = "EX"      // expire the value after [api.Expiry.Count] seconds
	Milliseconds     ExpiryType = "PX"      // expire the value after [api.Expiry.Count] milliseconds
	UnixSeconds      ExpiryType = "EXAT"    // expire the value after the Unix time specified by [api.Expiry.Count], in seconds
	UnixMilliseconds ExpiryType = "PXAT"    // expire the value after the Unix time specified by [api.Expiry.Count], in milliseconds
	Persist          ExpiryType = "PERSIST" // Remove the expiry associated with the key
)

// LPosOptions represents optional arguments for the [api.ListCommands.LPosWithOptions] and
// [api.ListCommands.LPosCountWithOptions] commands.
//
// See [valkey.io]
//
// [valkey.io]: https://valkey.io/commands/lpos/
type LPosOptions struct {
	// Represents if the rank option is set.
	IsRankSet bool
	// The rank of the match to return.
	Rank int64
	// Represents if the max length parameter is set.
	IsMaxLenSet bool
	// The maximum number of comparisons to make between the element and the items in the list.
	MaxLen int64
}

func NewLPosOptionsBuilder() *LPosOptions {
	return &LPosOptions{}
}

func (lposOptions *LPosOptions) SetRank(rank int64) *LPosOptions {
	lposOptions.IsRankSet = true
	lposOptions.Rank = rank
	return lposOptions
}

func (lposOptions *LPosOptions) SetMaxLen(maxLen int64) *LPosOptions {
	lposOptions.IsMaxLenSet = true
	lposOptions.MaxLen = maxLen
	return lposOptions
}

func (opts *LPosOptions) toArgs() []string {
	args := []string{}
	if opts.IsRankSet {
		args = append(args, RankKeyword, utils.IntToString(opts.Rank))
	}

	if opts.IsMaxLenSet {
		args = append(args, MaxLenKeyword, utils.IntToString(opts.MaxLen))
	}

	return args
}

const (
	CountKeyword  string = "COUNT"  // Valkey API keyword used to extract specific number of matching indices from a list.
	RankKeyword   string = "RANK"   // Valkey API keyword use to determine the rank of the match to return.
	MaxLenKeyword string = "MAXLEN" // Valkey API keyword used to determine the maximum number of list items to compare.
	MatchKeyword  string = "MATCH"  // Valkey API keyword used to indicate the match filter.
)

// A InsertPosition defines where to insert new elements into a list.
//
// See [valkey.io]
//
// [valkey.io]: https://valkey.io/commands/linsert/
type InsertPosition string

const (
	// Insert new element before the pivot.
	Before InsertPosition = "BEFORE"
	// Insert new element after the pivot.
	After InsertPosition = "AFTER"
)

func (insertPosition InsertPosition) toString() (string, error) {
	switch insertPosition {
	case Before:
		return string(Before), nil
	case After:
		return string(After), nil
	default:
		return "", &RequestError{"Invalid insert position"}
	}
}

// Enumeration representing element popping or adding direction for the [api.ListCommands].
type ListDirection string

const (
	// Represents the option that elements should be popped from or added to the left side of a list.
	Left ListDirection = "LEFT"
	// Represents the option that elements should be popped from or added to the right side of a list.
	Right ListDirection = "RIGHT"
)

func (listDirection ListDirection) toString() (string, error) {
	switch listDirection {
	case Left:
		return string(Left), nil
	case Right:
		return string(Right), nil
	default:
		return "", &RequestError{"Invalid list direction"}
	}
}

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
	// attribute of the objects, like their weights or timestamps. Supported in cluster mode since
	// Valkey version 8.0 and above.
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
		args = append(args, LIMIT_COMMAND_STRING, fmt.Sprintf("%d", opts.Limit.Offset), fmt.Sprintf("%d", opts.Limit.Count))
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

// This base option struct represents the common set of optional arguments for the SCAN family of commands.
// Concrete implementations of this class are tied to specific SCAN commands (`SCAN`, `SSCAN`).
type BaseScanOptions struct {
	match string
	count int64
}

func NewBaseScanOptionsBuilder() *BaseScanOptions {
	return &BaseScanOptions{}
}

// The match filter is applied to the result of the command and will only include
// strings that match the pattern specified. If the sorted set is large enough for scan commands to return
// only a subset of the sorted set then there could be a case where the result is empty although there are
// items that match the pattern specified. This is due to the default `COUNT` being `10` which indicates
// that it will only fetch and match `10` items from the list.
func (scanOptions *BaseScanOptions) SetMatch(m string) *BaseScanOptions {
	scanOptions.match = m
	return scanOptions
}

// `COUNT` is a just a hint for the command for how many elements to fetch from the
// sorted set. `COUNT` could be ignored until the sorted set is large enough for the `SCAN` commands to
// represent the results as compact single-allocation packed encoding.
func (scanOptions *BaseScanOptions) SetCount(c int64) *BaseScanOptions {
	scanOptions.count = c
	return scanOptions
}

func (opts *BaseScanOptions) toArgs() ([]string, error) {
	args := []string{}
	var err error
	if opts.match != "" {
		args = append(args, MatchKeyword, opts.match)
	}

	if opts.count != 0 {
		args = append(args, CountKeyword, strconv.FormatInt(opts.count, 10))
	}

	return args, err
}
