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
	// ConditionalSet is the key-level condition: [constants.OnlyIfExists] (XX) or [constants.OnlyIfDoesNotExist] (NX).
	// Key-level conditions require Valkey 9.1 and above.
	//
	// For backward compatibility, the deprecated field-level constants [constants.OnlyIfAllFieldsExist] (FXX) and
	// [constants.OnlyIfFieldsDoNotExist] (FNX) are still accepted here; use FieldConditionalSet instead.
	ConditionalSet constants.ConditionalSet
	// FieldConditionalSet is the field-level condition: [constants.HashAllFieldsExist] (FXX) or
	// [constants.HashFieldsDoNotExist] (FNX).
	FieldConditionalSet constants.HashFieldConditionalSet
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
// Passing the deprecated field-level constants ([constants.OnlyIfAllFieldsExist] or
// [constants.OnlyIfFieldsDoNotExist]) is still supported; use [HSetExOptions.SetFieldConditionalSet] instead.
func (opts HSetExOptions) SetConditionalSet(conditionalSet constants.ConditionalSet) HSetExOptions {
	opts.ConditionalSet = conditionalSet
	return opts
}

// SetFieldConditionalSet sets the field-level conditional set option: [constants.HashAllFieldsExist] (FXX) or
// [constants.HashFieldsDoNotExist] (FNX).
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
	// Backward compatibility: field-level conditions were previously passed via ConditionalSet.
	if legacy := constants.HashFieldConditionalSet(keyCondition); legacy == constants.HashAllFieldsExist ||
		legacy == constants.HashFieldsDoNotExist {
		if fieldCondition != "" {
			return nil, errors.New(
				"HSetExOptions: field-level condition set in both ConditionalSet and FieldConditionalSet",
			)
		}
		fieldCondition, keyCondition = legacy, ""
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
	case constants.HashAllFieldsExist, constants.HashFieldsDoNotExist:
		args = append(args, string(fieldCondition))
	default:
		return nil, errors.New("HSetExOptions: invalid field-level condition " + string(fieldCondition))
	}

	// Add expiry options
	if opts.Expiry != nil {
		expiryArgs, err := hsetExExpiryArgs(opts.Expiry)
		if err != nil {
			return nil, err
		}
		args = append(args, expiryArgs...)
	}

	return args, nil
}

// hsetExExpiryArgs converts an [Expiry] to the expiry arguments of the HSETEX command:
// EX, PX, EXAT, PXAT or KEEPTTL. Other expiry types, such as PERSIST, are not supported by HSETEX.
func hsetExExpiryArgs(expiry *Expiry) ([]string, error) {
	switch expiry.Type {
	case constants.Seconds:
		return []string{"EX", utils.IntToString(int64(expiry.Duration))}, nil
	case constants.Milliseconds:
		return []string{"PX", utils.IntToString(int64(expiry.Duration))}, nil
	case constants.UnixSeconds:
		return []string{"EXAT", utils.IntToString(int64(expiry.GetTime()))}, nil
	case constants.UnixMilliseconds:
		return []string{"PXAT", utils.IntToString(int64(expiry.GetTime()))}, nil
	case constants.KeepExisting:
		return []string{"KEEPTTL"}, nil
	default:
		return nil, errors.New("HSetExOptions: invalid expiry type " + string(expiry.Type))
	}
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
