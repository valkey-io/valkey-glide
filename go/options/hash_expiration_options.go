// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package options provides configuration options for hash field expiration commands.
// Available in Valkey 9.0 and above.
package options

import (
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// HSetExOptions represents optional arguments for the HSETEX command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/hsetex/
type HSetExOptions struct {
	ConditionalSet constants.ConditionalSet
	Expiry         *Expiry
}

// NewHSetExOptions creates a new HSetExOptions instance.
func NewHSetExOptions() HSetExOptions {
	return HSetExOptions{}
}

// SetConditionalSet sets the conditional set option.
func (opts HSetExOptions) SetConditionalSet(conditionalSet constants.ConditionalSet) HSetExOptions {
	opts.ConditionalSet = conditionalSet
	return opts
}

// SetExpiry sets the expiry options.
func (opts HSetExOptions) SetExpiry(expiry *Expiry) HSetExOptions {
	opts.Expiry = expiry
	return opts
}

// ToArgs converts the options to command arguments.
func (opts *HSetExOptions) ToArgs() ([]string, error) {
	args := []string{}

	// Add conditional set options only if set
	if opts.ConditionalSet != "" {
		conditionStr, err := opts.ConditionalSet.ToString()
		if err != nil {
			return nil, err
		}
		args = append(args, conditionStr)
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
