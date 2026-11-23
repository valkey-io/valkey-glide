// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"
)

// DelexOptions represents arguments for the [api.StringCommands.DelEx] command.
//
// Not supported when compression is enabled. See [Valkey GLIDE – Data Compression].
//
// See [valkey.io]
//
// [valkey.io]: https://valkey.io/commands/delex/
// [Valkey GLIDE – Data Compression]: https://glide.valkey.io/concepts/client-features/compression/#incompatible-commands
type DelexOptions struct {
	// The condition that the key must meet to be deleted.
	condition deleteCondition

	// Value to compare against the key.
	value string
}

// A deleteCondition defines the condition that the key must meet to be deleted.
type deleteCondition string

const (
	onlyIfEquals    deleteCondition = "IFEQ"
	onlyIfNotEquals deleteCondition = "IFNE"
)

// NewDelexOptionsOnlyIfEquals creates options to only delete the key if its value equals `value`.
// Equivalent to "IFEQ value" in the Valkey API.
func NewDelexOptionsOnlyIfEquals(value string) DelexOptions {
	return DelexOptions{condition: onlyIfEquals, value: value}
}

// NewDelexOptionsOnlyIfNotEquals creates options to only delete the key if its value does not equal `value`.
// Equivalent to "IFNE value" in the Valkey API.
func NewDelexOptionsOnlyIfNotEquals(value string) DelexOptions {
	return DelexOptions{condition: onlyIfNotEquals, value: value}
}

// ToArgs converts the options to command arguments.
// Returns an error if the options were not created with [NewDelexOptionsOnlyIfEquals] or
// [NewDelexOptionsOnlyIfNotEquals].
func (opts DelexOptions) ToArgs() ([]string, error) {
	if opts.condition == "" {
		return nil, errors.New(
			"DelexOptions: a condition is required: use NewDelexOptionsOnlyIfEquals or NewDelexOptionsOnlyIfNotEquals",
		)
	}
	return []string{string(opts.condition), opts.value}, nil
}
