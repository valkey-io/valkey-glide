// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"errors"
	"strconv"
)

// FailoverOptions provides optional arguments for the FAILOVER command.
//
// The FAILOVER command triggers a graceful failover from a primary to one of its replicas.
// It must be sent to a primary node. The primary will coordinate handover to the specified
// or auto-selected replica.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/failover/
type FailoverOptions struct {
	// Host specifies the target replica host. Must be used together with Port.
	Host string

	// Port specifies the target replica port. Must be used together with Host.
	Port int

	// TimeoutMs specifies the failover timeout in milliseconds.
	// If the failover doesn't complete within this time, it is aborted.
	TimeoutMs int64

	// Force forces the failover when the target replica is unreachable.
	// Requires Host and Port to be specified.
	Force bool

	// Abort cancels an ongoing failover operation.
	Abort bool
}

// ToArgs converts FailoverOptions to a command argument slice.
// Returns an error if option combinations are invalid.
func (opts *FailoverOptions) ToArgs() ([]string, error) {
	if opts == nil {
		return []string{}, nil
	}

	if opts.Abort {
		if opts.Host != "" || opts.Port > 0 || opts.Force || opts.TimeoutMs > 0 {
			return nil, errors.New("ABORT cannot be combined with other failover options")
		}
		return []string{"ABORT"}, nil
	}

	if opts.Force && (opts.Host == "" || opts.Port <= 0) {
		return nil, errors.New("FORCE requires both Host and Port to be specified")
	}

	if (opts.Host != "" && opts.Port <= 0) || (opts.Host == "" && opts.Port > 0) {
		return nil, errors.New("both Host and Port must be specified together")
	}

	args := []string{}
	if opts.Host != "" && opts.Port > 0 {
		args = append(args, "TO", opts.Host, strconv.Itoa(opts.Port))
		if opts.Force {
			args = append(args, "FORCE")
		}
	}
	if opts.TimeoutMs > 0 {
		args = append(args, "TIMEOUT", strconv.FormatInt(opts.TimeoutMs, 10))
	}
	return args, nil
}
