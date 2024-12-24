// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"errors"
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

// Optional arguments to `ZAdd` in [SortedSetCommands]
type ZAddOptions struct {
	conditionalChange ConditionalSet
	updateOptions     UpdateOptions
	changed           bool
	incr              bool
	increment         float64
	member            string
}

func NewZAddOptionsBuilder() *ZAddOptions {
	return &ZAddOptions{}
}

// `conditionalChange` defines conditions for updating or adding elements with `ZAdd` command.
func (options *ZAddOptions) SetConditionalChange(c ConditionalSet) *ZAddOptions {
	options.conditionalChange = c
	return options
}

// `updateOptions` specifies conditions for updating scores with `ZAdd` command.
func (options *ZAddOptions) SetUpdateOptions(u UpdateOptions) *ZAddOptions {
	options.updateOptions = u
	return options
}

// `Changed` changes the return value from the number of new elements added to the total number of elements changed.
func (options *ZAddOptions) SetChanged(ch bool) (*ZAddOptions, error) {
	if options.incr {
		return nil, errors.New("changed cannot be set when incr is true")
	}
	options.changed = ch
	return options, nil
}

// `INCR` sets the increment value to use when incr is true.
func (options *ZAddOptions) SetIncr(incr bool, increment float64, member string) (*ZAddOptions, error) {
	if options.changed {
		return nil, errors.New("incr cannot be set when changed is true")
	}
	options.incr = incr
	options.increment = increment
	options.member = member
	return options, nil
}

// `toArgs` converts the options to a list of arguments.
func (opts *ZAddOptions) toArgs() ([]string, error) {
	args := []string{}
	var err error

	if opts.conditionalChange == OnlyIfExists || opts.conditionalChange == OnlyIfDoesNotExist {
		args = append(args, string(opts.conditionalChange))
	}

	if opts.updateOptions == ScoreGreaterThanCurrent || opts.updateOptions == ScoreLessThanCurrent {
		args = append(args, string(opts.updateOptions))
	}

	if opts.changed {
		args = append(args, ChangedKeyword)
	}

	if opts.incr {
		args = append(args, IncrKeyword, utils.FloatToString(opts.increment), opts.member)
	}

	return args, err
}

type UpdateOptions string

const (
	// Only update existing elements if the new score is less than the current score. Equivalent to
	// "LT" in the Valkey API.
	ScoreLessThanCurrent UpdateOptions = "LT"
	// Only update existing elements if the new score is greater than the current score. Equivalent
	// to "GT" in the Valkey API.
	ScoreGreaterThanCurrent UpdateOptions = "GT"
)

const (
	ChangedKeyword string = "CH"   // Valkey API keyword used to return total number of elements changed
	IncrKeyword    string = "INCR" // Valkey API keyword to make zadd act like ZINCRBY.
)

type triStateBool int

// Tri-state bool for use option builders. We cannot rely on the default value of an non-initialized variable.
const (
	triStateBoolUndefined triStateBool = iota
	triStateBoolTrue
	triStateBoolFalse
)

// Optional arguments to `XAdd` in [StreamCommands]
type XAddOptions struct {
	id          string
	makeStream  triStateBool
	trimOptions *XTrimOptions
}

// Create new empty `XAddOptions`
func NewXAddOptions() *XAddOptions {
	return &XAddOptions{}
}

// New entry will be added with this `id“.
func (xao *XAddOptions) SetId(id string) *XAddOptions {
	xao.id = id
	return xao
}

// If set, a new stream won't be created if no stream matches the given key.
func (xao *XAddOptions) SetDontMakeNewStream() *XAddOptions {
	xao.makeStream = triStateBoolFalse
	return xao
}

// If set, add operation will also trim the older entries in the stream.
func (xao *XAddOptions) SetTrimOptions(options *XTrimOptions) *XAddOptions {
	xao.trimOptions = options
	return xao
}

func (xao *XAddOptions) toArgs() ([]string, error) {
	args := []string{}
	var err error
	if xao.makeStream == triStateBoolFalse {
		args = append(args, "NOMKSTREAM")
	}
	if xao.trimOptions != nil {
		moreArgs, err := xao.trimOptions.toArgs()
		if err != nil {
			return args, err
		}
		args = append(args, moreArgs...)
	}
	if xao.id != "" {
		args = append(args, xao.id)
	} else {
		args = append(args, "*")
	}
	return args, err
}

// Optional arguments for `XTrim` and `XAdd` in [StreamCommands]
type XTrimOptions struct {
	exact     triStateBool
	limit     int64
	method    string
	threshold string
}

// Option to trim the stream according to minimum ID.
func NewXTrimOptionsWithMinId(threshold string) *XTrimOptions {
	return &XTrimOptions{threshold: threshold, method: "MINID"}
}

// Option to trim the stream according to maximum stream length.
func NewXTrimOptionsWithMaxLen(threshold int64) *XTrimOptions {
	return &XTrimOptions{threshold: utils.IntToString(threshold), method: "MAXLEN"}
}

// Match exactly on the threshold.
func (xto *XTrimOptions) SetExactTrimming() *XTrimOptions {
	xto.exact = triStateBoolTrue
	return xto
}

// Trim in a near-exact manner, which is more efficient.
func (xto *XTrimOptions) SetNearlyExactTrimming() *XTrimOptions {
	xto.exact = triStateBoolFalse
	return xto
}

// Max number of stream entries to be trimmed for non-exact match.
func (xto *XTrimOptions) SetNearlyExactTrimmingAndLimit(limit int64) *XTrimOptions {
	xto.exact = triStateBoolFalse
	xto.limit = limit
	return xto
}

func (xto *XTrimOptions) toArgs() ([]string, error) {
	args := []string{}
	args = append(args, xto.method)
	if xto.exact == triStateBoolTrue {
		args = append(args, "=")
	} else if xto.exact == triStateBoolFalse {
		args = append(args, "~")
	}
	args = append(args, xto.threshold)
	if xto.limit > 0 {
		args = append(args, "LIMIT", utils.IntToString(xto.limit))
	}
	var err error
	return args, err
}
