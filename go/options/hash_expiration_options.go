// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package options provides configuration options for hash field expiration commands.
// Available in Valkey 9.0 and above.
package options

import (
	"errors"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// HSetExOptions represents optional arguments for the HSETEX command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/hsetex/
type HSetExOptions struct {
	// ConditionalSet is the key-level condition: [constants.OnlyIfExists] (XX) or [constants.OnlyIfDoesNotExist] (NX).
	// Key-level conditions require Valkey 9.1 and above.
	//
	// For backward compatibility, the field-level conditions [constants.OnlyIfAllFieldsExist] (FXX) and
	// [constants.OnlyIfFieldsDoNotExist] (FNX) are still accepted here, but are deprecated in favor of
	// FieldConditionalSet.
	ConditionalSet constants.ConditionalSet
	// FieldConditionalSet is the field-level condition: [constants.OnlyIfAllFieldsExist] (FXX) or
	// [constants.OnlyIfFieldsDoNotExist] (FNX).
	FieldConditionalSet constants.ConditionalSet
	Expiry              *Expiry
}

// NewHSetExOptions creates a new HSetExOptions instance.
func NewHSetExOptions() HSetExOptions {
	return HSetExOptions{}
}

// SetConditionalSet sets the key-level conditional set option: [constants.OnlyIfExists] (XX) or
// [constants.OnlyIfDoesNotExist] (NX).
//
// Since:
//
//	Valkey 9.1 and above for key-level conditions.
//
// Passing a field-level condition ([constants.OnlyIfAllFieldsExist] or [constants.OnlyIfFieldsDoNotExist]) is
// deprecated; use [HSetExOptions.SetFieldConditionalSet] instead.
func (opts HSetExOptions) SetConditionalSet(conditionalSet constants.ConditionalSet) HSetExOptions {
	opts.ConditionalSet = conditionalSet
	return opts
}

// SetFieldConditionalSet sets the field-level conditional set option: [constants.OnlyIfAllFieldsExist] (FXX) or
// [constants.OnlyIfFieldsDoNotExist] (FNX).
func (opts HSetExOptions) SetFieldConditionalSet(fieldConditionalSet constants.ConditionalSet) HSetExOptions {
	opts.FieldConditionalSet = fieldConditionalSet
	return opts
}

// SetExpiry sets the expiry options.
func (opts HSetExOptions) SetExpiry(expiry *Expiry) HSetExOptions {
	opts.Expiry = expiry
	return opts
}

// ToArgs converts the options to command arguments, in the order [NX | XX] [FNX | FXX] [expiry].
func (opts *HSetExOptions) ToArgs() ([]string, error) {
	args := []string{}

	keyCondition := opts.ConditionalSet
	fieldCondition := opts.FieldConditionalSet

	// Backward compatibility: field-level conditions were previously passed via ConditionalSet.
	if keyCondition == constants.OnlyIfAllFieldsExist || keyCondition == constants.OnlyIfFieldsDoNotExist {
		if fieldCondition != "" {
			return nil, errors.New(
				"HSetExOptions: field-level condition set in both ConditionalSet and FieldConditionalSet",
			)
		}
		fieldCondition, keyCondition = keyCondition, ""
	}

	switch keyCondition {
	case "":
	case constants.OnlyIfExists, constants.OnlyIfDoesNotExist:
		args = append(args, string(keyCondition))
	default:
		return nil, errors.New("HSetExOptions: invalid key-level condition " + string(keyCondition))
	}

	switch fieldCondition {
	case "":
	case constants.OnlyIfAllFieldsExist, constants.OnlyIfFieldsDoNotExist:
		args = append(args, string(fieldCondition))
	default:
		return nil, errors.New("HSetExOptions: invalid field-level condition " + string(fieldCondition))
	}

	// Add expiry options
	if opts.Expiry != nil {
		switch opts.Expiry.Type {
		case constants.Seconds:
			args = append(args, "EX", utils.IntToString(int64(opts.Expiry.Duration)))
		case constants.Milliseconds:
			args = append(args, "PX", utils.IntToString(int64(opts.Expiry.Duration)))
		case constants.UnixSeconds:
			args = append(args, "EXAT", utils.IntToString(int64(opts.Expiry.GetTime())))
		case constants.UnixMilliseconds:
			args = append(args, "PXAT", utils.IntToString(int64(opts.Expiry.GetTime())))
		case constants.KeepExisting:
			args = append(args, "KEEPTTL")
		}
	}

	return args, nil
}

// HGetExOptions represents optional arguments for the HGETEX command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/hgetex/
type HGetExOptions struct {
	Expiry *Expiry
}

// NewHGetExOptions creates a new HGetExOptions instance.
func NewHGetExOptions() HGetExOptions {
	return HGetExOptions{}
}

// SetExpiry sets the expiry options.
func (opts HGetExOptions) SetExpiry(expiry *Expiry) HGetExOptions {
	opts.Expiry = expiry
	return opts
}

// ToArgs converts the options to command arguments.
func (opts *HGetExOptions) ToArgs() ([]string, error) {
	args := []string{}

	// Add expiry options
	if opts.Expiry != nil {
		switch opts.Expiry.Type {
		case constants.Seconds:
			args = append(args, "EX", utils.IntToString(int64(opts.Expiry.Duration)))
		case constants.Milliseconds:
			args = append(args, "PX", utils.IntToString(int64(opts.Expiry.Duration)))
		case constants.UnixSeconds:
			args = append(args, "EXAT", utils.IntToString(int64(opts.Expiry.GetTime())))
		case constants.UnixMilliseconds:
			args = append(args, "PXAT", utils.IntToString(int64(opts.Expiry.GetTime())))
		case constants.Persist:
			args = append(args, "PERSIST")
		}
	}

	return args, nil
}

// HExpireOptions represents optional arguments for hash field expiration commands.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/hexpire/
type HExpireOptions struct {
	ExpireCondition constants.ExpireCondition
}

// NewHExpireOptions creates a new HExpireOptions instance.
func NewHExpireOptions() HExpireOptions {
	return HExpireOptions{}
}

// SetExpireCondition sets the expire condition.
func (opts HExpireOptions) SetExpireCondition(condition constants.ExpireCondition) HExpireOptions {
	opts.ExpireCondition = condition
	return opts
}

// ToArgs converts the options to command arguments.
func (opts *HExpireOptions) ToArgs() ([]string, error) {
	args := []string{}

	// Add expire condition options only if set
	if opts.ExpireCondition != "" {
		conditionStr, err := opts.ExpireCondition.ToString()
		if err != nil {
			return nil, err
		}
		args = append(args, conditionStr)
	}

	return args, nil
}
