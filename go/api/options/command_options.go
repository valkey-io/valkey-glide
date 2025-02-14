// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"strconv"

	"github.com/valkey-io/valkey-glide/go/api/errors"
	"github.com/valkey-io/valkey-glide/go/utils"
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

func NewSetOptions() *SetOptions {
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

func (opts *SetOptions) ToArgs() ([]string, error) {
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
			err = &errors.RequestError{Msg: "Invalid expiry type"}
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

func NewGetExOptions() *GetExOptions {
	return &GetExOptions{}
}

func (getExOptions *GetExOptions) SetExpiry(expiry *Expiry) *GetExOptions {
	getExOptions.Expiry = expiry
	return getExOptions
}

func (opts *GetExOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error

	if opts.Expiry != nil {
		switch opts.Expiry.Type {
		case Seconds, Milliseconds, UnixSeconds, UnixMilliseconds:
			args = append(args, string(opts.Expiry.Type), strconv.FormatUint(opts.Expiry.Count, 10))
		case Persist:
			args = append(args, string(opts.Expiry.Type))
		default:
			err = &errors.RequestError{Msg: "Invalid expiry type"}
		}
	}

	return args, err
}

func (expireCondition ExpireCondition) ToString() (string, error) {
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
		return "", &errors.RequestError{Msg: "Invalid expire condition"}
	}
}

// Expiry is used to configure the lifetime of a value.
type Expiry struct {
	Type  ExpiryType
	Count uint64
}

func NewExpiry() *Expiry {
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

func NewLPosOptions() *LPosOptions {
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

func (opts *LPosOptions) ToArgs() ([]string, error) {
	args := []string{}
	if opts.IsRankSet {
		args = append(args, RankKeyword, utils.IntToString(opts.Rank))
	}

	if opts.IsMaxLenSet {
		args = append(args, MaxLenKeyword, utils.IntToString(opts.MaxLen))
	}

	return args, nil
}

func (insertPosition InsertPosition) ToString() (string, error) {
	switch insertPosition {
	case Before:
		return string(Before), nil
	case After:
		return string(After), nil
	default:
		return "", &errors.RequestError{Msg: "Invalid insert position"}
	}
}

func (listDirection ListDirection) ToString() (string, error) {
	switch listDirection {
	case Left:
		return string(Left), nil
	case Right:
		return string(Right), nil
	default:
		return "", &errors.RequestError{Msg: "Invalid list direction"}
	}
}

func (scoreFilter ScoreFilter) ToString() (string, error) {
	switch scoreFilter {
	case MAX:
		return string(MAX), nil
	case MIN:
		return string(MIN), nil
	default:
		return "", &errors.RequestError{Msg: "Invalid score filter"}
	}
}

// Optional arguments to Restore(key string, ttl int64, value string, option RestoreOptions)
//
// Note IDLETIME and FREQ modifiers cannot be set at the same time.
//
// [valkey.io]: https://valkey.io/commands/restore/
type RestoreOptions struct {
	// Subcommand string to replace existing key.
	replace string
	// Subcommand string to represent absolute timestamp (in milliseconds) for TTL.
	absTTL string
	// It represents the idletime/frequency of object.
	eviction Eviction
}

func NewRestoreOptions() *RestoreOptions {
	return &RestoreOptions{}
}

// Custom setter methods to replace existing key.
func (restoreOption *RestoreOptions) SetReplace() *RestoreOptions {
	restoreOption.replace = ReplaceKeyword
	return restoreOption
}

// Custom setter methods to represent absolute timestamp (in milliseconds) for TTL.
func (restoreOption *RestoreOptions) SetABSTTL() *RestoreOptions {
	restoreOption.absTTL = ABSTTLKeyword
	return restoreOption
}

// For eviction purpose, you may use IDLETIME or FREQ modifiers.
type Eviction struct {
	// It represent IDLETIME or FREQ.
	Type EvictionType
	// It represents count(int) of the idletime/frequency of object.
	Count int64
}

// Custom setter methods set the idletime/frequency of object.
func (restoreOption *RestoreOptions) SetEviction(evictionType EvictionType, count int64) *RestoreOptions {
	restoreOption.eviction.Type = evictionType
	restoreOption.eviction.Count = count
	return restoreOption
}

func (opts *RestoreOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error
	if opts.replace != "" {
		args = append(args, string(opts.replace))
	}
	if opts.absTTL != "" {
		args = append(args, string(opts.absTTL))
	}
	if (opts.eviction != Eviction{}) {
		args = append(args, string(opts.eviction.Type), utils.IntToString(opts.eviction.Count))
	}
	return args, err
}

// Optional arguments for `Info` for standalone client
type InfoOptions struct {
	// A list of [Section] values specifying which sections of information to retrieve.
	// When no parameter is provided, [Section.Default] is assumed.
	// Starting with server version 7.0.0 `INFO` command supports multiple sections.
	Sections []Section
}

// Optional arguments for `Info` for cluster client
type ClusterInfoOptions struct {
	*InfoOptions
	*RouteOption
}

func (opts *InfoOptions) ToArgs() ([]string, error) {
	if opts == nil {
		return []string{}, nil
	}
	args := make([]string, 0, len(opts.Sections))
	for _, section := range opts.Sections {
		args = append(args, string(section))
	}
	return args, nil
}

// Optional arguments to Copy(source string, destination string, option CopyOptions)
//
// [valkey.io]: https://valkey.io/commands/Copy/
type CopyOptions struct {
	// The REPLACE option removes the destination key before copying the value to it.
	replace bool
	// Option allows specifying an alternative logical database index for the destination key
	dbDestination int64
}

func NewCopyOptions() *CopyOptions {
	return &CopyOptions{replace: false}
}

// Custom setter methods to removes the destination key before copying the value to it.
func (restoreOption *CopyOptions) SetReplace() *CopyOptions {
	restoreOption.replace = true
	return restoreOption
}

// Custom setter methods to allows specifying an alternative logical database index for the destination key.
func (copyOption *CopyOptions) SetDBDestination(destinationDB int64) *CopyOptions {
	copyOption.dbDestination = destinationDB
	return copyOption
}

func (opts *CopyOptions) ToArgs() ([]string, error) {
	args := []string{}
	var err error
	if opts.replace {
		args = append(args, string(ReplaceKeyword))
	}
	if opts.dbDestination >= 0 {
		args = append(args, "DB", utils.IntToString(opts.dbDestination))
	}
	return args, err
}

// Optional arguments for `ZPopMin` and `ZPopMax` commands.
type ZPopOptions struct {
	count int64
}

func NewZPopOptions() *ZPopOptions {
	return &ZPopOptions{}
}

// The maximum number of popped elements. If not specified, pops one member.
func (opts *ZPopOptions) SetCount(count int64) *ZPopOptions {
	opts.count = count
	return opts
}

func (opts *ZPopOptions) ToArgs() ([]string, error) {
	return []string{utils.IntToString(opts.count)}, nil
}
