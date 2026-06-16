// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import "strconv"

// FailoverOptions represents options for the FAILOVER command.
type FailoverOptions struct {
	Host      string
	Port      int
	Force     bool
	Abort     bool
	TimeoutMs int64
}

// NewFailoverOptionsWithAbort creates options to abort an ongoing failover.
func NewFailoverOptionsWithAbort() *FailoverOptions {
	return &FailoverOptions{Abort: true}
}

// NewFailoverOptionsWithTimeout creates options with only a timeout.
func NewFailoverOptionsWithTimeout(timeoutMs int64) *FailoverOptions {
	return &FailoverOptions{TimeoutMs: timeoutMs}
}

// NewFailoverOptionsWithTo creates options to failover to a specific replica.
func NewFailoverOptionsWithTo(host string, port int) *FailoverOptions {
	return &FailoverOptions{Host: host, Port: port}
}

// NewFailoverOptionsWithToAndTimeout creates options to failover to a specific replica with a timeout.
func NewFailoverOptionsWithToAndTimeout(host string, port int, timeoutMs int64) *FailoverOptions {
	return &FailoverOptions{Host: host, Port: port, TimeoutMs: timeoutMs}
}

// NewFailoverOptionsForced creates options to force failover to a specific replica after timeout.
func NewFailoverOptionsForced(host string, port int, timeoutMs int64) *FailoverOptions {
	return &FailoverOptions{Host: host, Port: port, Force: true, TimeoutMs: timeoutMs}
}

// ToArgs converts the options to command arguments.
func (o *FailoverOptions) ToArgs() []string {
	args := []string{}
	if o.Abort {
		args = append(args, "ABORT")
		return args
	}
	if o.Host != "" && o.Port > 0 {
		args = append(args, "TO", o.Host, strconv.Itoa(o.Port))
		if o.Force {
			args = append(args, "FORCE")
		}
	}
	if o.TimeoutMs > 0 {
		args = append(args, "TIMEOUT", strconv.FormatInt(o.TimeoutMs, 10))
	}
	return args
}
