// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "strconv"

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
