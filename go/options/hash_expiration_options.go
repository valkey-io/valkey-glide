// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package options provides configuration options for hash field expiration commands.
// Available since Valkey 9.0.0.
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
	// ConditionalSet is the key condition, see [constants.ConditionalSet]. Key conditions require Valkey 9.1.0.
	//
	// TODO: https://github.com/valkey-io/valkey-glide/issues/7489 Remove this backward compatibility note in the next
	// major release.
	// For backward compatibility, the deprecated field condition constants are still accepted here;
	// use FieldConditionalSet instead.
	ConditionalSet constants.ConditionalSet
	// FieldConditionalSet is the field condition, see [constants.HashFieldConditionalSet].
	FieldConditionalSet constants.HashFieldConditionalSet
	Expiry              *Expiry
}

// NewHSetExOptions creates a new HSetExOptions instance.
func NewHSetExOptions() HSetExOptions {
	return HSetExOptions{}
}

// SetConditionalSet sets the key conditional set option, see [constants.ConditionalSet].
//
// Since:
//
//	Valkey 9.1.0 for key conditions.
//
// TODO: https://github.com/valkey-io/valkey-glide/issues/7489 Remove this backward compatibility note in the next
// major release.
// Passing the deprecated field condition constants is still supported; use [HSetExOptions.SetFieldConditionalSet]
// instead.
func (opts HSetExOptions) SetConditionalSet(conditionalSet constants.ConditionalSet) HSetExOptions {
	opts.ConditionalSet = conditionalSet
	return opts
}

// SetFieldConditionalSet sets the field conditional set option, see [constants.HashFieldConditionalSet].
func (opts HSetExOptions) SetFieldConditionalSet(fieldConditionalSet constants.HashFieldConditionalSet) HSetExOptions {
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

	// TODO: https://github.com/valkey-io/valkey-glide/issues/7489
	// Backward compatibility: field conditions were previously passed via ConditionalSet.
	if legacy := constants.HashFieldConditionalSet(keyCondition); legacy == constants.HashAllFieldsExist ||
		legacy == constants.HashFieldsDoNotExist {
		if fieldCondition != "" {
			return nil, errors.New(
				"HSetExOptions: field condition set in both ConditionalSet and FieldConditionalSet",
			)
		}
		fieldCondition, keyCondition = legacy, ""
	}

	switch keyCondition {
	case "":
	case constants.OnlyIfExists, constants.OnlyIfDoesNotExist:
		args = append(args, string(keyCondition))
	default:
		return nil, errors.New("HSetExOptions: invalid key condition " + string(keyCondition))
	}

	switch fieldCondition {
	case "":
	case constants.HashAllFieldsExist, constants.HashFieldsDoNotExist:
		args = append(args, string(fieldCondition))
	default:
		return nil, errors.New("HSetExOptions: invalid field condition " + string(fieldCondition))
	}

	// Add expiry options
	if opts.Expiry != nil {
		// HSETEX does not support PERSIST.
		if opts.Expiry.Type == constants.Persist {
			return nil, errors.New("HSetExOptions: invalid expiry type " + string(opts.Expiry.Type))
		}
		expiryArgs, err := opts.Expiry.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, expiryArgs...)
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
